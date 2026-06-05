import warnings
from pathlib import Path
from photo_selector_toolbox.models import ExifData
from photo_selector_toolbox.readers import read_exif

# Suppress specific warnings from Pillow about potentially corrupt EXIF data
# which it often handles gracefully anyway.
warnings.filterwarnings("ignore", "(Possibly )?corrupt EXIF data", UserWarning)

# Canonical raw format extensions - single source of truth
RAW_EXTENSIONS = {
    ".arw", ".nef", ".cr2", ".dng", ".raw",
    ".cr3", ".raf", ".orf", ".rw2", ".pef", ".srw", ".sr2",
}

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
SUPPORTED_EXTENSIONS = FORCE_EXIFTOOL_EXTENSIONS | {".jpg", ".jpeg", ".tif", ".tiff"}


def get_exif_data(image_path: Path, debug: bool = False) -> ExifData | None:
    """
    Extracts relevant EXIF data from a single image file.
    Delegates to the registered reader strategies in preferred order.
    """
    return read_exif(image_path, debug=debug)
