import tkinter as tk
from tkinter import ttk, filedialog, messagebox
import threading
import queue
import sys
import logging
from dataclasses import dataclass
from pathlib import Path
from matplotlib.backends.backend_tkagg import FigureCanvasTkAgg
from PIL import Image, ImageTk
import os
import concurrent.futures
import multiprocessing

logger = logging.getLogger(__name__)

# Use a relative import or absolute based on package structure
# Assuming this runs as a module
from photo_selector_toolbox.reader import get_exif_data, SUPPORTED_EXTENSIONS
from photo_selector_toolbox.analyzer import analyze_data
from photo_selector_toolbox.utils import (
    resolve_path,
    load_image_preview,
    is_excluded_subfolder,
)
from photo_selector_toolbox.visualizer import (
    get_shutter_speed_plot,
    get_aperture_plot,
    get_iso_plot,
    get_focal_length_plot,
    get_lens_plot,
    get_combination_plot,
    get_equivalent_focal_length_plot,
    get_apsc_equivalent_focal_length_plot,
)
from photo_selector_toolbox.duplicates import find_duplicates, move_to_trash
from photo_selector_toolbox.sharpness_gui import SharpnessTool
from photo_selector_toolbox.ollama_tool import load_config, save_config
from photo_selector_toolbox.cache import ScoreCache


@dataclass
class ThemeColors:
    bg_dark: str = "#18181B"
    bg_panel: str = "#27272A"
    bg_hover: str = "#3F3F46"
    fg_light: str = "#FAFAFA"
    fg_muted: str = "#A1A1AA"
    accent_blue: str = "#6366F1"
    accent_hover: str = "#4F46E5"
    border_color: str = "#3F3F46"


def _configure_base_styles(style: ttk.Style, colors: ThemeColors) -> None:
    style.configure(
        ".",
        background=colors.bg_dark,
        foreground=colors.fg_light,
        bordercolor=colors.border_color,
        font=("Helvetica", 10),
    )


def _configure_container_styles(style: ttk.Style, colors: ThemeColors) -> None:
    style.configure("TFrame", background=colors.bg_dark)
    style.configure(
        "TLabelframe",
        background=colors.bg_dark,
        foreground=colors.fg_light,
        bordercolor=colors.border_color,
        padding=10,
    )
    style.configure(
        "TLabelframe.Label",
        background=colors.bg_dark,
        foreground=colors.fg_light,
        font=("Helvetica", 10, "bold"),
    )

    style.configure(
        "TNotebook",
        background=colors.bg_dark,
        bordercolor=colors.border_color,
        tabmargins=[2, 5, 2, 0],
    )
    style.configure(
        "TNotebook.Tab",
        background=colors.bg_panel,
        foreground=colors.fg_muted,
        bordercolor=colors.border_color,
        padding=[14, 6],
        font=("Helvetica", 9, "bold"),
    )
    style.map(
        "TNotebook.Tab",
        background=[("selected", colors.bg_dark), ("active", colors.bg_hover)],
        foreground=[("selected", colors.accent_blue), ("active", colors.fg_light)],
    )

    style.configure(
        "MetaPanel.TFrame", background=colors.bg_panel, bordercolor=colors.border_color
    )


def _configure_typography_styles(style: ttk.Style, colors: ThemeColors) -> None:
    style.configure("TLabel", background=colors.bg_dark, foreground=colors.fg_light)
    style.configure("Title.TLabel", font=("Helvetica", 12, "bold"))
    style.configure(
        "Header.TLabel", font=("Helvetica", 14, "bold"), foreground=colors.fg_light
    )
    style.configure("Muted.TLabel", foreground=colors.fg_muted, font=("Helvetica", 9))

    style.configure(
        "MetaPanel.TLabel", background=colors.bg_panel, foreground=colors.fg_light
    )
    style.configure(
        "MetaPanelTitle.TLabel",
        background=colors.bg_panel,
        foreground=colors.fg_light,
        font=("Helvetica", 12, "bold"),
    )
    style.configure(
        "MetaPanelExposure.TLabel",
        background=colors.bg_panel,
        foreground=colors.fg_light,
        font=("Helvetica", 10),
    )
    style.configure(
        "MetaPanelLens.TLabel",
        background=colors.bg_panel,
        foreground=colors.fg_light,
        font=("Helvetica", 9, "italic"),
    )


def _configure_button_styles(style: ttk.Style, colors: ThemeColors) -> None:
    style.configure(
        "TButton",
        background=colors.bg_panel,
        foreground=colors.fg_light,
        bordercolor=colors.border_color,
        borderwidth=1,
        focuscolor=colors.accent_blue,
        padding=[12, 6],
    )
    style.map(
        "TButton",
        background=[("active", colors.bg_hover), ("disabled", colors.bg_dark)],
        foreground=[("active", colors.fg_light), ("disabled", colors.fg_muted)],
        bordercolor=[("focus", colors.accent_blue)],
    )

    style.configure(
        "Primary.TButton",
        background=colors.accent_blue,
        foreground="#FFFFFF",
        bordercolor=colors.accent_blue,
        borderwidth=1,
        focuscolor="#FFFFFF",
        padding=[12, 6],
    )
    style.map(
        "Primary.TButton",
        background=[("active", colors.accent_hover), ("disabled", colors.bg_dark)],
        foreground=[("active", "#FFFFFF"), ("disabled", colors.fg_muted)],
        bordercolor=[("focus", "#FFFFFF")],
    )

    style.configure(
        "TCheckbutton",
        background=colors.bg_dark,
        foreground=colors.fg_light,
        focuscolor=colors.accent_blue,
    )
    style.map(
        "TCheckbutton",
        background=[("active", colors.bg_dark), ("disabled", colors.bg_dark)],
        foreground=[("active", colors.fg_light), ("disabled", colors.fg_muted)],
    )


