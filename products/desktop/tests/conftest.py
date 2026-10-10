import os
import sys
import pytest


# ── Platform markers ─────────────────────────────────────────────────
# Usage:  @pytest.mark.linux_only / @pytest.mark.mac_only / etc.

def pytest_configure(config):
    config.addinivalue_line("markers", "linux_only: skip unless running on Linux")
    config.addinivalue_line("markers", "mac_only: skip unless running on macOS")
    config.addinivalue_line("markers", "windows_only: skip unless running on Windows")
    config.addinivalue_line("markers", "gui_required: skip when no display is available")
    config.addinivalue_line("markers", "visual: visual regression tests requiring a display")
    _suppress_macos_gui_focus()


def pytest_collection_modifyitems(config, items):
    platform = sys.platform
    for item in items:
        if "linux_only" in item.keywords and platform != "linux":
            item.add_marker(pytest.mark.skip(reason="Linux only"))
        if "mac_only" in item.keywords and platform != "darwin":
            item.add_marker(pytest.mark.skip(reason="macOS only"))
        if "windows_only" in item.keywords and platform != "win32":
            item.add_marker(pytest.mark.skip(reason="Windows only"))
        if "gui_required" in item.keywords and not _display_available():
            item.add_marker(pytest.mark.skip(reason="No display available"))


def _display_available():
    """Check if a display is available for GUI tests."""
    if sys.platform == "win32" or sys.platform == "darwin":
        return True  # Windows/macOS always have a display context
    return bool(os.environ.get("DISPLAY"))


def _suppress_macos_gui_focus():
    """Prevent Tk on macOS from stealing focus and flashing windows during test runs.

    On macOS Aqua Tkinter, initializing `tk.Tk()` or creating windows invokes
    `[NSApp activateIgnoringOtherApps:YES]` and `[NSWindow makeKeyAndOrderFront:]`.
    This steals the developer's window focus and flashes native Aqua windows on screen.
    We swizzle NSApplication's activation methods and NSWindow's order-front methods
    using ctypes to make them no-ops during pytest execution, keeping test execution
    completely backgrounded with zero loss of test quality.
    """
    if sys.platform != "darwin" or os.environ.get("PST_SHOW_GUI"):
        return
    try:
        import ctypes
        import ctypes.util

        ctypes.cdll.LoadLibrary("/System/Library/Frameworks/AppKit.framework/AppKit")
        objc = ctypes.cdll.LoadLibrary(ctypes.util.find_library("objc"))

        objc.objc_getClass.restype = ctypes.c_void_p
        objc.objc_getClass.argtypes = [ctypes.c_char_p]
        objc.sel_registerName.restype = ctypes.c_void_p
        objc.sel_registerName.argtypes = [ctypes.c_char_p]
        objc.class_getInstanceMethod.restype = ctypes.c_void_p
        objc.class_getInstanceMethod.argtypes = [ctypes.c_void_p, ctypes.c_void_p]
        objc.method_setImplementation.restype = ctypes.c_void_p
        objc.method_setImplementation.argtypes = [ctypes.c_void_p, ctypes.c_void_p]

        imp_activate = ctypes.CFUNCTYPE(None, ctypes.c_void_p, ctypes.c_void_p, ctypes.c_bool)(
            lambda self, _cmd, flag: None
        )
        app_cls = objc.objc_getClass(b"NSApplication")
        for sel_name in [b"activateIgnoringOtherApps:", b"activate:"]:
            sel = objc.sel_registerName(sel_name)
            m = objc.class_getInstanceMethod(app_cls, sel)
            if m:
                objc.method_setImplementation(m, ctypes.cast(imp_activate, ctypes.c_void_p))

        imp_order = ctypes.CFUNCTYPE(None, ctypes.c_void_p, ctypes.c_void_p, ctypes.c_void_p)(
            lambda self, _cmd, sender: None
        )
        win_cls = objc.objc_getClass(b"NSWindow")
        for sel_name in [b"makeKeyAndOrderFront:", b"orderFront:", b"orderFrontRegardless"]:
            sel = objc.sel_registerName(sel_name)
            m = objc.class_getInstanceMethod(win_cls, sel)
            if m:
                objc.method_setImplementation(m, ctypes.cast(imp_order, ctypes.c_void_p))

        _suppress_macos_gui_focus._c_callbacks = (imp_activate, imp_order)
    except Exception:
        pass


_suppress_macos_gui_focus()


@pytest.fixture(autouse=True)
def guard_tkinter_messagebox(monkeypatch):
    """Prevent unmocked native modal message boxes from hanging automated test runs."""
    try:
        import tkinter.messagebox
        monkeypatch.setattr(tkinter.messagebox, "showerror", lambda *a, **k: None)
        monkeypatch.setattr(tkinter.messagebox, "showinfo", lambda *a, **k: None)
        monkeypatch.setattr(tkinter.messagebox, "showwarning", lambda *a, **k: None)
        monkeypatch.setattr(tkinter.messagebox, "askyesno", lambda *a, **k: False)
        monkeypatch.setattr(tkinter.messagebox, "askokcancel", lambda *a, **k: False)
    except ImportError:
        pass


# ── Fixtures ─────────────────────────────────────────────────────────

@pytest.fixture(autouse=True)
def test_db_env(tmp_path):
    """Ensure ScoreCache uses a temp database during tests."""
    try:
        from photo_selector_toolbox.core.cache import set_default_db_path
        db_path = tmp_path / "test_scores_cache.db"
        set_default_db_path(db_path)
        yield
        set_default_db_path(None)
    except ImportError:
        # If the cache module isn't loaded/created yet during initial test setups
        yield


@pytest.fixture(autouse=True)
def test_config_env(tmp_path, monkeypatch):
    """Ensure tests run with an isolated config file and don't mutate ~/.photo_selector_toolbox/settings.json."""
    try:
        from photo_selector_toolbox.core import config
        test_cfg_dir = tmp_path / ".photo_selector_toolbox"
        test_cfg_file = test_cfg_dir / "settings.json"
        monkeypatch.setattr(config, "CONFIG_DIR", test_cfg_dir)
        monkeypatch.setattr(config, "CONFIG_FILE", test_cfg_file)
    except ImportError:
        pass
