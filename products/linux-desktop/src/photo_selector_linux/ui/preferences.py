"""Modal preferences window isolating settings from main culling workspace.

Adheres to:
- R-LINUX-PREF-01: Decoupled AdwPreferencesWindow
- R-LINUX-PREF-02: Configurable Application Preferences
"""

from __future__ import annotations

try:
    import gi
    gi.require_version("Gtk", "4.0")
    gi.require_version("Adw", "1")
    from gi.repository import Gtk, Adw
    HAS_GI = True
except (ImportError, ValueError):
    HAS_GI = False
    Gtk = Adw = None


class PreferencesDialog(Adw.PreferencesWindow if HAS_GI else object):
    """Modal preferences window adhering to GNOME HIG standards."""

    def __init__(self, **kwargs):
        if HAS_GI:
            super().__init__(**kwargs)
            self.set_title("Preferences")
            self.set_default_size(560, 480)
            self._build_pages()

    def _build_pages(self) -> None:
        if not HAS_GI:
            return

        # Page 1: General Preferences
        page_gen = Adw.PreferencesPage()
        page_gen.set_title("General")
        page_gen.set_icon_name("preferences-other-symbolic")

        group_dest = Adw.PreferencesGroup()
        group_dest.set_title("Culling Destination")

        self.row_folder_name = Adw.EntryRow()
        self.row_folder_name.set_title("Selection Subfolder Name")
        self.row_folder_name.set_text("Selection")
        group_dest.add(self.row_folder_name)

        self.row_auto_advance = Adw.SwitchRow()
        self.row_auto_advance.set_title("Auto-Advance on Culling")
        self.row_auto_advance.set_subtitle("Move to next candidate after Move, Copy, or Trash")
        self.row_auto_advance.set_active(True)
        group_dest.add(self.row_auto_advance)

        self.row_companions = Adw.SwitchRow()
        self.row_companions.set_title("Bind Companion Files")
        self.row_companions.set_subtitle("Automatically group RAW+JPEG, .xmp sidecars, and -Edit derivatives")
        self.row_companions.set_active(True)
        group_dest.add(self.row_companions)

        page_gen.add(group_dest)
        self.add(page_gen)

        # Page 2: Quality & Scoring
        page_score = Adw.PreferencesPage()
        page_score.set_title("Scoring")
        page_score.set_icon_name("image-filter-symbolic")

        group_thresh = Adw.PreferencesGroup()
        group_thresh.set_title("Focus Sharpness Thresholds")

        self.row_blurry_cutoff = Adw.ActionRow()
        self.row_blurry_cutoff.set_title("Blurry Threshold (< 35.0)")
        scale_blurry = Gtk.Scale.new_with_range(Gtk.Orientation.HORIZONTAL, 10.0, 50.0, 1.0)
        scale_blurry.set_value(35.0)
        scale_blurry.set_hexpand(True)
        self.row_blurry_cutoff.add_suffix(scale_blurry)
        group_thresh.add(self.row_blurry_cutoff)

        self.row_sharp_cutoff = Adw.ActionRow()
        self.row_sharp_cutoff.set_title("Sharp Threshold (≥ 70.0)")
        scale_sharp = Gtk.Scale.new_with_range(Gtk.Orientation.HORIZONTAL, 50.0, 90.0, 1.0)
        scale_sharp.set_value(70.0)
        scale_sharp.set_hexpand(True)
        self.row_sharp_cutoff.add_suffix(scale_sharp)
        group_thresh.add(self.row_sharp_cutoff)

        page_score.add(group_thresh)
        self.add(page_score)

        # Page 3: Performance & Cache
        page_cache = Adw.PreferencesPage()
        page_cache.set_title("Performance")
        page_cache.set_icon_name("system-run-symbolic")

        group_cache = Adw.PreferencesGroup()
        group_cache.set_title("Memory & Prefetch")

        self.row_cache_limit = Adw.ActionRow()
        self.row_cache_limit.set_title("In-Memory LRU Cache Ceiling (MB)")
        scale_cache = Gtk.Scale.new_with_range(Gtk.Orientation.HORIZONTAL, 128.0, 2048.0, 64.0)
        scale_cache.set_value(512.0)
        scale_cache.set_hexpand(True)
        self.row_cache_limit.add_suffix(scale_cache)
        group_cache.add(self.row_cache_limit)

        page_cache.add(group_cache)
        self.add(page_cache)
