import pytest
from unittest.mock import patch, MagicMock
import sys
import tkinter as tk

# Save original modules to prevent test pollution
original_modules = {}

@pytest.fixture(scope="module", autouse=True)
def mock_sys_modules():

    import importlib
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
        if name == "photo_selector_toolbox.models":
            orig_mod = importlib.import_module("photo_selector_toolbox.models")
            mock_mod.ScanResult.side_effect = orig_mod.ScanResult
            mock_mod.ExifData.side_effect = orig_mod.ExifData
        elif name == "photo_selector_toolbox.utils":
            orig_mod = importlib.import_module("photo_selector_toolbox.utils")
            mock_mod.select_representative.side_effect = orig_mod.select_representative
            mock_mod.is_excluded_subfolder.return_value = False
        sys.modules[name] = mock_mod

    yield

    for name in modules_to_mock:
        orig = original_modules[name]
        if orig is None:
            sys.modules.pop(name, None)
        else:
            sys.modules[name] = orig

def get_mock_tool(root):
    from photo_selector_toolbox.sharpness_gui import SharpnessTool
    parent = tk.Frame(root)
    # mock tk components inside to avoid errors with unmapped widgets

    tool = SharpnessTool(parent)
    # Mock tkinter specific methods that fail without a real event loop
    tool.winfo_toplevel = MagicMock()
    tool.winfo_toplevel().winfo_width.return_value = 800
    tool.winfo_toplevel().winfo_height.return_value = 600
    tool.winfo_toplevel().winfo_rootx.return_value = 0
    tool.winfo_toplevel().winfo_rooty.return_value = 0
    return tool

def test_show_scan_dialog_no_folder():
    root = tk.Tk()

    with (
        patch("photo_selector_toolbox.sharpness_gui.tk.Toplevel"),
        patch("photo_selector_toolbox.sharpness_gui.SharpnessTool.bind_all"),
        patch("photo_selector_toolbox.sharpness_gui.messagebox.showerror") as mock_showerror
    ):
        tool = get_mock_tool(root)
        tool.folder_var = MagicMock()
        tool.folder_var.get.return_value = ""  # No folder selected

        tool.show_scan_dialog()
        mock_showerror.assert_called_once_with("Error", "Please select a valid folder first.")
    root.destroy()

def test_show_scan_dialog_success():

    root = tk.Tk()

    with (
        patch("photo_selector_toolbox.sharpness_gui.tk.Toplevel") as mock_toplevel,
        patch("photo_selector_toolbox.sharpness_gui.SharpnessTool.bind_all"),
        patch("pathlib.Path.exists", return_value=True),
        patch("photo_selector_toolbox.sharpness_gui.ttk.Combobox")
    ):
        tool = get_mock_tool(root)
        tool.folder_var = MagicMock()
        tool.folder_var.get.return_value = "/mock/folder"

        # Mock widgets to capture button commands
        mock_dialog = MagicMock()
        mock_toplevel.return_value = mock_dialog

        with patch("photo_selector_toolbox.sharpness_gui.ttk.Button") as mock_button:
            tool.show_scan_dialog()

            # Verify the dialog was created
            mock_toplevel.assert_called_once_with(tool)

            # Find the 'Start Scan' and 'Cancel' buttons
            start_command = None
            for call in mock_button.call_args_list:
                args, kwargs = call
                if kwargs.get('text') == "⚡ Start Scan":
                    start_command = kwargs.get('command')

            assert start_command is not None

            # Mock start_scan to verify it gets called
            tool.start_scan = MagicMock()

            # Execute the start command
            start_command()

            # Verify the dialog was destroyed and scan started
            mock_dialog.destroy.assert_called()
            tool.start_scan.assert_called_once()
    root.destroy()

