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
    """
    Displays an accessible tooltip over a Tkinter widget upon hover with a configurable delay.
    """

    def __init__(self, widget, text: str, delay_ms: int = 400):
        self.widget = widget
        self.text = text
        self.delay_ms = delay_ms
        self.tooltip_window = None
        self._schedule_id = None

        for seq, handler in [
            ("<Enter>", self._on_enter),
            ("<Leave>", self._on_leave),
            ("<FocusIn>", self._on_enter),
            ("<FocusOut>", self._on_leave),
            ("<ButtonPress>", self._on_leave),
            ("<Destroy>", self._on_destroy),
        ]:
            try:
                self.widget.bind(seq, handler, add="+")
            except TypeError:
                try:
                    self.widget.bind(seq, handler)
                except Exception:
                    pass
            except Exception:
                pass

    def _on_enter(self, event=None):
        self._cancel_scheduled()
        try:
            self._schedule_id = self.widget.after(self.delay_ms, self.show)
        except Exception:
            self._schedule_id = None

    def _on_leave(self, event=None):
        self._cancel_scheduled()
        self.hide()

    def _on_destroy(self, event=None):
        self._cancel_scheduled()
        self.hide()

    def _cancel_scheduled(self):
        if self._schedule_id is not None:
            try:
                self.widget.after_cancel(self._schedule_id)
            except Exception:
                pass
            self._schedule_id = None

    def show(self):
        self._cancel_scheduled()
        if not self.text:
            return

        try:
            if not self.widget.winfo_exists() or not self.widget.winfo_viewable():
                return
            x = self.widget.winfo_rootx() + (self.widget.winfo_width() // 2)
            y = self.widget.winfo_rooty() + self.widget.winfo_height() + 4
        except Exception:
            return

        if self.tooltip_window is not None:
            self.hide()

        try:
            import tkinter as tk
            tw = tk.Toplevel(self.widget)
            tw.wm_overrideredirect(True)
            try:
                tw.attributes("-topmost", True)
            except Exception:
                pass
            tw.wm_geometry(f"+{x}+{y}")

            label = tk.Label(
                tw,
                text=self.text,
                justify="left",
                background="#27272A",
                foreground="#FAFAFA",
                relief="solid",
                borderwidth=1,
                font=("Helvetica", 9),
                padx=6,
                pady=4,
            )
            label.pack()
            self.tooltip_window = tw
        except Exception:
            self.tooltip_window = None

    def hide(self):
        tw = self.tooltip_window
        self.tooltip_window = None
        if tw is not None:
            try:
                tw.destroy()
            except Exception:
                pass
