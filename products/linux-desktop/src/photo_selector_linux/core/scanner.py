"""Recursive progressive directory scanner with companion binding and dynamic exclusion.

Adheres to:
- R-LINUX-META-01: Supported File Formats
- R-LINUX-META-04: Companion RAW+JPEG, Sidecar & Edit Binding
- R-LINUX-META-05: Dynamic Selection Subfolder Exclusion Invariant
- R-LINUX-CULL-01: Progressive Asynchronous Streaming Ingestion
- R-LINUX-CULL-02: Natural Alphanumeric Sorting
"""

from __future__ import annotations

import os
import re
from pathlib import Path
from typing import Generator, Iterable, List, Optional, Set, Tuple, Union

from photo_selector_linux.core.models import CandidatePhoto

RAW_EXTENSIONS: Set[str] = {
    "cr2", "cr3", "nef", "arw", "dng", "raf", "rw2", "orf", "pef", "raw"
}

BITMAP_EXTENSIONS: Set[str] = {
    "jpg", "jpeg", "png", "heic", "tif", "tiff", "webp"
}

SUPPORTED_EXTENSIONS: Set[str] = RAW_EXTENSIONS | BITMAP_EXTENSIONS

DEFAULT_EXCLUDED_FOLDERS: Set[str] = {
    "selection",
    "selected",
    "phototok_selection",
    "phototok_leftswipe",
}


def natural_sort_key(s: str) -> List[Union[int, str]]:
    """Produce natural alphanumeric sort key (e.g. DSC_0002 before DSC_0010)."""
    return [int(text) if text.isdigit() else text.lower() for text in re.split(r"(\d+)", s)]


