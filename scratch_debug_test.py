import sys
from pathlib import Path
from unittest.mock import MagicMock, patch

# Setup sys.modules mocks as done in tests/test_sharpness_gui_basic.py
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
original_modules = {}
for name in modules_to_mock:
    original_modules[name] = sys.modules.get(name)
    sys.modules[name] = MagicMock()

# Mock tkinter and ttk
with (
    patch("photo_selector_toolbox.sharpness_gui.tk.Tk"),
    patch("photo_selector_toolbox.sharpness_gui.tk.StringVar", return_value=MagicMock()),
    patch("photo_selector_toolbox.sharpness_gui.tk.IntVar", return_value=MagicMock()),
    patch("photo_selector_toolbox.sharpness_gui.tk.DoubleVar", return_value=MagicMock()),
    patch("photo_selector_toolbox.sharpness_gui.tk.BooleanVar", return_value=MagicMock()),
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
    # Restore real models module to import ScanResult
    orig_models = original_modules.get("photo_selector_toolbox.models")
    if orig_models:
        sys.modules["photo_selector_toolbox.models"] = orig_models

    import photo_selector_toolbox.sharpness_gui as sg
    from photo_selector_toolbox.sharpness_gui import SharpnessTool
    from photo_selector_toolbox.models import ScanResult

    parent = MagicMock()
    parent.register = MagicMock()

    with (
        patch("photo_selector_toolbox.sharpness_gui.tk.Toplevel"),
        patch("photo_selector_toolbox.sharpness_gui.SharpnessTool.bind_all"),
    ):
        tool = SharpnessTool(parent)
        path = Path("/mock/folder/img1.jpg")
        
        # Test case 2
        tool.files_map[path] = ScanResult(path=path, score=42.5)
        print("files_map content:", tool.files_map)
        print("Lookup path in files_map:", tool.files_map.get(path))
        print("is_valid_metric:", tool._is_valid_metric(42.5))
        
        result = tool._get_candidate_listbox_text(path)
        print("Result:", repr(result))
