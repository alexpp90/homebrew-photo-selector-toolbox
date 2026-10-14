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
        self.selected_label_text: str = "0 Selected"
        self.trashed_label_text: str = "0 Trashed"
        self.remaining_label_text: str = "0 Remaining"
        self.selected_css_class: str = "status-counter-badge"
        self.trashed_css_class: str = "status-counter-badge"
        self.remaining_css_class: str = "status-counter-remaining"
        self.undo_label_text: str = "Undo (Ctrl+Z)"
        self.undo_sensitive: bool = False

        if HAS_GI:
            super().__init__()
            self.add_css_class("studio-actionbar")
            self._build_ui()

        self.viewmodel.connect("state-changed", self._on_state_changed)
        self._update_state()

    def _build_ui(self) -> None:
        if not HAS_GI:
            return

        # Start: Status counters (Selected · Trashed · Remaining)
        self.counter_box = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=8)
        self.lbl_selected = Gtk.Label(label=self.selected_label_text)
        self.lbl_selected.add_css_class(self.selected_css_class)
        self.lbl_trashed = Gtk.Label(label=self.trashed_label_text)
        self.lbl_trashed.add_css_class(self.trashed_css_class)
        self.lbl_remaining = Gtk.Label(label=self.remaining_label_text)
        self.lbl_remaining.add_css_class(self.remaining_css_class)

        self.counter_box.append(self.lbl_selected)
        self.counter_box.append(self.lbl_trashed)
        self.counter_box.append(self.lbl_remaining)
        self.pack_start(self.counter_box)

        # Center: Tactile Action Buttons
        center_box = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=12)

        self.btn_move = Gtk.Button(label="Move to Selection (M)")
        self.btn_move.add_css_class("action-btn-move")
        self.btn_move.add_css_class("suggested-action")
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
        self.btn_trash.add_css_class("destructive-action")
        self.btn_trash.set_tooltip_text("Move photo & companions to Trash (Delete/Backspace)")
        self.btn_trash.connect("clicked", lambda b: self.viewmodel.cull_trash())
        center_box.append(self.btn_trash)

        self.set_center_widget(center_box)

        # End: Transactional Undo
        self.btn_undo = Gtk.Button(label=self.undo_label_text)
        self.btn_undo.set_icon_name("edit-undo-symbolic")
        self.btn_undo.set_tooltip_text("Undo last culling action (Ctrl+Z)")
        self.btn_undo.connect("clicked", lambda b: self.viewmodel.undo())
        self.pack_end(self.btn_undo)

    def _on_state_changed(self, vm) -> None:
        self._update_state()

    def _update_state(self) -> None:
        sel = self.viewmodel.selected_count
        trs = self.viewmodel.trashed_count
        rem = self.viewmodel.remaining_count
        undo_depth = len(self.viewmodel.undo_stack)

        self.selected_label_text = f"{sel} Selected"
        self.trashed_label_text = f"{trs} Trashed"
        self.remaining_label_text = f"{rem} Remaining"
        self.selected_css_class = "status-counter-selected" if sel > 0 else "status-counter-badge"
        self.trashed_css_class = "status-counter-trashed" if trs > 0 else "status-counter-badge"
        self.remaining_css_class = "status-counter-remaining"
        self.undo_sensitive = undo_depth > 0
        self.undo_label_text = f"Undo ({undo_depth}) (Ctrl+Z)" if undo_depth > 0 else "Undo (Ctrl+Z)"

        if not HAS_GI:
            return

        self.lbl_selected.set_label(self.selected_label_text)
        self.lbl_selected.set_css_classes([self.selected_css_class])
        self.lbl_trashed.set_label(self.trashed_label_text)
        self.lbl_trashed.set_css_classes([self.trashed_css_class])
        self.lbl_remaining.set_label(self.remaining_label_text)
        self.lbl_remaining.set_css_classes([self.remaining_css_class])

        self.btn_undo.set_sensitive(self.undo_sensitive)
        self.btn_undo.set_label(self.undo_label_text)
