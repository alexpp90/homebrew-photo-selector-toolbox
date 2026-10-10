"""Optical EXIF metadata reader utilizing GExiv2 with Pillow fallback.

Adheres to:
- R-LINUX-META-02: Standardized ExposureMetadata Data Model
- R-LINUX-META-03: Formatted Optical Exposure Strip & Zero-Placeholder Invariant
- R-LINUX-META-06: APEX Exposure Math Fallbacks
"""

from __future__ import annotations

import math
from pathlib import Path
from typing import Any, Optional

from photo_selector_linux.core.models import ExposureMetadata

_HAVE_GEXIV2 = False
try:
    import gi
    gi.require_version("GExiv2", "0.10")
    from gi.repository import GExiv2, GLib
    _HAVE_GEXIV2 = True
except Exception:
    _HAVE_GEXIV2 = False

_HAVE_PIL = False
try:
    from PIL import Image, ExifTags
    _HAVE_PIL = True
except Exception:
    _HAVE_PIL = False


def _safe_float(val: Any) -> Optional[float]:
    """Safely cast numeric value or fraction to float, rejecting non-finite/negative values."""
    if val is None:
        return None
    try:
        if isinstance(val, (int, float)):
            f = float(val)
        elif hasattr(val, "numerator") and hasattr(val, "denominator"):
            denom = float(val.denominator)
            if denom == 0.0:
                return None
            f = float(val.numerator) / denom
        elif isinstance(val, str):
            val_s = val.strip()
            if "/" in val_s:
                num_s, den_s = val_s.split("/", 1)
                den = float(den_s)
                if den == 0.0:
                    return None
                f = float(num_s) / den
            else:
                f = float(val_s)
        else:
            return None

        if not math.isfinite(f) or f <= 0.0:
            return None
        return f
    except (ValueError, TypeError, ZeroDivisionError):
        return None


def read_metadata_gexiv2(path: Path) -> Optional[ExposureMetadata]:
    """Read EXIF using native GExiv2 C++ library (R-LINUX-META-02)."""
    if not _HAVE_GEXIV2:
        return None

    try:
        meta = GExiv2.Metadata()
        meta.open_path(str(path))
    except (GLib.Error, Exception):
        return None

    shutter_speed: Optional[float] = None
    aperture: Optional[float] = None
    iso: Optional[int] = None
    focal_length: Optional[float] = None
    lens: Optional[str] = None
    camera_make: Optional[str] = None
    camera_model: Optional[str] = None
    is_fallback: bool = False

    # 1. Shutter Speed (Exposure Time)
    if meta.has_tag("Exif.Photo.ExposureTime"):
        shutter_speed = _safe_float(meta.get_exposure_time())
    elif meta.has_tag("Exif.Photo.ShutterSpeedValue"):
        # APEX Tv conversion: t = 2^(-Tv) (R-LINUX-META-06)
        tv = _safe_float(meta.get_tag_string("Exif.Photo.ShutterSpeedValue"))
        if tv is not None:
            shutter_speed = 2.0 ** (-tv)
            is_fallback = True

    # 2. Aperture (FNumber)
    if meta.has_tag("Exif.Photo.FNumber"):
        aperture = _safe_float(meta.get_fnumber())
    elif meta.has_tag("Exif.Photo.ApertureValue"):
        # APEX Av conversion: f = 2^(Av / 2) (R-LINUX-META-06)
        av = _safe_float(meta.get_tag_string("Exif.Photo.ApertureValue"))
        if av is not None:
            aperture = 2.0 ** (av / 2.0)
            is_fallback = True

    # 3. ISO Sensitivity
    if meta.has_tag("Exif.Photo.ISOSpeedRatings"):
        try:
            raw_iso = meta.get_iso_speed()
            if raw_iso > 0:
                iso = int(raw_iso)
        except Exception:
            pass
    if iso is None and meta.has_tag("Exif.Photo.PhotographicSensitivity"):
        try:
            raw_iso = int(meta.get_tag_string("Exif.Photo.PhotographicSensitivity"))
            if raw_iso > 0:
                iso = raw_iso
        except Exception:
            pass

    # 4. Focal Length
    if meta.has_tag("Exif.Photo.FocalLength"):
        focal_length = _safe_float(meta.get_focal_length())

    # 5. Lens Model
    for tag in ("Exif.Photo.LensModel", "Exif.Canon.LensModel", "Exif.NikonLd3.LensIDNumber"):
        if meta.has_tag(tag):
            val = meta.get_tag_string(tag).strip()
            if val and val.lower() != "unknown":
                lens = val
                break

    # 6. Make & Model
    if meta.has_tag("Exif.Image.Make"):
        val = meta.get_tag_string("Exif.Image.Make").strip()
        if val and val.lower() != "unknown":
            camera_make = val
    if meta.has_tag("Exif.Image.Model"):
        val = meta.get_tag_string("Exif.Image.Model").strip()
        if val and val.lower() != "unknown":
            camera_model = val

    return ExposureMetadata(
        shutter_speed=shutter_speed,
        aperture=aperture,
        iso=iso,
        focal_length=focal_length,
        lens=lens,
        camera_make=camera_make,
        camera_model=camera_model,
        is_fallback=is_fallback,
    )


