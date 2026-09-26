import logging
from pathlib import Path
from unittest.mock import MagicMock, patch

from photo_selector_toolbox.exif.readers.exiftool import ExifToolReader


def test_can_handle():
    reader = ExifToolReader()
    assert reader.can_handle(Path("test.dng"))
    assert reader.can_handle(Path("test.heic"))
    assert reader.can_handle(Path("test.png"))
    assert not reader.can_handle(Path("test.jpg"))
    assert not reader.can_handle(Path("test.txt"))


def test_read_success_full_metadata():
    reader = ExifToolReader()
    dummy_path = Path("sample.dng")

    mock_metadata = [
        {
            "Composite:ShutterSpeed": "1/200",
            "Composite:Aperture": "2.8",
            "Composite:ISO": 100,
            "Composite:FocalLength": "50.0 mm",
            "Composite:FocalLength35efl": "75.0",
            "Composite:LensID": "FE 50mm F1.8",
        }
    ]

    mock_helper = MagicMock()
    mock_helper.__enter__.return_value = mock_helper
    mock_helper.get_tags.return_value = mock_metadata

    with patch("photo_selector_toolbox.exif.readers.exiftool.get_exiftool_path", return_value="/usr/bin/exiftool"), \
         patch("exiftool.ExifToolHelper", return_value=mock_helper) as mock_et_cls:

        exif_data = reader.read(dummy_path)

        mock_et_cls.assert_called_once_with(executable="/usr/bin/exiftool")
        assert exif_data is not None
        assert exif_data.shutter_speed == 0.005
        assert exif_data.aperture == 2.8
        assert exif_data.iso == 100.0
        assert exif_data.focal_length == 50.0
        assert exif_data.focal_length_35mm == 75.0
        assert not exif_data.is_fallback
        assert exif_data.lens == "FE 50mm F1.8"


def test_read_kwargs_empty_when_no_exiftool_path():
    reader = ExifToolReader()
    dummy_path = Path("sample.dng")

    mock_metadata = [
        {
            "Composite:ShutterSpeed": 0.01,
            "Composite:Aperture": 4.0,
            "Composite:ISO": 200,
            "Composite:FocalLength": 35.0,
            "Composite:FocalLength35efl": 35.0,
            "Composite:LensID": "35mm F2",
        }
    ]

    mock_helper = MagicMock()
    mock_helper.__enter__.return_value = mock_helper
    mock_helper.get_tags.return_value = mock_metadata

    with patch("photo_selector_toolbox.exif.readers.exiftool.get_exiftool_path", return_value=None), \
         patch("exiftool.ExifToolHelper", return_value=mock_helper) as mock_et_cls:

        exif_data = reader.read(dummy_path)

        mock_et_cls.assert_called_once_with()
        assert exif_data is not None


def test_read_fallbacks_and_parsing():
    reader = ExifToolReader()
    dummy_path = Path("sample.dng")

    mock_metadata = [
        {
            "Composite:ShutterSpeed": "invalid_fraction_1/0/0",
            "EXIF:ISO": "400",
            "EXIF:FocalLength": 85.0,
            "LensModel": "LensModel Fallback",
        }
    ]

    mock_helper = MagicMock()
    mock_helper.__enter__.return_value = mock_helper
    mock_helper.get_tags.return_value = mock_metadata

    with patch("photo_selector_toolbox.exif.readers.exiftool.get_exiftool_path", return_value=None), \
         patch("exiftool.ExifToolHelper", return_value=mock_helper):

        # Missing shutter_speed should cause read to return None
        exif_data = reader.read(dummy_path)
        assert exif_data is None

    # Now provide shutter_speed and test fallbacks for 35mm focal length and LensType
    mock_metadata_2 = [
        {
            "Composite:ShutterSpeed": "1/1000 sec",
            "Composite:Aperture": 1.4,
            "EXIF:ISO": "400",
            "EXIF:FocalLength": "85.0",
            "LensType": "LensType Fallback",
        }
    ]
    mock_helper.get_tags.return_value = mock_metadata_2

    with patch("photo_selector_toolbox.exif.readers.exiftool.get_exiftool_path", return_value=None), \
         patch("exiftool.ExifToolHelper", return_value=mock_helper):

        exif_data = reader.read(dummy_path)
        assert exif_data is not None
        assert exif_data.shutter_speed == 0.001
        assert exif_data.aperture == 1.4
        assert exif_data.iso == 400.0
        assert exif_data.focal_length == 85.0
        assert exif_data.focal_length_35mm == 85.0
        assert exif_data.is_fallback is True
        assert exif_data.lens == "LensType Fallback"


