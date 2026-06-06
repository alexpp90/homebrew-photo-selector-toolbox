import pytest
from unittest.mock import patch, MagicMock
import sys

# Save original modules to prevent test pollution
original_modules = {}

@pytest.fixture(scope="module", autouse=True)
def mock_sys_modules():
    modules_to_mock = [
        "rawpy",
        "photo_selector_toolbox.controllers",
        "photo_selector_toolbox.models",
        "photo_selector_toolbox.sharpness",
        "photo_selector_toolbox.reader",
        "photo_selector_toolbox.utils",
        "photo_selector_toolbox.formatting",
        "send2trash",
    ]
    for name in modules_to_mock:
        original_modules[name] = sys.modules.get(name)
        mock_mod = MagicMock()
        if name == "photo_selector_toolbox.utils":
            mock_mod.is_excluded_subfolder.return_value = False
        sys.modules[name] = mock_mod
        
    yield
    
    for name in modules_to_mock:
        orig = original_modules[name]
        if orig is None:
            sys.modules.pop(name, None)
        else:
            sys.modules[name] = orig


@pytest.fixture(autouse=True)
def mock_sharpness_gui_deps():
    with (
        patch("photo_selector_toolbox.sharpness_gui.tk.Tk"),
        patch(
            "photo_selector_toolbox.sharpness_gui.tk.StringVar",
            return_value=MagicMock(),
        ),
        patch(
            "photo_selector_toolbox.sharpness_gui.tk.IntVar", return_value=MagicMock()
        ),
        patch(
            "photo_selector_toolbox.sharpness_gui.tk.DoubleVar",
            return_value=MagicMock(),
        ),
        patch(
            "photo_selector_toolbox.sharpness_gui.tk.BooleanVar",
            return_value=MagicMock(),
        ),
        patch("photo_selector_toolbox.sharpness_gui.ttk.Frame"),
        patch("photo_selector_toolbox.sharpness_gui.ttk.LabelFrame"),
        patch("photo_selector_toolbox.sharpness_gui.ttk.Label"),
        patch("photo_selector_toolbox.sharpness_gui.ttk.Button"),
        patch("photo_selector_toolbox.sharpness_gui.ttk.Notebook"),
        patch("photo_selector_toolbox.sharpness_gui.ttk.Treeview"),
        patch("photo_selector_toolbox.sharpness_gui.ttk.Scrollbar"),
        patch("photo_selector_toolbox.sharpness_gui.ImageTk.PhotoImage"),
        patch("photo_selector_toolbox.controllers.ImageCacheManager"),
    ):
        yield


def test_sharpness_tool_init():
    from photo_selector_toolbox.sharpness_gui import SharpnessTool

    parent = MagicMock()

    # Needs to mock out parent methods used in __init__
    parent.register = MagicMock()

    with (
        patch("photo_selector_toolbox.sharpness_gui.tk.Toplevel"),
        patch("photo_selector_toolbox.sharpness_gui.SharpnessTool.setup_ui"),
        patch("photo_selector_toolbox.sharpness_gui.SharpnessTool.setup_focus_ui"),
        patch("photo_selector_toolbox.sharpness_gui.SharpnessTool.bind_all"),
    ):
        tool = SharpnessTool(parent)
        assert not tool.is_scanning
        assert tool.candidates == []
        assert tool.sorted_files == []


def test_sharpness_tool_filtering():
    from photo_selector_toolbox.sharpness_gui import SharpnessTool
    from pathlib import Path

    parent = MagicMock()
    parent.register = MagicMock()

    with (
        patch("photo_selector_toolbox.sharpness_gui.tk.Toplevel"),
        patch("photo_selector_toolbox.sharpness_gui.SharpnessTool.bind_all"),
    ):
        tool = SharpnessTool(parent)
        tool.config = MagicMock()
        tool.update = MagicMock()
        tool.folder_var.get.return_value = "/mock/folder"
        
        test_files = [
            Path("/mock/folder/img1.jpg"),
            Path("/mock/folder/img2.arw"),
            Path("/mock/folder/img3.jpg"),
            Path("/mock/folder/img4.png"),
        ]
        
        with patch("photo_selector_toolbox.sharpness_gui.Path.rglob", return_value=test_files):
            with patch("photo_selector_toolbox.reader.SUPPORTED_EXTENSIONS", {".jpg", ".arw", ".png"}):
                tool._load_folder_contents("/mock/folder")
                
                # Check sorted_files and candidates initially contain everything
                assert len(tool.sorted_files) == 4
                assert len(tool.candidates) == 4
                
                # Mock file_type_var.get to return ".JPG"
                tool.file_type_var.get.return_value = ".JPG"
                tool.on_file_type_change()
                
                assert len(tool.candidates) == 2
                assert all(f.suffix.upper() == ".JPG" for f in tool.candidates)
                
                # Test resetting to All
                tool.file_type_var.get.return_value = "All Supported"
                tool.on_file_type_change()
                
                assert len(tool.candidates) == 4


def test_update_scan_button_state_no_recreation():
    from photo_selector_toolbox.sharpness_gui import SharpnessTool

    parent = MagicMock()
    parent.register = MagicMock()

    with (
        patch("photo_selector_toolbox.sharpness_gui.tk.Toplevel"),
        patch("photo_selector_toolbox.sharpness_gui.SharpnessTool.bind_all"),
    ):
        tool = SharpnessTool(parent)
        # Verify that setup_review_ui created preview_area
        assert hasattr(tool, "preview_area")
        
        # Save reference of preview_area
        initial_preview_area = tool.preview_area
        
        # Call update_scan_button_state multiple times
        tool.update_scan_button_state()
        tool.is_scanning = True
        tool.update_scan_button_state()
        tool.is_scanning = False
        tool.update_scan_button_state()
        
        # Verify preview_area is still the same object and not recreated
        assert tool.preview_area is initial_preview_area


