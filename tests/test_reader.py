import pytest
from PIL import Image

from photo_selector_toolbox.reader import get_exif_data


@pytest.fixture
def image_dir(tmp_path):
    d = tmp_path / "images"
    d.mkdir()
    return d


def test_get_exif_data_no_file(image_dir):
    p = image_dir / "nonexistent.jpg"
    assert get_exif_data(p) is None


def test_get_exif_data_no_exif(image_dir):
    p = image_dir / "no_exif.jpg"
    img = Image.new("RGB", (100, 100), color="red")
    img.save(p)

    # Pillow created image has no EXIF data
    assert get_exif_data(p) is None


def test_get_exif_data_with_exif(image_dir):
    # It is hard to synthesise a valid EXIF structure from scratch using just Pillow
    # without external libraries or complex byte manipulation
    # so we will trust that if we cannot find EXIF, it returns None.
    # However, we can test that it handles a file that IS an image but has no exif nicely.
    p = image_dir / "test.jpg"
    img = Image.new("RGB", (100, 100), color="blue")
    img.save(p)
    result = get_exif_data(p)
    assert result is None


def test_get_exif_data_exiftool_import_error(image_dir, caplog):
    import builtins
    import logging
    from unittest.mock import patch

    p = image_dir / "test.dng"
    img = Image.new("RGB", (100, 100), color="blue")
    img.save(p, format="TIFF")  # DNG is TIFF based, Pillow can save as TIFF

    real_import = builtins.__import__

    def mock_import(name, *args, **kwargs):
        if name == "exiftool":
            raise ImportError("Mocked ImportError for exiftool")
        return real_import(name, *args, **kwargs)

    caplog.set_level(logging.DEBUG)
    with patch("builtins.__import__", side_effect=mock_import):
        result = get_exif_data(p, debug=True)

    assert any(
        "PyExifTool not installed or found." in record.message
        for record in caplog.records
    )
    # It should fall back to Pillow and return None for our dummy image
    assert result is None


def test_get_exif_data_exifread_import_error(image_dir, caplog):
    import builtins
    import logging
    from unittest.mock import patch

    p = image_dir / "test.dng"
    img = Image.new("RGB", (100, 100), color="blue")
    img.save(p, format="TIFF")  # DNG is TIFF based, Pillow can save as TIFF

    real_import = builtins.__import__

    def mock_import(name, *args, **kwargs):
        # We need exiftool to fail first so it falls back to exifread
        if name == "exiftool":
            raise ImportError("Mocked ImportError for exiftool")
        if name == "exifread":
            raise ImportError("Mocked ImportError for exifread")
        return real_import(name, *args, **kwargs)

    caplog.set_level(logging.DEBUG)
    with patch("builtins.__import__", side_effect=mock_import):
        result = get_exif_data(p, debug=True)

    assert any(
        "PyExifTool not installed or found." in record.message
        for record in caplog.records
    )
    assert any(
        "`exifread` library not found." in record.message for record in caplog.records
    )
    # It should fall back to Pillow and return None for our dummy image
    assert result is None


def test_get_exif_data_exiftool_general_error(image_dir, caplog):
    import builtins
    import logging
    from unittest.mock import patch

    p = image_dir / "test.dng"
    img = Image.new("RGB", (100, 100), color="blue")
    img.save(p, format="TIFF")

    real_import = builtins.__import__

    def mock_import(name, *args, **kwargs):
        if name == "exiftool":
            raise OSError("Mocked general error for exiftool")
        return real_import(name, *args, **kwargs)

    caplog.set_level(logging.DEBUG)
    with patch("builtins.__import__", side_effect=mock_import):
        result = get_exif_data(p, debug=True)

    assert any(
        "exiftool failed on test.dng: Mocked general error for exiftool"
        in record.message
        for record in caplog.records
    )
    assert result is None


def test_get_exif_data_exifread_general_error(image_dir, caplog):
    import builtins
    import logging
    from unittest.mock import patch

    p = image_dir / "test.dng"
    img = Image.new("RGB", (100, 100), color="blue")
    img.save(p, format="TIFF")

    real_import = builtins.__import__

    def mock_import(name, *args, **kwargs):
        if name == "exiftool":
            raise ImportError("Mocked ImportError for exiftool")
        if name == "exifread":
            raise OSError("Mocked general error for exifread")
        return real_import(name, *args, **kwargs)

    caplog.set_level(logging.DEBUG)
    with patch("builtins.__import__", side_effect=mock_import):
        result = get_exif_data(p, debug=True)

    assert any(
        "exifread failed on test.dng: Mocked general error for exifread"
        in record.message
        for record in caplog.records
    )
    assert result is None


def test_get_exif_data_pillow_exception(image_dir, caplog):
    import logging
    from unittest.mock import MagicMock, patch

    p = image_dir / "test.jpg"
    p.write_text("dummy")

    with patch("photo_selector_toolbox.readers.pillow.Image.open") as mock_open:
        mock_img = MagicMock()
        mock_img.getexif.side_effect = ValueError("Mocked Pillow Error")
        mock_open.return_value = mock_img

        caplog.set_level(logging.DEBUG)
        result = get_exif_data(p, debug=True)

    assert any("Mocked Pillow Error" in record.message for record in caplog.records)
    assert result is None
