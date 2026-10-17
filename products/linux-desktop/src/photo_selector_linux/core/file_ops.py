"""Transactional file operations and FreeDesktop trash integration for culling engine.

Adheres to:
- R-LINUX-CULL-03: Atomic Multi-File Move & Cross-Filesystem Rollback Safety
- R-LINUX-CULL-04: Atomic Multi-File Copy to Selection & Staged Rollback
- R-LINUX-CULL-05: FreeDesktop Safe Trash Integration
- R-LINUX-CULL-06: Transactional Copy-Undo Safety Invariant
- R-LINUX-CULL-07: Transactional Move and Trash Undo
"""

from __future__ import annotations

from datetime import datetime, timezone
import errno
import logging
import os
import shutil
import threading
from pathlib import Path
from typing import Dict, List, Optional, Tuple
from urllib.parse import quote, unquote

from photo_selector_linux.core.gvfs_names import (
    DisplayNameResolver,
    default_resolver,
    is_gvfs_fuse_path,
)
from photo_selector_linux.core.models import (
    CandidatePhoto,
    CullingActionType,
    CullingRecord,
    PhotoStatus,
)

logger = logging.getLogger(__name__)

_RENAME_LOCK = threading.Lock()

_HAVE_GIO = False
try:
    import gi
    gi.require_version("Gio", "2.0")
    from gi.repository import Gio
    _HAVE_GIO = True
except Exception:
    _HAVE_GIO = False


def _apply_bundle_suffix(name: str, base_stem: str, suffix_num: int) -> str:
    """Insert ``-{suffix_num}`` into ``name`` at the bundle's primary stem boundary.

    Preserves companion pairing across primary/twin files (``IMG_0001-2.CR3``, ``IMG_0001-2.JPG``),
    file- and stem-level sidecars (``IMG_0001-2.CR3.xmp``, ``IMG_0001-2.xmp``), and Lightroom
    derivatives (``IMG_0001-2-Edit.tif``, ``IMG_0001-2_Edit.tif``, ``IMG_0001-2-Enhanced-NR.dng``).
    """
    if suffix_num < 2:
        return name

    if base_stem and name[: len(base_stem)].lower() == base_stem.lower():
        remainder = name[len(base_stem):]
        if not remainder or remainder[0] in (".", "-", "_"):
            prefix = name[: len(base_stem)]
            return f"{prefix}-{suffix_num}{remainder}"

    lower = name.lower()
    if lower.endswith(".xmp"):
        inner = name[:-4]
        outer_ext = name[-4:]
        inner_path = Path(inner)
        if inner_path.suffix:
            return f"{inner_path.stem}-{suffix_num}{inner_path.suffix}{outer_ext}"
        return f"{inner}-{suffix_num}{outer_ext}"

    p = Path(name)
    stem = p.stem
    ext = p.suffix
    stem_lower = stem.lower()
    for marker in ("-edit", "_edit", "-enhanced-nr"):
        idx = stem_lower.find(marker)
        if idx > 0:
            return f"{stem[:idx]}-{suffix_num}{stem[idx:]}{ext}"

    return f"{stem}-{suffix_num}{ext}"


