import pytest
from unittest.mock import patch, MagicMock
import sys

# Save original modules to prevent test pollution
original_modules = {}

@pytest.fixture(scope="module", autouse=True)
def mock_sys_modules():
    modules_to_mock = [
        "rawpy",
        "image_metadata_analyzer.controllers",
        "image_metadata_analyzer.models",
        "image_metadata_analyzer.sharpness",
        "image_metadata_analyzer.reader",
        "image_metadata_analyzer.utils",
        "image_metadata_analyzer.formatting",
        "send2trash",
    ]
    for name in modules_to_mock:
        original_modules[name] = sys.modules.get(name)
        sys.modules[name] = MagicMock()
        
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
        patch("image_metadata_analyzer.sharpness_gui.tk.Tk"),
        patch(
            "image_metadata_analyzer.sharpness_gui.tk.StringVar",
            return_value=MagicMock(),
        ),
        patch(
            "image_metadata_analyzer.sharpness_gui.tk.IntVar", return_value=MagicMock()
        ),
        patch(
            "image_metadata_analyzer.sharpness_gui.tk.DoubleVar",
            return_value=MagicMock(),
        ),
        patch(
            "image_metadata_analyzer.sharpness_gui.tk.BooleanVar",
            return_value=MagicMock(),
        ),
        patch("image_metadata_analyzer.sharpness_gui.ttk.Frame"),
        patch("image_metadata_analyzer.sharpness_gui.ttk.LabelFrame"),
        patch("image_metadata_analyzer.sharpness_gui.ttk.Label"),
        patch("image_metadata_analyzer.sharpness_gui.ttk.Button"),
        patch("image_metadata_analyzer.sharpness_gui.ttk.Notebook"),
        patch("image_metadata_analyzer.sharpness_gui.ttk.Treeview"),
        patch("image_metadata_analyzer.sharpness_gui.ttk.Scrollbar"),
        patch("image_metadata_analyzer.sharpness_gui.ImageTk.PhotoImage"),
        patch("image_metadata_analyzer.controllers.ImageCacheManager"),
    ):
        yield


def test_sharpness_tool_init():
    from image_metadata_analyzer.sharpness_gui import SharpnessTool

    parent = MagicMock()

    # Needs to mock out parent methods used in __init__
    parent.register = MagicMock()

    with (
        patch("image_metadata_analyzer.sharpness_gui.tk.Toplevel"),
        patch("image_metadata_analyzer.sharpness_gui.SharpnessTool.setup_ui"),
        patch("image_metadata_analyzer.sharpness_gui.SharpnessTool.setup_focus_ui"),
        patch("image_metadata_analyzer.sharpness_gui.SharpnessTool.bind_all"),
    ):
        tool = SharpnessTool(parent)
        assert not tool.is_scanning
        assert tool.candidates == []
        assert tool.sorted_files == []