def test_show_ollama_config_dialog():
    root = tk.Tk()

    with (
        patch("photo_selector_toolbox.sharpness_gui.tk.Toplevel") as mock_toplevel,
        patch("photo_selector_toolbox.sharpness_gui.SharpnessTool.bind_all"),
        patch("photo_selector_toolbox.sharpness_gui.load_config") as mock_load_config,
        patch("photo_selector_toolbox.sharpness_gui.save_config") as mock_save_config,

        patch("photo_selector_toolbox.sharpness_gui.tk.Text") as mock_text,
    ):
        # Setup mock config
        mock_load_config.return_value = {
            "ollama_url": "http://localhost:11434",
            "ollama_model": "llava",
            "ollama_prompt": "Test prompt"
        }

        tool = get_mock_tool(root)
        mock_dialog = MagicMock()
        mock_toplevel.return_value = mock_dialog

        with patch("photo_selector_toolbox.sharpness_gui.ttk.Button") as mock_button:
            tool.show_ollama_config_dialog()

            # Verify the dialog was created
            mock_toplevel.assert_called_once_with(tool)

            # Find the 'Save Settings' button
            save_command = None
            for call in mock_button.call_args_list:
                args, kwargs = call
                if kwargs.get('text') == "💾 Save Settings":
                    save_command = kwargs.get('command')

            assert save_command is not None

            # Let's verify save logic
            # Mock get on text widget
            mock_text_instance = mock_text.return_value
            mock_text_instance.get.return_value = "Updated prompt "

            save_command()

            # save_config should be called with correct structure
            mock_save_config.assert_called_once()
            mock_dialog.destroy.assert_called()
    root.destroy()

def test_ollama_connection_test_success_match():
    root = tk.Tk()

    with (
        patch("photo_selector_toolbox.sharpness_gui.tk.Toplevel"),
        patch("photo_selector_toolbox.sharpness_gui.SharpnessTool.bind_all"),
        patch("photo_selector_toolbox.sharpness_gui.load_config",
              return_value={"ollama_url": "mock", "ollama_model": "m"}),
    ):
        tool = get_mock_tool(root)

        # We need to capture the inner run_test function via the threading.Thread call
        with patch("photo_selector_toolbox.sharpness_gui.threading.Thread") as mock_thread, \
             patch("photo_selector_toolbox.sharpness_gui.urllib.request.urlopen") as mock_urlopen, \
             patch("photo_selector_toolbox.sharpness_gui.ttk.Button") as mock_button, \
             patch("photo_selector_toolbox.sharpness_gui.ttk.Label") as mock_label:

            # Intercept variables
            url_var = MagicMock()
            url_var.get.return_value = "http://localhost:11434"
            model_var = MagicMock()
            model_var.get.return_value = "llava"

            with patch("photo_selector_toolbox.sharpness_gui.tk.StringVar", side_effect=[url_var, model_var]):
                tool.show_ollama_config_dialog()

            # Find the test button and its command
            test_cmd = None
            for call in mock_button.call_args_list:
                args, kwargs = call
                if kwargs.get('text') == "🔌 Test Connection":
                    test_cmd = kwargs.get('command')
                    break

            assert test_cmd is not None

            # Execute the command, which starts the thread
            test_cmd()

            # Get the target function from the thread creation
            run_test_func = mock_thread.call_args[1]['target']

            # Setup successful urllib response
            mock_resp = MagicMock()
            mock_resp.read.return_value = b'{"models": [{"name": "llava:latest"}]}'
            mock_resp.__enter__.return_value = mock_resp
            mock_urlopen.return_value = mock_resp

            # Run the test
            run_test_func()

            # Verify URL was requested correctly
            req_arg = mock_urlopen.call_args[0][0]
            assert "http://localhost:11434/api/tags" in req_arg.full_url

            # Check that label was configured with success message
            success_called = False
            for call in mock_label.return_value.config.call_args_list:
                args, kwargs = call
                if kwargs.get('foreground') == 'green' and 'Success' in kwargs.get('text', ''):
                    success_called = True

            assert success_called
    root.destroy()