def _resolve_bundle_destinations(
    candidate: CandidatePhoto,
    dest_dir: Path,
    name_resolver: Optional[DisplayNameResolver] = None,
) -> List[Tuple[Path, Path]]:
    """Resolve collision-free ``(src, dst)`` pairs for all existing files in ``candidate``.

    Checks both on-disk paths in ``dest_dir`` and GVFS display names reported by ``name_resolver``.
    If any member collides with an existing destination file (and is not a self-move/copy), the
    entire bundle receives the lowest integer suffix ``-N`` (``N >= 2``) for which every member
    is collision-free.
    """
    existing_sources = [src for src in candidate.all_paths if src.exists()]
    if not existing_sources:
        return []

    base_stem = candidate.stem
    display_map = name_resolver.names_in(dest_dir) if name_resolver is not None else {}
    existing_display_names = set(display_map.values())
    display_to_ondisk = {
        display: (dest_dir / on_disk).resolve()
        for on_disk, display in display_map.items()
    }

    def _is_occupied(src: Path, target_name: str, allow_self: bool) -> bool:
        dst = (dest_dir / target_name).resolve()
        src_res = src.resolve()
        if allow_self and src_res == dst:
            return False
        if allow_self and target_name in display_to_ondisk and display_to_ondisk[target_name] == src_res:
            return False
        if dst.exists() or dst.is_symlink():
            return True
        if target_name in existing_display_names:
            return True
        return False

    has_collision = any(
        _is_occupied(src, candidate.name_of(src), allow_self=True)
        for src in existing_sources
    )

    if not has_collision:
        return [
            (src, (dest_dir / candidate.name_of(src)).resolve())
            for src in existing_sources
        ]

    suffix_num = 2
    while True:
        suffixed_pairs = [
            (src, _apply_bundle_suffix(candidate.name_of(src), base_stem, suffix_num))
            for src in existing_sources
        ]
        if not any(
            _is_occupied(src, suffixed_name, allow_self=False)
            for src, suffixed_name in suffixed_pairs
        ):
            return [
                (src, (dest_dir / suffixed_name).resolve())
                for src, suffixed_name in suffixed_pairs
            ]
        suffix_num += 1


def _copy_file(src: Path, dst: Path, fsync: bool = False) -> None:
    """Copy content exclusively (never overwriting an existing file), then permissions and timestamps.

    Opens ``dst`` with ``O_WRONLY | O_CREAT | O_EXCL`` so an existing file is never truncated.
    When ``fsync=True``, flushes file data to disk before closing. GVFS cloud mounts (Google Drive)
    reject ``utime``/``chmod`` with ENOTSUP, which is ignored so copies and cross-mount moves succeed.
    """
    flags = os.O_WRONLY | os.O_CREAT | os.O_EXCL
    fd = os.open(dst, flags, 0o666)
    try:
        try:
            fobj = os.fdopen(fd, "wb")
        except BaseException:
            os.close(fd)
            raise
        with fobj as fdst:
            with open(src, "rb") as fsrc:
                shutil.copyfileobj(fsrc, fdst, length=1024 * 1024)
            fdst.flush()
            if fsync:
                try:
                    os.fsync(fdst.fileno())
                except OSError as e:
                    if e.errno not in (errno.EINVAL, errno.ENOTSUP, errno.EOPNOTSUPP):
                        raise
    except BaseException:
        if dst.exists() or dst.is_symlink():
            try:
                dst.unlink()
            except OSError:
                pass
        raise
    try:
        shutil.copystat(src, dst)
    except OSError as e:
        logger.debug("Metadata not preserved for %s: %s", dst, e)


def _move_no_overwrite(src: Path, dst: Path) -> None:
    """Atomically move ``src`` to ``dst`` on the same filesystem without ever overwriting ``dst``.

    Uses ``os.link`` + ``os.unlink`` on POSIX filesystems that support hardlinks (fails atomically
    with ``FileExistsError`` if ``dst`` exists), falling back to an existence check + ``os.rename``
    under ``_RENAME_LOCK`` for filesystems where hardlinks are unsupported (FAT/exFAT/GVFS) or symlinks.
    """
    with _RENAME_LOCK:
        if dst.exists() or dst.is_symlink():
            raise FileExistsError(errno.EEXIST, os.strerror(errno.EEXIST), str(dst))
        if not src.is_symlink():
            try:
                os.link(src, dst)
            except FileExistsError:
                raise
            except OSError:
                pass
            else:
                try:
                    os.unlink(src)
                except BaseException:
                    try:
                        os.unlink(dst)
                    except OSError:
                        pass
                    raise
                return
        if dst.exists() or dst.is_symlink():
            raise FileExistsError(errno.EEXIST, os.strerror(errno.EEXIST), str(dst))
        os.rename(src, dst)


