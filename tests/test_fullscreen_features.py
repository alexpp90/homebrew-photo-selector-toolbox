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
    to_pop = [
        m for m in list(sys.modules.keys())
        if m.startswith("photo_selector_toolbox") and m not in existing_modules
    ]
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

        def winfo_rootx(self):
            return 100

        def winfo_rooty(self):
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
        assert hasattr(viewer, "copy_btn")
        assert "<Delete>" in viewer.bindings
        assert "<BackSpace>" in viewer.bindings
        assert "<m>" in viewer.bindings
        assert "<M>" in viewer.bindings
        assert "<c>" in viewer.bindings
        assert "<C>" in viewer.bindings

        # Test confirm_delete_image behavior
        with (
            patch("photo_selector_toolbox.fullscreen_viewer.tk.Toplevel") as mock_toplevel,
            patch("photo_selector_toolbox.fullscreen_viewer.ttk.Button") as mock_button,
            patch.object(viewer, "execute_delete_current") as mock_execute_delete,
        ):
            # Test No Path logic
            viewer.path = None
            viewer.confirm_delete_image()
            mock_toplevel.assert_not_called()

            # Reset path
            viewer.path = path

            # mock_toplevel will return a mock dialog
            mock_dialog = MagicMock()
            mock_dialog.winfo_exists.return_value = True
            mock_toplevel.return_value = mock_dialog

            # Capture button commands
            button_kwargs = []
            def button_side_effect(*args, **kwargs):
                button_kwargs.append(kwargs)
                return MagicMock()
            mock_button.side_effect = button_side_effect

            viewer.confirm_delete_image()
            mock_toplevel.assert_called_once_with(viewer)

            # Verify bindings
            mock_dialog.bind.assert_any_call("<Delete>", button_kwargs[0]["command"])
            mock_dialog.bind.assert_any_call("<BackSpace>", button_kwargs[0]["command"])
            mock_dialog.bind.assert_any_call("<Escape>", button_kwargs[1]["command"])

            # Test multiple dialogs prevention
            viewer.confirm_delete_image()
            # Call count should still be 1
            assert mock_toplevel.call_count == 1

            # Test Confirm Command
            confirm_cmd = button_kwargs[0]["command"]
            confirm_cmd()
            mock_dialog.destroy.assert_called_once()
            mock_execute_delete.assert_called_once()

            # Reset mocks
            mock_dialog.destroy.reset_mock()
            mock_execute_delete.reset_mock()

            # Test Cancel Command
            cancel_cmd = button_kwargs[1]["command"]
            cancel_cmd()
            mock_dialog.destroy.assert_called_once()
            mock_execute_delete.assert_not_called()


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
        mock_jpg.stem = "test"
        mock_jpg.suffix = ".jpg"
        mock_jpg.exists.return_value = True

        mock_raw = MagicMock(spec=Path)
        mock_raw.name = "test.arw"
        mock_raw.stem = "test"
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
            patch("photo_selector_toolbox.sharpness_gui.load_config",
                  return_value={"selection_folder": "Selection", "separate_raw_jpeg": True}),
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

        with (
            patch.object(viewer.meta_filename_lbl, "config") as mock_filename_config,
            patch.object(viewer.meta_exposure_lbl, "config"),
        ):
            viewer.load_new_path(Path("other_image.jpg"))
            mock_filename_config.assert_called_with(text="other_image.jpg")


def test_copy_to_selection():
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
        mock_jpg.stem = "test"
        mock_jpg.suffix = ".jpg"
        mock_jpg.exists.return_value = True

        mock_raw = MagicMock(spec=Path)
        mock_raw.name = "test.arw"
        mock_raw.stem = "test"
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
            patch("photo_selector_toolbox.sharpness_gui.load_config",
                  return_value={"selection_folder": "Selection", "separate_raw_jpeg": True}),
            patch("photo_selector_toolbox.sharpness_gui.Path") as mock_path_cls,
            patch("photo_selector_toolbox.sharpness_gui.find_related_files", return_value=[mock_jpg, mock_raw]),
            patch("photo_selector_toolbox.sharpness_gui.RAW_EXTENSIONS", {".arw", ".nef", ".cr2", ".dng", ".raw"}),
            patch("photo_selector_toolbox.sharpness_gui.shutil.copy2") as mock_copy2,
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

            tool.execute_copy_to_selection(mock_jpg, 0)

            mock_dirs["JPEG"].mkdir.assert_called_once_with(parents=True, exist_ok=True)
            mock_dirs["RAW"].mkdir.assert_called_once_with(parents=True, exist_ok=True)
            mock_copy2.assert_any_call(mock_jpg, mock_dirs["JPEG"].__truediv__.return_value)
            mock_copy2.assert_any_call(mock_raw, mock_dirs["RAW"].__truediv__.return_value)

            # Candidate list should not shrink since it is copy (unlike move)
            assert mock_jpg in tool.candidates


