import os
import shutil
import subprocess
import sys
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
    zenity_available = shutil.which("zenity") is not None

    if is_linux and zenity_available:
        toplevel = None
        if parent is not None and hasattr(parent, "winfo_toplevel"):
            toplevel = parent.winfo_toplevel()

        cmd = ["zenity", "--file-selection", "--directory"]
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