def read_metadata_pil(path: Path) -> Optional[ExposureMetadata]:
    """Fallback EXIF reader using Pillow for headless/unit testing environments."""
    if not _HAVE_PIL:
        return None

    try:
        with Image.open(path) as img:
            exif_raw = img.getexif()
            if not exif_raw:
                return ExposureMetadata()

            # Merge IFD sub-dictionaries
            exif_dict = dict(exif_raw.items())
            try:
                for ifd_id in (ExifTags.IFD.Exif, ExifTags.IFD.Makernote):
                    ifd = exif_raw.get_ifd(ifd_id)
                    if ifd:
                        exif_dict.update(ifd.items())
            except Exception:
                pass
    except Exception:
        return None

    shutter_speed: Optional[float] = None
    aperture: Optional[float] = None
    iso: Optional[int] = None
    focal_length: Optional[float] = None
    lens: Optional[str] = None
    camera_make: Optional[str] = None
    camera_model: Optional[str] = None
    is_fallback: bool = False

    # 1. Shutter speed
    exp_time_val = exif_dict.get(0x829A)  # ExposureTime
    if exp_time_val is not None:
        shutter_speed = _safe_float(exp_time_val)
    elif 0x9201 in exif_dict:  # ShutterSpeedValue (APEX Tv)
        tv = _safe_float(exif_dict.get(0x9201))
        if tv is not None:
            shutter_speed = 2.0 ** (-tv)
            is_fallback = True

    # 2. Aperture
    fnumber_val = exif_dict.get(0x829D)  # FNumber
    if fnumber_val is not None:
        aperture = _safe_float(fnumber_val)
    elif 0x9202 in exif_dict:  # ApertureValue (APEX Av)
        av = _safe_float(exif_dict.get(0x9202))
        if av is not None:
            aperture = 2.0 ** (av / 2.0)
            is_fallback = True

    # 3. ISO
    iso_val = exif_dict.get(0x8827) or exif_dict.get(0x8833)  # ISOSpeedRatings / PhotographicSensitivity
    if iso_val is not None:
        if isinstance(iso_val, (tuple, list)) and len(iso_val) > 0:
            iso_candidate = iso_val[0]
        else:
            iso_candidate = iso_val
        try:
            val_int = int(iso_candidate)
            if val_int > 0:
                iso = val_int
        except Exception:
            pass

    # 4. Focal Length
    fl_val = exif_dict.get(0x920A)  # FocalLength
    if fl_val is not None:
        focal_length = _safe_float(fl_val)

    # 5. Lens Model
    lens_val = exif_dict.get(0xA434)  # LensModel
    if lens_val and isinstance(lens_val, str) and lens_val.strip().lower() != "unknown":
        lens = lens_val.strip()

    # 6. Make & Model
    make_val = exif_dict.get(0x010F)  # Make
    if make_val and isinstance(make_val, str) and make_val.strip().lower() != "unknown":
        camera_make = make_val.strip()
    model_val = exif_dict.get(0x0110)  # Model
    if model_val and isinstance(model_val, str) and model_val.strip().lower() != "unknown":
        camera_model = model_val.strip()

    return ExposureMetadata(
        shutter_speed=shutter_speed,
        aperture=aperture,
        iso=iso,
        focal_length=focal_length,
        lens=lens,
        camera_make=camera_make,
        camera_model=camera_model,
        is_fallback=is_fallback,
    )


def read_metadata(path: Path) -> ExposureMetadata:
    """Read EXIF metadata from file using GExiv2 with Pillow fallback."""
    if _HAVE_GEXIV2:
        meta = read_metadata_gexiv2(path)
        if meta is not None:
            return meta

    if _HAVE_PIL:
        meta = read_metadata_pil(path)
        if meta is not None:
            return meta

    return ExposureMetadata()
