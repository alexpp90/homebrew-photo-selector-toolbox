from unittest.mock import patch, MagicMock
from photo_selector_toolbox.gui.widgets import ask_directory


def test_ask_directory_non_linux():
    with patch("sys.platform", "darwin"), \
         patch("photo_selector_toolbox.gui.widgets.filedialog.askdirectory") as mock_ask:
        mock_ask.return_value = "/mock/dir"
        res = ask_directory(title="Test", initialdir="/start")
        assert res == "/mock/dir"
        mock_ask.assert_called_once_with(title="Test", initialdir="/start")


def test_ask_directory_linux_no_zenity():
    with patch("sys.platform", "linux"), \
         patch("photo_selector_toolbox.gui.widgets.shutil.which", return_value=None), \
         patch("photo_selector_toolbox.gui.widgets.filedialog.askdirectory") as mock_ask:
        mock_ask.return_value = "/mock/dir"
        res = ask_directory(title="Test", initialdir="/start")
        assert res == "/mock/dir"
        mock_ask.assert_called_once_with(title="Test", initialdir="/start")


def test_ask_directory_linux_with_zenity_success():
    mock_completed = MagicMock()
    mock_completed.returncode = 0
    mock_completed.stdout = "/selected/dir\n"

    with patch("sys.platform", "linux"), \
         patch("photo_selector_toolbox.gui.widgets.shutil.which", return_value="/usr/bin/zenity"), \
         patch("photo_selector_toolbox.gui.widgets.subprocess.run", return_value=mock_completed) as mock_run, \
         patch("photo_selector_toolbox.gui.widgets.filedialog.askdirectory") as mock_ask:

        res = ask_directory(title="Test", initialdir="/start")
        assert res == "/selected/dir"
        mock_run.assert_called_once()
        called_args = mock_run.call_args[0][0]
        # Check if the filename option is constructed correctly
        assert "--filename" in called_args
        filename_idx = called_args.index("--filename")
        # Use os.sep to be robust across different OS path separators testing
        import os
        assert called_args[filename_idx + 1].endswith(f"start{os.sep}")

        # Check if the title option is constructed correctly
        assert "--title" in called_args
        title_idx = called_args.index("--title")
        assert called_args[title_idx + 1] == "Test"
        mock_ask.assert_not_called()


def test_ask_directory_linux_with_zenity_cancel():
    mock_completed = MagicMock()
    mock_completed.returncode = 1

    with patch("sys.platform", "linux"), \
         patch("photo_selector_toolbox.gui.widgets.shutil.which", return_value="/usr/bin/zenity"), \
         patch("photo_selector_toolbox.gui.widgets.subprocess.run", return_value=mock_completed) as mock_run, \
         patch("photo_selector_toolbox.gui.widgets.filedialog.askdirectory") as mock_ask:

        res = ask_directory(title="Test", initialdir="/start")
        assert res == ""
        mock_run.assert_called_once()
        mock_ask.assert_not_called()


def test_ask_directory_linux_with_zenity_fallback():
    mock_completed = MagicMock()
    mock_completed.returncode = -1

    with patch("sys.platform", "linux"), \
         patch("photo_selector_toolbox.gui.widgets.shutil.which", return_value="/usr/bin/zenity"), \
         patch("photo_selector_toolbox.gui.widgets.subprocess.run", return_value=mock_completed) as mock_run, \
         patch("photo_selector_toolbox.gui.widgets.filedialog.askdirectory", return_value="/fallback/dir") as mock_ask:

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
         patch("photo_selector_toolbox.gui.widgets.shutil.which", return_value="/usr/bin/zenity"), \
         patch("photo_selector_toolbox.gui.widgets.subprocess.run", return_value=mock_completed):

        res = ask_directory(parent=mock_parent, title="Test", initialdir="/start")
        assert res == "/selected/dir"

        mock_toplevel.attributes.assert_any_call("-disabled", True)
        mock_toplevel.attributes.assert_any_call("-disabled", False)


