import pytest
from unittest.mock import MagicMock, patch
import tkinter as tk
import sys

# Minimal mock to avoid test hangs/crashes
@pytest.fixture(scope="module", autouse=True)
def mock_sys_modules():
    original_modules = {}
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

def test_key_handlers_early_returns():
    from photo_selector_toolbox.sharpness_gui import SharpnessTool
    root = tk.Tk()
    parent = tk.Frame(root)
    with patch("photo_selector_toolbox.sharpness_gui.tk.Toplevel"), \
         patch("photo_selector_toolbox.sharpness_gui.SharpnessTool.setup_ui"), \
         patch("photo_selector_toolbox.sharpness_gui.SharpnessTool.setup_focus_ui"), \
         patch("photo_selector_toolbox.sharpness_gui.SharpnessTool.bind_all"):
        tool = SharpnessTool(parent)
        tool.notebook = MagicMock()
        tool.review_frame = tk.Frame(tool)

        event = MagicMock()
        event.widget = "some_widget"

        for action in ["left", "right", "delete", "move", "copy"]:
            method = getattr(tool, f"on_{action}_key")
            target_name = {"left": "prev_candidate", "right": "next_candidate",
                                   "delete": "delete_current_candidate",
                                   "move": "move_current_to_selection",
                                   "copy": "copy_current_to_selection"}[action]
            setattr(tool, target_name, MagicMock())
            target = getattr(tool, target_name)

            # 1. Widget is an entry -> early return
            entry = tk.Entry(root)
            tool._resolve_widget = MagicMock(return_value=entry)
            method(event)
            target.assert_not_called()

            # 2. Not in review tab and not in focus mode -> early return
            widget = MagicMock()
            tool._resolve_widget.return_value = widget
            widget.winfo_toplevel.return_value = "top"
            tool.winfo_toplevel = MagicMock(return_value="top")

            tool.notebook.select.return_value = "not_review"
            tool.focus_mode = False
            method(event)
            target.assert_not_called()

            # 3. Successful call (in review mode)
            tool.notebook.select.return_value = str(tool.review_frame)
            method(event)
            target.assert_called_once()
            target.reset_mock()

            # 4. Successful call (in focus mode)
            tool.notebook.select.return_value = "not_review"
            tool.focus_mode = True
            method(event)
            target.assert_called_once()
            target.reset_mock()
    root.destroy()

def test_escape_key_handler():
    from photo_selector_toolbox.sharpness_gui import SharpnessTool
    root = tk.Tk()
    parent = tk.Frame(root)
    with patch("photo_selector_toolbox.sharpness_gui.tk.Toplevel"), \
         patch("photo_selector_toolbox.sharpness_gui.SharpnessTool.setup_ui"), \
         patch("photo_selector_toolbox.sharpness_gui.SharpnessTool.setup_focus_ui"), \
         patch("photo_selector_toolbox.sharpness_gui.SharpnessTool.bind_all"):
        tool = SharpnessTool(parent)
        tool.notebook = MagicMock()
        tool.review_frame = tk.Frame(tool)

        event = MagicMock()
        event.widget = "some_widget"
        tool.toggle_focus_mode = MagicMock()

        # 1. Early return if not in review tab and not focus mode
        widget = MagicMock()
        tool._resolve_widget = MagicMock(return_value=widget)
        widget.winfo_toplevel.return_value = "top"
        tool.winfo_toplevel = MagicMock(return_value="top")

        tool.notebook.select.return_value = "not_review"
        tool.focus_mode = False
        tool.on_escape_key(event)
        tool.toggle_focus_mode.assert_not_called()

        # 2. Early return if in review tab but not focus mode
        tool.notebook.select.return_value = str(tool.review_frame)
        tool.focus_mode = False
        tool.on_escape_key(event)
        tool.toggle_focus_mode.assert_not_called()

        # 3. Successful call if in focus mode
        tool.notebook.select.return_value = "not_review"
        tool.focus_mode = True
        tool.on_escape_key(event)
        tool.toggle_focus_mode.assert_called_once()

    root.destroy()

