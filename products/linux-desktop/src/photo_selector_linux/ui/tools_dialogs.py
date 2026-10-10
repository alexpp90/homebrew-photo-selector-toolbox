"""Secondary tool dialogs: Duplicate Finder and Library Statistics.

Adheres to:
- R-LINUX-TOOLS-01: Streaming Cryptographic Duplicate Finder Dialog
- R-LINUX-TOOLS-02: Library Statistics Dialog with Optical Histograms
"""

from __future__ import annotations

try:
    import gi
    gi.require_version("Gtk", "4.0")
    gi.require_version("Adw", "1")
    from gi.repository import Gtk, Adw, GLib
    HAS_GI = True
except (ImportError, ValueError):
    HAS_GI = False
    Gtk = Adw = GLib = None

from photo_selector_linux.core.duplicate_finder import find_duplicates
from photo_selector_linux.core.statistics import calculate_library_statistics


class DuplicateFinderDialog(Adw.Window if HAS_GI else object):
    """Dialog for cryptographic duplicate detection and safe file deduplication."""

    def __init__(self, transient_for=None, viewmodel=None):
        self.viewmodel = viewmodel
        if HAS_GI:
            super().__init__()
            self.set_title("Duplicate Finder")
            self.set_default_size(700, 520)
            if transient_for:
                self.set_transient_for(transient_for)
            self._build_ui()

    def _build_ui(self) -> None:
        if not HAS_GI:
            return

        box = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=12)
        box.set_margin_top(16)
        box.set_margin_bottom(16)
        box.set_margin_start(16)
        box.set_margin_end(16)

        header = Gtk.Label(label="Cryptographic Duplicate Finder (SHA-256)")
        header.add_css_class("title-2")
        box.append(header)

        self.lbl_status = Gtk.Label(label="Ready to scan loaded library.")
        self.lbl_status.add_css_class("dim-label")
        box.append(self.lbl_status)

        self.btn_scan = Gtk.Button(label="Scan for Duplicates")
        self.btn_scan.add_css_class("suggested-action")
        self.btn_scan.connect("clicked", self._on_scan_clicked)
        box.append(self.btn_scan)

        # Scrolled results list
        self.scrolled = Gtk.ScrolledWindow()
        self.scrolled.set_vexpand(True)
        self.scrolled.set_hexpand(True)
        self.results_box = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=8)
        self.scrolled.set_child(self.results_box)
        box.append(self.scrolled)

        self.set_content(box)

    def _on_scan_clicked(self, btn) -> None:
        if not self.viewmodel or not self.viewmodel.candidates:
            self.lbl_status.set_label("No photographs loaded to scan.")
            return

        self.lbl_status.set_label("Scanning files...")
        clusters = find_duplicates(self.viewmodel.candidates)

        # Clear existing
        while child := self.results_box.get_first_child():
            self.results_box.remove(child)

        if not clusters:
            self.lbl_status.set_label("No duplicate photographs detected.")
            return

        total_wasted = sum(c.wasted_bytes for c in clusters)
        mb_wasted = total_wasted / (1024 * 1024)
        self.lbl_status.set_label(
            f"Found {len(clusters)} duplicate clusters ({mb_wasted:.1f} MB potential space savings)."
        )

        for c in clusters:
            row = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=4)
            size_kb = c.file_size / 1024
            title = Gtk.Label(
                label=f"SHA-256: {c.hash[:16]}... ({len(c.file_paths)} copies, {size_kb:.1f} KB each)"
            )
            title.set_halign(Gtk.Align.START)
            row.append(title)

            for p in c.file_paths:
                p_lbl = Gtk.Label(label=f"  • {p.name} ({p.parent.name}/)")
                p_lbl.set_halign(Gtk.Align.START)
                p_lbl.add_css_class("dim-label")
                row.append(p_lbl)

            self.results_box.append(row)


class LibraryStatisticsDialog(Adw.Window if HAS_GI else object):
    """Dialog rendering optical EXIF and culling statistics distributions."""

    def __init__(self, transient_for=None, viewmodel=None):
        self.viewmodel = viewmodel
        if HAS_GI:
            super().__init__()
            self.set_title("Library Statistics")
            self.set_default_size(680, 560)
            if transient_for:
                self.set_transient_for(transient_for)
            self._build_ui()

    def _build_ui(self) -> None:
        if not HAS_GI:
            return

        scrolled = Gtk.ScrolledWindow()
        box = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=16)
        box.set_margin_top(16)
        box.set_margin_bottom(16)
        box.set_margin_start(16)
        box.set_margin_end(16)
        scrolled.set_child(box)

        photos = self.viewmodel.candidates if self.viewmodel else []
        stats = calculate_library_statistics(photos)

        # 1. Summary Card
        summary_group = Adw.PreferencesGroup()
        summary_group.set_title("Library Summary")

        row_total = Adw.ActionRow()
        row_total.set_title("Total Photographs")
        row_total.set_subtitle(str(stats.total_photo_count))
        summary_group.add(row_total)

        row_storage = Adw.ActionRow()
        row_storage.set_title("Total Storage")
        row_storage.set_subtitle(f"{stats.total_storage_bytes / (1024 * 1024):.1f} MB")
        summary_group.add(row_storage)

        if stats.average_sharpness is not None:
            row_sharp = Adw.ActionRow()
            row_sharp.set_title("Average Sharpness Score")
            row_sharp.set_subtitle(f"{stats.average_sharpness:.1f} / 100.0")
            summary_group.add(row_sharp)

        box.append(summary_group)

        # 2. Formats Group
        fmt_group = Adw.PreferencesGroup()
        fmt_group.set_title("File Formats")
        for fmt in stats.format_statistics:
            row = Adw.ActionRow()
            row.set_title(fmt.canonical_name)
            row.set_subtitle(f"{fmt.count} files ({fmt.percentage_of_total:.1f}%)")
            fmt_group.add(row)
        box.append(fmt_group)

        # 3. Optical Histograms Group
        fl_group = Adw.PreferencesGroup()
        fl_group.set_title("Focal Length Distribution")
        for bucket, count in stats.focal_length_histogram.items():
            if count > 0:
                row = Adw.ActionRow()
                row.set_title(bucket)
                row.set_subtitle(f"{count} photos")
                fl_group.add(row)
        box.append(fl_group)

        self.set_content(scrolled)