def test_tooltip_lifecycle():
    from photo_selector_toolbox.gui.widgets import ToolTip

    mock_widget = MagicMock()
    mock_widget.winfo_exists.return_value = True
    mock_widget.winfo_viewable.return_value = True
    mock_widget.winfo_rootx.return_value = 100
    mock_widget.winfo_rooty.return_value = 200
    mock_widget.winfo_width.return_value = 50
    mock_widget.winfo_height.return_value = 20
    mock_widget.after.return_value = "after_id_123"

    tip = ToolTip(mock_widget, "Sample help text", delay_ms=300)
    assert tip.text == "Sample help text"
    assert tip.delay_ms == 300
    assert mock_widget.bind.call_count >= 4

    # Test Enter schedules show
    tip._on_enter()
    mock_widget.after.assert_called_with(300, tip.show)
    assert tip._schedule_id == "after_id_123"

    # Test Leave cancels schedule
    tip._on_leave()
    mock_widget.after_cancel.assert_called_with("after_id_123")
    assert tip._schedule_id is None

    # Test show()
    with patch("tkinter.Toplevel") as mock_top, patch("tkinter.Label") as mock_lbl:
        mock_tw = MagicMock()
        mock_top.return_value = mock_tw

        tip.show()
        mock_top.assert_called_once_with(mock_widget)
        mock_tw.wm_geometry.assert_called_once_with("+125+224")
        mock_lbl.assert_called_once()
        assert tip.tooltip_window == mock_tw

        # Test hide() destroys window
        tip.hide()
        mock_tw.destroy.assert_called_once()
        assert tip.tooltip_window is None


def test_tooltip_empty_or_hidden_widget():
    from photo_selector_toolbox.gui.widgets import ToolTip

    mock_widget = MagicMock()
    tip_empty = ToolTip(mock_widget, "")
    tip_empty.show()
    assert tip_empty.tooltip_window is None

    mock_widget.winfo_exists.return_value = False
    tip = ToolTip(mock_widget, "Help")
    tip.show()
    assert tip.tooltip_window is None


def test_apply_dark_theme_to_fig():
    import matplotlib.pyplot as plt
    import matplotlib.colors as mcolors
    from photo_selector_toolbox.gui.app import apply_dark_theme_to_fig

    fig, (ax1, ax2) = plt.subplots(1, 2)
    ax1.set_title("Title 1")
    ax1.set_xlabel("X1")
    ax1.set_ylabel("Y1")
    bars = ax1.bar(["A", "B"], [10, 20])

    ax2.set_title("Title 2")
    ax2.set_xlabel("X2")
    ax2.set_ylabel("Y2")

    apply_dark_theme_to_fig(fig)

    assert mcolors.to_hex(fig.patch.get_facecolor()).upper() == "#18181B"

    for ax in (ax1, ax2):
        assert mcolors.to_hex(ax.get_facecolor()).upper() == "#27272A"
        for spine in ("bottom", "top", "left", "right"):
            assert mcolors.to_hex(ax.spines[spine].get_edgecolor()).upper() == "#3F3F46"
        assert mcolors.to_hex(ax.title.get_color()).upper() == "#F4F4F5"
        assert mcolors.to_hex(ax.xaxis.label.get_color()).upper() == "#F4F4F5"
        assert mcolors.to_hex(ax.yaxis.label.get_color()).upper() == "#F4F4F5"

    for bar in bars:
        assert mcolors.to_hex(bar.get_facecolor()).upper() == "#6366F1"

    plt.close(fig)


def test_apply_dark_theme_to_fig_empty():
    import matplotlib.pyplot as plt
    import matplotlib.colors as mcolors
    from photo_selector_toolbox.gui.app import apply_dark_theme_to_fig

    fig = plt.figure()
    apply_dark_theme_to_fig(fig)
    assert mcolors.to_hex(fig.patch.get_facecolor()).upper() == "#18181B"
    plt.close(fig)
