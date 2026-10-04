import os
import shutil
import subprocess
import sys
from pathlib import Path
from tkinter import filedialog


import tkinter as tk

class ToolTip:
    def __init__(self, widget, text):
        self.widget = widget
        self.text = text
        self.tooltip_window = None
        self.id = None

        self.widget.bind("<Enter>", self.enter)
        self.widget.bind("<Leave>", self.leave)
        self.widget.bind("<ButtonPress>", self.leave)

    def enter(self, event=None):
        self.schedule()

    def leave(self, event=None):
        self.unschedule()
        self.hide()

    def schedule(self):
        self.unschedule()
        self.id = self.widget.after(500, self.show)

    def unschedule(self):
        idx = self.id
        self.id = None
        if idx:
            self.widget.after_cancel(idx)

    def show(self):
        self.unschedule()
        if not self.text:
            return

        try:
            x = self.widget.winfo_rootx() + (self.widget.winfo_width() // 2)
            y = self.widget.winfo_rooty() + self.widget.winfo_height() + 5
        except tk.TclError:
            return

        self.tooltip_window = tw = tk.Toplevel(self.widget)
        tw.wm_overrideredirect(True)
        try:
            tw.attributes("-topmost", True)
        except Exception:
            pass
        tw.wm_geometry(f"+{x}+{y}")

        label = tk.Label(
            tw, text=self.text, justify='left',
            background="#27272A", foreground="#FAFAFA",
            relief='solid', borderwidth=1,
            font=("Helvetica", 9)
        )
        label.pack(ipadx=6, ipady=4)

    def hide(self):
        tw = self.tooltip_window
        self.tooltip_window = None
        if tw:
            try:
                tw.destroy()
            except Exception:
                pass

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
