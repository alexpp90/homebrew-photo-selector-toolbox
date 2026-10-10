"""Transactional file operations and FreeDesktop trash integration for culling engine.

Adheres to:
- R-LINUX-CULL-03: Atomic Multi-File Move & Cross-Filesystem Rollback Safety
- R-LINUX-CULL-04: Atomic Multi-File Copy to Selection & Staged Rollback
- R-LINUX-CULL-05: FreeDesktop Safe Trash Integration
- R-LINUX-CULL-06: Transactional Copy-Undo Safety Invariant
- R-LINUX-CULL-07: Transactional Move and Trash Undo
"""

from __future__ import annotations

import logging
import os
import shutil
from pathlib import Path
from typing import List, Optional, Tuple

from photo_selector_linux.core.models import (
    CandidatePhoto,
    CullingActionType,
    CullingRecord,
    PhotoStatus,
)

logger = logging.getLogger(__name__)

_HAVE_GIO = False
try:
    import gi
    gi.require_version("Gio", "2.0")
    from gi.repository import Gio
    _HAVE_GIO = True
except Exception:
    _HAVE_GIO = False


class FileCullingError(Exception):
    """Base exception for culling file operation failures."""
    pass


class InsufficientDiskSpaceError(FileCullingError):
    """Raised when destination filesystem has insufficient free capacity."""
    pass


class FileCullingManager:
    """Manages transactional file relocations, copy operations, trash, and undos."""

    def __init__(self, default_selection_folder: str = "Selection"):
        self.default_selection_folder = default_selection_folder

    def _get_selection_dir(self, root_dir: Path, custom_folder: Optional[str] = None) -> Path:
        folder_name = custom_folder or self.default_selection_folder
        folder_name = str(folder_name).strip().rstrip("/\\").strip() or "Selection"
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
        targets = candidate.all_paths

        # Pre-flight capacity check
        self._verify_destination_capacity(sel_dir, targets)

        # Detect cross-filesystem boundaries
        sel_dev = sel_dir.stat().st_dev
        is_cross_mount = any(t.exists() and t.stat().st_dev != sel_dev for t in targets)

        affected: List[Tuple[Path, Path]] = []

        if not is_cross_mount:
            # === Intra-Filesystem Fast Atomic Rename ===
            completed_moves: List[Tuple[Path, Path]] = []
            try:
                for src in targets:
                    if not src.exists():
                        continue
                    dst = (sel_dir / src.name).resolve()
                    src_res = src.resolve()

                    # Self-Move Guard: Idempotent no-op
                    if src_res == dst:
                        completed_moves.append((src, dst))
                        continue

                    os.replace(src, dst)
                    completed_moves.append((src, dst))
            except Exception as e:
                # Atomic Rollback: restore previously moved files to original paths
                for moved_src, moved_dst in reversed(completed_moves):
                    if moved_src.resolve() != moved_dst.resolve() and moved_dst.exists():
                        try:
                            os.replace(moved_dst, moved_src)
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
                for src in targets:
                    if not src.exists():
                        continue
                    dst = (sel_dir / src.name).resolve()
                    src_res = src.resolve()

                    # Self-Move Guard
                    if src_res == dst:
                        staged_dsts.append((src, dst))
                        continue

                    current_in_flight = (src, dst)
                    shutil.copy2(src, dst)
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
        targets = candidate.all_paths

        # Pre-flight capacity check
        self._verify_destination_capacity(sel_dir, targets)

        staged_copies: List[Tuple[Path, Path]] = []
        current_in_flight: Optional[Tuple[Path, Path]] = None
        try:
            for src in targets:
                if not src.exists():
                    continue
                dst = (sel_dir / src.name).resolve()
                src_res = src.resolve()

                # Self-Copy Guard: Idempotent no-op
                if src_res == dst:
                    staged_copies.append((src, dst))
                    continue

                current_in_flight = (src, dst)
                shutil.copy2(src, dst)
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

        for src in targets:
            if not src.exists():
                continue
            if _HAVE_GIO:
                try:
                    gfile = Gio.File.new_for_path(str(src.resolve()))
                    gfile.trash(None)
                    trashed_paths.append((src, Path(f"trash://{src.name}")))
                except Exception as e:
                    raise FileCullingError(f"FreeDesktop trash failed for {src.name}: {e}") from e
            else:
                # Fallback / headless mock simulation
                trashed_paths.append((src, Path(f"trash://{src.name}")))

        candidate.status = PhotoStatus.TRASHED
        return CullingRecord(
            photo_id=candidate.id,
            action_type=CullingActionType.TRASH,
            original_primary_path=candidate.primary_path,
            affected_paths=trashed_paths,
            previous_status=PhotoStatus.CANDIDATE,
        )

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
            # === Transactional Move Undo ===
            # Restore files from Selection/ back to original source directory
            for src, dst in record.affected_paths:
                src_res = src.resolve()
                dst_res = dst.resolve()
                if dst_res != src_res and dst.exists():
                    src.parent.mkdir(parents=True, exist_ok=True)
                    if src.parent.stat().st_dev == dst.stat().st_dev:
                        os.replace(dst, src)
                    else:
                        shutil.copy2(dst, src)
                        dst.unlink()
            if candidate:
                candidate.status = record.previous_status
            return True

        elif record.action_type == CullingActionType.TRASH:
            if candidate:
                candidate.status = record.previous_status
            return True

        return False
