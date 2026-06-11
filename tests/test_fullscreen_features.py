import sys
from pathlib import Path
from unittest.mock import MagicMock, patch
import pytest

@pytest.fixture(autouse=True)
def mock_sys_modules():
    existing_modules = set(sys.modules.keys())
    modules_to_mock = [
        "rawpy",
        "photo_selector_toolbox.controllers",
        "photo_selector_toolbox.models",
        "photo_selector_toolbox.sharpness",
        "photo_selector_toolbox.reader",
        "photo_selector_toolbox.utils",
        "photo_selector_toolbox.formatting",
        "send2trash",
        "PIL",
        "PIL.Image",
        "PIL.ImageTk",
    ]
    original_modules = {}
    for name in modules_to_mock:
        original_modules[name] = sys.modules.get(name)
        sys.modules[name] = MagicMock()

    # Clear GUI modules from sys.modules cache to force a re-import
    # with the mocked Tkinter and other classes
    cleared_gui_modules = {}
    gui_modules = ["photo_selector_toolbox.fullscreen_viewer", "photo_selector_toolbox.sharpness_gui"]
    for name in gui_modules:
        if name in sys.modules:
            cleared_gui_modules[name] = sys.modules.pop(name)
        
    yield
    
    # Restore the cleared GUI modules
    for name, mod in cleared_gui_modules.items():
        sys.modules[name] = mod

    for name in modules_to_mock:
        orig = original_modules[name]
        if orig is None:
            sys.modules.pop(name, None)
        else:
            sys.modules[name] = orig

    # Pop any newly imported photo_selector_toolbox modules imported during mock session to prevent pollution
    to_pop = [m for m in list(sys.modules.keys()) if m.startswith("photo_selector_toolbox") and m not in existing_modules]
    for m in to_pop:
        sys.modules.pop(m, None)



@pytest.fixture(autouse=True)
def mock_tkinter_and_ttk():
    # Store original classes
    import tkinter as tk
    from tkinter import ttk
    
    orig_toplevel = tk.Toplevel
    orig_tk = tk.Tk
    orig_canvas = tk.Canvas
    orig_frame = ttk.Frame
    orig_labelframe = ttk.LabelFrame
    orig_label = ttk.Label
    orig_button = ttk.Button
    orig_notebook = ttk.Notebook
    orig_scrollbar = ttk.Scrollbar
    orig_separator = ttk.Separator
    orig_stringvar = tk.StringVar
    orig_booleanvar = tk.BooleanVar
    orig_doublevar = tk.DoubleVar
    orig_intvar = tk.IntVar
    
    # Define lightweight dummy classes
    class DummyToplevel:
        def __init__(self, parent=None, *args, **kwargs):
            self.parent = parent
            self.title_val = ""
            self.geometry_val = ""
            self.bindings = {}

        def title(self, val):
            self.title_val = val

        def attributes(self, *args):
            pass

        def geometry(self, val):
            self.geometry_val = val

        def bind(self, sequence, func):
            self.bindings[sequence] = func

        def bind_all(self, sequence, func):
            pass

        def after(self, ms, func, *args):
            pass

        def destroy(self):
            pass

        def winfo_screenwidth(self):
            return 1920

        def winfo_screenheight(self):
            return 1080

        def winfo_width(self):
            return 800

        def winfo_height(self):
            return 600

        def winfo_x(self):
            return 100

        def winfo_y(self):
            return 100

        def winfo_exists(self):
            return True

        def winfo_toplevel(self):
            return self

        def pack(self, *args, **kwargs):
            pass

        def place(self, *args, **kwargs):
            pass

        def grid(self, *args, **kwargs):
            pass

        def pack_forget(self, *args, **kwargs):
            pass

        def grid_propagate(self, *args, **kwargs):
            pass

        def pack_propagate(self, *args, **kwargs):
            pass

        def rowconfigure(self, *args, **kwargs):
            pass

        def columnconfigure(self, *args, **kwargs):
            pass

        def state(self, *args, **kwargs):
            pass

        def focus_set(self):
            pass

        def config(self, *args, **kwargs):
            pass

        def configure(self, *args, **kwargs):
            pass

        def lift(self, *args, **kwargs):
            pass

        def delete(self, *args, **kwargs):
            pass

        def create_image(self, *args, **kwargs):
            pass

    # Inject
    tk.Toplevel = DummyToplevel
    tk.Tk = DummyToplevel
    tk.Canvas = DummyToplevel
    ttk.Frame = DummyToplevel
    ttk.LabelFrame = DummyToplevel
    ttk.Label = DummyToplevel
    ttk.Button = DummyToplevel
    ttk.Notebook = DummyToplevel
    ttk.Scrollbar = DummyToplevel
    ttk.Separator = DummyToplevel
    
    # Mock Tkinter variables to avoid default root window creation
    tk.StringVar = MagicMock
    tk.BooleanVar = MagicMock
    tk.DoubleVar = MagicMock
    tk.IntVar = MagicMock
    
    yield
    
    # Restore
    tk.Toplevel = orig_toplevel
    tk.Tk = orig_tk
    tk.Canvas = orig_canvas
    ttk.Frame = orig_frame
    ttk.LabelFrame = orig_labelframe
    ttk.Label = orig_label
    ttk.Button = orig_button
    ttk.Notebook = orig_notebook
    ttk.Scrollbar = orig_scrollbar
    ttk.Separator = orig_separator
    tk.StringVar = orig_stringvar
    tk.BooleanVar = orig_booleanvar
    tk.DoubleVar = orig_doublevar
    tk.IntVar = orig_intvar


