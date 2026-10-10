"""Streaming cryptographic duplicate finder with size grouping pre-filter.

Adheres to:
- R-LINUX-TOOLS-01: Streaming Cryptographic Duplicate Finder
- R-LINUX-META-05: Dynamic Selection Subfolder Exclusion Invariant
"""

from __future__ import annotations

import hashlib
import os
from collections import defaultdict
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Callable, List, Optional, Set

CHUNK_SIZE: int = 65536  # 64 KB memory chunks

DEFAULT_EXCLUDED_DIRS: Set[str] = {
    "selection",
    "selected",
    "phototok_selection",
    "phototok_leftswipe",
}


@dataclass
class DuplicateCluster:
    """Cluster of byte-identical files sharing exact size and SHA-256 digest."""
    hash: str
    file_size: int
    file_paths: List[Path]

    @property
    def wasted_bytes(self) -> int:
        """Total storage wasted by redundant duplicate copies beyond the first."""
        return self.file_size * max(0, len(self.file_paths) - 1)

    @property
    def count(self) -> int:
        return len(self.file_paths)


def is_path_excluded(
    path: Path,
    root: Optional[Path] = None,
    custom_selection_folder: Optional[str] = None,
) -> bool:
    """Check if path resides inside an excluded Selection directory (R-LINUX-META-05).

    Uses exact individual component matching (case-insensitive).
    """
    excluded = set(DEFAULT_EXCLUDED_DIRS)
    if custom_selection_folder:
        cleaned = str(custom_selection_folder).strip().rstrip("/\\").strip()
        name = Path(cleaned).name.strip().lower() if cleaned else ""
        if name:
            excluded.add(name)

    # If root is provided, check relative path components
    if root is not None:
        try:
            rel = path.resolve().relative_to(root.resolve())
            parts = [p.lower() for p in rel.parts[:-1]]  # parent directories only
            return any(p in excluded for p in parts)
        except ValueError:
            pass

    # Fallback: check all parent components
    return any(p.name.lower() in excluded for p in path.parents)


def compute_file_sha256(path: Path, chunk_size: int = CHUNK_SIZE) -> Optional[str]:
    """Stream file in 64 KB chunks to compute lowercase SHA-256 hex digest."""
    hasher = hashlib.sha256()
    try:
        with open(path, "rb") as f:
            while chunk := f.read(chunk_size):
                hasher.update(chunk)
        return hasher.hexdigest()
    except (OSError, PermissionError):
        return None


def find_duplicates(
    root_or_files: Any,
    custom_selection_folder: Optional[str] = None,
    progress_callback: Optional[Callable[[int, int], None]] = None,
    is_cancelled: Optional[Callable[[], bool]] = None,
) -> List[DuplicateCluster]:
    """Find duplicate clusters using two-stage size filtering and streaming SHA-256 (R-LINUX-TOOLS-01).

    Stage 1: Group files by exact st_size. Skip unique file sizes immediately without hashing.
    Stage 2: For size collisions only, stream 64 KB chunks into SHA-256.
    """
    size_groups = defaultdict(list)

    if isinstance(root_or_files, (str, Path)):
        root = Path(root_or_files).resolve()
        if not root.exists():
            return []

        for dirpath, dirnames, filenames in os.walk(root, followlinks=False):
            if is_cancelled and is_cancelled():
                return []

            curr_dir = Path(dirpath).resolve()

            # Dynamic exclusion pruning
            try:
                rel = curr_dir.relative_to(root)
                parts = [p.lower() for p in rel.parts]
                if any(p in DEFAULT_EXCLUDED_DIRS for p in parts):
                    dirnames.clear()
                    continue
            except ValueError:
                pass

            dirnames[:] = [
                d
                for d in dirnames
                if not is_path_excluded(curr_dir / d, root, custom_selection_folder)
            ]

            for fname in filenames:
                if fname.startswith("._") or fname.startswith("."):
                    continue
                file_path = curr_dir / fname
                try:
                    st = file_path.stat()
                    if st.st_size > 0:
                        size_groups[st.st_size].append(file_path)
                except OSError:
                    continue
    else:
        # Collection of CandidatePhoto objects or Path instances
        for item in root_or_files:
            if hasattr(item, "primary_path"):
                p = Path(item.primary_path).resolve()
            else:
                p = Path(item).resolve()

            if is_path_excluded(p, custom_selection_folder=custom_selection_folder):
                continue
            try:
                st = p.stat()
                if st.st_size > 0:
                    size_groups[st.st_size].append(p)
            except OSError:
                continue

    # Stage 1: Retain size collisions only (count >= 2)
    colliding_groups = {
        size: paths for size, paths in size_groups.items() if len(paths) >= 2
    }

    total_files_to_hash = sum(len(paths) for paths in colliding_groups.values())
    processed_count = 0

    # Stage 2: Streaming SHA-256 Hashing
    clusters: List[DuplicateCluster] = []

    for size, paths in colliding_groups.items():
        if is_cancelled and is_cancelled():
            return []

        hash_groups = defaultdict(list)
        for p in paths:
            if is_cancelled and is_cancelled():
                return []

            digest = compute_file_sha256(p)
            processed_count += 1
            if progress_callback:
                progress_callback(processed_count, total_files_to_hash)

            if digest:
                hash_groups[digest].append(p)

        for digest, matched_paths in hash_groups.items():
            if len(matched_paths) >= 2:
                clusters.append(
                    DuplicateCluster(
                        hash=digest,
                        file_size=size,
                        file_paths=sorted(matched_paths, key=lambda f: f.name),
                    )
                )

    # Sort clusters by potential wasted space descending
    clusters.sort(key=lambda c: c.wasted_bytes, reverse=True)
    return clusters