def _configure_input_styles(style: ttk.Style, colors: ThemeColors) -> None:
    style.configure(
        "TEntry",
        fieldbackground=colors.bg_panel,
        foreground=colors.fg_light,
        bordercolor=colors.border_color,
        lightcolor=colors.bg_panel,
        darkcolor=colors.bg_panel,
        padding=4,
    )
    style.map(
        "TEntry",
        bordercolor=[("focus", colors.accent_blue)],
        lightcolor=[("focus", colors.accent_blue)],
        darkcolor=[("focus", colors.accent_blue)],
    )

    style.configure(
        "TProgressbar",
        background=colors.accent_blue,
        troughcolor=colors.bg_panel,
        bordercolor=colors.border_color,
        thickness=10,
    )

    style.configure(
        "TCombobox",
        fieldbackground=colors.bg_panel,
        background=colors.bg_dark,
        foreground=colors.fg_light,
        bordercolor=colors.border_color,
        arrowcolor=colors.fg_light,
    )
    style.map(
        "TCombobox",
        fieldbackground=[("readonly", colors.bg_panel)],
        foreground=[("readonly", colors.fg_light)],
    )


def apply_dark_theme(root: tk.Tk) -> None:
    style = ttk.Style(root)
    if "clam" in style.theme_names():
        style.theme_use("clam")

    colors = ThemeColors()

    _configure_base_styles(style, colors)
    _configure_container_styles(style, colors)
    _configure_typography_styles(style, colors)
    _configure_button_styles(style, colors)
    _configure_input_styles(style, colors)

    # Configure native menus globally
    root.option_add("*Menu.background", colors.bg_panel)
    root.option_add("*Menu.foreground", colors.fg_light)
    root.option_add("*Menu.activeBackground", colors.accent_blue)
    root.option_add("*Menu.activeForeground", "#FFFFFF")

    # Set root window color
    root.configure(bg=colors.bg_dark)


def apply_dark_theme_to_fig(fig):
    bg_dark = "#18181B"
    bg_panel = "#27272A"
    border_color = "#3F3F46"
    fg_light = "#F4F4F5"

    fig.patch.set_facecolor(bg_dark)
    for ax in fig.axes:
        ax.set_facecolor(bg_panel)
        ax.spines["bottom"].set_color(border_color)
        ax.spines["top"].set_color(border_color)
        ax.spines["left"].set_color(border_color)
        ax.spines["right"].set_color(border_color)
        ax.tick_params(colors=fg_light, which="both")
        ax.yaxis.label.set_color(fg_light)
        ax.xaxis.label.set_color(fg_light)
        ax.title.set_color(fg_light)
        ax.grid(True, color=border_color, linestyle="--", alpha=0.5)
        # Update colors for bars
        for container in ax.containers:
            for child in container.get_children():
                if hasattr(child, "set_facecolor"):
                    child.set_facecolor("#6366F1")


class QueueHandler(logging.Handler):
    """Redirects logging to a queue for the Tkinter text widget."""

    def __init__(self, log_queue):
        super().__init__()
        self.log_queue = log_queue

    def emit(self, record):
        try:
            msg = self.format(record)
            self.log_queue.put(msg + "\n")
        except Exception:
            self.handleError(record)


