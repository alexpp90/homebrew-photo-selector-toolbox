"""Pytest configuration and universal mocking harness for PyGObject / GTK4.

Guarantees headless test execution on macOS developer workstations (Poetry venv)
and headless Linux CI runners without display servers.
"""

import os
import sys
from unittest.mock import MagicMock

# Guarantee headless backend if GTK is loaded
os.environ.setdefault("GDK_BACKEND", "headless")
os.environ.setdefault("ADW_DISABLE_PORTAL", "1")

# If PyGObject ('gi') is not installed or importable, inject comprehensive test stubs
if "gi" not in sys.modules:
    try:
        import gi  # noqa: F401
    except ImportError:
        def mock_require_version(namespace, version):
            if namespace == "GExiv2":
                raise ValueError("Namespace GExiv2 not available")
            return None

        mock_gi = MagicMock()
        mock_gi.require_version = mock_require_version

        # Mock base widget classes that can be inherited from
        class MockGtkWidget:
            def __init__(self, *args, **kwargs):
                pass

            def connect(self, *args, **kwargs):
                pass

            def set_visible(self, *args, **kwargs):
                pass

            def get_visible(self):
                return True

            def add_css_class(self, *args, **kwargs):
                pass

            def set_css_classes(self, *args, **kwargs):
                pass

            def set_halign(self, *args, **kwargs):
                pass

            def set_valign(self, *args, **kwargs):
                pass

            def set_hexpand(self, *args, **kwargs):
                pass

            def set_vexpand(self, *args, **kwargs):
                pass

            def append(self, *args, **kwargs):
                pass

            def remove(self, *args, **kwargs):
                pass

            def get_first_child(self):
                return None

            def set_size_request(self, *args, **kwargs):
                pass

            def set_homogeneous(self, *args, **kwargs):
                pass

        # Mock Gtk
        mock_gtk = MagicMock()
        mock_gtk.Box = MockGtkWidget
        mock_gtk.ActionBar = MockGtkWidget
        mock_gtk.Widget = MockGtkWidget
        mock_gtk.Orientation.HORIZONTAL = 0
        mock_gtk.Orientation.VERTICAL = 1
        mock_gtk.Align.START = 1
        mock_gtk.Align.END = 2
        mock_gtk.Align.CENTER = 3
        mock_gtk.PropagationPhase.CAPTURE = 1
        mock_gtk.PropagationPhase.BUBBLE = 2
        mock_gtk.STYLE_PROVIDER_PRIORITY_APPLICATION = 600

        # Mock Gdk
        mock_gdk = MagicMock()
        mock_gdk.ModifierType.SHIFT_MASK = 1 << 0
        mock_gdk.ModifierType.LOCK_MASK = 1 << 1
        mock_gdk.ModifierType.CONTROL_MASK = 1 << 2
        mock_gdk.ModifierType.MOD1_MASK = 1 << 3
        mock_gdk.ModifierType.MOD2_MASK = 1 << 4
        mock_gdk.EVENT_STOP = True
        mock_gdk.EVENT_PROPAGATE = False
        mock_gdk.keyval_to_lower = lambda k: k + 0x20 if 0x0041 <= k <= 0x005A else k

        # Mock Adw
        mock_adw = MagicMock()
        mock_adw.Application = MockGtkWidget
        mock_adw.ApplicationWindow = MockGtkWidget
        mock_adw.PreferencesWindow = MockGtkWidget
        mock_adw.Window = MockGtkWidget
        mock_adw.ColorScheme.FORCE_DARK = 2

        # Mock Gio & GLib
        mock_gio = MagicMock()
        mock_glib = MagicMock()

        mock_repository = MagicMock()
        mock_repository.Gtk = mock_gtk
        mock_repository.Gdk = mock_gdk
        mock_repository.Adw = mock_adw
        mock_repository.Gio = mock_gio
        mock_repository.GLib = mock_glib

        sys.modules["gi"] = mock_gi
        sys.modules["gi.repository"] = mock_repository
        sys.modules["gi.repository.Gtk"] = mock_gtk
        sys.modules["gi.repository.Gdk"] = mock_gdk
        sys.modules["gi.repository.Adw"] = mock_adw
        sys.modules["gi.repository.Gio"] = mock_gio
        sys.modules["gi.repository.GLib"] = mock_glib
