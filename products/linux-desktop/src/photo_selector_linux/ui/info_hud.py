"""Floating translucent metadata and optical quality score HUD.

Adheres to:
- R-LINUX-UI-09: Floating Translucent Metadata & Score HUD
- R-LINUX-SCORE-05: Metric Omission Rule
"""

from __future__ import annotations

from typing import Optional

try:
    import gi
    gi.require_version("Gtk", "4.0")
    from gi.repository import Gtk
    HAS_GI = True
except (ImportError, ValueError):
    HAS_GI = False
    Gtk = None

from photo_selector_linux.core.models import CandidatePhoto


class InfoHUD(Gtk.Box if HAS_GI else object):
    """Floating translucent card displaying optical EXIF strip and quality metrics."""

    def __init__(self, viewmodel=None):
        self.viewmodel = viewmodel
        if HAS_GI:
            super().__init__(orientation=Gtk.Orientation.VERTICAL, spacing=4)
            self.add_css_class("floating-hud")
            self.set_halign(Gtk.Align.END)
            self.set_valign(Gtk.Align.START)
            self.set_margin_top(16)
            self.set_margin_end(16)
            self._build_ui()
            if self.viewmodel:
                self.viewmodel.connect("state-changed", self._on_state_changed)

    def _build_ui(self) -> None:
        if not HAS_GI:
            return

        self.lbl_filename = Gtk.Label(label="")
        self.lbl_filename.set_halign(Gtk.Align.START)
        self.lbl_filename.add_css_class("optical-strip-primary")
        self.append(self.lbl_filename)

        self.lbl_exif = Gtk.Label(label="")
        self.lbl_exif.set_halign(Gtk.Align.START)
        self.lbl_exif.add_css_class("optical-strip-secondary")
        self.append(self.lbl_exif)

        # Score line container
        self.score_box = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=6)
        self.score_box.set_halign(Gtk.Align.START)

        self.lbl_score_badge = Gtk.Label(label="")
        self.score_box.append(self.lbl_score_badge)

        self.lbl_clipping = Gtk.Label(label="")
        self.lbl_clipping.add_css_class("optical-strip-secondary")
        self.score_box.append(self.lbl_clipping)

        self.append(self.score_box)

    def _on_state_changed(self, vm) -> None:
        if not HAS_GI:
            return
        photo = vm.current_photo
        self.update_photo(photo)

    def update_photo(self, photo: Optional[CandidatePhoto]) -> None:
        """Update displayed metadata and scores according to Metric Omission Rule."""
        if not HAS_GI:
            return

        if not photo:
            self.set_visible(False)
            return

        self.set_visible(True)
        self.lbl_filename.set_label(photo.primary_path.name)

        # Optical EXIF Summary
        exif_text = ""
        if photo.exif:
            exif_text = photo.exif.formatted_summary
            if photo.exif.lens:
                exif_text = f"{exif_text} · {photo.exif.lens}" if exif_text else photo.exif.lens
        self.lbl_exif.set_label(exif_text)
        self.lbl_exif.set_visible(bool(exif_text))

        # Metric Omission Rule (R-LINUX-SCORE-05): Strictly omit if not yet computed
        if photo.scores is not None:
            self.score_box.set_visible(True)
            category = photo.scores.focus_category.value
            score_text = f"Sharpness: {photo.scores.formatted_sharpness} ({category.capitalize()})"
            self.lbl_score_badge.set_label(score_text)

            css_class = f"score-badge-{category}"
            self.lbl_score_badge.set_css_classes([css_class])

            clip_text = (
                f"HL: {photo.scores.formatted_highlight_clipping} | "
                f"SH: {photo.scores.formatted_shadow_clipping}"
            )
            self.lbl_clipping.set_label(clip_text)
        else:
            self.score_box.set_visible(False)
