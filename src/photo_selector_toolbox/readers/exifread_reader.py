import logging
from pathlib import Path
from typing import Optional
from photo_selector_toolbox.models import ExifData
from photo_selector_toolbox.readers.base import ExifReader

logger = logging.getLogger(__name__)

class ExifReadReader(ExifReader):
    """EXIF reader using exifread library."""
    
    def can_handle(self, path: Path) -> bool:
        from photo_selector_toolbox.reader import FORCE_EXIFTOOL_EXTENSIONS
        return path.suffix.lower() in FORCE_EXIFTOOL_EXTENSIONS

    def read(self, path: Path, debug: bool = False) -> Optional[ExifData]:
        try:
            import exifread

            with open(path, "rb") as f:
                tags = exifread.process_file(f, details=False)

            if tags:
                def get_tag_float(tag_name):
                    tag = tags.get(tag_name)
                    if not tag or not tag.values:
                        return None
                    val = tag.values[0]
                    if hasattr(val, "num"):  # It's a Ratio object
                        if val.den == 0:
                            return None
                        return float(val.num) / float(val.den)
                    try:
                        return float(val)
                    except (TypeError, ValueError):
                        return None

                shutter_speed = get_tag_float("EXIF ExposureTime")
                aperture = get_tag_float("EXIF FNumber")
                focal_length = get_tag_float("EXIF FocalLength")
                focal_length_35 = get_tag_float("EXIF FocalLengthIn35mmFilm")

                is_fallback = False
                if focal_length_35 is None and focal_length is not None:
                    focal_length_35 = focal_length
                    is_fallback = True

                iso_tag = tags.get("EXIF ISOSpeedRatings")
                iso = iso_tag.values[0] if iso_tag and iso_tag.values else None
                if iso is not None:
                    if hasattr(iso, "num"):
                        if iso.den != 0:
                            iso = float(iso.num) / float(iso.den)
                        else:
                            iso = None
                    else:
                        try:
                            iso = float(iso)
                        except (TypeError, ValueError):
                            iso = None

                lens_model_tag = tags.get("EXIF LensModel") or tags.get(
                    "MakerNote LensModel"
                )
                lens_model = (
                    str(lens_model_tag.values).strip() if lens_model_tag else "Unknown"
                )

                if all(
                    v is not None for v in [shutter_speed, aperture, focal_length, iso]
                ):
                    logger.debug(f"Successfully processed {path.name} with exifread.")
                    return ExifData(
                        shutter_speed=shutter_speed,
                        aperture=aperture,
                        focal_length=focal_length,
                        focal_length_35mm=focal_length_35,
                        is_fallback=is_fallback,
                        iso=iso,
                        lens=lens_model,
                    )
        except ImportError:
            logger.debug(
                "Warning: `exifread` library not found. "
                "Falling back to Pillow for raw files. "
                "For better raw file support, `pip install exifread`"
            )
        except (OSError, ValueError) as e:
            logger.debug(f"exifread failed on {path.name}: {e}")
        return None