def test_ollama_connection_test_success_no_match():
    root = tk.Tk()

    with (
        patch("photo_selector_toolbox.sharpness_gui.tk.Toplevel"),
        patch("photo_selector_toolbox.sharpness_gui.SharpnessTool.bind_all"),
        patch("photo_selector_toolbox.sharpness_gui.load_config",
              return_value={"ollama_url": "mock", "ollama_model": "m"}),
    ):
        tool = get_mock_tool(root)

        with patch("photo_selector_toolbox.sharpness_gui.threading.Thread") as mock_thread, \
             patch("photo_selector_toolbox.sharpness_gui.urllib.request.urlopen") as mock_urlopen, \
             patch("photo_selector_toolbox.sharpness_gui.ttk.Button") as mock_button, \
             patch("photo_selector_toolbox.sharpness_gui.ttk.Label") as mock_label:

            url_var = MagicMock()
            url_var.get.return_value = "http://localhost:11434"
            model_var = MagicMock()
            model_var.get.return_value = "llava" # We want llava

            with patch("photo_selector_toolbox.sharpness_gui.tk.StringVar", side_effect=[url_var, model_var]):
                tool.show_ollama_config_dialog()

            test_cmd = None
            for call in mock_button.call_args_list:
                args, kwargs = call
                if kwargs.get('text') == "🔌 Test Connection":
                    test_cmd = kwargs.get('command')
                    break

            test_cmd()
            run_test_func = mock_thread.call_args[1]['target']

            # Only has llama3, not llava
            mock_resp = MagicMock()
            mock_resp.read.return_value = b'{"models": [{"name": "llama3:latest"}]}'
            mock_resp.__enter__.return_value = mock_resp
            mock_urlopen.return_value = mock_resp

            run_test_func()

            # Check that label was configured with warning message
            orange_called = False
            for call in mock_label.return_value.config.call_args_list:
                args, kwargs = call
                if kwargs.get('foreground') == 'orange' and 'is not pulled' in kwargs.get('text', ''):
                    orange_called = True

            assert orange_called
    root.destroy()

def test_ollama_connection_test_error():
    import urllib.error
    root = tk.Tk()

    with (
        patch("photo_selector_toolbox.sharpness_gui.tk.Toplevel"),
        patch("photo_selector_toolbox.sharpness_gui.SharpnessTool.bind_all"),
        patch("photo_selector_toolbox.sharpness_gui.load_config",
              return_value={"ollama_url": "inv", "ollama_model": "m"}),
    ):
        tool = get_mock_tool(root)

        with patch("photo_selector_toolbox.sharpness_gui.threading.Thread") as mock_thread, \
             patch("photo_selector_toolbox.sharpness_gui.urllib.request.urlopen") as mock_urlopen, \
             patch("photo_selector_toolbox.sharpness_gui.ttk.Button") as mock_button, \
             patch("photo_selector_toolbox.sharpness_gui.ttk.Label") as mock_label:

            url_var = MagicMock()
            url_var.get.return_value = "invalid-url"
            model_var = MagicMock()
            model_var.get.return_value = "llava"

            with patch("photo_selector_toolbox.sharpness_gui.tk.StringVar", side_effect=[url_var, model_var]):
                tool.show_ollama_config_dialog()

            test_cmd = None
            for call in mock_button.call_args_list:
                args, kwargs = call
                if kwargs.get('text') == "🔌 Test Connection":
                    test_cmd = kwargs.get('command')
                    break

            test_cmd()
            run_test_func = mock_thread.call_args[1]['target']

            # Connection error
            mock_urlopen.side_effect = urllib.error.URLError("Connection refused")

            run_test_func()

            # Check that label was configured with error message
            red_called = False
            for call in mock_label.return_value.config.call_args_list:
                args, kwargs = call
                if kwargs.get('foreground') == 'red' and 'Cannot connect' in kwargs.get('text', ''):
                    red_called = True

            assert red_called
    root.destroy()
