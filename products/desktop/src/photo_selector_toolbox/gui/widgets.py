import os
import shutil
import subprocess
import sys
import tkinter as tk
from pathlib import Path
from tkinter import filedialog


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

class ToolTip:
    def __init__(self, widget, text):
        self.widget = widget
        self.text = text
        self.tipwindow = None
        self.id = None
        self.x = self.y = 0
        self.widget.bind("<Enter>", self.enter)
        self.widget.bind("<Leave>", self.leave)
        self.widget.bind("<ButtonPress>", self.leave)

    def enter(self, event=None):
        self.schedule()

    def leave(self, event=None):
        self.unschedule()
        self.hidetip()

    def schedule(self):
        self.unschedule()
        self.id = self.widget.after(500, self.showtip)

    def unschedule(self):
        id_ = self.id
        self.id = None
        if id_:
            self.widget.after_cancel(id_)

    def showtip(self, event=None):
        if self.tipwindow or not self.text:
            return

        # Position slightly below and to the right of the widget
        x = self.widget.winfo_rootx() + (self.widget.winfo_width() // 2)
        y = self.widget.winfo_rooty() + self.widget.winfo_height() + 5

        self.tipwindow = tw = tk.Toplevel(self.widget)
        tw.wm_overrideredirect(True)
        tw.wm_geometry(f"+{x}+{y}")

        # Use dark theme colors to match app
        label = tk.Label(tw, text=self.text, justify=tk.LEFT,
                         background="#27272A", foreground="#FAFAFA",
                         relief=tk.SOLID, borderwidth=1,
                         font=("Helvetica", 9, "normal"), padx=4, pady=2)
        label.pack(ipadx=1)

    def hidetip(self):
        tw = self.tipwindow
        self.tipwindow = None
        if tw:
            tw.destroy()
