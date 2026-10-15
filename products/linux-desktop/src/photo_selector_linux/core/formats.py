"""Supported file formats — the Linux Desktop side of the shared format contract.

The contract itself is ``testdata/formats/manifest.json`` (docs/shared/SUPPORTED_FORMATS.md);
``tests/unit/test_format_conformance.py`` fails when these sets drift from it.

Adheres to:
- R-LINUX-META-01: Supported File Formats
"""

from __future__ import annotations

from pathlib import Path
from typing import FrozenSet, Union

# Required RAW formats: discovered, paired, displayed, EXIF read and scored.
REQUIRED_RAW_EXTENSIONS: FrozenSet[str] = frozenset({
    "arw", "cr2", "cr3", "nef", "nrw", "raf", "rw2", "orf", "pef", "dng", "srw",
})

# Recognized RAW formats: discovered and paired as RAW; display is best effort.
RECOGNIZED_RAW_EXTENSIONS: FrozenSet[str] = frozenset({
    "3fr", "fff", "iiq", "rwl", "raw", "sr2", "srf", "mrw", "x3f", "erf", "mef", "mos",
})

RAW_EXTENSIONS: FrozenSet[str] = REQUIRED_RAW_EXTENSIONS | RECOGNIZED_RAW_EXTENSIONS

BITMAP_EXTENSIONS: FrozenSet[str] = frozenset({
    "jpg", "jpeg", "heic", "heif", "png", "tif", "tiff", "webp",
})

SUPPORTED_EXTENSIONS: FrozenSet[str] = RAW_EXTENSIONS | BITMAP_EXTENSIONS


def extension_of(path: Union[str, Path]) -> str:
    """Lower-case extension without the dot (``DSC0001.ARW`` -> ``arw``); '' when there is none."""
    name = Path(path).name
    if "." not in name.lstrip("."):
        return ""
    return name.rsplit(".", 1)[-1].lower()


def is_raw(path: Union[str, Path]) -> bool:
    return extension_of(path) in RAW_EXTENSIONS


def is_supported(path: Union[str, Path]) -> bool:
    return extension_of(path) in SUPPORTED_EXTENSIONS