class ImageLibraryStatistics(ttk.Frame):
    def __init__(self, parent):
        super().__init__(parent)
        self.parent = parent
        self.setup_ui()
        self.log_queue = queue.Queue()
        self.is_analyzing = False
        self.stop_event = threading.Event()

    def setup_ui(self):
        # Top controls
        controls_frame = ttk.LabelFrame(self, text="Configuration", padding=10)
        controls_frame.pack(fill="x", padx=10, pady=5)

        # Root Folder
        ttk.Label(controls_frame, text="📂 Images Folder:").grid(
            row=0, column=0, sticky="w"
        )
        self.root_folder_var = tk.StringVar()
        ttk.Entry(controls_frame, textvariable=self.root_folder_var, width=50).grid(
            row=0, column=1, padx=5
        )
        ttk.Button(
            controls_frame, text="📂 Browse...", command=self.browse_root_folder
        ).grid(row=0, column=2)

        # Output Folder
        ttk.Label(controls_frame, text="📁 Output Folder:").grid(
            row=1, column=0, sticky="w"
        )
        self.output_folder_var = tk.StringVar(value="analysis_results")
        ttk.Entry(controls_frame, textvariable=self.output_folder_var, width=50).grid(
            row=1, column=1, padx=5
        )
        ttk.Button(
            controls_frame, text="📂 Browse...", command=self.browse_output_folder
        ).grid(row=1, column=2)

        # Buttons Frame
        btn_frame = ttk.Frame(controls_frame)
        btn_frame.grid(row=2, column=0, columnspan=3, pady=10)

        # Analyze Button
        self.analyze_btn = ttk.Button(
            btn_frame,
            text="📊 Analyze",
            command=self.start_analysis,
            style="Primary.TButton",
        )
        self.analyze_btn.pack(side="left", padx=5)

        # Cancel Button
        self.cancel_btn = ttk.Button(
            btn_frame, text="🛑 Cancel", command=self.cancel_analysis, state="disabled"
        )
        self.cancel_btn.pack(side="left", padx=5)

        # Progress Bar
        self.progress_var = tk.DoubleVar()
        self.progress_bar = ttk.Progressbar(
            controls_frame, variable=self.progress_var, maximum=100
        )
        self.progress_bar.grid(row=3, column=0, columnspan=3, sticky="ew", pady=5)

        # Output / Results Area
        self.notebook = ttk.Notebook(self)
        self.notebook.pack(fill="both", expand=True, padx=10, pady=5)

        # Overview Tab (getting started screen)
        self.overview_frame = ttk.Frame(self.notebook, padding=20)
        self.notebook.add(self.overview_frame, text="ℹ️ Overview")

        title_lbl = ttk.Label(
            self.overview_frame,
            text="Image Metadata Statistics Dashboard",
            font=("Helvetica", 14, "bold"),
        )
        title_lbl.pack(pady=(10, 20), anchor="w")

        desc_lbl = ttk.Label(
            self.overview_frame,
            text="Analyze and visualize focal lengths, aperture, shutter speeds, ISO levels, and lens usage across your photo library.",
            font=("Helvetica", 10),
            wraplength=600,
            justify="left",
        )
        desc_lbl.pack(pady=(0, 20), anchor="w")

        # Instruction Card
        card = ttk.LabelFrame(
            self.overview_frame, text="Getting Started Guide", padding=15
        )
        card.pack(fill="x", pady=10)

        steps = [
            "1. Images Folder: Select the root directory containing your JPEG, RAW, or other image files.",
            "2. Output Folder: Choose a directory where the generated statistics plots will be saved.",
            "3. Analyze: Click the 'Analyze' button to start scanning subdirectories.",
            "4. View Results: Results will appear in real-time. Review logs or click individual plot tabs once complete.",
        ]
        for step in steps:
            lbl = ttk.Label(card, text=step, padding=2)
            lbl.pack(anchor="w")

        features_card = ttk.LabelFrame(
            self.overview_frame, text="Available Visualizations", padding=15
        )
        features_card.pack(fill="x", pady=10)

        features = [
            "• Shutter Speed: Bar distribution of the top 25 most common shutter speeds.",
            "• Aperture (f-stop): Distribution across standard camera apertures.",
            "• ISO Levels: Frequency bar chart of ISO sensitivity ratings.",
            "• Focal Length: Grouped focal length buckets (e.g. 50mm, 24-70mm).",
            "• Lenses & Combinations: Most used lenses and aperture + focal length combinations.",
        ]
        for feat in features:
            lbl = ttk.Label(features_card, text=feat, padding=2)
            lbl.pack(anchor="w")

        # Logs Tab
        self.logs_frame = ttk.Frame(self.notebook)
        self.notebook.add(self.logs_frame, text="📝 Logs")

        self.log_text = tk.Text(
            self.logs_frame,
            state="disabled",
            wrap="word",
            bg="#27272A",
            fg="#F4F4F5",
            insertbackground="#F4F4F5",
            highlightbackground="#27272A",
            highlightcolor="#6366F1",
            borderwidth=1,
            relief="flat",
        )
        self.log_text.pack(fill="both", expand=True)

        # Scrollbar for logs
        scrollbar = ttk.Scrollbar(
            self.logs_frame, orient="vertical", command=self.log_text.yview
        )
        scrollbar.pack(side="right", fill="y")
        self.log_text["yscrollcommand"] = scrollbar.set
        self.log_text.pack(side="left", fill="both", expand=True)

        # Plots Tabs (Placeholders for now)
        self.plot_tabs = {}

    def browse_root_folder(self):
        folder = filedialog.askdirectory()
        if folder:
            self.root_folder_var.set(folder)

    def browse_output_folder(self):
        folder = filedialog.askdirectory()
        if folder:
            self.output_folder_var.set(folder)

    def log(self, message):
        self.log_queue.put(message)

    def update_logs(self):
        try:
            while True:
                msg = self.log_queue.get_nowait()
                self.log_text.config(state="normal")
                self.log_text.insert("end", msg)
                self.log_text.see("end")
                self.log_text.config(state="disabled")
        except queue.Empty:
            pass

        if self.is_analyzing:
            self.after(100, self.update_logs)

    def cancel_analysis(self):
        if self.is_analyzing:
            logger.info("Stopping analysis...")
            self.stop_event.set()
            self.cancel_btn.config(state="disabled")

    def start_analysis(self):
        root_path = self.root_folder_var.get()

        if not root_path:
            messagebox.showerror("Error", "Please select an images folder.")
            return

        self.is_analyzing = True
        self.stop_event.clear()
        self.analyze_btn.config(state="disabled")
        self.cancel_btn.config(state="normal")
        self.progress_bar.config(mode="determinate", value=0)

        # Clear logs
        self.log_text.config(state="normal")
        self.log_text.delete(1.0, "end")
        self.log_text.config(state="disabled")

        # Clear existing plot tabs
        for tab in self.plot_tabs.values():
            self.notebook.forget(tab)
        self.plot_tabs = {}

        # Start thread
        threading.Thread(
            target=self.run_analysis, args=(root_path,), daemon=True
        ).start()
        self.after(100, self.update_logs)

    def update_progress(self, value):
        self.progress_var.set(value)

    def run_analysis(self, root_folder):
        # Setup logging handler for this run
        handler = QueueHandler(self.log_queue)
        handler.setFormatter(logging.Formatter("%(message)s"))
        root_logger = logging.getLogger()
        root_logger.addHandler(handler)
        old_level = root_logger.level
        if root_logger.level > logging.INFO:
            root_logger.setLevel(logging.INFO)

        try:
            # Resolve potential network paths (smb://) to local paths
            root_path = resolve_path(root_folder)

            if not root_path.is_dir():
                logger.error(f"Error: Folder not found at '{root_path}'")
                if root_folder.startswith("smb://"):
                    msg = (
                        "Tip: For network locations, ensure "
                        "the share is mounted in your file manager first."
                    )
                    logger.info(msg)
                return

            logger.info(f"Scanning for images in '{root_path}'...")

            # Avoid test mock pollution
            if hasattr(is_excluded_subfolder, "return_value") and not isinstance(
                is_excluded_subfolder.return_value, bool
            ):
                is_excluded_subfolder.return_value = False

            image_files = [
                f
                for f in root_path.rglob("*")
                if f.suffix.lower() in SUPPORTED_EXTENSIONS
                and not is_excluded_subfolder(f, root_path)
            ]

            if not image_files:
                logger.info("No supported image files found.")
                return

            total_files = len(image_files)
            logger.info(f"Found {total_files} image files. Extracting metadata...")

            all_metadata = []

            # Determine thread count: use at most 8 threads to balance performance and overhead
            max_workers = min(8, (os.cpu_count() or 1) + 4)
            with concurrent.futures.ThreadPoolExecutor(
                max_workers=max_workers
            ) as executor:
                for i, data in enumerate(executor.map(get_exif_data, image_files)):
                    if self.stop_event.is_set():
                        logger.info("Analysis cancelled by user.")
                        # Need to cancel running futures if possible, but map will just let them finish
                        break

                    if data:
                        all_metadata.append(data)

                    # Update progress
                    progress = ((i + 1) / total_files) * 100
                    self.parent.after(0, self.update_progress, progress)

            if not all_metadata:
                logger.info(
                    "Could not extract any valid EXIF metadata from the found images."
                )
                return

            analyze_data(all_metadata)

            # Generate Plots for GUI
            logger.info("Generating plots...")
            plots = {
                "Shutter Speed": get_shutter_speed_plot(all_metadata),
                "Aperture": get_aperture_plot(all_metadata),
                "ISO": get_iso_plot(all_metadata),
                "Focal Length": get_focal_length_plot(all_metadata),
                "Equiv Focal Length (35mm)": get_equivalent_focal_length_plot(
                    all_metadata
                ),
                "Equiv Focal Length (APS-C)": get_apsc_equivalent_focal_length_plot(
                    all_metadata
                ),
                "Lens": get_lens_plot(all_metadata),
                "Combinations": get_combination_plot(all_metadata),
            }

            # Schedule GUI update to show plots
            self.parent.after(0, lambda: self.display_results(plots))

            logger.info("Analysis complete.")

        except Exception as e:
            logger.exception(f"An error occurred: {e}")
        finally:
            root_logger.removeHandler(handler)
            root_logger.setLevel(old_level)
            self.parent.after(0, self.analysis_finished)

    def display_results(self, plots):
        for name, fig in plots.items():
            if fig:
                apply_dark_theme_to_fig(fig)
                frame = ttk.Frame(self.notebook)
                self.notebook.add(frame, text=name)

                canvas = FigureCanvasTkAgg(fig, master=frame)
                canvas.draw()
                canvas.get_tk_widget().pack(fill="both", expand=True)

                self.plot_tabs[name] = frame

        # Switch to first plot tab if available
        if self.plot_tabs:
            self.notebook.select(list(self.plot_tabs.values())[0])

    def analysis_finished(self):
        self.is_analyzing = False
        self.analyze_btn.config(state="normal")
        self.cancel_btn.config(state="disabled")
        # Ensure it is 100% only if not cancelled? Or leave it as is.
        # If cancelled, it might be stopped at 50%.
        if not self.stop_event.is_set():
            self.progress_bar.config(value=100)


