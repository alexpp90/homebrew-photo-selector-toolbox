"""Main Libadwaita application window.

Adheres to:
- R-LINUX-UI-01: Pure GNOME HIG / Libadwaita Window Architecture
- R-LINUX-UI-03: Viewport Canvas Area Allocation (>85% of total window area)
"""

from __future__ import annotations

from pathlib import Path

try:
    import gi
    gi.require_version("Gtk", "4.0")
    gi.require_version("Adw", "1")
    from gi.repository import Gtk, Gdk, Adw, Gio, GLib
    HAS_GI = True
except (ImportError, ValueError):
    HAS_GI = False
    Gtk = Gdk = Adw = Gio = GLib = None

from photo_selector_linux.ui.action_bar import ActionBarView
from photo_selector_linux.ui.canvas import CanvasView
from photo_selector_linux.ui.keyboard_router import KeyboardRouter
from photo_selector_linux.viewmodels.culling_workspace import CullingWorkspaceViewModel


class MainWindow(Adw.ApplicationWindow if HAS_GI else object):
    """Main window housing the HeaderBar, Multi-View Canvas, and Bottom ActionBar."""

    def __init__(self, application=None, **kwargs):
        if HAS_GI:
            super().__init__(application=application, **kwargs)
            self.set_default_size(1440, 900)
            self.set_title("Photo Selector")

        self.viewmodel = CullingWorkspaceViewModel()
        if HAS_GI:
            self._build_ui()
            self._attach_keyboard_router()
            self._bind_viewmodel()

    def _build_ui(self) -> None:
        self.toolbar_view = Adw.ToolbarView()
        self.toast_overlay = Adw.ToastOverlay()
        self.toast_overlay.set_child(self.toolbar_view)
        self.set_content(self.toast_overlay)

        # HeaderBar
        self.header_bar = Adw.HeaderBar()
        self._build_header_bar()
        self.toolbar_view.add_top_bar(self.header_bar)

        # Main Canvas View
        self.canvas_view = CanvasView(viewmodel=self.viewmodel)
        self.toolbar_view.set_content(self.canvas_view)

        # Bottom Action Bar
        self.action_bar = ActionBarView(viewmodel=self.viewmodel)
        self.toolbar_view.add_bottom_bar(self.action_bar)

    def _build_header_bar(self) -> None:
        # Open Folder button
        self.btn_open = Gtk.Button.new_from_icon_name("folder-open-symbolic")
        self.btn_open.set_tooltip_text("Open Folder (Ctrl+O)")
        self.btn_open.set_action_name("app.open_folder")
        self.header_bar.pack_start(self.btn_open)

        # Path label
        self.lbl_path = Gtk.Label(label="No folder loaded")
        self.lbl_path.set_ellipsize(3)  # PANGO_ELLIPSIZE_MIDDLE
        self.lbl_path.add_css_class("dim-label")
        self.header_bar.pack_start(self.lbl_path)

        # Title widget: Mode Switcher & Counter Box
        title_box = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=12)

        # Segmented view mode buttons
        mode_box = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL)
        mode_box.add_css_class("linked")
        self.btn_mode_1 = Gtk.Button(label="1-Up (1)")
        self.btn_mode_2 = Gtk.Button(label="2-Up (2)")
        self.btn_mode_3 = Gtk.Button(label="Focus 3-Up (3)")

        self.btn_mode_1.connect("clicked", lambda b: self.viewmodel.set_comparison_mode(1))
        self.btn_mode_2.connect("clicked", lambda b: self.viewmodel.set_comparison_mode(2))
        self.btn_mode_3.connect("clicked", lambda b: self.viewmodel.set_comparison_mode(3))

        mode_box.append(self.btn_mode_1)
        mode_box.append(self.btn_mode_2)
        mode_box.append(self.btn_mode_3)
        title_box.append(mode_box)

        # Counter pill
        self.lbl_counter = Gtk.Label(label="0 / 0")
        self.lbl_counter.add_css_class("photo-counter-pill")
        title_box.append(self.lbl_counter)

        self.header_bar.set_title_widget(title_box)

        # Menu Button (Primary Menu)
        menu_button = Gtk.MenuButton()
        menu_button.set_icon_name("open-menu-symbolic")
        menu_button.set_tooltip_text("Main Menu")
        menu = Gio.Menu()

        section_tools = Gio.Menu()
        section_tools.append("Duplicate Finder (Ctrl+D)", "app.duplicate_finder")
        section_tools.append("Library Statistics (Ctrl+L)", "app.statistics")
        menu.append_section(None, section_tools)

        section_app = Gio.Menu()
        section_app.append("Preferences (Ctrl+,)", "app.preferences")
        section_app.append("Keyboard Shortcuts (Ctrl+?)", "app.shortcuts")
        menu.append_section(None, section_app)

        menu_button.set_menu_model(menu)
        self.header_bar.pack_end(menu_button)

    def _attach_keyboard_router(self) -> None:
        self.router = KeyboardRouter(window=self, viewmodel=self.viewmodel)
        self.router.attach(self)

    def _bind_viewmodel(self) -> None:
        self.viewmodel.connect("state-changed", self._on_viewmodel_state_changed)
        self.viewmodel.connect("notification", self._on_viewmodel_notification)

    def _on_viewmodel_state_changed(self, vm) -> None:
        if not HAS_GI:
            return

        total = vm.candidate_count
        current = vm.current_index + 1 if total > 0 else 0
        self.lbl_counter.set_label(f"Photo {current} of {total}")

        if vm.current_folder:
            self.lbl_path.set_label(vm.current_folder.name)
        else:
            self.lbl_path.set_label("No folder loaded")

        mode = vm.comparison_mode
        self.btn_mode_1.set_css_classes(["suggested-action"] if mode == 1 else [])
        self.btn_mode_2.set_css_classes(["suggested-action"] if mode == 2 else [])
        self.btn_mode_3.set_css_classes(["suggested-action"] if mode == 3 else [])

    def _on_viewmodel_notification(self, vm, message: str) -> None:
        if HAS_GI:
            toast = Adw.Toast.new(message)
            toast.set_timeout(3)
            self.toast_overlay.add_toast(toast)

    def show_open_folder_dialog(self) -> None:
        if not HAS_GI:
            return
        dialog = Gtk.FileDialog()
        dialog.set_title("Select Folder with Photographs")
        dialog.select_folder(self, None, self._on_folder_dialog_finish)

    def _on_folder_dialog_finish(self, dialog, result) -> None:
        try:
            folder = dialog.select_folder_finish(result)
            if folder:
                path = Path(folder.get_path())
                self.open_directory(path)
        except Exception as e:
            if HAS_GI and GLib:
                GLib.idle_add(self._on_viewmodel_notification, self.viewmodel, f"Open folder cancelled: {e}")

    def open_directory(self, path: Path) -> None:
        self.viewmodel.load_directory(path)

    def show_duplicate_finder(self) -> None:
        from photo_selector_linux.ui.tools_dialogs import DuplicateFinderDialog
        dlg = DuplicateFinderDialog(transient_for=self, viewmodel=self.viewmodel)
        dlg.present()

    def show_library_statistics(self) -> None:
        from photo_selector_linux.ui.tools_dialogs import LibraryStatisticsDialog
        dlg = LibraryStatisticsDialog(transient_for=self, viewmodel=self.viewmodel)
        dlg.present()

    def show_preferences(self) -> None:
        from photo_selector_linux.ui.preferences import PreferencesDialog
        prefs = PreferencesDialog(transient_for=self)
        prefs.present()

    def show_shortcuts_guide(self) -> None:
        from photo_selector_linux.ui.shortcuts_dialog import ShortcutsDialog
        dlg = ShortcutsDialog(transient_for=self)
        dlg.present()
