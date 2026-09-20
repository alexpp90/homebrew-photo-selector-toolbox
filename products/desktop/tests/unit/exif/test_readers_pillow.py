from pathlib import Path
from unittest.mock import MagicMock, patch
import pytest
from PIL import Image

from photo_selector_toolbox.exif.readers.pillow import PillowReader
from photo_selector_toolbox.core.models import ExifData


class FractionMock:
    def __init__(self, numerator, denominator):
        self.numerator = numerator
        self.denominator = denominator


def test_pillow_reader_can_handle():
    reader = PillowReader()
    assert reader.can_handle(Path("any_image.jpg")) is True


def test_pillow_reader_no_exif(tmp_path):
    img_path = tmp_path / "test.jpg"
    img = Image.new("RGB", (10, 10))
    img.save(img_path)

    reader = PillowReader()
    assert reader.read(img_path) is None


def test_pillow_reader_attribute_error_fallback(tmp_path):
    img_path = tmp_path / "test.jpg"
    reader = PillowReader()

    with patch("photo_selector_toolbox.exif.readers.pillow.Image.open") as mock_open:
        mock_img = MagicMock()
        mock_img.getexif.side_effect = AttributeError
        mock_img._getexif.return_value = None
        mock_open.return_value = mock_img

        result = reader.read(img_path)
        assert result is None
        mock_img._getexif.assert_called_once()


def test_pillow_reader_ifd_key_error_and_all_essential_none(tmp_path, caplog):
    img_path = tmp_path / "test.jpg"
    reader = PillowReader()

    mock_raw_exif = MagicMock()
    mock_raw_exif.items.return_value = [("Artist", "Test Artist")]
    mock_raw_exif.get_ifd.side_effect = KeyError

    with patch("photo_selector_toolbox.exif.readers.pillow.Image.open") as mock_open:
        mock_img = MagicMock()
        mock_img.getexif.return_value = mock_raw_exif
        mock_open.return_value = mock_img

        with caplog.at_level("DEBUG"):
            result = reader.read(img_path, debug=True)

        assert result is None
        assert "Artist" in caplog.text


def test_pillow_reader_empty_exif_data_after_merge(tmp_path, caplog):
    img_path = tmp_path / "test.jpg"
    reader = PillowReader()

    mock_raw_exif = MagicMock()
    mock_raw_exif.items.return_value = []
    mock_raw_exif.get_ifd.return_value = {}

    with patch("photo_selector_toolbox.exif.readers.pillow.Image.open") as mock_open:
        mock_img = MagicMock()
        mock_img.getexif.return_value = mock_raw_exif
        mock_open.return_value = mock_img

        with caplog.at_level("DEBUG"):
            result = reader.read(img_path, debug=True)

        assert result is None
        assert "contains no known tags after merging" in caplog.text


def test_pillow_reader_success_full_extraction(tmp_path):
    img_path = tmp_path / "test.jpg"
    reader = PillowReader()

    raw_items = {
        "ExposureTime": FractionMock(1, 200),
        "FNumber": (28, 10),
        "FocalLength": 50.0,
        "FocalLengthIn35mmFilm": b"50\x00",
        "ISOSpeedRatings": (400, 0),
        "LensModel": "50mm F1.8",
    }

    mock_raw_exif = MagicMock()
    mock_raw_exif.items.return_value = raw_items.items()
    mock_raw_exif.get_ifd.return_value = {}

    with patch("photo_selector_toolbox.exif.readers.pillow.Image.open") as mock_open:
        mock_img = MagicMock()
        mock_img.getexif.return_value = mock_raw_exif
        mock_open.return_value = mock_img

        result = reader.read(img_path)

        assert isinstance(result, ExifData)
        assert result.shutter_speed == 0.005
        assert result.aperture == 2.8
        assert result.focal_length == 50.0
        assert result.focal_length_35mm == 50.0
        assert result.is_fallback is False
        assert result.iso == 400.0
        assert result.lens == "50mm F1.8"


def test_pillow_reader_focal_length_35_fallback(tmp_path):
    img_path = tmp_path / "test.jpg"
    reader = PillowReader()

    raw_items = {
        "FocalLength": 35.0,
        "ISOSpeedRatings": 100,
    }

    mock_raw_exif = MagicMock()
    mock_raw_exif.items.return_value = raw_items.items()
    mock_raw_exif.get_ifd.return_value = {}

    with patch("photo_selector_toolbox.exif.readers.pillow.Image.open") as mock_open:
        mock_img = MagicMock()
        mock_img.getexif.return_value = mock_raw_exif
        mock_open.return_value = mock_img

        result = reader.read(img_path)

        assert isinstance(result, ExifData)
        assert result.focal_length == 35.0
        assert result.focal_length_35mm == 35.0
        assert result.is_fallback is True
        assert result.lens == "Unknown"


def test_pillow_reader_get_float_edge_cases(tmp_path):
    img_path = tmp_path / "test.jpg"
    reader = PillowReader()

    raw_items = {
        "ExposureTime": FractionMock(1, 0),
        "FNumber": (28, 0),
        "FocalLength": b"invalid_bytes\x00",
        "ISOSpeedRatings": "unparseable_string",
        "LensModel": "50mm F1.8",
    }

    mock_raw_exif = MagicMock()
    mock_raw_exif.items.return_value = raw_items.items()
    mock_raw_exif.get_ifd.return_value = {}

    with patch("photo_selector_toolbox.exif.readers.pillow.Image.open") as mock_open:
        mock_img = MagicMock()
        mock_img.getexif.return_value = mock_raw_exif
        mock_open.return_value = mock_img

        result = reader.read(img_path)

        assert isinstance(result, ExifData)
        assert result.shutter_speed is None
        assert result.aperture is None
        assert result.focal_length is None
        assert result.iso is None
        assert result.lens == "50mm F1.8"


@pytest.mark.parametrize("exception_cls", [
    Image.UnidentifiedImageError, OSError, IOError, ValueError
])
def test_pillow_reader_handles_open_exceptions(tmp_path, exception_cls):
    img_path = tmp_path / "test.jpg"
    reader = PillowReader()

    with patch("photo_selector_toolbox.exif.readers.pillow.Image.open", side_effect=exception_cls("Test error")):
        result = reader.read(img_path)
        assert result is None
