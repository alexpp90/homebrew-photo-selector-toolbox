import pytest
from unittest.mock import patch, MagicMock


# Mock out tk and ttk heavily for headless CI
@pytest.fixture(autouse=True)
def mock_gui_deps():
    with (
        patch("photo_selector_toolbox.gui.tk.Tk") as MockTk,
        patch("photo_selector_toolbox.gui.tk.Toplevel"),
        patch("photo_selector_toolbox.gui.tk.Frame"),
        patch("photo_selector_toolbox.gui.tk.Label"),
        patch("photo_selector_toolbox.gui.tk.StringVar", return_value=MagicMock()),
        patch("photo_selector_toolbox.gui.tk.DoubleVar", return_value=MagicMock()),
        patch("photo_selector_toolbox.gui.tk.BooleanVar", return_value=MagicMock()),
        patch("photo_selector_toolbox.gui.ttk") as MockTtk,
        patch("photo_selector_toolbox.gui.FigureCanvasTkAgg"),
        patch("photo_selector_toolbox.gui.ImageTk.PhotoImage"),
    ):
        yield MockTk, MockTtk


def test_image_library_statistics_init(mock_gui_deps):
    from photo_selector_toolbox.gui import ImageLibraryStatistics

    parent = MagicMock()
    frame = ImageLibraryStatistics(parent)
    assert not frame.is_analyzing
    assert frame.root_folder_var is not None


def test_duplicate_finder_init(mock_gui_deps):
    from photo_selector_toolbox.gui import DuplicateFinder

    parent = MagicMock()
    finder = DuplicateFinder(parent)
    assert not finder.is_scanning
    assert finder.found_duplicates == []


def test_sidebar_init(mock_gui_deps):
    from photo_selector_toolbox.gui import Sidebar

    parent = MagicMock()
    controller = MagicMock()
    sidebar = Sidebar(parent, controller)
    assert sidebar.controller == controller


def test_main_app_init(mock_gui_deps):
    from photo_selector_toolbox.gui import MainApp

    with patch("photo_selector_toolbox.gui.SharpnessTool") as MockTool:
        MockTool.__name__ = "SharpnessTool"
        # Since Tk is mocked, MainApp's super init won't actually fail.
        # We need to ensure the mocked methods exist to avoid AttributeError.
        app = MainApp()
        assert "ImageLibraryStatistics" in app.frames
        assert "DuplicateFinder" in app.frames
        assert "SharpnessTool" in app.frames


def test_show_about(mock_gui_deps):
    from photo_selector_toolbox.gui import MainApp
    with (
        patch("photo_selector_toolbox.gui.SharpnessTool") as MockTool,
        patch("photo_selector_toolbox.gui.AboutDialog") as MockAbout
    ):
        MockTool.__name__ = "SharpnessTool"
        app = MainApp()
        app.show_about()
        MockAbout.assert_called_once_with(app)
