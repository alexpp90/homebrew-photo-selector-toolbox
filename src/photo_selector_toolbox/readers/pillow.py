import logging
from pathlib import Path
from typing import Optional
from PIL import Image, ExifTags
from photo_selector_toolbox.models import ExifData
from photo_selector_toolbox.readers.base import ExifReader

logger = logging.getLogger(__name__)


class PillowReader(ExifReader):
    """EXIF reader using Pillow library."""

    def can_handle(self, path: Path) -> bool:
        return True

    def read(self, path: Path, debug: bool = False) -> Optional[ExifData]:
        try:
            img = Image.open(path)
            try:
                exif_data_raw = img.getexif()
            except AttributeError:
                exif_data_raw = img._getexif()

            if not exif_data_raw:
                logger.debug(
                    f"\n--- Debugging failed extraction for: {path.name} ---\n"
                    f"  Reason: No EXIF data found in the image file.\n"
                    f"----------------------------------------------------"
                )
                return None

            try:
                exif_ifd = exif_data_raw.get_ifd(34665)
            except KeyError:
                exif_ifd = {}

            exif_data = {ExifTags.TAGS.get(k, k): v for k, v in exif_data_raw.items()}
            exif_ifd_data = {ExifTags.TAGS.get(k, k): v for k, v in exif_ifd.items()}
            exif_data.update(exif_ifd_data)

            if not exif_data:
                logger.debug(
                    f"\n--- Debugging (Pillow) failed extraction for: {path.name} ---\n"
                    f"  Reason: EXIF data was found, but it contains no known tags after merging.\n"
                    f"----------------------------------------------------"
                )
                return None

            def get_float(value):
                if value is None:
                    return None
                if hasattr(value, "numerator") and hasattr(value, "denominator"):
                    if value.denominator == 0:
                        return None
                    return float(value.numerator) / float(value.denominator)
                if isinstance(value, tuple) and len(value) == 2:
                    num, den = value
                    if den == 0:
                        return None
                    return float(num) / float(den)
                if isinstance(value, bytes):
                    try:
                        return float(
                            value.strip(b"\x00").decode("utf-8", errors="ignore")
                        )
                    except (ValueError, UnicodeDecodeError):
                        return None
                try:
                    return float(value)
                except (TypeError, ValueError):
                    return None

            shutter_speed_raw = exif_data.get("ExposureTime")
            aperture_raw = exif_data.get("FNumber")
            focal_length_raw = exif_data.get("FocalLength")
            iso_raw = exif_data.get("ISOSpeedRatings")
            lens_model_raw = exif_data.get("LensModel")

            shutter_speed = get_float(shutter_speed_raw)
            aperture = get_float(aperture_raw)
            focal_length = get_float(focal_length_raw)

            focal_length_35_raw = exif_data.get("FocalLengthIn35mmFilm")
            focal_length_35 = get_float(focal_length_35_raw)

            is_fallback = False
            if focal_length_35 is None and focal_length is not None:
                focal_length_35 = focal_length
                is_fallback = True

            iso = get_float(iso_raw[0] if isinstance(iso_raw, tuple) else iso_raw)
            lens_model = lens_model_raw or "Unknown"

            if all(
                v is None
                for v in [shutter_speed, aperture, focal_length, iso, lens_model_raw]
            ):
                available_keys_str = ""
                if exif_data:
                    import textwrap

                    available_keys = ", ".join(
                        sorted([str(k) for k in exif_data.keys()])
                    )
                    available_keys_str = (
                        "\n  Available EXIF keys found in this file (merged):\n"
                        + textwrap.fill(
                            available_keys,
                            width=80,
                            initial_indent="    ",
                            subsequent_indent="    ",
                        )
                    )
                else:
                    available_keys_str = (
                        "\n  No known EXIF keys were found in this file."
                    )

                logger.debug(
                    f"\n--- Debugging (Pillow) failed extraction for: {path.name} ---\n"
                    f"  Raw Shutter Speed: {shutter_speed_raw!r} -> Parsed: {shutter_speed}\n"
                    f"  Raw Aperture:      {aperture_raw!r} -> Parsed: {aperture}\n"
                    f"  Raw Focal Length:  {focal_length_raw!r} -> Parsed: {focal_length}\n"
                    f"  Raw ISO:           {iso_raw!r} -> Parsed: {iso}\n"
                    f"  Lens Model:        {lens_model!r}\n"
                    f"  Reason: None of the essential metadata fields could be found or parsed."
                    f"{available_keys_str}\n"
                    f"----------------------------------------------------"
                )
                return None

            return ExifData(
                shutter_speed=shutter_speed,
                aperture=aperture,
                focal_length=focal_length,
                focal_length_35mm=focal_length_35,
                is_fallback=is_fallback,
                iso=iso,
                lens=lens_model,
            )
        except (Image.UnidentifiedImageError, OSError, IOError, ValueError) as e:
            logger.debug(
                f"\n--- Debugging (Pillow) failed extraction for: {path.name} ---\n"
                f"  An unexpected error occurred: {e}\n"
                f"----------------------------------------------------"
            )
            return None