def test_show_scan_dialog_validation():
    from photo_selector_toolbox.sharpness_gui import SharpnessTool
    root = tk.Tk()
    parent = tk.Frame(root)
    with patch("photo_selector_toolbox.sharpness_gui.tk.Toplevel") as mock_toplevel, \
         patch("photo_selector_toolbox.sharpness_gui.SharpnessTool.setup_ui"), \
         patch("photo_selector_toolbox.sharpness_gui.SharpnessTool.setup_focus_ui"), \
         patch("photo_selector_toolbox.sharpness_gui.SharpnessTool.bind_all"), \
         patch("photo_selector_toolbox.sharpness_gui.messagebox.showerror") as mock_showerror, \
         patch("photo_selector_toolbox.sharpness_gui.Path.exists", return_value=False):

        tool = SharpnessTool(parent)

        # Test empty folder
        tool.folder_var = MagicMock()
        tool.folder_var.get.return_value = ""
        tool.show_scan_dialog()
        mock_showerror.assert_called_once_with("Error", "Please select a valid folder first.")
        mock_toplevel.assert_not_called()
        mock_showerror.reset_mock()

        # Test non-existent folder
        tool.folder_var.get.return_value = "/invalid/path"
        tool.show_scan_dialog()
        mock_showerror.assert_called_once_with("Error", "Please select a valid folder first.")
        mock_toplevel.assert_not_called()

    root.destroy()

def test_show_scan_dialog_creation():
    from photo_selector_toolbox.sharpness_gui import SharpnessTool
    root = tk.Tk()
    parent = tk.Frame(root)

    # We need to mock a lot of tk widgets to not have to spawn a real Toplevel
    with patch("photo_selector_toolbox.sharpness_gui.tk.Toplevel") as mock_toplevel, \
         patch("photo_selector_toolbox.sharpness_gui.ttk.Label"), \
         patch("photo_selector_toolbox.sharpness_gui.ttk.Frame"), \
         patch("photo_selector_toolbox.sharpness_gui.ttk.Checkbutton"), \
         patch("photo_selector_toolbox.sharpness_gui.ttk.Combobox"), \
         patch("photo_selector_toolbox.sharpness_gui.ttk.Button"), \
         patch("photo_selector_toolbox.sharpness_gui.SharpnessTool.setup_ui"), \
         patch("photo_selector_toolbox.sharpness_gui.SharpnessTool.setup_focus_ui"), \
         patch("photo_selector_toolbox.sharpness_gui.SharpnessTool.bind_all"), \
         patch("photo_selector_toolbox.sharpness_gui.Path.exists", return_value=True):

        tool = SharpnessTool(parent)
        tool.folder_var = MagicMock()
        tool.folder_var.get.return_value = "/valid/path"

        mock_dialog = MagicMock()
        mock_toplevel.return_value = mock_dialog

        tool.show_scan_dialog()

        mock_toplevel.assert_called_once()
        mock_dialog.title.assert_called_once_with("Scan Settings")
        mock_dialog.transient.assert_called_once()
        mock_dialog.grab_set.assert_called_once()

    root.destroy()

def test_toggle_focus_mode():
    from photo_selector_toolbox.sharpness_gui import SharpnessTool
    root = tk.Tk()
    parent = tk.Frame(root)
    main_app = MagicMock()
    parent.master = main_app

    with patch("photo_selector_toolbox.sharpness_gui.tk.Toplevel"), \
         patch("photo_selector_toolbox.sharpness_gui.SharpnessTool.setup_ui"), \
         patch("photo_selector_toolbox.sharpness_gui.SharpnessTool.setup_focus_ui"), \
         patch("photo_selector_toolbox.sharpness_gui.SharpnessTool.bind_all"):

        tool = SharpnessTool(parent)

        # Setup mocks for UI elements
        tool.notebook = MagicMock()
        tool.focus_frame = MagicMock()
        tool.panel_curr = MagicMock()
        tool.panel_curr.path = "some_path"
        tool.load_triplet_view = MagicMock()
        tool.refresh_active_view = MagicMock()

        # Test enabling focus mode
        tool.focus_mode = False
        tool.toggle_focus_mode()

        assert tool.focus_mode is True
        main_app.toggle_sidebar.assert_called_once_with(False)
        tool.notebook.pack_forget.assert_called_once()
        tool.focus_frame.pack.assert_called_once()
        tool.focus_frame.focus_set.assert_called_once()
        tool.load_triplet_view.assert_called_once_with("some_path")

        # Reset mocks
        main_app.toggle_sidebar.reset_mock()
        tool.notebook.pack_forget.reset_mock()
        tool.focus_frame.pack.reset_mock()
        tool.load_triplet_view.reset_mock()

        # Test disabling focus mode
        tool.focus_mode = True
        tool.toggle_focus_mode()

        assert tool.focus_mode is False
        main_app.toggle_sidebar.assert_called_once_with(True)
        tool.focus_frame.pack_forget.assert_called_once()
        tool.notebook.pack.assert_called_once()
        tool.load_triplet_view.assert_called_once_with("some_path")

    root.destroy()