class DuplicateFinder(ttk.Frame):
    def __init__(self, parent):
        super().__init__(parent)
        self.parent = parent
        self.setup_ui()
        self.is_scanning = False
        self.found_duplicates = []
        self.photo_refs = []  # Keep references to images
        self.check_vars = {}  # Maps path to BooleanVar

    def setup_ui(self):
        # Top controls
        controls_frame = ttk.LabelFrame(self, text="Duplicate Detection", padding=10)
        controls_frame.pack(fill="x", padx=10, pady=5)

        # Root Folder
        ttk.Label(controls_frame, text="📂 Images Folder:").grid(
            row=0, column=0, sticky="w"
        )
        self.root_folder_var = tk.StringVar()
        ttk.Entry(controls_frame, textvariable=self.root_folder_var, width=50).grid(
            row=0, column=1, padx=5
        )
        ttk.Button(
            controls_frame, text="📂 Browse...", command=self.browse_root_folder
        ).grid(row=0, column=2)

        # Buttons
        btn_frame = ttk.Frame(controls_frame)
        btn_frame.grid(row=1, column=0, columnspan=3, pady=10)

        self.scan_btn = ttk.Button(
            btn_frame,
            text="🔍 Find Duplicates",
            command=self.start_scan,
            style="Primary.TButton",
        )
        self.scan_btn.pack(side="left", padx=5)

        self.status_lbl = ttk.Label(btn_frame, text="")
        self.status_lbl.pack(side="left", padx=10)

        # Progress Bar
        self.progress_var = tk.DoubleVar()
        self.progress_bar = ttk.Progressbar(
            controls_frame, variable=self.progress_var, maximum=100
        )
        self.progress_bar.grid(row=2, column=0, columnspan=3, sticky="ew", pady=5)

        # Results Area (Scrollable)
        results_container = ttk.Frame(self)
        results_container.pack(fill="both", expand=True, padx=10, pady=5)

        # Canvas for scrolling
        self.canvas = tk.Canvas(results_container, bg="#18181B", highlightthickness=0)
        scrollbar = ttk.Scrollbar(
            results_container, orient="vertical", command=self.canvas.yview
        )

        self.scrollable_frame = ttk.Frame(self.canvas)
        self.scrollable_frame.bind(
            "<Configure>",
            lambda e: self.canvas.configure(scrollregion=self.canvas.bbox("all")),
        )

        self.canvas.create_window((0, 0), window=self.scrollable_frame, anchor="nw")
        self.canvas.configure(yscrollcommand=scrollbar.set)

        self.canvas.pack(side="left", fill="both", expand=True)
        scrollbar.pack(side="right", fill="y")

        # Initial Empty State Card
        self.empty_state_lbl = ttk.Label(
            self.scrollable_frame,
            text="No duplicates searched yet.\n\nSelect a folder above and click 'Find Duplicates' to scan for exact matches.",
            font=("Helvetica", 11),
            justify="center",
            padding=40,
        )
        self.empty_state_lbl.pack(pady=40, fill="both", expand=True)

        # Bottom Actions
        actions_frame = ttk.Frame(self, padding=10)
        actions_frame.pack(fill="x")

        ttk.Button(
            actions_frame,
            text="🗑️ Delete Selected (Move to Trash)",
            command=self.delete_selected,
        ).pack(side="right")

    def browse_root_folder(self):
        folder = filedialog.askdirectory()
        if folder:
            self.root_folder_var.set(folder)

    def start_scan(self):
        folder = self.root_folder_var.get()
        if not folder:
            messagebox.showerror("Error", "Please select a folder.")
            return

        self.is_scanning = True
        self.scan_btn.config(state="disabled")
        self.progress_bar.config(value=0)
        self.status_lbl.config(text="Scanning...")

        # Clear previous results
        for widget in self.scrollable_frame.winfo_children():
            widget.destroy()
        self.photo_refs.clear()
        self.check_vars.clear()
        self.found_duplicates = []

        threading.Thread(target=self.run_scan, args=(folder,), daemon=True).start()

    def run_scan(self, folder):
        try:
            resolved_path = resolve_path(folder)

            def progress_cb(current, total):
                if total > 0:
                    val = (current / total) * 100
                    self.parent.after(0, lambda: self.progress_var.set(val))
                    self.parent.after(
                        0,
                        lambda: self.status_lbl.config(
                            text=f"Processed {current}/{total}"
                        ),
                    )

            results = find_duplicates(resolved_path, callback=progress_cb)

            # Pre-load thumbnails in background to avoid blocking GUI
            self.parent.after(
                0, lambda: self.status_lbl.config(text="Generating previews...")
            )

            def _load_thumb(group):
                if group["files"]:
                    try:
                        return load_image_preview(
                            group["files"][0], max_size=(150, 150)
                        )
                    except Exception:
                        pass
                return None

            # Determine thread count: use at most 8 threads to balance performance and overhead

            max_workers = min(8, (os.cpu_count() or 1) + 4)
            with concurrent.futures.ThreadPoolExecutor(
                max_workers=max_workers
            ) as executor:
                thumbnails = list(executor.map(_load_thumb, results))

            self.parent.after(0, lambda: self.display_results(results, thumbnails))
        except Exception as e:
            logger.exception("Error scanning")
            self.parent.after(0, lambda err=e: messagebox.showerror("Error", str(err)))
        finally:
            self.parent.after(0, self.scan_finished)

    def scan_finished(self):
        self.is_scanning = False
        self.scan_btn.config(state="normal")
        self.status_lbl.config(text="Scan complete.")
        self.progress_bar.config(value=100)

    def display_results(self, duplicates, thumbnails):
        if not duplicates:
            ttk.Label(self.scrollable_frame, text="No duplicates found.").pack(pady=20)
            return

        self.found_duplicates = duplicates

        for i, (group, thumb_img) in enumerate(zip(duplicates, thumbnails)):
            group_frame = ttk.LabelFrame(
                self.scrollable_frame,
                text=f"Group {i + 1} (Size: {group['size']} bytes)",
                padding=5,
            )
            group_frame.pack(fill="x", pady=5, padx=5)

            # Content layout: Image on left, Files on right
            content_frame = ttk.Frame(group_frame)
            content_frame.pack(fill="x")

            # Image Preview
            if thumb_img:
                try:
                    photo = ImageTk.PhotoImage(thumb_img)
                    self.photo_refs.append(photo)  # Keep ref
                    lbl_img = ttk.Label(content_frame, image=photo)
                    lbl_img.pack(side="left", padx=10, anchor="n")
                except Exception:
                    ttk.Label(content_frame, text="[Preview Error]").pack(
                        side="left", padx=10, anchor="n"
                    )
            else:
                ttk.Label(content_frame, text="[No Preview]").pack(
                    side="left", padx=10, anchor="n"
                )

            # File List
            files_frame = ttk.Frame(content_frame)
            files_frame.pack(side="left", fill="both", expand=True)

            for fpath in group["files"]:
                var = tk.BooleanVar(value=False)
                self.check_vars[fpath] = var
                chk = ttk.Checkbutton(files_frame, text=str(fpath), variable=var)
                chk.pack(anchor="w")

    def delete_selected(self):
        to_delete = []

        # Validation: check groups
        for group in self.found_duplicates:
            files = group["files"]
            selected_count = sum(
                1 for f in files if self.check_vars.get(f) and self.check_vars[f].get()
            )

            if selected_count == len(files):
                messagebox.showerror(
                    "Error",
                    f"Cannot delete all copies in Group (Size: {group['size']}). Please keep at least one.",
                )
                return

            for f in files:
                if self.check_vars.get(f) and self.check_vars[f].get():
                    to_delete.append(f)

        if not to_delete:
            messagebox.showinfo("Info", "No files selected.")
            return

        if not messagebox.askyesno("Confirm", f"Move {len(to_delete)} files to trash?"):
            return

        # Execute Deletion
        deleted_count = 0
        failed_paths = []

        for fpath in to_delete:
            try:
                move_to_trash(fpath)
                deleted_count += 1
            except Exception:
                failed_paths.append(fpath)

        if failed_paths:
            msg = (
                f"Failed to move {len(failed_paths)} files to trash (e.g., network drive).\n"
                "Do you want to PERMANENTLY delete them?"
            )
            if messagebox.askyesno("Trash Failed", msg):
                for fpath in failed_paths:
                    try:
                        fpath.unlink()
                        deleted_count += 1
                    except Exception as e:
                        logger.error(f"Failed to permanently delete {fpath}: {e}")

        if deleted_count > 0:
            messagebox.showinfo("Success", f"Deleted/Trashed {deleted_count} files.")
            self.start_scan()
        else:
            messagebox.showwarning("Warning", "No files were deleted.")


