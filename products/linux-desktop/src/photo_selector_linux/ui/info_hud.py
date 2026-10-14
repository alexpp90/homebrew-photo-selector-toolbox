"""Floating translucent metadata and optical quality score HUD.

Adheres to:
- R-LINUX-UI-09: Floating Translucent Metadata & Score HUD with normalized goodness bar
- R-LINUX-SCORE-05: Metric Omission Rule
"""

from __future__ import annotations

from typing import List, Optional, Tuple

try:
    import gi
    gi.require_version("Gtk", "4.0")
    from gi.repository import Gtk
    HAS_GI = True
except (ImportError, ValueError):
    HAS_GI = False
    Gtk = None

from photo_selector_linux.core.models import CandidatePhoto
from photo_selector_linux.ui.styling import (
    compute_sharpness_goodness,
    format_companion_badges,
)


class InfoHUD(Gtk.Box if HAS_GI else object):
    """Floating translucent card displaying optical EXIF strip and quality metrics."""

    def __init__(self, viewmodel=None):
        self.viewmodel = viewmodel
        self.is_hud_visible: bool = False
        self.filename_text: str = ""
        self.companion_badges: List[Tuple[str, str]] = []
        self.exif_text: str = ""
        self.camera_text: str = ""
        self.score_visible: bool = False
        self.score_badge_text: str = ""
        self.score_css_class: str = ""
        self.clipping_text: str = ""
        self.sharpness_goodness: Optional[float] = None

        if HAS_GI:
            super().__init__(orientation=Gtk.Orientation.VERTICAL, spacing=6)
            self.add_css_class("floating-hud")
            self.set_halign(Gtk.Align.START)
            self.set_valign(Gtk.Align.START)
            self.set_margin_top(16)
            self.set_margin_start(16)
            self._build_ui()

        if self.viewmodel:
            self.viewmodel.connect("state-changed", self._on_state_changed)
            self.update_photo(self.viewmodel.current_photo)

    def _build_ui(self) -> None:
        if not HAS_GI:
            return

        # Top row: Filename + Companion Badges (RAW+JPG, XMP)
        self.header_row = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=8)
        self.header_row.set_halign(Gtk.Align.START)

        self.lbl_filename = Gtk.Label(label="")
        self.lbl_filename.set_halign(Gtk.Align.START)
        self.lbl_filename.add_css_class("optical-strip-primary")
        self.header_row.append(self.lbl_filename)

        self.companion_box = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=4)
        self.header_row.append(self.companion_box)
        self.append(self.header_row)

        # Optical EXIF strip (1/250s · f/8.0 · ISO 100 · 24mm)
        self.lbl_exif = Gtk.Label(label="")
        self.lbl_exif.set_halign(Gtk.Align.START)
        self.lbl_exif.add_css_class("optical-strip-secondary")
        self.append(self.lbl_exif)

        # Camera & Lens strip (Sony Alpha 7 IV · FE 24-70mm F2.8 GM II)
        self.lbl_camera = Gtk.Label(label="")
        self.lbl_camera.set_halign(Gtk.Align.START)
        self.lbl_camera.add_css_class("optical-strip-secondary")
        self.append(self.lbl_camera)

        # Score line + Normalized Sharpness Goodness Meter container
        self.score_box = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=4)
        self.score_box.set_halign(Gtk.Align.FILL)

        score_row = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=8)
        score_row.set_halign(Gtk.Align.START)

        self.lbl_score_badge = Gtk.Label(label="")
        score_row.append(self.lbl_score_badge)

        self.lbl_clipping = Gtk.Label(label="")
        self.lbl_clipping.add_css_class("optical-strip-secondary")
        score_row.append(self.lbl_clipping)
        self.score_box.append(score_row)

        self.sharpness_meter = Gtk.ProgressBar()
        self.sharpness_meter.add_css_class("sharpness-meter-track")
        self.sharpness_meter.set_hexpand(True)
        self.score_box.append(self.sharpness_meter)

        self.append(self.score_box)

    def _on_state_changed(self, vm) -> None:
        self.update_photo(vm.current_photo)

    def update_photo(self, photo: Optional[CandidatePhoto]) -> None:
        """Update displayed metadata and scores according to Metric Omission Rule."""
        if not photo:
            self.is_hud_visible = False
            self.filename_text = ""
            self.companion_badges = []
            self.exif_text = ""
            self.camera_text = ""
            self.score_visible = False
            self.score_badge_text = ""
            self.score_css_class = ""
            self.clipping_text = ""
            self.sharpness_goodness = None
            if HAS_GI:
                self.set_visible(False)
            return

        self.is_hud_visible = True
        self.filename_text = photo.primary_path.name

        has_raw_jpeg = any(
            p.suffix.lower() != ".xmp" and p.resolve() != photo.primary_path.resolve()
            for p in photo.companion_paths
        )
        has_xmp = photo.sidecar_path is not None
        self.companion_badges = format_companion_badges(
            has_raw_jpeg_pair=has_raw_jpeg,
            has_xmp_sidecar=has_xmp,
        )

        # Optical EXIF Summary (Zero-Placeholder Invariant R-LINUX-META-03)
        exif_text = ""
        camera_text = ""
        if photo.exif:
            exif_text = photo.exif.formatted_summary
            cam_parts = [p for p in (photo.exif.camera_model, photo.exif.lens) if p]
            camera_text = " · ".join(cam_parts)
        self.exif_text = exif_text
        self.camera_text = camera_text

        # Metric Omission Rule (R-LINUX-SCORE-05): Strictly omit if not yet computed
        if photo.scores is not None:
            self.score_visible = True
            category = photo.scores.focus_category.value
            self.score_badge_text = f"Focus: {photo.scores.formatted_sharpness} ({category.capitalize()})"
            self.score_css_class = f"score-badge-{category}"
            self.clipping_text = (
                f"HL: {photo.scores.formatted_highlight_clipping} · "
                f"SH: {photo.scores.formatted_shadow_clipping}"
            )
            self.sharpness_goodness = compute_sharpness_goodness(photo.scores.sharpness)
        else:
            self.score_visible = False
            self.score_badge_text = ""
            self.score_css_class = ""
            self.clipping_text = ""
            self.sharpness_goodness = None

        if not HAS_GI:
            return

        self.set_visible(True)
        self.lbl_filename.set_label(self.filename_text)

        while child := self.companion_box.get_first_child():
            self.companion_box.remove(child)
        for badge_text, badge_css in self.companion_badges:
            pill = Gtk.Label(label=badge_text)
            pill.add_css_class(badge_css)
            self.companion_box.append(pill)

        self.lbl_exif.set_label(self.exif_text)
        self.lbl_exif.set_visible(bool(self.exif_text))

        self.lbl_camera.set_label(self.camera_text)
        self.lbl_camera.set_visible(bool(self.camera_text))

        if self.score_visible and self.sharpness_goodness is not None:
            self.score_box.set_visible(True)
            self.lbl_score_badge.set_label(self.score_badge_text)
            self.lbl_score_badge.set_css_classes([self.score_css_class])
            self.lbl_clipping.set_label(self.clipping_text)
            self.sharpness_meter.set_fraction(self.sharpness_goodness)
        else:
            self.score_box.set_visible(False)

