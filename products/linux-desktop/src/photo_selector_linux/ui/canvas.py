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

from typing import Any, Dict, List

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
from photo_selector_linux.ui.photo_loader import PhotoTextureLoader
from photo_selector_linux.ui.styling import (
    APP_BRAND_NAME,
    APP_BRAND_TAGLINE,
    APP_ICON_NAME,
    get_candidate_companion_badges,
    get_slot_role_label,
)


class CanvasView(Gtk.Box if HAS_GI else object):
    """Responsive culling canvas managing image viewports and comparison modes."""

    def __init__(self, viewmodel):
        self.viewmodel = viewmodel
        self.is_1to1_zoom: bool = False
        self.hud_enabled: bool = True
        self.rendered_slots: List[Dict[str, Any]] = []
        self.hud = InfoHUD(viewmodel=self.viewmodel)
        self.loader = PhotoTextureLoader() if HAS_GI else None
        self._loaded_folder = None

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
        self.hud_enabled = not self.hud_enabled
        if hasattr(self, "hud"):
            self.hud.set_hud_enabled(self.hud_enabled)

    def _on_state_changed(self, vm) -> None:
        if vm.comparison_mode != 1 and self.is_1to1_zoom:
            self.is_1to1_zoom = False
        if self.loader is not None and vm.current_folder != self._loaded_folder:
            # A new folder invalidates every decoded texture of the previous one.
            self._loaded_folder = vm.current_folder
            self.loader.clear()
        self._render_view()
        self._prefetch_neighbours()

    def _prefetch_neighbours(self) -> None:
        if self.loader is None:
            return
        candidates = self.viewmodel.candidates
        idx = self.viewmodel.current_index
        window = range(max(0, idx - 1), min(len(candidates), idx + 3))
        self.loader.prefetch(candidates[i] for i in window)

    def _build_slot_descriptor(
        self,
        candidate,
        is_active: bool,
        slot_offset: int,
        allow_1to1: bool = False,
    ) -> Dict[str, Any]:
        role = get_slot_role_label(
            self.viewmodel.comparison_mode,
            slot_offset,
            is_active=is_active,
        )
        badges = get_candidate_companion_badges(candidate)
        return {
            "type": "photo",
            "role": role,
            "is_active": is_active,
            "slot_offset": slot_offset,
            "filename": candidate.filename,
            "badges": badges,
            "zoom_1to1": allow_1to1,
            "css_classes": ["card-pane", "accent-border"] if is_active else ["card-pane", "card-pane-inactive"],
        }

    def _render_view(self) -> None:
        mode = self.viewmodel.comparison_mode
        candidates = self.viewmodel.candidates
        idx = self.viewmodel.current_index
        count = len(candidates)
        self.rendered_slots = []

        if count == 0:
            if HAS_GI:
                while child := self.slots_box.get_first_child():
                    self.slots_box.remove(child)
                self.slots_box.append(self._create_empty_state())
            return

        if mode == 1:
            desc = self._build_slot_descriptor(
                candidates[idx],
                is_active=True,
                slot_offset=0,
                allow_1to1=self.is_1to1_zoom,
            )
            self.rendered_slots.append(desc)
        elif mode == 2:
            self.rendered_slots.append(
                self._build_slot_descriptor(candidates[idx], is_active=True, slot_offset=0)
            )
            if idx + 1 < count:
                self.rendered_slots.append(
                    self._build_slot_descriptor(candidates[idx + 1], is_active=False, slot_offset=1)
                )
            else:
                self.rendered_slots.append({"type": "boundary", "slot_type": "last"})
        elif mode == 3:
            if idx > 0:
                self.rendered_slots.append(
                    self._build_slot_descriptor(candidates[idx - 1], is_active=False, slot_offset=-1)
                )
            else:
                self.rendered_slots.append({"type": "boundary", "slot_type": "first"})

            self.rendered_slots.append(
                self._build_slot_descriptor(candidates[idx], is_active=True, slot_offset=0)
            )

            if idx < count - 1:
                self.rendered_slots.append(
                    self._build_slot_descriptor(candidates[idx + 1], is_active=False, slot_offset=1)
                )
            else:
                self.rendered_slots.append({"type": "boundary", "slot_type": "last"})

        if not HAS_GI:
            return

        # Clear existing GTK slot children
        while child := self.slots_box.get_first_child():
            self.slots_box.remove(child)

        if mode == 1:
            slot = self._create_photo_slot(
                candidates[idx],
                is_active=True,
                slot_offset=0,
                allow_1to1=self.is_1to1_zoom,
            )
            self.slots_box.append(slot)

        elif mode == 2:
            champ_slot = self._create_photo_slot(candidates[idx], is_active=True, slot_offset=0)
            self.slots_box.append(champ_slot)

            if idx + 1 < count:
                challenger_slot = self._create_photo_slot(
                    candidates[idx + 1],
                    is_active=False,
                    slot_offset=1,
                )
                self.slots_box.append(challenger_slot)
            else:
                self.slots_box.append(BoundarySlotPane(slot_type="last"))

        elif mode == 3:
            if idx > 0:
                left_slot = self._create_photo_slot(
                    candidates[idx - 1],
                    is_active=False,
                    slot_offset=-1,
                )
                self.slots_box.append(left_slot)
            else:
                self.slots_box.append(BoundarySlotPane(slot_type="first"))

            center_slot = self._create_photo_slot(
                candidates[idx],
                is_active=True,
                slot_offset=0,
            )
            self.slots_box.append(center_slot)

            if idx < count - 1:
                right_slot = self._create_photo_slot(
                    candidates[idx + 1],
                    is_active=False,
                    slot_offset=1,
                )
                self.slots_box.append(right_slot)
            else:
                self.slots_box.append(BoundarySlotPane(slot_type="last"))

    def _create_empty_state(self):
        hero = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=12)
        hero.set_halign(Gtk.Align.CENTER)
        hero.set_valign(Gtk.Align.CENTER)
        hero.add_css_class("empty-state-hero")

        icon = Gtk.Image.new_from_icon_name(APP_ICON_NAME)
        icon.set_pixel_size(72)
        hero.append(icon)

        title = Gtk.Label(label=APP_BRAND_NAME)
        title.add_css_class("empty-state-title")
        hero.append(title)

        tagline = Gtk.Label(label=APP_BRAND_TAGLINE)
        tagline.add_css_class("empty-state-subtitle")
        hero.append(tagline)

        prompt = Gtk.Label(label="No photographs loaded. Press Ctrl+O to open a directory or SD card.")
        prompt.add_css_class("optical-strip-secondary")
        hero.append(prompt)

        hints_row = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=8)
        hints_row.set_halign(Gtk.Align.CENTER)
        for hint in (
            "Ctrl+O Open Folder",
            "1 / 2 / 3 View Modes",
            "M Move · C Copy · Del Trash",
            "I Optical HUD",
        ):
            pill = Gtk.Label(label=hint)
            pill.add_css_class("hotkey-hint-pill")
            hints_row.append(pill)
        hero.append(hints_row)

        return hero

    def _create_photo_slot(
        self,
        candidate,
        is_active: bool = False,
        slot_offset: int = 0,
        allow_1to1: bool = False,
    ):
        desc = self._build_slot_descriptor(
            candidate,
            is_active=is_active,
            slot_offset=slot_offset,
            allow_1to1=allow_1to1,
        )
        frame = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=4)
        for cls_name in desc["css_classes"]:
            frame.add_css_class(cls_name)

        # Header badge row (Role pill + 1:1 Zoom pill + Companion badges)
        badge_box = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=6)
        badge_box.set_halign(Gtk.Align.CENTER)

        role_pill = Gtk.Label(label=desc["role"])
        role_pill.add_css_class("active-pill" if is_active else "slot-role-pill")
        badge_box.append(role_pill)

        if allow_1to1:
            zoom_pill = Gtk.Label(label="1:1 PIXEL ZOOM")
            zoom_pill.add_css_class("zoom-indicator-pill")
            badge_box.append(zoom_pill)

        for badge_text, badge_css in desc["badges"]:
            comp_pill = Gtk.Label(label=badge_text)
            comp_pill.add_css_class(badge_css)
            badge_box.append(comp_pill)

        frame.append(badge_box)

        # Image view — decoded off the main loop (RAW via its embedded preview)
        picture = Gtk.Picture()
        picture.set_hexpand(True)
        picture.set_vexpand(True)
        picture.set_can_shrink(True)
        picture.set_keep_aspect_ratio(True)

        image_area = Gtk.Overlay()
        image_area.set_hexpand(True)
        image_area.set_vexpand(True)
        status = Gtk.Spinner()
        status.set_halign(Gtk.Align.CENTER)
        status.set_valign(Gtk.Align.CENTER)
        status.set_size_request(32, 32)
        status.start()
        image_area.add_overlay(status)

        if allow_1to1:
            scrolled = Gtk.ScrolledWindow()
            scrolled.set_hexpand(True)
            scrolled.set_vexpand(True)
            picture.set_can_shrink(False)
            scrolled.set_child(picture)
            image_area.set_child(scrolled)
        else:
            image_area.set_child(picture)
        frame.append(image_area)

        self.loader.request(
            candidate,
            lambda texture: self._on_texture_ready(candidate, picture, image_area, status, texture),
            full_size=allow_1to1,
        )

        # Footer row: File name label + compact score badge (if computed)
        footer_box = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=6)
        footer_box.set_halign(Gtk.Align.CENTER)

        lbl = Gtk.Label(label=candidate.filename)
        lbl.set_ellipsize(3)  # PANGO_ELLIPSIZE_MIDDLE
        lbl.set_max_width_chars(36)
        lbl.add_css_class("optical-strip-secondary")
        footer_box.append(lbl)

        if candidate.scores is not None:
            cat = candidate.scores.focus_category.value
            score_pill = Gtk.Label(label=f"{candidate.scores.formatted_sharpness}")
            score_pill.add_css_class(f"score-badge-{cat}")
            footer_box.append(score_pill)

        frame.append(footer_box)

        return frame

    def _on_texture_ready(self, candidate, picture, image_area, status, texture) -> None:
        status.stop()
        image_area.remove_overlay(status)
        if texture is not None:
            picture.set_paintable(texture)
        else:
            unavailable = Gtk.Label(label=f"Cannot display {candidate.filename}")
            unavailable.add_css_class("optical-strip-secondary")
            unavailable.set_halign(Gtk.Align.CENTER)
            unavailable.set_valign(Gtk.Align.CENTER)
            image_area.add_overlay(unavailable)
        # EXIF is read during the first decode; refresh the HUD once it is known.
        if candidate is self.viewmodel.current_photo:
            self.hud.update_photo(candidate)