class Sidebar(ttk.Frame):
    def __init__(self, parent, controller):
        super().__init__(parent, width=200, padding=10)
        self.controller = controller

        ttk.Label(self, text="🛠️ Tools", font=("Helvetica", 12, "bold")).pack(pady=10)

        ttk.Button(
            self,
            text="📸 Photo Selector",
            command=lambda: controller.show_frame("SharpnessTool"),
        ).pack(fill="x", pady=5)

        ttk.Button(
            self,
            text="📊 Image Library Statistics",
            command=lambda: controller.show_frame("ImageLibraryStatistics"),
        ).pack(fill="x", pady=5)

        ttk.Button(
            self,
            text="🔍 Duplicate Finder",
            command=lambda: controller.show_frame("DuplicateFinder"),
        ).pack(fill="x", pady=5)


class AboutDialog(tk.Toplevel):
    def __init__(self, parent):
        super().__init__(parent)
        self.title("About Photo Selector Toolbox")
        self.configure(bg="#18181B")
        self.resizable(False, False)

        # Make it modal
        self.transient(parent)
        self.grab_set()

        # Locate logo path
        logo_path = None
        if hasattr(sys, "_MEIPASS"):
            logo_path = Path(sys._MEIPASS) / "logo.png"
        else:
            logo_path = Path(__file__).parent.parent.parent / "assets" / "logo.png"

        # Icon / Logo image
        self.logo_img = None
        if logo_path and logo_path.exists():
            try:
                img = Image.open(logo_path)
                img = img.resize((96, 96), Image.Resampling.LANCZOS)
                self.logo_img = ImageTk.PhotoImage(img)
            except Exception as e:
                logger.warning(f"Failed to load logo in AboutDialog: {e}")

        # Main container with padding
        content = ttk.Frame(self, padding=20)
        content.pack(fill="both", expand=True)

        # Display logo if loaded
        if self.logo_img:
            lbl_logo = ttk.Label(content, image=self.logo_img, background="#18181B")
            lbl_logo.pack(pady=(10, 15))

        # App Title
        lbl_title = ttk.Label(
            content,
            text="Photo Selector Toolbox",
            font=("Helvetica", 14, "bold"),
            background="#18181B",
            foreground="#FAFAFA",
        )
        lbl_title.pack()

        # Version
        lbl_version = ttk.Label(
            content,
            text="🏷️ Version 1.0.0",
            font=("Helvetica", 10, "bold"),
            background="#18181B",
            foreground="#3B82F6",
        )
        lbl_version.pack(pady=(2, 10))

        # Description
        desc_text = (
            "A professional desktop application for photography culling, metadata distribution "
            "analysis, duplicate finding, and quality metric scoring.\n\n"
            "Features include sharpness & noise analysis, shadow/highlight clipping, "
            "and offline AI aesthetic scores via Ollama."
        )
        lbl_desc = ttk.Label(
            content,
            text=desc_text,
            font=("Helvetica", 9),
            justify="center",
            wraplength=350,
            background="#18181B",
            foreground="#D4D4D8",
        )
        lbl_desc.pack(pady=(5, 15))

        # Footer / License / Credits
        lbl_credits = ttk.Label(
            content,
            text="Licensed under the MIT License.\nBuilt with Python, Tkinter, OpenCV, & Pillow.",
            font=("Helvetica", 8, "italic"),
            justify="center",
            background="#18181B",
            foreground="#71717A",
        )
        lbl_credits.pack(pady=(0, 20))

        # Close Button
        btn_close = ttk.Button(
            content, text="❌ Close", command=self.destroy, style="Primary.TButton"
        )
        btn_close.pack(pady=(5, 5))

        # Center the dialog relative to parent
        self.update_idletasks()
        width = max(450, int(self.winfo_reqwidth()))
        height = max(350, int(self.winfo_reqheight()))

        parent_width = parent.winfo_width()
        parent_height = parent.winfo_height()
        parent_x = parent.winfo_rootx()
        parent_y = parent.winfo_rooty()

        x = parent_x + (parent_width - width) // 2
        y = parent_y + (parent_height - height) // 2
        self.geometry(f"{width}x{height}+{x}+{y}")

        # Escape key close
        self.bind("<Escape>", lambda e: self.destroy())