def test_sharpness_tool_key_event_filtering():
    from photo_selector_toolbox.sharpness_gui import SharpnessTool

    parent = MagicMock()
    parent.register = MagicMock()

    with (
        patch("photo_selector_toolbox.sharpness_gui.SharpnessTool.setup_ui"),
        patch("photo_selector_toolbox.sharpness_gui.SharpnessTool.setup_focus_ui"),
        patch("photo_selector_toolbox.sharpness_gui.SharpnessTool.bind_all"),
    ):
        tool = SharpnessTool(parent)
        # Mock winfo_toplevel
        tool.winfo_toplevel = MagicMock(return_value="MainWindow")
        tool.prev_candidate = MagicMock()
        tool.toggle_focus_mode = MagicMock()

        # Simulate key event on main window
        event_main = MagicMock()
        event_main.widget.winfo_toplevel.return_value = "MainWindow"
        
        tool.focus_mode = True
        tool.notebook = MagicMock()
        tool.review_frame = "review_frame"
        tool.notebook.select.return_value = "review_frame"
        
        # Test escape key on main window
        tool.on_escape_key(event_main)
        tool.toggle_focus_mode.assert_called_once()
        tool.toggle_focus_mode.reset_mock()

        # Simulate key event on another window (e.g. FullscreenViewer)
        event_other = MagicMock()
        event_other.widget.winfo_toplevel.return_value = "OtherWindow"
        
        tool.on_escape_key(event_other)
        tool.toggle_focus_mode.assert_not_called()


def test_fullscreen_viewer_init_and_delete():
    from photo_selector_toolbox.fullscreen_viewer import FullscreenViewer

    parent = MagicMock()
    path = Path("test_image.jpg")
    file_list = [path]

    with (
        patch("photo_selector_toolbox.fullscreen_viewer.FullscreenViewer.load_image"),
    ):
        viewer = FullscreenViewer(parent, path, file_list=file_list)
        
        # Check that delete button was created and Delete/BackSpace keys bound
        assert hasattr(viewer, "del_btn")
        assert "<Delete>" in viewer.bindings
        assert "<BackSpace>" in viewer.bindings
        assert "<m>" in viewer.bindings
        assert "<M>" in viewer.bindings
        
        # Test confirm_delete_image creates dialog
        with (
            patch("photo_selector_toolbox.fullscreen_viewer.tk.Toplevel") as mock_toplevel,
        ):
            # mock_toplevel will return a mock dialog
            mock_dialog = MagicMock()
            mock_toplevel.return_value = mock_dialog
            
            viewer.confirm_delete_image()
            mock_toplevel.assert_called_once_with(viewer)


