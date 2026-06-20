from unittest.mock import patch, MagicMock
from photo_selector_toolbox.gui_utils import ask_directory


def test_ask_directory_non_linux():
    with patch("sys.platform", "darwin"), \
         patch("photo_selector_toolbox.gui_utils.filedialog.askdirectory") as mock_ask:
        mock_ask.return_value = "/mock/dir"
        res = ask_directory(title="Test", initialdir="/start")
        assert res == "/mock/dir"
        mock_ask.assert_called_once_with(title="Test", initialdir="/start")


def test_ask_directory_linux_no_zenity():
    with patch("sys.platform", "linux"), \
         patch("photo_selector_toolbox.gui_utils.shutil.which", return_value=None), \
         patch("photo_selector_toolbox.gui_utils.filedialog.askdirectory") as mock_ask:
        mock_ask.return_value = "/mock/dir"
        res = ask_directory(title="Test", initialdir="/start")
        assert res == "/mock/dir"
        mock_ask.assert_called_once_with(title="Test", initialdir="/start")


def test_ask_directory_linux_with_zenity_success():
    mock_completed = MagicMock()
    mock_completed.returncode = 0
    mock_completed.stdout = "/selected/dir\n"

    with patch("sys.platform", "linux"), \
         patch("photo_selector_toolbox.gui_utils.shutil.which", return_value="/usr/bin/zenity"), \
         patch("photo_selector_toolbox.gui_utils.subprocess.run", return_value=mock_completed) as mock_run, \
         patch("photo_selector_toolbox.gui_utils.filedialog.askdirectory") as mock_ask:

        res = ask_directory(title="Test", initialdir="/start")
        assert res == "/selected/dir"
        mock_run.assert_called_once()
        called_args = mock_run.call_args[0][0]
        # Check if the filename option is constructed correctly
        filename_arg = [arg for arg in called_args if arg.startswith("--filename=")]
        assert len(filename_arg) == 1
        assert filename_arg[0].endswith("/start/")
        mock_ask.assert_not_called()


def test_ask_directory_linux_with_zenity_cancel():
    mock_completed = MagicMock()
    mock_completed.returncode = 1

    with patch("sys.platform", "linux"), \
         patch("photo_selector_toolbox.gui_utils.shutil.which", return_value="/usr/bin/zenity"), \
         patch("photo_selector_toolbox.gui_utils.subprocess.run", return_value=mock_completed) as mock_run, \
         patch("photo_selector_toolbox.gui_utils.filedialog.askdirectory") as mock_ask:

        res = ask_directory(title="Test", initialdir="/start")
        assert res == ""
        mock_run.assert_called_once()
        mock_ask.assert_not_called()


def test_ask_directory_linux_with_zenity_fallback():
    mock_completed = MagicMock()
    mock_completed.returncode = -1

    with patch("sys.platform", "linux"), \
         patch("photo_selector_toolbox.gui_utils.shutil.which", return_value="/usr/bin/zenity"), \
         patch("photo_selector_toolbox.gui_utils.subprocess.run", return_value=mock_completed) as mock_run, \
         patch("photo_selector_toolbox.gui_utils.filedialog.askdirectory", return_value="/fallback/dir") as mock_ask:

        res = ask_directory(title="Test", initialdir="/start")
        assert res == "/fallback/dir"
        mock_run.assert_called_once()
        mock_ask.assert_called_once_with(title="Test", initialdir="/start")


def test_ask_directory_modality():
    mock_parent = MagicMock()
    mock_toplevel = MagicMock()
    mock_parent.winfo_toplevel.return_value = mock_toplevel

    mock_completed = MagicMock()
    mock_completed.returncode = 0
    mock_completed.stdout = "/selected/dir\n"

    with patch("sys.platform", "linux"), \
         patch("photo_selector_toolbox.gui_utils.shutil.which", return_value="/usr/bin/zenity"), \
         patch("photo_selector_toolbox.gui_utils.subprocess.run", return_value=mock_completed):

        res = ask_directory(parent=mock_parent, title="Test", initialdir="/start")
        assert res == "/selected/dir"

        mock_toplevel.attributes.assert_any_call("-disabled", True)
        mock_toplevel.attributes.assert_any_call("-disabled", False)