class FileCullingError(Exception):
    """Base exception for culling file operation failures."""
    pass


class InsufficientDiskSpaceError(FileCullingError):
    """Raised when destination filesystem has insufficient free capacity."""
    pass


class FileCullingManager:
    """Manages transactional file relocations, copy operations, trash, and undos."""

    def __init__(
        self,
        default_selection_folder: str = "Selection",
        trash_dir: Optional[Path] = None,
        name_resolver: Optional[DisplayNameResolver] = None,
    ):
        self.default_selection_folder = default_selection_folder
        self._trash_dir = trash_dir
        self.name_resolver: DisplayNameResolver = name_resolver or default_resolver()

    def _resolve_bundle_destinations(
        self,
        candidate: CandidatePhoto,
        dest_dir: Path,
    ) -> List[Tuple[Path, Path]]:
        """Resolve collision-free ``(src, dst)`` pairs in ``dest_dir`` for ``candidate``."""
        return _resolve_bundle_destinations(candidate, dest_dir, self.name_resolver)

    def _get_trash_dir(self) -> Path:
        """Return the FreeDesktop.org Trash base directory ($XDG_DATA_HOME/Trash)."""
        if self._trash_dir is not None:
            return self._trash_dir
        xdg_data = os.environ.get("XDG_DATA_HOME")
        if xdg_data:
            return Path(xdg_data) / "Trash"
        return Path.home() / ".local" / "share" / "Trash"

    def _trash_file_freedesktop(self, src: Path, name: Optional[str] = None) -> Path:
        """Relocate a file into the FreeDesktop.org Trash specification layout (files/ + info/).

        ``name`` is the user-facing filename when it differs from the on-disk one (GVFS cloud
        mounts); the trash entry and its recorded original path use it.
        """
        trash_base = self._get_trash_dir()
        files_dir = trash_base / "files"
        info_dir = trash_base / "info"
        files_dir.mkdir(parents=True, exist_ok=True)
        info_dir.mkdir(parents=True, exist_ok=True)

        src_resolved = src.resolve()
        name = name or src.name
        candidate_name = name
        counter = 1
        while (files_dir / candidate_name).exists() or (info_dir / f"{candidate_name}.trashinfo").exists():
            counter += 1
            candidate_name = f"{Path(name).stem}.{counter}{Path(name).suffix}"

        dst_file = files_dir / candidate_name
        info_file = info_dir / f"{candidate_name}.trashinfo"
        deletion_date = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%S")
        encoded_path = quote(str(src_resolved.parent / name), safe="/")
        info_content = f"[Trash Info]\nPath={encoded_path}\nDeletionDate={deletion_date}\n"
        info_file.write_text(info_content, encoding="utf-8")

        try:
            if src_resolved.parent.stat().st_dev == files_dir.stat().st_dev:
                _move_no_overwrite(src_resolved, dst_file)
            else:
                _copy_file(src_resolved, dst_file, fsync=True)
                src_resolved.unlink()
        except Exception:
            if info_file.exists():
                try:
                    info_file.unlink()
                except Exception:
                    pass
            raise

        return dst_file

    def _find_trashed_file(self, original_src: Path) -> Optional[Path]:
        """Locate a trashed file in FreeDesktop.org Trash matching original_src."""
        trash_base = self._get_trash_dir()
        files_dir = trash_base / "files"
        info_dir = trash_base / "info"
        if not files_dir.is_dir():
            return None

        target_resolved = str(original_src.resolve())
        if info_dir.is_dir():
            matches: List[Tuple[float, Path]] = []
            for info_file in info_dir.glob("*.trashinfo"):
                try:
                    raw = info_file.read_text(encoding="utf-8", errors="replace")
                    for line in raw.splitlines():
                        if line.startswith("Path="):
                            recorded_path = unquote(line.split("=", 1)[1].strip())
                            if (
                                recorded_path == target_resolved
                                or Path(recorded_path).resolve() == Path(target_resolved)
                            ):
                                trashed_name = info_file.name[:-len(".trashinfo")]
                                payload = files_dir / trashed_name
                                if payload.exists():
                                    matches.append((info_file.stat().st_mtime, payload))
                except Exception:
                    continue
            if matches:
                matches.sort(key=lambda item: item[0], reverse=True)
                return matches[0][1]

        direct = files_dir / original_src.name
        if direct.exists():
            return direct
        return None

    def _restore_from_trash(
        self,
        src: Path,
        dst: Path,
        restore_target: Optional[Path] = None,
    ) -> None:
        """Restore a single file from FreeDesktop.org Trash back to ``restore_target`` (or ``src``)."""
        trash_file: Optional[Path] = None
        if not str(dst).startswith("trash://") and dst.exists():
            trash_file = dst
        else:
            trash_file = self._find_trashed_file(src)

        target = restore_target if restore_target is not None else src
        if trash_file is not None and trash_file.exists():
            target.parent.mkdir(parents=True, exist_ok=True)
            if target.parent.stat().st_dev == trash_file.stat().st_dev:
                _move_no_overwrite(trash_file, target)
            else:
                _copy_file(trash_file, target, fsync=True)
                trash_file.unlink()

            info_file = trash_file.parent.parent / "info" / f"{trash_file.name}.trashinfo"
            if info_file.exists():
                try:
                    info_file.unlink()
                except Exception:
                    pass

    def _relink_restored(self, candidate: CandidatePhoto) -> None:
        """Point the candidate at its restored files.

        A file written back to a GVFS cloud mount gets a new opaque ID, so the candidate's old
        on-disk paths are gone; find each file again by its user-facing name.
        """
        stale = [p for p in candidate.all_paths if not p.exists()]
        if not stale:
            return
        by_name = {}
        for parent in {p.parent for p in stale}:
            for on_disk, display in self.name_resolver.names_in(parent).items():
                by_name[(parent, display)] = parent / on_disk
        relinked = {}
        for old in stale:
            name = candidate.name_of(old)
            relinked[old] = by_name.get((old.parent, name), old.parent / name)
        candidate.primary_path = relinked.get(candidate.primary_path, candidate.primary_path)
        candidate.companion_paths = [relinked.get(p, p) for p in candidate.companion_paths]
        for old, new in relinked.items():
            name = candidate.display_names.pop(old, old.name)
            if new.name != name:
                candidate.display_names[new] = name

    def _get_selection_dir(self, root_dir: Path, custom_folder: Optional[str] = None) -> Path:
        folder_name = custom_folder or self.default_selection_folder
        folder_name = str(folder_name).strip().rstrip("/\\").strip() or "Selection"
        # On GVFS cloud mounts an existing folder is listed under an opaque ID, not its name:
        # reuse it instead of creating a duplicate "Selection" on every cull.
        for on_disk, display in self.name_resolver.names_in(root_dir).items():
            if display == folder_name and (root_dir / on_disk).is_dir():
                return (root_dir / on_disk).resolve()
        sel_dir = (root_dir / folder_name).resolve()
        sel_dir.mkdir(parents=True, exist_ok=True)
        return sel_dir

    def _verify_destination_capacity(self, sel_dir: Path, files: List[Path]) -> None:
        """Pre-flight check: ensure destination filesystem has enough space (R-LINUX-CULL-03)."""
        total_bundle_size = sum(f.stat().st_size for f in files if f.exists())
        try:
            st = os.statvfs(sel_dir)
            available_bytes = st.f_bavail * st.f_frsize
            if available_bytes < total_bundle_size:
                raise InsufficientDiskSpaceError(
                    f"Insufficient free space at {sel_dir}: "
                    f"required {total_bundle_size} bytes, available {available_bytes} bytes."
                )
        except OSError as e:
            if isinstance(e, InsufficientDiskSpaceError):
                raise
            # If statvfs is unsupported on host, proceed

    def move_candidate(
        self,
        candidate: CandidatePhoto,
        root_dir: Path,
        selection_folder: Optional[str] = None,
    ) -> CullingRecord:
        """Atomically move candidate photo and all bound companions to Selection/ (R-LINUX-CULL-03)."""
        sel_dir = self._get_selection_dir(root_dir, selection_folder)
        planned_moves = self._resolve_bundle_destinations(candidate, sel_dir)
        targets = [src for src, _ in planned_moves]

        # Pre-flight capacity check
        self._verify_destination_capacity(sel_dir, targets)

        # Detect cross-filesystem boundaries
        sel_dev = sel_dir.stat().st_dev
        is_cross_mount = any(t.exists() and t.stat().st_dev != sel_dev for t in targets)

        affected: List[Tuple[Path, Path]] = []

        if not is_cross_mount:
            # === Intra-Filesystem Collision-Free Atomic Move ===
            completed_moves: List[Tuple[Path, Path]] = []
            try:
                for src, dst in planned_moves:
                    src_res = src.resolve()

                    # Self-Move Guard: Idempotent no-op
                    if src_res == dst:
                        completed_moves.append((src, dst))
                        continue

                    _move_no_overwrite(src, dst)
                    completed_moves.append((src, dst))
            except Exception as e:
                # Atomic Rollback: restore previously moved files to original paths
                for moved_src, moved_dst in reversed(completed_moves):
                    if moved_src.resolve() != moved_dst.resolve() and moved_dst.exists():
                        try:
                            _move_no_overwrite(moved_dst, moved_src)
                        except Exception:
                            pass
                raise FileCullingError(f"Intra-filesystem move failed, rolled back: {e}") from e

            affected = completed_moves
        else:
            # === Cross-Filesystem Two-Phase Staged Transaction ===
            staged_dsts: List[Tuple[Path, Path]] = []
            current_in_flight: Optional[Tuple[Path, Path]] = None

            # --- Phase 1: Staging (replicate all companions to destination) ---
            try:
                for src, dst in planned_moves:
                    src_res = src.resolve()

                    # Self-Move Guard
                    if src_res == dst:
                        staged_dsts.append((src, dst))
                        continue

                    current_in_flight = (src, dst)
                    _copy_file(src, dst, fsync=True)
                    # Verify integrity
                    if dst.stat().st_size != src.stat().st_size:
                        raise IOError(f"Byte mismatch during cross-mount staging for {src.name}")
                    staged_dsts.append((src, dst))
                    current_in_flight = None
            except Exception as e:
                # Rollback Phase 1: Clean up in-flight partial/corrupt destination replica
                if current_in_flight is not None:
                    c_src, c_dst = current_in_flight
                    if c_src.resolve() != c_dst.resolve() and c_dst.exists():
                        try:
                            c_dst.unlink()
                        except Exception:
                            pass
                # Clean up all previously staged destination replicas, leaving sources untouched
                for src, dst in staged_dsts:
                    if src.resolve() != dst.resolve() and dst.exists():
                        try:
                            dst.unlink()
                        except Exception:
                            pass
                raise FileCullingError(f"Cross-filesystem move failed during staging, aborted safely: {e}") from e

            # --- Phase 2: Commit (unlink sources only after ALL destination replicas are verified) ---
            # Invariant: Once Phase 2 begins, destination replicas must NEVER be deleted.
            failed_unlinks: List[Tuple[Path, Exception]] = []
            for src, dst in staged_dsts:
                if src.resolve() != dst.resolve() and src.exists():
                    try:
                        src.unlink()
                    except Exception as unlink_err:
                        failed_unlinks.append((src, unlink_err))

            if failed_unlinks:
                failed_names = ", ".join(f"{s.name} ({err})" for s, err in failed_unlinks)
                logger.error(
                    "Cross-filesystem move commit partially succeeded: destination replicas preserved "
                    "in %s, but source file(s) could not be removed: %s",
                    sel_dir,
                    failed_names,
                )
                raise FileCullingError(
                    f"Cross-filesystem move commit partially succeeded: destination replicas preserved, "
                    f"but source files could not be removed: {failed_names}"
                )

            affected = staged_dsts

        candidate.status = PhotoStatus.SELECTED
        return CullingRecord(
            photo_id=candidate.id,
            action_type=CullingActionType.MOVE,
            original_primary_path=candidate.primary_path,
            affected_paths=affected,
            previous_status=PhotoStatus.CANDIDATE,
        )

    def copy_candidate(
        self,
        candidate: CandidatePhoto,
        root_dir: Path,
        selection_folder: Optional[str] = None,
    ) -> CullingRecord:
        """Atomically copy candidate photo and all bound companions to Selection/ (R-LINUX-CULL-04)."""
        sel_dir = self._get_selection_dir(root_dir, selection_folder)
        planned_copies = self._resolve_bundle_destinations(candidate, sel_dir)
        targets = [src for src, _ in planned_copies]

        # Pre-flight capacity check
        self._verify_destination_capacity(sel_dir, targets)

        staged_copies: List[Tuple[Path, Path]] = []
        current_in_flight: Optional[Tuple[Path, Path]] = None
        try:
            for src, dst in planned_copies:
                src_res = src.resolve()

                # Self-Copy Guard: Idempotent no-op
                if src_res == dst:
                    staged_copies.append((src, dst))
                    continue

                current_in_flight = (src, dst)
                _copy_file(src, dst)
                if dst.stat().st_size != src.stat().st_size:
                    raise IOError(f"Byte mismatch copying {src.name}")
                staged_copies.append((src, dst))
                current_in_flight = None
        except Exception as e:
            # Clean up in-flight partial/corrupt destination replica
            if current_in_flight is not None:
                c_src, c_dst = current_in_flight
                if c_src.resolve() != c_dst.resolve() and c_dst.exists():
                    try:
                        c_dst.unlink()
                    except Exception:
                        pass
            # Clean up all previously staged destination copies on failure, never touching originals
            for src, dst in staged_copies:
                if src.resolve() != dst.resolve() and dst.exists():
                    try:
                        dst.unlink()
                    except Exception:
                        pass
            raise FileCullingError(f"Atomic copy failed mid-flight, rolled back: {e}") from e

        candidate.status = PhotoStatus.COPIED
        return CullingRecord(
            photo_id=candidate.id,
            action_type=CullingActionType.COPY,
            original_primary_path=candidate.primary_path,
            affected_paths=staged_copies,
            previous_status=PhotoStatus.CANDIDATE,
        )

    def trash_candidate(self, candidate: CandidatePhoto) -> CullingRecord:
        """Safely send candidate and companions to FreeDesktop Trash (R-LINUX-CULL-05)."""
        targets = candidate.all_paths
        trashed_paths: List[Tuple[Path, Path]] = []

        try:
            for src in targets:
                if not src.exists():
                    continue
                if is_gvfs_fuse_path(src):
                    # GIO cannot trash through the FUSE mount, and the cloud's own trash is out of
                    # undo's reach: keep the file in the local Trash under its real name instead.
                    name = candidate.name_of(src)
                    dst_trash = self._trash_file_freedesktop(src, name)
                    trashed_paths.append((src.resolve().parent / name, dst_trash))
                elif _HAVE_GIO and self._trash_dir is None and "XDG_DATA_HOME" not in os.environ:
                    gfile = Gio.File.new_for_path(str(src.resolve()))
                    gfile.trash(None)
                    located = self._find_trashed_file(src)
                    trashed_paths.append((src, located if located is not None else Path(f"trash://{src.name}")))
                else:
                    dst_trash = self._trash_file_freedesktop(src)
                    trashed_paths.append((src, dst_trash))
        except Exception as e:
            for orig_src, trash_dst in reversed(trashed_paths):
                try:
                    self._restore_from_trash(orig_src, trash_dst)
                except Exception:
                    pass
            raise FileCullingError(f"FreeDesktop trash failed, rolled back: {e}") from e

        candidate.status = PhotoStatus.TRASHED
        return CullingRecord(
            photo_id=candidate.id,
            action_type=CullingActionType.TRASH,
            original_primary_path=candidate.primary_path,
            affected_paths=trashed_paths,
            previous_status=PhotoStatus.CANDIDATE,
        )

    def _resolve_undo_targets(
        self,
        record: CullingRecord,
        candidate: Optional[CandidatePhoto] = None,
    ) -> List[Tuple[Path, Path, Path]]:
        """Resolve collision-free ``(orig_src, restore_target, current_dst)`` triples for undo.

        If any original source path is now occupied by a file created after the operation, the
        entire restored bundle is assigned a shared collision-free ``-N`` suffix (``N >= 2``)
        in ``orig_src.parent`` so existing user files are never overwritten or deleted.
        """
        if not record.affected_paths:
            return []

        primary_name = (
            candidate.name_of(record.original_primary_path)
            if candidate is not None
            else record.original_primary_path.name
        )
        base_stem = Path(primary_name).stem

        display_by_parent: Dict[Path, Dict[str, str]] = {}

        def _display_names_in(parent: Path) -> Dict[str, str]:
            if parent not in display_by_parent:
                display_by_parent[parent] = self.name_resolver.names_in(parent)
            return display_by_parent[parent]

        def _orig_name(orig_src: Path) -> str:
            if candidate is not None:
                return candidate.name_of(orig_src)
            return orig_src.name

        def _is_self_move(orig_src: Path, current_dst: Path) -> bool:
            if record.action_type != CullingActionType.MOVE:
                return False
            if str(current_dst).startswith("trash://"):
                return False
            try:
                return orig_src.resolve() == current_dst.resolve()
            except OSError:
                return False

        def _target_occupied(parent: Path, name: str, current_dst: Path, allow_self: bool) -> bool:
            target = parent / name
            if allow_self and _is_self_move(target, current_dst):
                return False
            if target.exists() or target.is_symlink():
                return True
            if name in _display_names_in(parent).values():
                return True
            return False

        has_collision = any(
            not _is_self_move(orig_src, current_dst)
            and (
                orig_src.exists()
                or orig_src.is_symlink()
                or _target_occupied(orig_src.parent, _orig_name(orig_src), current_dst, allow_self=True)
            )
            for orig_src, current_dst in record.affected_paths
        )

        if not has_collision:
            return [
                (orig_src, orig_src, current_dst)
                for orig_src, current_dst in record.affected_paths
            ]

        suffix_num = 2
        while True:
            candidate_triples: List[Tuple[Path, Path, Path]] = []
            occupied = False
            for orig_src, current_dst in record.affected_paths:
                if _is_self_move(orig_src, current_dst):
                    candidate_triples.append((orig_src, orig_src, current_dst))
                    continue
                suffixed_name = _apply_bundle_suffix(_orig_name(orig_src), base_stem, suffix_num)
                if _target_occupied(orig_src.parent, suffixed_name, current_dst, allow_self=False):
                    occupied = True
                    break
                candidate_triples.append((orig_src, orig_src.parent / suffixed_name, current_dst))
            if not occupied:
                return candidate_triples
            suffix_num += 1

    def _update_candidate_restored_paths(
        self,
        candidate: CandidatePhoto,
        restored_pairs: List[Tuple[Path, Path]],
    ) -> None:
        """Update ``candidate`` paths when undo restores a bundle under a collision-free suffix."""
        path_map: Dict[Path, Path] = {}
        resolved_map: Dict[Path, Path] = {}
        for orig_src, restore_target in restored_pairs:
            path_map[orig_src] = restore_target
            try:
                resolved_map[orig_src.resolve()] = restore_target
            except OSError:
                pass

        def _map_path(p: Path) -> Path:
            if p in path_map:
                return path_map[p]
            try:
                res = p.resolve()
                if res in resolved_map:
                    return resolved_map[res]
            except OSError:
                pass
            return p

        old_primary = candidate.primary_path
        new_primary = _map_path(old_primary)
        candidate.primary_path = new_primary
        candidate.companion_paths = [_map_path(p) for p in candidate.companion_paths]

        for orig_src, restore_target in restored_pairs:
            if orig_src in candidate.display_names and restore_target != orig_src:
                candidate.display_names.pop(orig_src, None)
                candidate.display_names[restore_target] = restore_target.name

    def undo_culling(self, record: CullingRecord, candidate: Optional[CandidatePhoto] = None) -> bool:
        """Atomically reverse culling operation with absolute source preservation (R-LINUX-CULL-06/07)."""
        if record.action_type == CullingActionType.COPY:
            # === Transactional Copy Undo ===
            # Strict Invariant: Delete ONLY the destination copy in Selection/.
            # NEVER modify, touch, or delete the original source media on disk!
            for src, dst in record.affected_paths:
                src_res = src.resolve()
                dst_res = dst.resolve()
                if dst_res != src_res and dst.exists():
                    try:
                        dst.unlink()
                    except Exception as e:
                        raise FileCullingError(f"Failed to delete destination copy during undo: {e}") from e
            if candidate:
                candidate.status = record.previous_status
            return True

        elif record.action_type == CullingActionType.MOVE:
            # === Transactional Move Undo (R-LINUX-CULL-07) ===
            # Restore files from Selection/ back to original source directory (with collision suffix if occupied)
            undo_targets = self._resolve_undo_targets(record, candidate)
            restored_pairs: List[Tuple[Path, Path]] = []
            completed_moves: List[Tuple[Path, Path]] = []
            try:
                for orig_src, restore_target, dst in undo_targets:
                    target_res = restore_target.resolve()
                    dst_res = dst.resolve()
                    if dst_res != target_res and dst.exists():
                        restore_target.parent.mkdir(parents=True, exist_ok=True)
                        if restore_target.parent.stat().st_dev == dst.stat().st_dev:
                            _move_no_overwrite(dst, restore_target)
                        else:
                            _copy_file(dst, restore_target, fsync=True)
                            if restore_target.stat().st_size != dst.stat().st_size:
                                raise IOError(f"Byte mismatch restoring {dst.name}")
                            dst.unlink()
                        completed_moves.append((restore_target, dst))
                    restored_pairs.append((orig_src, restore_target))
            except Exception as e:
                for moved_target, orig_dst in reversed(completed_moves):
                    if moved_target.exists() and not orig_dst.exists():
                        try:
                            if orig_dst.parent.stat().st_dev == moved_target.stat().st_dev:
                                _move_no_overwrite(moved_target, orig_dst)
                            else:
                                _copy_file(moved_target, orig_dst, fsync=True)
                                moved_target.unlink()
                        except Exception:
                            pass
                raise FileCullingError(f"Failed to restore moved file during undo: {e}") from e

            if candidate:
                self._update_candidate_restored_paths(candidate, restored_pairs)
                self._relink_restored(candidate)
                candidate.status = record.previous_status
            return True

        elif record.action_type == CullingActionType.TRASH:
            # === Transactional Trash Undo (R-LINUX-CULL-07) ===
            # Restore trashed primary image and all companions from FreeDesktop Trash
            undo_targets = self._resolve_undo_targets(record, candidate)
            restored_pairs = []
            for orig_src, restore_target, dst in undo_targets:
                try:
                    self._restore_from_trash(orig_src, dst, restore_target=restore_target)
                    restored_pairs.append((orig_src, restore_target))
                except Exception as e:
                    raise FileCullingError(f"Failed to restore trashed file {orig_src.name} during undo: {e}") from e
            if candidate:
                self._update_candidate_restored_paths(candidate, restored_pairs)
                self._relink_restored(candidate)
                candidate.status = record.previous_status
            return True

        return False
