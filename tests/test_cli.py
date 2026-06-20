import sys
from unittest.mock import patch

import pytest

from photo_selector_toolbox.cli import main


def test_main_no_args(capsys):
    """Test that main exits with an error if no arguments are provided."""
    with patch.object(sys, "argv", ["cli.py"]):
        with pytest.raises(SystemExit):
            main()

    captured = capsys.readouterr()
    assert "the following arguments are required: root_folder" in captured.err


def test_main_invalid_folder(capsys, tmp_path):
    """Test that main reports an error if the specified folder does not exist."""
    invalid_folder = tmp_path / "nonexistent"
    with patch.object(sys, "argv", ["cli.py", str(invalid_folder)]):
        main()

    captured = capsys.readouterr()
    assert f"Error: Folder not found at '{invalid_folder}'" in captured.out


def test_main_no_supported_images(capsys, tmp_path):
    """Test that main stops if the folder exists but has no supported images."""
    with patch.object(sys, "argv", ["cli.py", str(tmp_path)]):
        main()

    captured = capsys.readouterr()
    assert f"Scanning for images in '{tmp_path}'..." in captured.out
    assert "No supported image files found." in captured.out


def test_main_images_no_metadata(capsys, tmp_path):
    """Test that main stops if images are found but no metadata can be extracted."""
    img_path = tmp_path / "test.jpg"
    img_path.touch()

    with patch.object(sys, "argv", ["cli.py", str(tmp_path)]):
        with patch("photo_selector_toolbox.cli.get_exif_data", return_value=None):
            main()

    captured = capsys.readouterr()
    assert "Found 1 image files. Extracting metadata..." in captured.out
    assert "Could not extract any valid EXIF metadata from the found images." in captured.out


def test_main_success(capsys, tmp_path):
    """Test a successful run processing images and invoking downstream functions."""
    img_path1 = tmp_path / "test1.jpg"
    img_path2 = tmp_path / "test2.png"
    img_path1.touch()
    img_path2.touch()

    from photo_selector_toolbox.models import ExifData
    fake_metadata = ExifData(aperture=2.8, shutter_speed=0.01)
    out_dir = tmp_path / "out"

    args = [
        "cli.py",
        str(tmp_path),
        "--output", str(out_dir),
        "--debug",
        "--show-plots"
    ]

    with patch.object(sys, "argv", args):
        with patch("photo_selector_toolbox.cli.get_exif_data", return_value=fake_metadata) as mock_get_exif:
            with patch("photo_selector_toolbox.cli.analyze_data") as mock_analyze:
                with patch("photo_selector_toolbox.cli.create_plots") as mock_create_plots:
                    main()

    captured = capsys.readouterr()
    assert "Found 2 image files. Extracting metadata..." in captured.out

    assert mock_get_exif.call_count == 2
    mock_get_exif.assert_any_call(img_path1, debug=True)
    mock_get_exif.assert_any_call(img_path2, debug=True)

    mock_analyze.assert_called_once_with([fake_metadata, fake_metadata])
    mock_create_plots.assert_called_once_with([fake_metadata, fake_metadata], out_dir, show_plots=True)


def test_main_success_json(capsys, tmp_path):
    """Test a successful run outputting metadata as JSON."""
    img_path = tmp_path / "test.jpg"
    img_path.touch()

    from photo_selector_toolbox.models import ExifData
    fake_metadata = ExifData(aperture=2.8, shutter_speed=0.01)

    args = [
        "cli.py",
        str(tmp_path),
        "--format", "json"
    ]

    with patch.object(sys, "argv", args):
        with patch("photo_selector_toolbox.cli.get_exif_data", return_value=fake_metadata):
            with patch("photo_selector_toolbox.cli.analyze_data_json", return_value={"test": "data"}) as mock_analyze_json:
                main()

    captured = capsys.readouterr()
    assert "test" in captured.out
    mock_analyze_json.assert_called_once()


def test_main_success_csv(capsys, tmp_path):
    """Test a successful run outputting metadata as CSV."""
    img_path = tmp_path / "test.jpg"
    img_path.touch()

    from photo_selector_toolbox.models import ExifData
    fake_metadata = ExifData(aperture=2.8, shutter_speed=0.01)
    fake_metadata._filepath = img_path

    args = [
        "cli.py",
        str(tmp_path),
        "--format", "csv"
    ]

    with patch.object(sys, "argv", args):
        with patch("photo_selector_toolbox.cli.get_exif_data", return_value=fake_metadata):
            main()

    captured = capsys.readouterr()
    assert "filename,shutter_speed,aperture,iso,focal_length,focal_length_35mm,lens" in captured.out
