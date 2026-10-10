"""Persistent bottom tactile action bar.

Adheres to:
- R-LINUX-UI-10: Tactile Bottom Action Bar with High-Contrast Action Buttons
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


class ActionBarView(Gtk.ActionBar if HAS_GI else object):
    """Bottom tactile action bar with culling buttons, undo counter, and status tallies."""

    def __init__(self, viewmodel):
        self.viewmodel = viewmodel
        if HAS_GI:
            super().__init__()
            self._build_ui()
            self.viewmodel.connect("state-changed", self._on_state_changed)
            self._update_state()

    def _build_ui(self) -> None:
        if not HAS_GI:
            return

        # Start: Status counters
        self.counter_box = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=8)
        self.lbl_selected = Gtk.Label(label="Selected: 0")
        self.lbl_selected.add_css_class("status-counter-badge")
        self.lbl_trashed = Gtk.Label(label="Trashed: 0")
        self.lbl_trashed.add_css_class("status-counter-badge")
        self.lbl_remaining = Gtk.Label(label="Remaining: 0")
        self.lbl_remaining.add_css_class("status-counter-badge")

        self.counter_box.append(self.lbl_selected)
        self.counter_box.append(self.lbl_trashed)
        self.counter_box.append(self.lbl_remaining)
        self.pack_start(self.counter_box)

        # Center: Tactile Action Buttons
        center_box = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=12)

        self.btn_move = Gtk.Button(label="Move to Selection (M)")
        self.btn_move.add_css_class("action-btn-move")
        self.btn_move.set_tooltip_text("Move photo & companions to Selection/ (M)")
        self.btn_move.connect("clicked", lambda b: self.viewmodel.cull_move())
        center_box.append(self.btn_move)

        self.btn_copy = Gtk.Button(label="Copy to Selection (C)")
        self.btn_copy.add_css_class("action-btn-copy")
        self.btn_copy.set_tooltip_text("Copy photo & companions to Selection/ (C)")
        self.btn_copy.connect("clicked", lambda b: self.viewmodel.cull_copy())
        center_box.append(self.btn_copy)

        self.btn_trash = Gtk.Button(label="Move to Trash (Del)")
        self.btn_trash.add_css_class("action-btn-trash")
        self.btn_trash.set_tooltip_text("Move photo & companions to Trash (Delete/Backspace)")
        self.btn_trash.connect("clicked", lambda b: self.viewmodel.cull_trash())
        center_box.append(self.btn_trash)

        self.set_center_widget(center_box)

        # End: Transactional Undo
        self.btn_undo = Gtk.Button(label="Undo (Ctrl+Z)")
        self.btn_undo.set_icon_name("edit-undo-symbolic")
        self.btn_undo.set_tooltip_text("Undo last culling action (Ctrl+Z)")
        self.btn_undo.connect("clicked", lambda b: self.viewmodel.undo())
        self.pack_end(self.btn_undo)

    def _on_state_changed(self, vm) -> None:
        self._update_state()

    def _update_state(self) -> None:
        if not HAS_GI:
            return

        self.lbl_selected.set_label(f"Selected: {self.viewmodel.selected_count}")
        self.lbl_trashed.set_label(f"Trashed: {self.viewmodel.trashed_count}")
        self.lbl_remaining.set_label(f"Remaining: {self.viewmodel.remaining_count}")

        undo_depth = len(self.viewmodel.undo_stack)
        self.btn_undo.set_sensitive(undo_depth > 0)
        self.btn_undo.set_label(f"Undo ({undo_depth})" if undo_depth > 0 else "Undo")