def test_move_to_selection():
    from photo_selector_toolbox.sharpness_gui import SharpnessTool

    parent = MagicMock()
    parent.register = MagicMock()

    with (
        patch("photo_selector_toolbox.sharpness_gui.SharpnessTool.setup_ui"),
        patch("photo_selector_toolbox.sharpness_gui.SharpnessTool.setup_focus_ui"),
    ):
        tool = SharpnessTool(parent)
        tool.folder_var = MagicMock()
        tool.folder_var.get.return_value = "/mock/dir"
        
        mock_jpg = MagicMock(spec=Path)
        mock_jpg.name = "test.jpg"
        mock_jpg.suffix = ".jpg"
        mock_jpg.exists.return_value = True

        mock_raw = MagicMock(spec=Path)
        mock_raw.name = "test.arw"
        mock_raw.suffix = ".arw"
        mock_raw.exists.return_value = True
        
        tool.candidates = [mock_jpg]
        tool.sorted_files = [mock_jpg]
        tool.files_map = {mock_jpg: MagicMock()}
        tool.candidate_listbox = MagicMock()
        tool.candidate_listbox.curselection.return_value = (0,)
        tool.candidate_listbox.size.return_value = 1
        tool.panel_curr = MagicMock()
        tool.panel_prev = MagicMock()
        tool.panel_next = MagicMock()
        tool.meta_lbl = MagicMock()
        
        with (
            patch("photo_selector_toolbox.sharpness_gui.Path") as mock_path_cls,
            patch("photo_selector_toolbox.sharpness_gui.find_related_files", return_value=[mock_jpg, mock_raw]),
            patch("photo_selector_toolbox.sharpness_gui.RAW_EXTENSIONS", {".arw", ".nef", ".cr2", ".dng", ".raw"}),
        ):
            mock_selection_dir = MagicMock()
            mock_path_cls.return_value = MagicMock()
            mock_path_cls.return_value.__truediv__.return_value = mock_selection_dir
            
            mock_dirs = {}
            def get_subfolder_mock(subfolder):
                if subfolder not in mock_dirs:
                    mock_dirs[subfolder] = MagicMock()
                return mock_dirs[subfolder]
            mock_selection_dir.__truediv__.side_effect = get_subfolder_mock

            tool.execute_move_to_selection(mock_jpg, 0)
            
            mock_dirs["JPEG"].mkdir.assert_called_once_with(parents=True, exist_ok=True)
            mock_dirs["RAW"].mkdir.assert_called_once_with(parents=True, exist_ok=True)
            mock_jpg.rename.assert_called_once_with(mock_dirs["JPEG"].__truediv__.return_value)
            mock_raw.rename.assert_called_once_with(mock_dirs["RAW"].__truediv__.return_value)


def test_fullscreen_viewer_metadata_display():
    from photo_selector_toolbox.fullscreen_viewer import FullscreenViewer

    parent = MagicMock()
    path = Path("test_image.jpg")
    file_list = [path]

    # Setup parent files_map as a dict so update_metadata finds it
    parent.files_map = {}

    with (
        patch("photo_selector_toolbox.fullscreen_viewer.FullscreenViewer.load_image"),
    ):
        viewer = FullscreenViewer(parent, path, file_list=file_list)
        
        # Verify metadata labels were created
        assert hasattr(viewer, "meta_panel")
        assert hasattr(viewer, "meta_filename_lbl")
        assert hasattr(viewer, "meta_exposure_lbl")
        assert hasattr(viewer, "meta_lens_lbl")
        assert hasattr(viewer, "metric_labels")
        assert "sharpness" in viewer.metric_labels
        assert "noise" in viewer.metric_labels
        assert "highlight" in viewer.metric_labels
        assert "shadow" in viewer.metric_labels
        assert "aesthetic" in viewer.metric_labels

        # Verify config calls on path update
        with (
            patch.object(viewer.meta_filename_lbl, "config") as mock_filename_config,
            patch.object(viewer.meta_exposure_lbl, "config") as mock_exposure_config,
        ):
            viewer.load_new_path(Path("other_image.jpg"))
            mock_filename_config.assert_called_with(text="other_image.jpg")
