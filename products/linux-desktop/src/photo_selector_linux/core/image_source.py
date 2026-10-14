"""Encoded display sources for candidate photographs, RAW included.

Neither GdkPixbuf nor Pillow can decode camera RAW containers (``.ARW``, ``.NEF``, ``.CR3``, …)
on a stock Debian install, so a RAW file is shown through the largest JPEG preview its camera
embedded, rotated by the RAW file's own EXIF orientation (the preview carries none). Bitmap
formats are passed through unchanged and keep their embedded orientation.

Adheres to:
- R-LINUX-META-01: Supported File Formats (RAW display through the embedded preview)
"""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Optional, Tuple

from photo_selector_linux.core.scanner import RAW_EXTENSIONS, _extension_of

try:
    import gi
    gi.require_version("GExiv2", "0.10")
    from gi.repository import GExiv2
    HAS_GEXIV2 = True
except (ImportError, ValueError):
    HAS_GEXIV2 = False
    GExiv2 = None


@dataclass(frozen=True)
class DisplaySource:
    """Encoded image bytes ready for a decoder, plus how to orient the decoded pixels.

    ``orientation`` is an EXIF orientation (1–8) to apply explicitly, or ``None`` when the
    bytes carry their own orientation tag.
    """
    data: bytes
    orientation: Optional[int]
    is_raw_preview: bool


def is_raw_name(name: str) -> bool:
    """True when ``name`` (a user-facing filename) has a camera RAW extension."""
    return _extension_of(name) in RAW_EXTENSIONS


def orientation_ops(orientation: Optional[int]) -> Tuple[bool, int]:
    """EXIF orientation → (flip horizontally first, then rotate counter-clockwise by degrees).

    Unknown or missing values map to the identity.
    """
    return {
        1: (False, 0),
        2: (True, 0),
        3: (False, 180),
        4: (True, 180),
        5: (True, 90),
        6: (False, 270),
        7: (True, 270),
        8: (False, 90),
    }.get(orientation or 1, (False, 0))


def read_raw_preview(path: Path, data: Optional[bytes] = None) -> Optional[DisplaySource]:
    """Extract the largest embedded JPEG preview of a RAW file, or ``None`` if it has none.

    ``data`` is the file's content when already in memory (parsed instead of reopening ``path``).
    """
    if not HAS_GEXIV2:
        return None
    try:
        meta = GExiv2.Metadata()
        if data is not None:
            meta.open_buf(data)
        else:
            meta.open_path(str(path))
        previews = [
            p for p in (meta.get_preview_properties() or [])
            if (p.get_mime_type() or "").lower() == "image/jpeg"
        ]
        if not previews:
            return None
        best = max(previews, key=lambda p: p.get_width() * p.get_height())
        getter = getattr(meta, "try_get_preview_image", None) or meta.get_preview_image
        preview = getter(best).get_data()
        orientation_getter = getattr(meta, "try_get_orientation", None) or meta.get_orientation
        try:
            orientation = int(orientation_getter())
        except Exception:
            orientation = 1
    except Exception:
        return None
    if not preview:
        return None
    return DisplaySource(data=bytes(preview), orientation=orientation, is_raw_preview=True)


def read_display_source(
    path: Path,
    name: Optional[str] = None,
    data: Optional[bytes] = None,
) -> Optional[DisplaySource]:
    """Encoded bytes to display for ``path``; ``name`` is its user-facing filename.

    The name decides the format, because on GVFS cloud mounts the on-disk name is an opaque ID.
    ``data`` is the file's content when the caller has already read it.
    """
    if is_raw_name(name or path.name):
        return read_raw_preview(path, data)
    if data is None:
        try:
            data = path.read_bytes()
        except OSError:
            return None
    return DisplaySource(data=data, orientation=None, is_raw_preview=False)
