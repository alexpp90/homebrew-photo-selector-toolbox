import logging
import builtins
from pathlib import Path
from unittest.mock import patch

from photo_selector_toolbox.exif.readers.exifread_reader import ExifReadReader


class MockRatio:
    def __init__(self, num, den):
        self.num = num
        self.den = den


class MockTag:
    def __init__(self, values):
        self.values = values if isinstance(values, list) else [values]


class MockStringTag:
    def __init__(self, value):
        self.values = value


def test_can_handle():
    reader = ExifReadReader()
    assert reader.can_handle(Path("test.dng")) is True
    assert reader.can_handle(Path("test.cr2")) is True
    assert reader.can_handle(Path("test.jpg")) is False


def test_read_success_full(tmp_path, caplog):
    reader = ExifReadReader()
    test_file = tmp_path / "test.dng"
    test_file.write_bytes(b"dummy image content")

    mock_tags = {
        "EXIF ExposureTime": MockTag(MockRatio(1, 100)),
        "EXIF FNumber": MockTag(MockRatio(28, 10)),
        "EXIF FocalLength": MockTag(MockRatio(50, 1)),
        "EXIF FocalLengthIn35mmFilm": MockTag(50),
        "EXIF ISOSpeedRatings": MockTag(100),
        "EXIF LensModel": MockStringTag("50mm f/1.8"),
    }

    with patch("exifread.process_file", return_value=mock_tags):
        caplog.set_level(logging.DEBUG)
        data = reader.read(test_file, debug=True)

    assert data is not None
    assert data.shutter_speed == 0.01
    assert data.aperture == 2.8
    assert data.focal_length == 50.0
    assert data.focal_length_35mm == 50.0
    assert data.is_fallback is False
    assert data.iso == 100.0
    assert data.lens == "50mm f/1.8"


def test_read_success_focal_length_fallback_and_makernote_lens(tmp_path):
    reader = ExifReadReader()
    test_file = tmp_path / "test.dng"
    test_file.write_bytes(b"dummy image content")

    mock_tags = {
        "EXIF ExposureTime": MockTag(MockRatio(1, 200)),
        "EXIF FNumber": MockTag(MockRatio(40, 10)),
        "EXIF FocalLength": MockTag(MockRatio(85, 1)),
        "EXIF ISOSpeedRatings": MockTag(MockRatio(200, 1)),
        "MakerNote LensModel": MockStringTag("85mm f/1.4"),
    }

    with patch("exifread.process_file", return_value=mock_tags):
        data = reader.read(test_file)

    assert data is not None
    assert data.shutter_speed == 0.005
    assert data.aperture == 4.0
    assert data.focal_length == 85.0
    assert data.focal_length_35mm == 85.0
    assert data.is_fallback is True
    assert data.iso == 200.0
    assert data.lens == "85mm f/1.4"


def test_read_ratio_zero_denominator_and_invalid_values(tmp_path):
    reader = ExifReadReader()
    test_file = tmp_path / "test.dng"
    test_file.write_bytes(b"dummy image content")

    mock_tags = {
        "EXIF ExposureTime": MockTag(MockRatio(1, 0)),
        "EXIF FNumber": MockTag("invalid"),
        "EXIF FocalLength": MockTag([]),
        "EXIF ISOSpeedRatings": MockTag(MockRatio(100, 0)),
    }

    with patch("exifread.process_file", return_value=mock_tags):
        data = reader.read(test_file)

    assert data is None


def test_read_iso_non_ratio_conversion_and_unknown_lens(tmp_path):
    reader = ExifReadReader()
    test_file = tmp_path / "test.dng"
    test_file.write_bytes(b"dummy image content")

    mock_tags = {
        "EXIF ExposureTime": MockTag("0.01"),
        "EXIF FNumber": MockTag("2.8"),
        "EXIF FocalLength": MockTag("50"),
        "EXIF ISOSpeedRatings": MockTag("400"),
    }

    with patch("exifread.process_file", return_value=mock_tags):
        data = reader.read(test_file)

    assert data is not None
    assert data.iso == 400.0
    assert data.lens == "Unknown"


def test_read_iso_invalid_string(tmp_path):
    reader = ExifReadReader()
    test_file = tmp_path / "test.dng"
    test_file.write_bytes(b"dummy image content")

    mock_tags = {
        "EXIF ExposureTime": MockTag("0.01"),
        "EXIF FNumber": MockTag("2.8"),
        "EXIF FocalLength": MockTag("50"),
        "EXIF ISOSpeedRatings": MockTag("invalid_iso"),
    }

    with patch("exifread.process_file", return_value=mock_tags):
        data = reader.read(test_file)

    assert data is None


def test_read_empty_tags(tmp_path):
    reader = ExifReadReader()
    test_file = tmp_path / "test.dng"
    test_file.write_bytes(b"dummy image content")

    with patch("exifread.process_file", return_value={}):
        data = reader.read(test_file)

    assert data is None


def test_read_import_error(tmp_path, caplog):
    reader = ExifReadReader()
    test_file = tmp_path / "test.dng"
    test_file.write_bytes(b"dummy image content")

    real_import = builtins.__import__

    def mock_import(name, *args, **kwargs):
        if name == "exifread":
            raise ImportError("Mocked exifread import error")
        return real_import(name, *args, **kwargs)

    caplog.set_level(logging.DEBUG)
    with patch("builtins.__import__", side_effect=mock_import):
        data = reader.read(test_file)

    assert data is None
    assert any("`exifread` library not found" in record.message for record in caplog.records)


def test_read_oserror(tmp_path, caplog):
    reader = ExifReadReader()
    test_file = tmp_path / "nonexistent.dng"

    caplog.set_level(logging.DEBUG)
    data = reader.read(test_file)

    assert data is None
    assert any("exifread failed on nonexistent.dng" in record.message for record in caplog.records)
