"""Modal preferences window isolating settings from main culling workspace.

Adheres to:
- R-LINUX-PREF-01: Decoupled AdwPreferencesWindow
- R-LINUX-PREF-02: Configurable Application Preferences backed by SettingsStore
"""

from __future__ import annotations

from typing import Optional

try:
    import gi
    gi.require_version("Gtk", "4.0")
    gi.require_version("Adw", "1")
    from gi.repository import Gtk, Adw
    HAS_GI = True
except (ImportError, ValueError):
    HAS_GI = False
    Gtk = Adw = None

from photo_selector_linux.core.settings import SettingsStore


class PreferencesDialog(Adw.PreferencesWindow if HAS_GI else object):
    """Modal preferences window adhering to GNOME HIG standards and backed by SettingsStore."""

    def __init__(
        self,
        settings: Optional[SettingsStore] = None,
        viewmodel=None,
        **kwargs,
    ):
        self.viewmodel = viewmodel
        if settings is not None:
            self.settings = settings
        elif viewmodel is not None and getattr(viewmodel, "settings", None) is not None:
            self.settings = viewmodel.settings
        else:
            self.settings = SettingsStore()
            if viewmodel is not None:
                viewmodel.settings = self.settings
                self.settings.connect(viewmodel._on_settings_changed)

        self.row_folder_name = None
        self.row_sel_folder = None
        self.row_auto_advance = None
        self.row_blurry_cutoff = None
        self.row_sharp_cutoff = None
        self.row_cache_limit = None
        self.scale_blurry = None
        self.scale_sharp = None
        self.scale_cache = None

        if HAS_GI:
            super().__init__(**kwargs)
            self.set_title("Preferences")
            self.set_default_size(560, 480)
            self._build_pages()
        else:
            self._build_headless_controls()

    def _build_headless_controls(self) -> None:
        class _EntryStub:
            def __init__(self, text: str):
                self._text = text

            def get_text(self) -> str:
                return self._text

            def set_text(self, text: str) -> None:
                self._text = text

        class _SwitchStub:
            def __init__(self, active: bool):
                self._active = active

            def get_active(self) -> bool:
                return self._active

            def set_active(self, active: bool) -> None:
                self._active = bool(active)

        class _ScaleStub:
            def __init__(self, value: float):
                self._value = float(value)

            def get_value(self) -> float:
                return self._value

            def set_value(self, value: float) -> None:
                self._value = float(value)

        self.row_folder_name = _EntryStub(self.settings.selection_folder_name)
        self.row_sel_folder = self.row_folder_name
        self.row_auto_advance = _SwitchStub(self.settings.auto_advance)
        self.scale_blurry = _ScaleStub(self.settings.blurry_threshold)
        self.scale_sharp = _ScaleStub(self.settings.sharp_threshold)
        self.scale_cache = _ScaleStub(self.settings.cache_limit_mb)

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
        self.row_sel_folder = self.row_folder_name
        self.row_folder_name.set_title("Selection Subfolder Name")
        self.row_folder_name.set_text(self.settings.selection_folder_name)
        self.row_folder_name.connect(
            "notify::text",
            lambda row, _pspec=None: self._on_selection_folder_changed(row),
        )
        group_dest.add(self.row_folder_name)

        self.row_auto_advance = Adw.SwitchRow()
        self.row_auto_advance.set_title("Auto-Advance on Culling")
        self.row_auto_advance.set_subtitle("Move to next candidate after Move, Copy, or Trash")
        self.row_auto_advance.set_active(self.settings.auto_advance)
        self.row_auto_advance.connect(
            "notify::active",
            lambda row, _pspec=None: self._on_auto_advance_changed(row),
        )
        group_dest.add(self.row_auto_advance)

        page_gen.add(group_dest)
        self.add(page_gen)

        # Page 2: Quality & Scoring
        page_score = Adw.PreferencesPage()
        page_score.set_title("Scoring")
        page_score.set_icon_name("image-filter-symbolic")

        group_thresh = Adw.PreferencesGroup()
        group_thresh.set_title("Focus Sharpness Thresholds")

        self.row_blurry_cutoff = Adw.ActionRow()
        self.row_blurry_cutoff.set_title(f"Blurry Threshold (< {self.settings.blurry_threshold:.1f})")
        self.scale_blurry = Gtk.Scale.new_with_range(Gtk.Orientation.HORIZONTAL, 10.0, 50.0, 1.0)
        self.scale_blurry.set_value(self.settings.blurry_threshold)
        self.scale_blurry.set_hexpand(True)
        self.scale_blurry.connect("value-changed", self._on_blurry_changed)
        self.row_blurry_cutoff.add_suffix(self.scale_blurry)
        group_thresh.add(self.row_blurry_cutoff)

        self.row_sharp_cutoff = Adw.ActionRow()
        self.row_sharp_cutoff.set_title(f"Sharp Threshold (≥ {self.settings.sharp_threshold:.1f})")
        self.scale_sharp = Gtk.Scale.new_with_range(Gtk.Orientation.HORIZONTAL, 50.0, 90.0, 1.0)
        self.scale_sharp.set_value(self.settings.sharp_threshold)
        self.scale_sharp.set_hexpand(True)
        self.scale_sharp.connect("value-changed", self._on_sharp_changed)
        self.row_sharp_cutoff.add_suffix(self.scale_sharp)
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
        self.scale_cache = Gtk.Scale.new_with_range(Gtk.Orientation.HORIZONTAL, 128.0, 2048.0, 64.0)
        self.scale_cache.set_value(self.settings.cache_limit_mb)
        self.scale_cache.set_hexpand(True)
        self.scale_cache.connect("value-changed", self._on_cache_changed)
        self.row_cache_limit.add_suffix(self.scale_cache)
        group_cache.add(self.row_cache_limit)

        page_cache.add(group_cache)
        self.add(page_cache)

    def _on_selection_folder_changed(self, row, *_args) -> None:
        self.set_selection_folder_name(row.get_text())

    def _on_auto_advance_changed(self, row, *_args) -> None:
        self.set_auto_advance(bool(row.get_active()))

    def _on_blurry_changed(self, scale, *_args) -> None:
        self.set_blurry_threshold(float(scale.get_value()))

    def _on_sharp_changed(self, scale, *_args) -> None:
        self.set_sharp_threshold(float(scale.get_value()))

    def _on_cache_changed(self, scale, *_args) -> None:
        self.set_cache_limit_mb(float(scale.get_value()))

    def set_selection_folder_name(self, name: str) -> None:
        self.settings.update(selection_folder_name=name)
        if HAS_GI and self.row_folder_name is not None:
            self.row_folder_name.set_text(self.settings.selection_folder_name)

    def set_auto_advance(self, active: bool) -> None:
        self.settings.update(auto_advance=bool(active))
        if HAS_GI and self.row_auto_advance is not None:
            self.row_auto_advance.set_active(self.settings.auto_advance)

    def set_blurry_threshold(self, value: float) -> None:
        self.settings.update(blurry_threshold=float(value))
        if HAS_GI and self.row_blurry_cutoff is not None:
            self.row_blurry_cutoff.set_title(
                f"Blurry Threshold (< {self.settings.blurry_threshold:.1f})"
            )

    def set_sharp_threshold(self, value: float) -> None:
        self.settings.update(sharp_threshold=float(value))
        if HAS_GI and self.row_sharp_cutoff is not None:
            self.row_sharp_cutoff.set_title(
                f"Sharp Threshold (≥ {self.settings.sharp_threshold:.1f})"
            )

    def set_cache_limit_mb(self, value: float) -> None:
        self.settings.update(cache_limit_mb=float(value))
        if HAS_GI and self.scale_cache is not None:
            self.scale_cache.set_value(self.settings.cache_limit_mb)
