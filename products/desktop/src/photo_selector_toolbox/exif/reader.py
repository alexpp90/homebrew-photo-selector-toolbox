import warnings
from pathlib import Path
from photo_selector_toolbox.core.models import ExifData
from photo_selector_toolbox.exif.readers import read_exif

# Suppress specific warnings from Pillow about potentially corrupt EXIF data
# which it often handles gracefully anyway.
warnings.filterwarnings("ignore", "(Possibly )?corrupt EXIF data", UserWarning)

# Canonical raw format extensions - single source of truth for the whole product
# (scanning, RAW/JPEG companion classification, rawpy decoding, ExifTool routing).
# Mirrors the `kind: raw` formats of the shared contract
# testdata/formats/manifest.json; tests/unit/test_format_conformance.py fails if
# the two drift apart.
RAW_EXTENSIONS = frozenset({
    # Required tier: a real camera sample of each is in the conformance corpus.
    ".arw", ".cr2", ".cr3", ".nef", ".nrw", ".raf", ".rw2", ".orf", ".pef",
    ".dng", ".srw",
    # Recognized tier: discovered, paired and decoded by LibRaw, without a
    # corpus sample.
    ".3fr", ".fff", ".iiq", ".rwl", ".raw", ".sr2", ".srf", ".mrw", ".x3f",
    ".erf", ".mef", ".mos",
})

# Bitmap formats a camera or phone writes alongside (or instead of) RAW.
JPEG_EXTENSIONS = frozenset({".jpg", ".jpeg"})

# Extensions that should be processed with ExifTool if available, as they often contain
# complex metadata or are not well-supported by Pillow/exifread.
FORCE_EXIFTOOL_EXTENSIONS = RAW_EXTENSIONS | {
    # High Efficiency formats
    ".heic",
    ".heif",
    # Web/Lossless formats (better metadata support in ExifTool)
    ".png",
    ".webp",
}

# All supported extensions. Includes the above plus standard formats handled well by Pillow.
SUPPORTED_EXTENSIONS = FORCE_EXIFTOOL_EXTENSIONS | JPEG_EXTENSIONS | {".tif", ".tiff"}


def is_supported_image_name(name: str) -> bool:
    """Whether a file *name* is a photo the product scans, case-insensitively.

    macOS AppleDouble files (``._DSC0001.ARW``) carry a supported extension but
    no image, so they are rejected. Reads `SUPPORTED_EXTENSIONS` at call time.
    """
    return not name.startswith("._") and name.lower().endswith(tuple(SUPPORTED_EXTENSIONS))


def is_raw_name(name: str) -> bool:
    """Whether a file name has a RAW extension, case-insensitively."""
    dot = name.rfind(".")
    return dot >= 0 and name[dot:].lower() in RAW_EXTENSIONS


def get_exif_data(image_path: Path, debug: bool = False) -> ExifData | None:
    """
    Extracts relevant EXIF data from a single image file.
    Delegates to the registered reader strategies in preferred order.
    """
    return read_exif(image_path, debug=debug)
