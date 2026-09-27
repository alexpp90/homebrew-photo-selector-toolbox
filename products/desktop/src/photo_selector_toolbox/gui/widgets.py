import os
import shutil
import subprocess
import sys
from pathlib import Path
import tkinter as tk
from tkinter import filedialog, ttk


class Tooltip:
    """
    A simple tooltip class for Tkinter widgets.
    """
    def __init__(self, widget, text, delay=500):
        self.widget = widget
        self.text = text
        self.delay = delay
        self.tooltip_window = None
        self.id = None
        self.widget.bind("<Enter>", self.schedule)
        self.widget.bind("<Leave>", self.hide)
        self.widget.bind("<ButtonPress>", self.hide)

    def schedule(self, event=None):
        self.unschedule()
        self.id = self.widget.after(self.delay, self.show)

    def unschedule(self):
        id_ = self.id
        self.id = None
        if id_:
            self.widget.after_cancel(id_)

    def show(self, event=None):
        if self.tooltip_window or not self.text:
            return
        x = self.widget.winfo_rootx() + 20
        y = self.widget.winfo_rooty() + self.widget.winfo_height() + 5
        self.tooltip_window = tw = tk.Toplevel(self.widget)
        tw.wm_overrideredirect(True)
        tw.wm_geometry(f"+{x}+{y}")
        label = ttk.Label(tw, text=self.text, justify='left',
                          background="#ffffe0", relief='solid', borderwidth=1,
                          font=("tahoma", "8", "normal"), padding=(4, 2))
        label.pack(ipadx=1)

    def hide(self, event=None):
        self.unschedule()
        tw = self.tooltip_window
        self.tooltip_window = None
        if tw:
            tw.destroy()


def add_tooltip(widget, text, delay=500):
    return Tooltip(widget, text, delay)


def ask_directory(parent=None, title=None, initialdir=None):
    """
    Open a directory selector dialog. On Linux, if zenity is available, use zenity.
    Otherwise, fall back to Tkinter's filedialog.askdirectory.

    Args:
        parent: The Tkinter parent window/widget.
        title: Descriptive window title.
        initialdir: The starting directory path.

    Returns:
        str: Selected folder path, or empty string if cancelled.
    """
    is_linux = sys.platform.startswith("linux")
    zenity_path = shutil.which("zenity")

    if is_linux and zenity_path:
        toplevel = None
        if parent is not None and hasattr(parent, "winfo_toplevel"):
            toplevel = parent.winfo_toplevel()

        cmd = [zenity_path, "--file-selection", "--directory"]
        if title:
            cmd.extend(["--title", title])

        if initialdir:
            initialdir_str = str(Path(initialdir).absolute())
            if not initialdir_str.endswith(os.sep):
                initialdir_str += os.sep
            cmd.extend(["--filename", initialdir_str])

        try:
            if toplevel:
                try:
                    toplevel.attributes("-disabled", True)
                except Exception:
                    pass

            result = subprocess.run(cmd, capture_output=True, text=True)

            if result.returncode == 0:
                return result.stdout.strip()
            elif result.returncode == 1:
                return ""
            else:
                raise RuntimeError(f"Zenity exited with return code {result.returncode}")
        except Exception:
            pass
        finally:
            if toplevel:
                try:
                    toplevel.attributes("-disabled", False)
                    toplevel.focus_force()
                except Exception:
                    pass

    # Fallback to Tkinter filedialog.askdirectory
    kwargs = {}
    if parent is not None:
        kwargs["parent"] = parent
    if title is not None:
        kwargs["title"] = title
    if initialdir is not None:
        kwargs["initialdir"] = initialdir

    return filedialog.askdirectory(**kwargs)
