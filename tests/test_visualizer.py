from pathlib import Path
from unittest.mock import patch
from photo_selector_toolbox.models import ExifData
from photo_selector_toolbox.visualizer import (
    get_shutter_speed_plot,
    get_aperture_plot,
    get_iso_plot,
    get_focal_length_plot,
    get_equivalent_focal_length_plot,
    get_apsc_equivalent_focal_length_plot,
    get_lens_plot,
    get_combination_plot,
    create_plots,
    _open_file_for_user,
)


def test_get_shutter_speed_plot():
    data = [ExifData(shutter_speed=0.01), ExifData(shutter_speed=0.02), ExifData(shutter_speed=0.01)]
    fig = get_shutter_speed_plot(data)
    assert fig is not None


def test_get_shutter_speed_plot_empty():
    data = []
    fig = get_shutter_speed_plot(data)
    assert fig is None


def test_get_aperture_plot():
    data = [ExifData(aperture=2.8), ExifData(aperture=4.0)]
    fig = get_aperture_plot(data)
    assert fig is not None


def test_get_iso_plot():
    data = [ExifData(iso=100), ExifData(iso=200)]
    fig = get_iso_plot(data)
    assert fig is not None


def test_get_focal_length_plot():
    data = [ExifData(focal_length=50), ExifData(focal_length=85)]
    fig = get_focal_length_plot(data)
    assert fig is not None



def test_get_focal_length_plot_empty():
    data = [ExifData(focal_length=None)]
    fig = get_focal_length_plot(data)
    assert fig is None


def test_get_equivalent_focal_length_plot():
    data = [ExifData(focal_length_35mm=50.2), ExifData(focal_length_35mm=84.8)]
    fig = get_equivalent_focal_length_plot(data)
    assert fig is not None


def test_get_equivalent_focal_length_plot_empty():
    data = [ExifData(focal_length=50)]
    fig = get_equivalent_focal_length_plot(data)
    assert fig is None


def test_get_apsc_equivalent_focal_length_plot():
    data = [ExifData(focal_length_35mm=75.0), ExifData(focal_length_35mm=50.0)]
    fig = get_apsc_equivalent_focal_length_plot(data)
    assert fig is not None


def test_get_apsc_equivalent_focal_length_plot_empty():
    data = []
    fig = get_apsc_equivalent_focal_length_plot(data)
    assert fig is None


def test_get_apsc_equivalent_focal_length_plot_missing_key():
    data = [ExifData(aperture=2.8)]
    fig = get_apsc_equivalent_focal_length_plot(data)
    assert fig is None


def test_get_lens_plot():
    data = [ExifData(lens="Lens A"), ExifData(lens="Lens B")]
    fig = get_lens_plot(data)
    assert fig is not None


def test_get_combination_plot():
    data = [ExifData(aperture=2.8, focal_length=50)]
    fig = get_combination_plot(data)
    assert fig is not None


@patch("photo_selector_toolbox.visualizer._open_file_for_user")
def test_create_plots(mock_open, tmp_path):
    data = [
        ExifData(
            shutter_speed=0.01,
            aperture=2.8,
            iso=100.0,
            focal_length=50.0,
            focal_length_35mm=75.0,
            lens="Test Lens",
        )
    ]

    # We mock fig.savefig to avoid issues with matplotlib interacting with the fake PIL module in sys.modules
    # because tests/test_utils.py injects a fake PIL into sys.modules to mock it out, which breaks
    # matplotlib when it tries to import PIL.PngImagePlugin if run in the same test session.
    with patch("matplotlib.figure.Figure.savefig") as mock_savefig:
        create_plots(data, tmp_path, show_plots=True)

        # Even though we mock savefig, we can verify that create_plots ran properly and
        # attempted to save the correct files.
        expected_files = [
            "shutter_speed_distribution.png",
            "aperture_distribution.png",
            "iso_distribution.png",
            "focal_length_distribution.png",
            "equivalent_focal_length_35mm_distribution.png",
            "equivalent_focal_length_apsc_distribution.png",
            "lens_usage.png",
            "aperture_focal_length_combinations.png",
        ]

        # Verify savefig was called with paths for all our expected files
        saved_paths = [call.args[0].name for call in mock_savefig.call_args_list if call.args]
        for filename in expected_files:
            assert filename in saved_paths, f"Expected {filename} to be saved."

        assert mock_open.called


@patch("photo_selector_toolbox.visualizer._open_file_for_user")
def test_create_plots_empty_data(mock_open, tmp_path):
    data = []

    with patch("matplotlib.figure.Figure.savefig") as mock_savefig:
        create_plots(data, tmp_path, show_plots=True)

        # Should not save any png files
        mock_savefig.assert_not_called()

        # _open_file_for_user shouldn't be called because no plots were generated
        assert not mock_open.called


@patch("photo_selector_toolbox.visualizer.webbrowser.open")
def test_open_file_for_user_absolute_path(mock_webbrowser):
    """Test that _open_file_for_user uses webbrowser.open with absolute URI."""
    test_path = Path("-test_file.png")
    expected_uri = test_path.resolve().as_uri()

    _open_file_for_user(test_path)
    mock_webbrowser.assert_called_once_with(expected_uri)

@patch("photo_selector_toolbox.visualizer.webbrowser.open")
def test_open_file_for_user_whitelist(mock_webbrowser):
    """Test that _open_file_for_user rejects non-png files."""
    test_path = Path("test_file.txt")
    _open_file_for_user(test_path)
    mock_webbrowser.assert_not_called()