def test_read_lens_unknown_fallback():
    reader = ExifToolReader()
    dummy_path = Path("sample.dng")

    mock_metadata = [
        {
            "Composite:ShutterSpeed": 0.01,
            "Composite:Aperture": 2.0,
            "Composite:ISO": 100,
            "Composite:FocalLength": 50.0,
        }
    ]

    mock_helper = MagicMock()
    mock_helper.__enter__.return_value = mock_helper
    mock_helper.get_tags.return_value = mock_metadata

    with patch("photo_selector_toolbox.exif.readers.exiftool.get_exiftool_path", return_value=None), \
         patch("exiftool.ExifToolHelper", return_value=mock_helper):

        exif_data = reader.read(dummy_path)
        assert exif_data is not None
        assert exif_data.lens == "Unknown"


def test_read_empty_metadata_list():
    reader = ExifToolReader()
    dummy_path = Path("sample.dng")

    mock_helper = MagicMock()
    mock_helper.__enter__.return_value = mock_helper
    mock_helper.get_tags.return_value = []

    with patch("photo_selector_toolbox.exif.readers.exiftool.get_exiftool_path", return_value=None), \
         patch("exiftool.ExifToolHelper", return_value=mock_helper):

        assert reader.read(dummy_path) is None


def test_read_parse_val_edge_cases():
    reader = ExifToolReader()
    dummy_path = Path("sample.dng")

    mock_metadata = [
        {
            "Composite:ShutterSpeed": ["invalid", "type"],  # non str/int/float
            "Composite:Aperture": "not_a_number",  # string causing ValueError in float(val)
            "Composite:ISO": None,
            "Composite:FocalLength": "100.5",
        }
    ]

    mock_helper = MagicMock()
    mock_helper.__enter__.return_value = mock_helper
    mock_helper.get_tags.return_value = mock_metadata

    with patch("photo_selector_toolbox.exif.readers.exiftool.get_exiftool_path", return_value=None), \
         patch("exiftool.ExifToolHelper", return_value=mock_helper):

        assert reader.read(dummy_path) is None


def test_read_import_error(caplog):
    reader = ExifToolReader()
    dummy_path = Path("sample.dng")

    import builtins
    real_import = builtins.__import__

    def mock_import(name, *args, **kwargs):
        if name == "exiftool":
            raise ImportError("PyExifTool missing")
        return real_import(name, *args, **kwargs)

    caplog.set_level(logging.DEBUG)
    with patch("builtins.__import__", side_effect=mock_import):
        result = reader.read(dummy_path)

    assert result is None
    assert any("PyExifTool not installed or found." in record.message for record in caplog.records)


def test_read_os_or_value_error(caplog):
    reader = ExifToolReader()
    dummy_path = Path("sample.dng")

    mock_helper = MagicMock()
    mock_helper.__enter__.return_value = mock_helper
    mock_helper.get_tags.side_effect = OSError("ExifTool process error")

    caplog.set_level(logging.DEBUG)
    with patch("photo_selector_toolbox.exif.readers.exiftool.get_exiftool_path", return_value=None), \
         patch("exiftool.ExifToolHelper", return_value=mock_helper):

        result = reader.read(dummy_path)

    assert result is None
    assert any("exiftool failed on sample.dng: ExifTool process error" in record.message for record in caplog.records)
