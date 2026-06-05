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