def test_move_to_selection_no_separation():
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
        mock_jpg.stem = "test"
        mock_jpg.suffix = ".jpg"
        mock_jpg.exists.return_value = True

        mock_raw = MagicMock(spec=Path)
        mock_raw.name = "test.arw"
        mock_raw.stem = "test"
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
            patch("photo_selector_toolbox.sharpness_gui.load_config",
                  return_value={"selection_folder": "Selection", "separate_raw_jpeg": False}),
            patch("photo_selector_toolbox.sharpness_gui.Path") as mock_path_cls,
            patch("photo_selector_toolbox.sharpness_gui.find_related_files", return_value=[mock_jpg, mock_raw]),
            patch("photo_selector_toolbox.sharpness_gui.RAW_EXTENSIONS", {".arw", ".nef", ".cr2", ".dng", ".raw"}),
        ):
            mock_selection_dir = MagicMock()
            mock_path_cls.return_value = MagicMock()
            # Since no separation, we use the root selection_dir itself
            mock_path_cls.return_value.__truediv__.return_value = mock_selection_dir

            tool.execute_move_to_selection(mock_jpg, 0)

            # Directory should be created once at the beginning
            mock_selection_dir.mkdir.assert_called_once_with(parents=True, exist_ok=True)
            # rename should be directly to the root of selection folder
            mock_jpg.rename.assert_called_once_with(mock_selection_dir.__truediv__.return_value)
            mock_raw.rename.assert_called_once_with(mock_selection_dir.__truediv__.return_value)


def test_move_to_selection_custom_absolute_path():
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
        mock_jpg.stem = "test"
        mock_jpg.suffix = ".jpg"
        mock_jpg.exists.return_value = True

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
            patch("photo_selector_toolbox.sharpness_gui.load_config",
                  return_value={"selection_folder": "/custom/absolute/path", "separate_raw_jpeg": False}),
            patch("photo_selector_toolbox.sharpness_gui.Path") as mock_path_cls,
            patch("photo_selector_toolbox.sharpness_gui.find_related_files", return_value=[mock_jpg]),
            patch("photo_selector_toolbox.sharpness_gui.os.path.isabs", return_value=True),
        ):
            mock_custom_dir = MagicMock()
            mock_path_cls.return_value = mock_custom_dir

            tool.execute_move_to_selection(mock_jpg, 0)

            # Should create and use /custom/absolute/path directly
            mock_custom_dir.mkdir.assert_called_once_with(parents=True, exist_ok=True)
            mock_jpg.rename.assert_called_once_with(mock_custom_dir.__truediv__.return_value)


def test_move_to_selection_with_lightroom_edit():
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
        mock_jpg.stem = "test"
        mock_jpg.suffix = ".jpg"
        mock_jpg.exists.return_value = True

        mock_edit_tif = MagicMock(spec=Path)
        mock_edit_tif.name = "test-Edit.tif"
        mock_edit_tif.stem = "test-Edit"
        mock_edit_tif.suffix = ".tif"
        mock_edit_tif.exists.return_value = True

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
            patch("photo_selector_toolbox.sharpness_gui.load_config",
                  return_value={"selection_folder": "Selection", "separate_raw_jpeg": True}),
            patch("photo_selector_toolbox.sharpness_gui.Path") as mock_path_cls,
            patch("photo_selector_toolbox.sharpness_gui.find_related_files", return_value=[mock_jpg, mock_edit_tif]),
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

            # The JPG goes to JPEG subfolder, but the Lightroom Edit goes to RAW subfolder
            mock_dirs["JPEG"].mkdir.assert_called_once_with(parents=True, exist_ok=True)
            mock_dirs["RAW"].mkdir.assert_called_once_with(parents=True, exist_ok=True)
            mock_jpg.rename.assert_called_once_with(mock_dirs["JPEG"].__truediv__.return_value)
            mock_edit_tif.rename.assert_called_once_with(mock_dirs["RAW"].__truediv__.return_value)