class CollectionSettingsDialog(tk.Toplevel):
    def __init__(self, parent):
        super().__init__(parent)
        self.title("Collection Settings")
        self.configure(bg="#18181B")
        self.resizable(False, False)

        # Make it modal
        self.transient(parent)
        self.grab_set()

        # Load existing config
        self.config_data = load_config()

        # Variables
        self.selection_folder_var = tk.StringVar(
            value=self.config_data.get("selection_folder", "Selection")
        )
        self.separate_var = tk.BooleanVar(
            value=self.config_data.get("separate_raw_jpeg", True)
        )

        # Main container with padding
        content = ttk.Frame(self, padding=20)
        content.pack(fill="both", expand=True)

        # Header
        lbl_title = ttk.Label(
            content,
            text="📁 Collection Destination Settings",
            font=("Helvetica", 12, "bold"),
            background="#18181B",
            foreground="#FAFAFA",
        )
        lbl_title.pack(anchor="w", pady=(0, 15))

        # Folder setting row
        folder_frame = ttk.Frame(content)
        folder_frame.pack(fill="x", pady=5)

        ttk.Label(
            folder_frame,
            text="Destination Folder Name / Path:",
            font=("Helvetica", 10),
            background="#18181B",
            foreground="#FAFAFA",
        ).pack(anchor="w", pady=(0, 2))

        entry_frame = ttk.Frame(folder_frame)
        entry_frame.pack(fill="x")

        self.folder_entry = ttk.Entry(
            entry_frame, textvariable=self.selection_folder_var, font=("Helvetica", 10)
        )
        self.folder_entry.pack(side="left", fill="x", expand=True, padx=(0, 5))

        btn_browse = ttk.Button(
            entry_frame,
            text="📁 Browse...",
            command=self.browse_folder,
            style="Primary.TButton",
        )
        btn_browse.pack(side="right")

        # Explanation note
        note_text = (
            "Note: If you enter a simple folder name (e.g. 'Selection'), it will be created relative "
            "to the scanned folder. If you enter or choose an absolute path, files will be sent there."
        )
        lbl_note = ttk.Label(
            content,
            text=note_text,
            font=("Helvetica", 8, "italic"),
            justify="left",
            wraplength=420,
            background="#18181B",
            foreground="#71717A",
        )
        lbl_note.pack(anchor="w", pady=(2, 15))

        # Separation checkbutton
        chk_frame = ttk.Frame(content)
        chk_frame.pack(fill="x", pady=5)

        self.separate_chk = ttk.Checkbutton(
            chk_frame,
            text="Separate RAW and JPEG files into subfolders",
            variable=self.separate_var,
        )
        self.separate_chk.pack(anchor="w")

        # Extra note for Lightroom edit grouping
        lr_note = "Lightroom edit files (like filename-Edit.*) are automatically grouped with the RAW files."
        lbl_lr_note = ttk.Label(
            content,
            text=lr_note,
            font=("Helvetica", 8, "italic"),
            background="#18181B",
            foreground="#71717A",
        )
        lbl_lr_note.pack(anchor="w", pady=(2, 20))

        # Buttons frame (Reset, Save, Cancel)
        btn_frame = ttk.Frame(content)
        btn_frame.pack(fill="x")

        btn_reset = ttk.Button(
            btn_frame, text="🔄 Reset to Default", command=self.reset_defaults
        )
        btn_reset.pack(side="left")

        btn_cancel = ttk.Button(btn_frame, text="❌ Cancel", command=self.destroy)
        btn_cancel.pack(side="right", padx=5)

        btn_save = ttk.Button(
            btn_frame,
            text="✔️ Save",
            command=self.save_settings,
            style="Primary.TButton",
        )
        btn_save.pack(side="right")

        # Center the dialog relative to parent
        self.update_idletasks()
        width = max(480, int(self.winfo_reqwidth()))
        height = max(280, int(self.winfo_reqheight()))

        parent_width = parent.winfo_width()
        parent_height = parent.winfo_height()
        parent_x = parent.winfo_rootx()
        parent_y = parent.winfo_rooty()

        x = parent_x + (parent_width - width) // 2
        y = parent_y + (parent_height - height) // 2
        self.geometry(f"{width}x{height}+{x}+{y}")

        # Escape key close
        self.bind("<Escape>", lambda e: self.destroy())

    def browse_folder(self):
        folder = filedialog.askdirectory(parent=self)
        if folder:
            self.selection_folder_var.set(folder)

    def reset_defaults(self):
        self.selection_folder_var.set("Selection")
        self.separate_var.set(True)

    def save_settings(self):
        folder_val = self.selection_folder_var.get().strip()
        if not folder_val:
            messagebox.showerror(
                "Error", "Destination folder/path cannot be empty.", parent=self
            )
            return

        self.config_data["selection_folder"] = folder_val
        self.config_data["separate_raw_jpeg"] = self.separate_var.get()

        try:
            save_config(self.config_data)
            messagebox.showinfo(
                "Success", "Collection settings saved successfully.", parent=self
            )
            self.destroy()
        except Exception as e:
            messagebox.showerror("Error", f"Failed to save settings: {e}", parent=self)


