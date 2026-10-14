"""Display-name resolution for folders reached through the GVFS FUSE bridge.

GNOME exposes cloud mounts (Google Drive via GNOME Online Accounts, …) under
``/run/user/<uid>/gvfs/``. Google Drive names every file and folder there by its opaque Drive
ID — ``1aW85wHUJbUsFMmlvGW9QR4IMNtWJGmpv`` instead of ``DSC01100.ARW`` — so nothing about a file's
format can be read from its on-disk name. The mount's native GIO location (``google-drive://…``)
still reports the real name as ``standard::display-name``; this module maps the FUSE names of
one directory onto those display names.

Adheres to:
- R-LINUX-META-07: Cloud Mounts via GVFS (privacy-first: no cloud SDK, only the platform's
  own file provider)
"""

from __future__ import annotations

import os
from pathlib import Path
from typing import Dict, List, Optional, Protocol, Sequence, Tuple

try:
    import gi  # noqa: F401
    from gi.repository import Gio
    HAS_GIO = True
except (ImportError, ValueError):
    HAS_GIO = False
    Gio = None

_ATTRIBUTES = "standard::name,standard::display-name"


class DisplayNameResolver(Protocol):
    """Maps on-disk entry names of one directory to user-facing names."""

    def names_in(self, directory: Path) -> Dict[str, str]:
        """Return ``{on_disk_name: display_name}`` for entries whose names differ."""
        ...


class IdentityNameResolver:
    """Resolver for plain local filesystems: every name is already the display name."""

    def names_in(self, directory: Path) -> Dict[str, str]:
        return {}


class GioDisplayNameResolver:
    """Resolves display names through the native GIO location of a non-``file://`` mount."""

    def __init__(self, mounts: Optional[List[Tuple[Path, object]]] = None) -> None:
        # (FUSE root path, native root GFile) pairs; discovered lazily from the volume monitor.
        self._mounts = mounts

    def _native_mounts(self) -> List[Tuple[Path, object]]:
        if self._mounts is None:
            mounts: List[Tuple[Path, object]] = []
            if HAS_GIO:
                try:
                    for mount in Gio.VolumeMonitor.get().get_mounts():
                        root = mount.get_root()
                        if root.has_uri_scheme("file"):
                            continue
                        fuse_path = root.get_path()
                        if fuse_path:
                            mounts.append((Path(fuse_path), root))
                except Exception:
                    mounts = []
            self._mounts = mounts
        return self._mounts

    def names_in(self, directory: Path) -> Dict[str, str]:
        for fuse_root, native_root in self._native_mounts():
            try:
                rel = directory.relative_to(fuse_root)
            except ValueError:
                continue
            native_dir = native_root.resolve_relative_path(rel.as_posix()) if rel.parts else native_root
            return _enumerate_display_names(native_dir)
        return {}


def _enumerate_display_names(native_dir) -> Dict[str, str]:
    names: Dict[str, str] = {}
    try:
        enumerator = native_dir.enumerate_children(_ATTRIBUTES, Gio.FileQueryInfoFlags.NONE, None)
        for info in enumerator:
            on_disk = info.get_name()
            display = info.get_display_name()
            if on_disk and display and display != on_disk:
                names[on_disk] = display
        enumerator.close(None)
    except Exception:
        return names
    return names


def gvfs_fuse_roots() -> Tuple[Path, ...]:
    """Directories under which GVFS exposes its mounts through FUSE."""
    runtime_dir = os.environ.get("XDG_RUNTIME_DIR") or f"/run/user/{os.getuid()}"
    return (Path(runtime_dir) / "gvfs", Path.home() / ".gvfs")


def is_gvfs_fuse_path(path: Path, roots: Optional[Sequence[Path]] = None) -> bool:
    """True when ``path`` lies on a GVFS FUSE mount (cloud or network storage).

    Such files are fetched over the network on every read; path-based parsers that seek around
    (Exiv2 on RAW containers) turn into one round trip per seek, so read them into memory once.
    """
    for root in roots if roots is not None else gvfs_fuse_roots():
        try:
            path.relative_to(root)
            return True
        except ValueError:
            continue
    return False


def default_resolver() -> DisplayNameResolver:
    """GIO-backed resolver when PyGObject is present, identity otherwise."""
    return GioDisplayNameResolver() if HAS_GIO else IdentityNameResolver()
