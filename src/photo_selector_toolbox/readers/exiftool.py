import logging
from pathlib import Path
from typing import Optional, Any
from photo_selector_toolbox.models import ExifData
from photo_selector_toolbox.readers.base import ExifReader
from photo_selector_toolbox.utils import get_exiftool_path

logger = logging.getLogger(__name__)

class ExifToolReader(ExifReader):
    """EXIF reader using PyExifTool."""
    
    def can_handle(self, path: Path) -> bool:
        # Avoid top-level import to prevent circular dependency
        from photo_selector_toolbox.reader import FORCE_EXIFTOOL_EXTENSIONS
        return path.suffix.lower() in FORCE_EXIFTOOL_EXTENSIONS

    def read(self, path: Path, debug: bool = False) -> Optional[ExifData]:
        try:
            import exiftool

            exiftool_path = get_exiftool_path()
            kwargs = {"executable": exiftool_path} if exiftool_path else {}

            with exiftool.ExifToolHelper(**kwargs) as et:
                tags_to_fetch = [
                    "Composite:ShutterSpeed",
                    "Composite:Aperture",
                    "Composite:ISO",
                    "EXIF:ISO",
                    "Composite:FocalLength",
                    "EXIF:FocalLength",
                    "Composite:FocalLength35efl",
                    "EXIF:FocalLengthIn35mmFormat",
                    "Composite:LensID",
                    "LensModel",
                    "LensType",
                ]
                metadata = et.get_tags(str(path), tags=tags_to_fetch)

                if metadata:
                    data = metadata[0]

                    def parse_val(val):
                        if val is None:
                            return None
                        if isinstance(val, (int, float)):
                            return float(val)
                        if isinstance(val, str):
                            val = val.split(" ")[0]
                            if "/" in val:
                                try:
                                    n, d = val.split("/")
                                    return float(n) / float(d)
                                except ValueError:
                                    pass
                            try:
                                return float(val)
                            except ValueError:
                                return None
                        return None

                    shutter_speed = parse_val(data.get("Composite:ShutterSpeed"))
                    aperture = parse_val(data.get("Composite:Aperture"))
                    iso_val = data.get("Composite:ISO") or data.get("EXIF:ISO")
                    iso = parse_val(iso_val)
                    fl_val = data.get("Composite:FocalLength") or data.get("EXIF:FocalLength")
                    focal_length = parse_val(fl_val)
                    fl35_val = data.get("Composite:FocalLength35efl") or data.get("EXIF:FocalLengthIn35mmFormat")
                    focal_length_35 = parse_val(fl35_val)

                    is_fallback = False
                    if focal_length_35 is None and focal_length is not None:
                        focal_length_35 = focal_length
                        is_fallback = True

                    lens_model = (
                        data.get("Composite:LensID")
                        or data.get("LensModel")
                        or data.get("LensType")
                        or "Unknown"
                    )

                    if all(
                        v is not None
                        for v in [shutter_speed, aperture, focal_length, iso]
                    ):
                        logger.debug(f"Successfully processed {path.name} with exiftool.")
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
            logger.debug("PyExifTool not installed or found.")
        except (OSError, ValueError) as e:
            logger.debug(f"exiftool failed on {path.name}: {e}")
        return None