class DirectoryScanner:
    """High-performance directory scanner for SD cards and local folders."""

    def __init__(
        self,
        custom_selection_folder: Optional[str] = None,
        default_exclusions: Optional[Set[str]] = None,
    ):
        self.excluded_names: Set[str] = set(default_exclusions or DEFAULT_EXCLUDED_FOLDERS)
        if custom_selection_folder:
            cleaned = str(custom_selection_folder).strip().rstrip("/\\").strip()
            sanitized = Path(cleaned).name.strip().lower() if cleaned else ""
            if sanitized:
                self.excluded_names.add(sanitized)

    def is_path_excluded(self, dir_path: Path, root_path: Path) -> bool:
        """Check if dir_path has any relative path component matching excluded names (R-LINUX-META-05).

        If dir_path is identical to root_path, it is NEVER excluded (root scan exception).
        """
        try:
            rel = dir_path.resolve().relative_to(root_path.resolve())
        except ValueError:
            return False

        # If dir_path is root, relative parts are empty -> not excluded
        parts = [p.lower() for p in rel.parts]
        return any(p in self.excluded_names for p in parts)

    def find_companion_files(self, primary_path: Path) -> List[Path]:
        """Find all bound companion files (RAW, JPEG, XMP, -Edit) in the same directory (R-LINUX-META-04)."""
        parent = primary_path.parent
        if not parent.is_dir():
            return [primary_path]

        stem_lower = primary_path.stem.lower()
        full_lower = primary_path.name.lower()
        dot_xmp = f"{full_lower}.xmp"
        edit_hyphen = f"{stem_lower}-edit"
        edit_underscore = f"{stem_lower}_edit"
        nr_suffix = f"{stem_lower}-enhanced-nr"

        companions: Set[Path] = {primary_path}

        try:
            for entry in os.scandir(parent):
                if not entry.is_file() or entry.name.startswith("."):
                    continue

                entry_path = Path(entry.path)
                entry_name_lower = entry.name.lower()
                entry_stem_lower = entry_path.stem.lower()
                entry_ext_lower = entry_path.suffix.lstrip(".").lower()

                # 1. Exact stem match (e.g. DSC0001.ARW <-> DSC0001.JPG or DSC0001.xmp)
                if entry_stem_lower == stem_lower:
                    if entry_ext_lower in SUPPORTED_EXTENSIONS or entry_ext_lower == "xmp":
                        companions.add(entry_path)
                    continue

                # 2. Direct filename sidecar (e.g. DSC0001.ARW.xmp)
                if entry_name_lower == dot_xmp:
                    companions.add(entry_path)
                    continue

                # 3. Lightroom derivatives (-Edit.*, _Edit.*, -Enhanced-NR.*)
                if (
                    entry_stem_lower.startswith(edit_hyphen)
                    or entry_stem_lower.startswith(edit_underscore)
                    or entry_stem_lower.startswith(nr_suffix)
                ):
                    companions.add(entry_path)
                    continue
        except (OSError, PermissionError):
            pass

        return sorted(list(companions), key=lambda p: natural_sort_key(p.name))

    def _resolve_primary_and_companions(self, group: Iterable[Path]) -> Tuple[Path, List[Path]]:
        """Select RAW as primary if present, otherwise sort alphabetically (R-LINUX-META-04)."""
        paths = list(group)
        raws = [p for p in paths if p.suffix.lstrip(".").lower() in RAW_EXTENSIONS]
        if raws:
            raws.sort(key=lambda p: natural_sort_key(p.name))
            primary = raws[0]
        else:
            paths.sort(key=lambda p: natural_sort_key(p.name))
            primary = paths[0]

        companions = [p for p in paths if p.resolve() != primary.resolve()]
        return primary, companions

    def scan_stream(
        self,
        root_path: Path,
        recursive: bool = True,
        batch_size: int = 30,
    ) -> Generator[List[CandidatePhoto], None, None]:
        """Progressively stream CandidatePhoto batches (R-LINUX-CULL-01)."""
        root_path = root_path.resolve()
        claimed_paths: Set[Path] = set()
        current_batch: List[CandidatePhoto] = []

        if recursive:
            walk_gen = os.walk(root_path, followlinks=False)
        else:
            walk_gen = [(str(root_path), [], [f.name for f in os.scandir(root_path) if f.is_file()])]

        for root_str, dirnames, filenames in walk_gen:
            curr_dir = Path(root_str).resolve()

            # Dynamic Selection Subfolder Exclusion (R-LINUX-META-05)
            if curr_dir != root_path and self.is_path_excluded(curr_dir, root_path):
                dirnames.clear()  # Prune subtree
                continue

            # Prune child directories that match exclusions before walking into them
            dirnames[:] = [
                d for d in dirnames
                if not self.is_path_excluded(curr_dir / d, root_path)
            ]

            # Filter valid candidate files in current directory
            valid_files: List[Path] = []
            for fname in filenames:
                if fname.startswith("."):
                    continue
                ext = fname.rsplit(".", 1)[-1].lower() if "." in fname else ""
                if ext in SUPPORTED_EXTENSIONS:
                    valid_files.append(curr_dir / fname)

            # Sort files naturally within folder
            valid_files.sort(key=lambda p: natural_sort_key(p.name))

            for file_path in valid_files:
                resolved_file = file_path.resolve()
                if resolved_file in claimed_paths:
                    continue

                # Check if this is an Edit file whose base capture exists in same folder
                search_target = file_path
                stem_lower = file_path.stem.lower()
                for edit_marker in ("-edit", "_edit", "-enhanced-nr"):
                    if edit_marker in stem_lower:
                        base_stem = stem_lower.split(edit_marker)[0]
                        for candidate_base in valid_files:
                            if candidate_base.stem.lower() == base_stem:
                                search_target = candidate_base
                                break
                        break

                all_companions = self.find_companion_files(search_target)
                for comp in all_companions:
                    claimed_paths.add(comp.resolve())

                primary, companions = self._resolve_primary_and_companions(all_companions)
                try:
                    file_size = primary.stat().st_size
                except OSError:
                    file_size = 0

                candidate = CandidatePhoto(
                    primary_path=primary,
                    companion_paths=companions,
                    _file_size=file_size,
                )

                current_batch.append(candidate)
                if len(current_batch) >= batch_size:
                    yield current_batch
                    current_batch = []

        if current_batch:
            yield current_batch

    def scan_directory(self, root_path: Path, recursive: bool = True) -> List[CandidatePhoto]:
        """Enumerate and return all candidates sorted naturally by primary filename (R-LINUX-CULL-02)."""
        candidates: List[CandidatePhoto] = []
        for batch in self.scan_stream(root_path, recursive=recursive, batch_size=50):
            candidates.extend(batch)

        for candidate in candidates:
            if candidate._file_size is None:
                try:
                    candidate._file_size = candidate.primary_path.stat().st_size
                except OSError:
                    candidate._file_size = 0

        candidates.sort(key=lambda c: natural_sort_key(c.primary_path.name))
        return candidates


def scan_directory(
    root_path: Path,
    recursive: bool = True,
    custom_selection_folder: Optional[str] = None,
) -> List[CandidatePhoto]:
    """Convenience functional interface for directory scanning."""
    scanner = DirectoryScanner(custom_selection_folder=custom_selection_folder)
    return scanner.scan_directory(root_path, recursive=recursive)