def test_fullscreen_viewer_update_metadata_error_path():
    from photo_selector_toolbox.fullscreen_viewer import FullscreenViewer

    parent = MagicMock()
    path = Path("test_image.jpg")
    file_list = [path]
    parent.files_map = {}

    with (
        patch("photo_selector_toolbox.fullscreen_viewer.FullscreenViewer.load_image"),
        patch("photo_selector_toolbox.fullscreen_viewer.FullscreenViewer._update_basic_labels") as mock_update,
        patch("photo_selector_toolbox.fullscreen_viewer.logger.debug") as mock_logger,
    ):
        viewer = FullscreenViewer(parent, path, file_list=file_list)
        mock_update.side_effect = Exception("Test Exception")
        viewer.update_metadata()
        mock_logger.assert_any_call("Error updating metadata overlay in fullscreen: Test Exception")

def test_fullscreen_viewer_redraw_error_path():
    from photo_selector_toolbox.fullscreen_viewer import FullscreenViewer

    parent = MagicMock()
    path = Path("test_image.jpg")
    file_list = [path]

    with patch("photo_selector_toolbox.fullscreen_viewer.FullscreenViewer.load_image"), \
         patch("photo_selector_toolbox.fullscreen_viewer.logger.error") as mock_logger_error:

        viewer = FullscreenViewer(parent, path, file_list=file_list)
        viewer.pil_image = MagicMock()
        viewer.pil_image.width = 100
        viewer.pil_image.height = 100
        viewer.pil_image.crop.side_effect = Exception("Test redraw error")

        viewer.scale = 1.0
        viewer.offset_x = 0
        viewer.offset_y = 0
        viewer.winfo_width = MagicMock(return_value=100)
        viewer.winfo_height = MagicMock(return_value=100)
        viewer.canvas = MagicMock()

        viewer.redraw()

        mock_logger_error.assert_called_once_with("Redraw error: Test redraw error")

def test_fullscreen_viewer_metadata_error_handling():
    from photo_selector_toolbox.fullscreen_viewer import FullscreenViewer

    path = Path("test_image.jpg")

    class DummyScanResult:
        def __init__(self):
            self.exif = None
            self.sharpness = 0.5
            self.noise = 0.5
            self.highlight = 0.5
            self.shadow = 0.5
            self.aesthetic = 0.5
            self.score = 0.5
            self.noise_score = 0.5
            self.highlight_score = 0.5
            self.shadow_score = 0.5
            self.aesthetic_score = 0.5
            self.scores = {}

    dummy_res = DummyScanResult()

    class FakeParent:
        pass

    fake_parent = FakeParent()
    fake_parent.files_map = {path: dummy_res}

    viewer = MagicMock()
    viewer.path = path
    viewer.parent = fake_parent

    # By mocking out photo_selector_toolbox.fullscreen_viewer.ExifData we control exactly what goes into dummy_res.exif
    class MockExifData:
        pass

    with patch("photo_selector_toolbox.fullscreen_viewer.get_exif_data", side_effect=Exception("Test Exif Error")):
        with patch("photo_selector_toolbox.fullscreen_viewer.ExifData", return_value=MockExifData()):
            with patch("photo_selector_toolbox.fullscreen_viewer.logger.debug") as mock_logger:
                FullscreenViewer.update_metadata(viewer)

                mock_logger.assert_any_call("Failed to load EXIF data dynamically in fullscreen: Test Exif Error")

                assert dummy_res.exif is not None
                assert type(dummy_res.exif).__name__ == "MockExifData"

def test_fullscreen_viewer_redraw_canvas_error():
    from photo_selector_toolbox.fullscreen_viewer import FullscreenViewer

    parent = MagicMock()
    path = Path("test_image.jpg")
    file_list = [path]

    with patch("photo_selector_toolbox.fullscreen_viewer.FullscreenViewer.load_image"), \
         patch("photo_selector_toolbox.fullscreen_viewer.ImageTk.PhotoImage"), \
         patch("photo_selector_toolbox.fullscreen_viewer.logger.error") as mock_logger_error:

        viewer = FullscreenViewer(parent, path, file_list=file_list)

        # Setup mock pil_image
        viewer.pil_image = MagicMock()
        viewer.pil_image.width = 100
        viewer.pil_image.height = 100
        # Mock crop and resize so they don't fail
        mock_region = MagicMock()
        mock_region.width = 50
        mock_region.height = 50
        mock_region.resize.return_value = mock_region
        viewer.pil_image.crop.return_value = mock_region

        viewer.scale = 1.0
        viewer.offset_x = 0
        viewer.offset_y = 0
        viewer.winfo_width = MagicMock(return_value=100)
        viewer.winfo_height = MagicMock(return_value=100)

        # Setup canvas mock to fail on create_image
        viewer.canvas = MagicMock()
        viewer.canvas.create_image.side_effect = Exception("Canvas create_image error")

        viewer.redraw()

        mock_logger_error.assert_called_once_with("Redraw error: Canvas create_image error")
