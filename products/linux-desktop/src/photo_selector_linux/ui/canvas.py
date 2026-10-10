"""Responsive culling canvas supporting 1-Up, 2-Up, and 3-Up Focus Mode.

Adheres to:
- R-LINUX-UI-03: Viewport Canvas Area Allocation (>85% of total window area)
- R-LINUX-UI-04: 1-Up Single View Mode
- R-LINUX-UI-05: 2-Up Side-by-Side Comparison Mode
- R-LINUX-UI-06: 3-Up Focus Mode (Sliding Triplet)
- R-LINUX-UI-07: BoundarySlotPane Indicators
- R-LINUX-UI-08: 1:1 Actual-Pixel Zoom Toggle
"""

from __future__ import annotations

try:
    import gi
    gi.require_version("Gtk", "4.0")
    from gi.repository import Gtk
    HAS_GI = True
except (ImportError, ValueError):
    HAS_GI = False
    Gtk = None

from photo_selector_linux.ui.boundary_slot import BoundarySlotPane
from photo_selector_linux.ui.info_hud import InfoHUD


class CanvasView(Gtk.Box if HAS_GI else object):
    """Responsive culling canvas managing image viewports and comparison modes."""

    def __init__(self, viewmodel):
        self.viewmodel = viewmodel
        self.is_1to1_zoom = False

        if HAS_GI:
            super().__init__(orientation=Gtk.Orientation.VERTICAL)
            self.set_hexpand(True)
            self.set_vexpand(True)
            self.add_css_class("studio-dark-surface")

            self.overlay = Gtk.Overlay()
            self.overlay.set_hexpand(True)
            self.overlay.set_vexpand(True)
            self.append(self.overlay)

            # Container for slot panes
            self.slots_box = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=12)
            self.slots_box.set_homogeneous(True)
            self.slots_box.set_hexpand(True)
            self.slots_box.set_vexpand(True)
            self.overlay.set_child(self.slots_box)

            # Floating Info HUD overlay
            self.hud = InfoHUD(viewmodel=self.viewmodel)
            self.overlay.add_overlay(self.hud)

            self.viewmodel.connect("state-changed", self._on_state_changed)
            self._render_view()

    def toggle_zoom(self) -> None:
        """Toggle 100% 1:1 pixel zoom in 1-Up mode (Space) (R-LINUX-UI-08)."""
        if self.viewmodel.comparison_mode != 1:
            return
        self.is_1to1_zoom = not self.is_1to1_zoom
        self._render_view()

    def reset_zoom(self) -> None:
        """Reset zoom back to fit-to-window (Escape)."""
        if self.is_1to1_zoom:
            self.is_1to1_zoom = False
            self._render_view()

    def toggle_hud(self) -> None:
        """Toggle floating Info HUD visibility (I) (R-LINUX-UI-09)."""
        if HAS_GI and hasattr(self, "hud"):
            self.hud.set_visible(not self.hud.get_visible())

    def _on_state_changed(self, vm) -> None:
        self._render_view()

    def _render_view(self) -> None:
        if not HAS_GI:
            return

        # Clear existing slot children
        while child := self.slots_box.get_first_child():
            self.slots_box.remove(child)

        mode = self.viewmodel.comparison_mode
        candidates = self.viewmodel.candidates
        idx = self.viewmodel.current_index
        count = len(candidates)

        if count == 0:
            empty_lbl = Gtk.Label(label="No photographs loaded. Press Ctrl+O to open a directory.")
            empty_lbl.add_css_class("dim-label")
            self.slots_box.append(empty_lbl)
            return

        if mode == 1:
            # 1-Up Single View
            slot = self._create_photo_slot(
                candidates[idx],
                is_active=True,
                allow_1to1=self.is_1to1_zoom,
            )
            self.slots_box.append(slot)

        elif mode == 2:
            # 2-Up Side-by-Side Mode (50/50)
            champ_slot = self._create_photo_slot(candidates[idx], is_active=True)
            self.slots_box.append(champ_slot)

            if idx + 1 < count:
                challenger_slot = self._create_photo_slot(candidates[idx + 1], is_active=False)
                self.slots_box.append(challenger_slot)
            else:
                self.slots_box.append(BoundarySlotPane(slot_type="last"))

        elif mode == 3:
            # 3-Up Focus Mode (Sliding Triplet)
            # Left Slot (idx - 1)
            if idx > 0:
                left_slot = self._create_photo_slot(candidates[idx - 1], is_active=False)
                self.slots_box.append(left_slot)
            else:
                self.slots_box.append(BoundarySlotPane(slot_type="first"))

            # Center Active Slot (idx)
            center_slot = self._create_photo_slot(candidates[idx], is_active=True)
            self.slots_box.append(center_slot)

            # Right Slot (idx + 1)
            if idx < count - 1:
                right_slot = self._create_photo_slot(candidates[idx + 1], is_active=False)
                self.slots_box.append(right_slot)
            else:
                self.slots_box.append(BoundarySlotPane(slot_type="last"))

    def _create_photo_slot(
        self,
        candidate,
        is_active: bool = False,
        allow_1to1: bool = False,
    ):
        frame = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=4)
        frame.add_css_class("card-pane")
        if is_active:
            frame.add_css_class("accent-border")

        # Active header badge in 3-Up Focus Mode
        if is_active and self.viewmodel.comparison_mode == 3:
            badge_box = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL)
            badge_box.set_halign(Gtk.Align.CENTER)
            pill = Gtk.Label(label="ACTIVE")
            pill.add_css_class("active-pill")
            badge_box.append(pill)
            frame.append(badge_box)

        # Image view
        picture = Gtk.Picture.new_for_filename(str(candidate.primary_path))
        picture.set_hexpand(True)
        picture.set_vexpand(True)
        picture.set_can_shrink(True)
        picture.set_keep_aspect_ratio(True)

        if allow_1to1:
            scrolled = Gtk.ScrolledWindow()
            scrolled.set_hexpand(True)
            scrolled.set_vexpand(True)
            picture.set_can_shrink(False)
            scrolled.set_child(picture)
            frame.append(scrolled)
        else:
            frame.append(picture)

        # File name label
        lbl = Gtk.Label(label=candidate.primary_path.name)
        lbl.add_css_class("dim-label")
        frame.append(lbl)

        return frame
