"""Unit tests for optical metadata extraction and APEX conversions.

Adheres to:
- R-LINUX-META-02: ExposureMetadata parsing
- R-LINUX-META-03: Zero-Placeholder Invariant
- R-LINUX-META-06: APEX exposure math fallbacks
"""

import math
from pathlib import Path
from unittest.mock import MagicMock, patch
from photo_selector_linux.core.metadata import (
    _safe_float,
    read_metadata,
)
from photo_selector_linux.core.models import ExposureMetadata


def test_safe_float_edge_cases():
    """Verify safe float conversion handles fractions, strings, and guards against zero division."""
    assert _safe_float(4) == 4.0
    assert _safe_float(2.8) == 2.8
    assert _safe_float("1/250") == 0.004
    assert _safe_float("0.5") == 0.5

    # Zero denominator / division by zero
    assert _safe_float("1/0") is None

    # Non-positive or non-finite values
    assert _safe_float(0) is None
    assert _safe_float(-2.5) is None
    assert _safe_float(float("nan")) is None
    assert _safe_float(float("inf")) is None
    assert _safe_float("garbage") is None
    assert _safe_float(None) is None


def test_apex_tv_conversion():
    """Verify APEX Tv conversion: t = 2^(-Tv) when ExposureTime is absent (R-LINUX-META-06)."""
    # For Tv = 8.0: t = 2^(-8) = 1/256 ≈ 0.00390625
    with patch("photo_selector_linux.core.metadata._HAVE_GEXIV2", False), \
         patch("photo_selector_linux.core.metadata.Image.open") as mock_open:

        mock_img = MagicMock()
        mock_exif = MagicMock()
        # 0x9201 is ShutterSpeedValue (Tv)
        mock_exif.items.return_value = [(0x9201, 8.0)]
        mock_exif.get_ifd.return_value = {}
        mock_img.getexif.return_value = mock_exif
        mock_open.return_value.__enter__.return_value = mock_img

        meta = read_metadata(Path("/tmp/test.jpg"))
        assert meta.is_fallback is True
        assert meta.shutter_speed is not None
        assert math.isclose(meta.shutter_speed, 2.0 ** -8.0, rel_tol=1e-4)
        assert meta.formatted_shutter_speed == "1/256s"


def test_apex_av_conversion():
    """Verify APEX Av conversion: f = 2^(Av / 2) when FNumber is absent (R-LINUX-META-06)."""
    # For Av = 2.0: f = 2^(2 / 2) = 2.0 (f/2.0)
    # For Av = 5.0: f = 2^(2.5) ≈ 5.6568 (f/5.7)
    with patch("photo_selector_linux.core.metadata._HAVE_GEXIV2", False), \
         patch("photo_selector_linux.core.metadata.Image.open") as mock_open:

        mock_img = MagicMock()
        mock_exif = MagicMock()
        # 0x9202 is ApertureValue (Av)
        mock_exif.items.return_value = [(0x9202, 5.0)]
        mock_exif.get_ifd.return_value = {}
        mock_img.getexif.return_value = mock_exif
        mock_open.return_value.__enter__.return_value = mock_img

        meta = read_metadata(Path("/tmp/test.jpg"))
        assert meta.is_fallback is True
        assert meta.aperture is not None
        assert math.isclose(meta.aperture, 2.0 ** 2.5, rel_tol=1e-3)
        assert meta.formatted_aperture == "f/5.7"


def test_direct_exif_tags_priority():
    """Verify direct EXIF tags take precedence over APEX tags (is_fallback=False)."""
    with patch("photo_selector_linux.core.metadata._HAVE_GEXIV2", False), \
         patch("photo_selector_linux.core.metadata.Image.open") as mock_open:

        mock_img = MagicMock()
        mock_exif = MagicMock()
        mock_exif.items.return_value = [
            (0x829A, 0.002),   # ExposureTime (1/500s)
            (0x9201, 8.0),     # Tv (1/256s) - should be ignored
            (0x829D, 2.8),     # FNumber (f/2.8)
            (0x9202, 5.0),     # Av (f/5.7) - should be ignored
            (0x8827, 400),     # ISO
            (0x920A, 50.0),    # FocalLength
        ]
        mock_exif.get_ifd.return_value = {}
        mock_img.getexif.return_value = mock_exif
        mock_open.return_value.__enter__.return_value = mock_img

        meta = read_metadata(Path("/tmp/test.jpg"))
        assert meta.is_fallback is False
        assert meta.shutter_speed == 0.002
        assert meta.formatted_shutter_speed == "1/500s"
        assert meta.aperture == 2.8
        assert meta.formatted_aperture == "f/2.8"
        assert meta.iso == 400
        assert meta.formatted_iso == "ISO 400"
        assert meta.focal_length == 50.0
        assert meta.formatted_focal_length == "50mm"
        assert meta.formatted_summary == "1/500s · f/2.8 · ISO 400 · 50mm"


def test_corrupt_exif_graceful_handling(tmp_path: Path):
    """Verify corrupted or truncated metadata fails gracefully without uncaught exceptions."""
    corrupt_file = tmp_path / "corrupt.jpg"
    corrupt_file.write_bytes(b"\xFF\xD8\xFF\xE1" + b"\x00" * 20)  # Invalid JPEG header

    meta = read_metadata(corrupt_file)
    assert isinstance(meta, ExposureMetadata)
    assert meta.shutter_speed is None
    assert meta.aperture is None