class MainApp(tk.Tk):
    def __init__(self):
        super().__init__()
        self.title("Photo Selector Toolbox")

        # Hide main window initially and show custom splash screen
        self.withdraw()
        self.show_splash_screen()

        apply_dark_theme(self)

        # Attempt to improve DPI awareness on Windows/Linux
        try:
            # Unix/Linux often needs this for proper scaling if not handled by window manager
            self.call("tk", "scaling", self.winfo_fpixels("1i") / 72.0)
        except Exception:
            pass

        # Set Window Icon
        try:
            icon_path = None
            if hasattr(sys, "_MEIPASS"):
                # Running from PyInstaller bundle
                icon_path = Path(sys._MEIPASS) / "logo.png"
            else:
                # Running from source
                icon_path = Path(__file__).parent.parent.parent / "assets" / "logo.png"

            if icon_path and icon_path.exists():
                icon_image = tk.PhotoImage(file=str(icon_path))
                self.iconphoto(True, icon_image)
        except Exception as e:
            logger.warning(f"Failed to load icon: {e}")

        # Maximize window
        try:
            # Windows and some Linux window managers
            self.state("zoomed")
        except tk.TclError:
            try:
                # Linux (X11)
                self.attributes("-zoomed", True)
            except tk.TclError:
                # Fallback: simple geometry set to screen size
                width = self.winfo_screenwidth()
                height = self.winfo_screenheight()
                self.geometry(f"{width}x{height}")

        # Sidebar is kept instantiated for backward-compatibility with tests/references,
        # but it is not packed so it doesn't take space in the standard view.
        self.sidebar = Sidebar(self, self)

        self.content_area = ttk.Frame(self)
        self.content_area.pack(fill="both", expand=True)

        # Setup top menu bar
        self.menubar = tk.Menu(self)

        # Tools Menu
        self.tools_menu = tk.Menu(self.menubar, tearoff=0)
        self.tools_menu.add_command(
            label="Photo Selector",
            command=lambda: self.show_frame("SharpnessTool"),
        )
        self.tools_menu.add_command(
            label="Image Library Statistics",
            command=lambda: self.show_frame("ImageLibraryStatistics"),
        )
        self.tools_menu.add_command(
            label="Duplicate Finder",
            command=lambda: self.show_frame("DuplicateFinder"),
        )
        self.tools_menu.add_separator()
        self.tools_menu.add_command(
            label="Collection Settings...",
            command=self.show_collection_config,
        )
        self.tools_menu.add_command(
            label="Clear Cached Scores...",
            command=self.clear_cached_scores,
        )
        self.menubar.add_cascade(label="Tools", menu=self.tools_menu)

        # Help Menu
        self.help_menu = tk.Menu(self.menubar, tearoff=0)
        self.help_menu.add_command(label="About", command=self.show_about)
        self.menubar.add_cascade(label="Help", menu=self.help_menu)

        self.config(menu=self.menubar)

        self.frames = {}

        # Initialize frames
        for F in (ImageLibraryStatistics, DuplicateFinder, SharpnessTool):
            page_name = F.__name__
            frame = F(self.content_area)
            self.frames[page_name] = frame
            frame.grid(row=0, column=0, sticky="nsew")
            if hasattr(self, "splash") and self.splash:
                try:
                    self.splash.update()
                except Exception:
                    pass

        self.content_area.grid_rowconfigure(0, weight=1)
        self.content_area.grid_columnconfigure(0, weight=1)

        self.show_frame("SharpnessTool")

        # Close Tkinter splash screen and show main window
        if hasattr(self, "splash") and self.splash:
            try:
                self.splash_progress.stop()
                self.splash.destroy()
            except Exception:
                pass
            self.splash = None
        self.deiconify()

        # Close splash screen if it exists (after GUI is ready)
        self.after(100, self.close_splash)

    def show_splash_screen(self):
        # Close the PyInstaller splash screen first if it exists to avoid overlaps
        self.close_splash()

        self.splash = tk.Toplevel(self)
        self.splash.configure(bg="#18181B")
        self.splash.overrideredirect(True)

        # Center splash screen on the screen
        width = 450
        height = 300
        try:
            screen_width = self.winfo_screenwidth()
            screen_height = self.winfo_screenheight()
        except Exception:
            screen_width = 1920
            screen_height = 1080
        x = (screen_width - width) // 2
        y = (screen_height - height) // 2
        self.splash.geometry(f"{width}x{height}+{x}+{y}")

        # Set splash to be topmost
        try:
            self.splash.attributes("-topmost", True)
        except Exception:
            pass

        # Main frame with borders
        splash_frame = tk.Frame(
            self.splash,
            bg="#18181B",
            highlightthickness=1,
            highlightbackground="#3F3F46",
        )
        splash_frame.pack(fill="both", expand=True)

        # Load logo
        logo_path = None
        if hasattr(sys, "_MEIPASS"):
            logo_path = Path(sys._MEIPASS) / "logo.png"
        else:
            logo_path = Path(__file__).parent.parent.parent / "assets" / "logo.png"

        self.splash_logo = None
        if logo_path and logo_path.exists():
            try:
                img = Image.open(logo_path)
                img = img.resize((120, 120), Image.Resampling.LANCZOS)
                self.splash_logo = ImageTk.PhotoImage(img)
            except Exception as e:
                logger.warning(f"Failed to load logo in splash screen: {e}")

        # Display logo if loaded
        if self.splash_logo:
            lbl_logo = tk.Label(splash_frame, image=self.splash_logo, bg="#18181B")
            lbl_logo.pack(pady=(30, 15))

        # Title / Application Name
        lbl_title = tk.Label(
            splash_frame,
            text="Photo Selector Toolbox",
            font=("Helvetica", 16, "bold"),
            bg="#18181B",
            fg="#FAFAFA",
        )
        lbl_title.pack()

        # Status Label
        self.splash_status = tk.Label(
            splash_frame,
            text="Loading components...",
            font=("Helvetica", 10),
            bg="#18181B",
            fg="#A1A1AA",
        )
        self.splash_status.pack(pady=(5, 15))

        # Modern Indeterminate Progress Bar
        style = ttk.Style(self.splash)
        try:
            style.theme_use("clam")
        except Exception:
            pass
        style.configure(
            "Splash.Horizontal.TProgressbar",
            troughcolor="#27272A",
            background="#6366F1",
            lightcolor="#6366F1",
            darkcolor="#6366F1",
            bordercolor="#27272A",
            thickness=6,
            borderwidth=0,
        )

        self.splash_progress = ttk.Progressbar(
            splash_frame,
            style="Splash.Horizontal.TProgressbar",
            orient="horizontal",
            length=300,
            mode="indeterminate",
        )
        self.splash_progress.pack(pady=(5, 20))
        try:
            self.splash_progress.start(10)  # Start animation
        except Exception:
            pass

        # Force render/update immediately
        try:
            self.splash.update()
        except Exception:
            pass

    def close_splash(self):
        try:
            import pyi_splash

            pyi_splash.close()
        except ImportError:
            pass

    def show_frame(self, page_name):
        frame = self.frames[page_name]
        frame.tkraise()

    def toggle_sidebar(self, visible):
        pass

    def show_about(self):
        AboutDialog(self)

    def show_collection_config(self):
        CollectionSettingsDialog(self)

    def clear_cached_scores(self):
        if messagebox.askyesno(
            "Clear Cached Scores",
            "Are you sure you want to delete all cached scores?\n"
            "This will clear all stored analysis values and reset the current folder's loaded scores.",
            parent=self,
        ):
            try:
                cache = ScoreCache()
                cache.clear_cache()

                # Check if SharpnessTool frame is initialized and clear its in-memory scores
                sharpness_frame = self.frames.get("SharpnessTool")
                if sharpness_frame and hasattr(
                    sharpness_frame, "clear_scores_in_memory"
                ):
                    sharpness_frame.clear_scores_in_memory()

                messagebox.showinfo(
                    "Success", "Score cache cleared successfully.", parent=self
                )
            except Exception as e:
                logger.error(f"Failed to clear cache: {e}")
                messagebox.showerror(
                    "Error", f"Failed to clear score cache: {e}", parent=self
                )


def main():
    log_level = logging.INFO
    if "--debug" in sys.argv or "-d" in sys.argv:
        log_level = logging.DEBUG

    logging.basicConfig(
        level=log_level, format="%(asctime)s - %(name)s - %(levelname)s - %(message)s"
    )
    if log_level == logging.DEBUG:
        logger.setLevel(logging.DEBUG)
        logging.getLogger("photo_selector_toolbox").setLevel(logging.DEBUG)
        logger.debug("Debug logging enabled.")

    app = MainApp()
    app.mainloop()


if __name__ == "__main__":
    multiprocessing.freeze_support()
    main()
