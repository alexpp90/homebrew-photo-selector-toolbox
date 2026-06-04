import logging
import queue
import threading
import tkinter as tk
from pathlib import Path
from typing import List, Dict
from tkinter import filedialog, messagebox, ttk

import send2trash
from PIL import Image, ImageTk

from image_metadata_analyzer.sharpness import (
    find_related_files,
)
from image_metadata_analyzer.utils import load_image_preview
from image_metadata_analyzer.formatting import format_score, format_meta
from image_metadata_analyzer.controllers import ImageCacheManager, ScanController
from image_metadata_analyzer.models import ScanResult
from image_metadata_analyzer.fullscreen_viewer import FullscreenViewer
from image_metadata_analyzer.image_panels import ImagePanelsMixin

logger = logging.getLogger(__name__)





class SharpnessTool(ttk.Frame, ImagePanelsMixin):
    def __init__(self, parent):
        super().__init__(parent)
        self.parent = parent
        self.log_queue = queue.Queue()
        self.is_scanning = False
        self.stop_event = threading.Event()

        # State
        self.scan_results: List[ScanResult] = []
        self.files_map: Dict[Path, ScanResult] = {}
        self.sorted_files: List[Path] = []
        self.candidates: List[Path] = []

        # Controllers and State
        self.cache_manager = ImageCacheManager(preview_size=(1200, 900))
        self.scan_controller = ScanController()
        self.has_switched_to_review = False

        # Defaults
        self.default_blur_threshold = 100.0
        self.default_sharp_threshold = 500.0
        self.default_grid_size = "8x8"
        self.focus_mode = False

        self.setup_ui()
        self.setup_focus_ui()

        # Setup global key bindings for Review/Focus mode
        self.bind_all("<Escape>", self.on_escape_key)
        self.bind_all("<Left>", self.on_left_key)
        self.bind_all("<Right>", self.on_right_key)
        self.bind_all("<Delete>", self.on_delete_key)
        self.bind_all("<BackSpace>", self.on_delete_key)

    def on_escape_key(self, event):
        if event.widget.winfo_toplevel() != self.winfo_toplevel():
            return
        # We only want to process this if we are in the SharpnessTool (specifically Review tab)
        # Note: Event binding on toplevel can be global, but FullscreenViewer intercepts Escape as well
        # and stops propagation or is higher up.
        if self.notebook.select() != str(self.review_frame) and not self.focus_mode:
            return
        if self.focus_mode:
            self.toggle_focus_mode()

    def on_left_key(self, event):
        if event.widget.winfo_toplevel() != self.winfo_toplevel():
            return
        # Don't trigger if user is typing in a text entry
        if isinstance(event.widget, (tk.Entry, tk.Text, ttk.Entry, ttk.Combobox)):
            return
        if self.notebook.select() != str(self.review_frame) and not self.focus_mode:
            return
        self.prev_candidate()

    def on_right_key(self, event):
        if event.widget.winfo_toplevel() != self.winfo_toplevel():
            return
        # Don't trigger if user is typing in a text entry
        if isinstance(event.widget, (tk.Entry, tk.Text, ttk.Entry, ttk.Combobox)):
            return
        if self.notebook.select() != str(self.review_frame) and not self.focus_mode:
            return
        self.next_candidate()

    def on_delete_key(self, event):
        if event.widget.winfo_toplevel() != self.winfo_toplevel():
            return
        # Don't trigger if user is typing in a text entry
        if isinstance(event.widget, (tk.Entry, tk.Text, ttk.Entry, ttk.Combobox)):
            return
        if self.notebook.select() != str(self.review_frame) and not self.focus_mode:
            return
        self.delete_current_candidate()

    def setup_ui(self):
        # Notebook for switching between Setup, Scanning, and Review
        self.notebook = ttk.Notebook(self)
        self.notebook.pack(fill="both", expand=True)

        # --- Tab 1: Configuration ---
        self.config_frame = ttk.Frame(self.notebook)
        self.notebook.add(self.config_frame, text="Configuration")
        self.setup_config_ui()

        # --- Tab 2: Scanning ---
        self.scan_frame = ttk.Frame(self.notebook)
        self.notebook.add(self.scan_frame, text="Scanning")
        self.setup_scan_ui()

        # --- Tab 3: Review ---
        self.review_frame = ttk.Frame(self.notebook)
        self.notebook.add(self.review_frame, text="Review")
        self.setup_review_ui()

        # Initially disable other tabs
        self.notebook.tab(1, state="disabled")
        self.notebook.tab(2, state="disabled")

    def setup_config_ui(self):
        # Controls container
        container = ttk.Frame(self.config_frame, padding=20)
        container.pack(fill="x")

        # Folder Selection
        ttk.Label(container, text="Images Folder:").grid(
            row=0, column=0, sticky="w", pady=5
        )
        self.folder_var = tk.StringVar()
        ttk.Entry(container, textvariable=self.folder_var, width=50).grid(
            row=0, column=1, padx=5, pady=5
        )
        ttk.Button(container, text="Browse...", command=self.browse_folder).grid(
            row=0, column=2, pady=5
        )

        # Tool Selection
        tools_frame = ttk.LabelFrame(container, text="Analysis Tools", padding=10)
        tools_frame.grid(row=1, column=0, columnspan=3, pady=10, sticky="ew")

        # Create rows for each tool inside tools_frame for better alignment
        sharpness_row = ttk.Frame(tools_frame)
        sharpness_row.pack(fill="x", pady=5)

        self.tool_sharpness_var = tk.BooleanVar(value=True)
        ttk.Checkbutton(
            sharpness_row, text="Sharpness Analysis", variable=self.tool_sharpness_var
        ).pack(side="left", padx=5)

        ttk.Label(sharpness_row, text="Grid Analysis Size:").pack(
            side="left", padx=(20, 5)
        )
        self.grid_size_var = tk.StringVar(value=self.default_grid_size)
        grid_combo = ttk.Combobox(
            sharpness_row,
            textvariable=self.grid_size_var,
            values=["1x1 (Global)", "2x2", "3x3", "4x4", "5x5", "8x8"],
            state="readonly",
            width=12,
        )
        grid_combo.pack(side="left", padx=5)
        ttk.Label(
            sharpness_row,
            text="(Higher grid size helps find small sharp subjects in blurry backgrounds)",
        ).pack(side="left", padx=5)

        dummy1_row = ttk.Frame(tools_frame)
        dummy1_row.pack(fill="x", pady=5)
        self.tool_noise_var = tk.BooleanVar(value=False)
        ttk.Checkbutton(
            dummy1_row, text="Noise Analysis", variable=self.tool_noise_var
        ).pack(side="left", padx=5)

        dummy2_row = ttk.Frame(tools_frame)
        dummy2_row.pack(fill="x", pady=5)
        self.tool_dummy2_var = tk.BooleanVar(value=False)
        ttk.Checkbutton(
            dummy2_row, text="Dummy Tool 2", variable=self.tool_dummy2_var
        ).pack(side="left", padx=5)

        # Start Button
        self.start_btn = ttk.Button(
            container, text="Start Scan", command=self.start_scan
        )
        self.start_btn.grid(row=2, column=0, columnspan=3, pady=20)

    def setup_scan_ui(self):
        container = ttk.Frame(self.scan_frame, padding=20)
        container.pack(fill="both", expand=True)

        self.progress_var = tk.DoubleVar()
        self.progress_bar = ttk.Progressbar(
            container, variable=self.progress_var, maximum=100
        )
        self.progress_bar.pack(fill="x", pady=20)

        self.scan_status_lbl = ttk.Label(container, text="Ready...")
        self.scan_status_lbl.pack(pady=5)

        # Log area
        self.log_text = tk.Text(container, height=15, state="disabled")
        self.log_text.pack(fill="both", expand=True, pady=10)

        self.cancel_btn = ttk.Button(
            container, text="Cancel Scan", command=self.cancel_scan
        )
        self.cancel_btn.pack(pady=10)

    def setup_review_ui(self):
        # Layout: Left Sidebar (List), Right Main (Preview)
        self.paned = ttk.PanedWindow(self.review_frame, orient="horizontal")
        self.paned.pack(fill="both", expand=True)

        # Sidebar
        self.sidebar = ttk.Frame(self.paned, width=250, padding=5)
        self.paned.add(self.sidebar, weight=1)

        ttk.Label(self.sidebar, text="Images").pack(pady=5)

        # Scan Progress (Visible during review)
        self.scan_progress_frame = ttk.Frame(self.sidebar)
        self.scan_progress_frame.pack(fill="x", pady=(0, 10))

        self.review_status_lbl = ttk.Label(
            self.scan_progress_frame, text="Scan Progress: 0%"
        )
        self.review_status_lbl.pack(side="top", anchor="w")

        self.review_progress_var = tk.DoubleVar()
        self.review_progress_bar = ttk.Progressbar(
            self.scan_progress_frame, variable=self.review_progress_var, maximum=100
        )
        self.review_progress_bar.pack(fill="x")

        # Scrollbar and Listbox
        sb = ttk.Scrollbar(self.sidebar)
        sb.pack(side="right", fill="y")

        self.candidate_listbox = tk.Listbox(
            self.sidebar, yscrollcommand=sb.set, selectmode="single"
        )
        self.candidate_listbox.pack(fill="both", expand=True)
        sb.config(command=self.candidate_listbox.yview)

        self.candidate_listbox.bind("<<ListboxSelect>>", self.on_candidate_select)

        # Main Preview Area
        self.preview_area = ttk.Frame(self.paned, padding=10)
        self.paned.add(self.preview_area, weight=4)

        # --- Top Container: Main Candidate + Controls ---
        self.top_container = ttk.Frame(self.preview_area)
        self.top_container.pack(side="top", fill="both", expand=True, pady=(0, 10))

        # Grid Layout for Top Container (Image Left, Controls Right)
        self.top_container.columnconfigure(0, weight=3)  # Image Left
        self.top_container.columnconfigure(1, weight=1)  # Controls Right

        # Current Candidate (Left)
        self.panel_curr = self.create_image_panel(self.top_container, "Current Image")
        # Using sticky="nsew" so it expands and centers properly if window shrinks
        self.panel_curr.grid(row=0, column=0, padx=10, sticky="nsew")

        # Info & Actions (Right)
        self.info_frame = ttk.Frame(self.top_container, padding=5)
        self.info_frame.grid(row=0, column=1, sticky="ns", padx=10)

        # Metadata Label
        self.meta_lbl = ttk.Label(
            self.info_frame,
            text="",
            font=("Helvetica", 10),
            justify="left",
            wraplength=200,
        )
        self.meta_lbl.pack(pady=10, anchor="w")

        # Buttons (Vertical Stack)
        btn_frame = ttk.Frame(self.info_frame)
        btn_frame.pack(pady=10, fill="x")

        self.prev_btn = ttk.Button(
            btn_frame, text="< Prev", command=self.prev_candidate
        )
        self.prev_btn.pack(side="top", fill="x", pady=2)

        self.next_btn = ttk.Button(
            btn_frame, text="Next >", command=self.next_candidate
        )
        self.next_btn.pack(side="top", fill="x", pady=2)

        ttk.Separator(btn_frame, orient="horizontal").pack(fill="x", pady=10)

        self.del_btn = ttk.Button(
            btn_frame,
            text="Delete (Trash)",
            command=self.delete_current_candidate,
        )
        self.del_btn.pack(side="top", fill="x", pady=2)

        self.move_btn = ttk.Button(
            btn_frame,
            text="Move to Selection",
            command=self.move_current_to_selection,
        )
        self.move_btn.pack(side="top", fill="x", pady=2)

        ttk.Separator(btn_frame, orient="horizontal").pack(fill="x", pady=10)

        self.focus_toggle_btn = ttk.Button(
            btn_frame, text="Focus Mode", command=self.toggle_focus_mode
        )
        self.focus_toggle_btn.pack(side="top", fill="x", pady=2)

        # --- Bottom Container: Neighbors ---
        self.bottom_container = ttk.Frame(self.preview_area)
        self.bottom_container.pack(side="bottom", fill="both", expand=True, ipady=5)

        # Neighbors
        self.panel_prev = self.create_image_panel(
            self.bottom_container, "Previous Image"
        )
        self.panel_prev.pack(side="left", fill="both", expand=True, padx=2)

        self.panel_next = self.create_image_panel(self.bottom_container, "Next Image")
        self.panel_next.pack(side="right", fill="both", expand=True, padx=2)

    def setup_focus_ui(self):
        """Builds the fullscreen-optimized focus layout."""
        self.focus_frame = ttk.Frame(self)

        # Grid Configuration
        self.focus_frame.rowconfigure(0, weight=1, uniform="row")
        self.focus_frame.rowconfigure(1, weight=1, uniform="row")

        # Columns for Top Row
        self.focus_frame.columnconfigure(0, weight=1, uniform="col")  # Metadata Left
        self.focus_frame.columnconfigure(
            1, weight=3, uniform="col"
        )  # Image Center (Prominent)
        self.focus_frame.columnconfigure(2, weight=1, uniform="col")  # Controls Right

        self._setup_focus_left_panel()
        self._setup_focus_center_panel()
        self._setup_focus_right_panel()
        self._setup_focus_bottom_panel()

        # Keyboard bindings are now handled globally in SharpnessTool init

    def _setup_focus_left_panel(self):
        # --- Row 0: Main Area ---

        # Left (Row 0, Col 0) - Metadata
        self.focus_left_panel = ttk.Frame(self.focus_frame)
        self.focus_left_panel.grid(row=0, column=0, sticky="nsew", padx=10, pady=10)

        # "Current" title
        ttk.Label(
            self.focus_left_panel, text="Current", font=("Helvetica", 14, "bold")
        ).pack(side="top", pady=(10, 5), anchor="w")

        ttk.Separator(self.focus_left_panel, orient="horizontal").pack(fill="x", pady=5)

        self.focus_score_lbl = ttk.Label(
            self.focus_left_panel,
            text="Sharpness Score: --",
            font=("Helvetica", 12, "bold"),
        )
        self.focus_score_lbl.pack(side="top", pady=(5, 0), anchor="w")

        self.focus_noise_lbl = ttk.Label(
            self.focus_left_panel,
            text="Noise Level: --",
            font=("Helvetica", 12, "bold"),
        )
        self.focus_noise_lbl.pack(side="top", pady=(0, 5), anchor="w")

        self.focus_cat_lbl = ttk.Label(
            self.focus_left_panel, text="", font=("Helvetica", 10)
        )
        self.focus_cat_lbl.pack(side="top", pady=5, anchor="w")

        self.focus_filename_lbl = ttk.Label(
            self.focus_left_panel, text="", wraplength=150
        )
        self.focus_filename_lbl.pack(side="top", pady=5, anchor="w")

        self.focus_meta_lbl = ttk.Label(
            self.focus_left_panel, text="", justify="left", wraplength=150
        )
        self.focus_meta_lbl.pack(side="top", pady=5, anchor="w")

    def _setup_focus_center_panel(self):
        # Center (Row 0, Col 1) - Current Candidate
        self.focus_curr_container = ttk.Frame(self.focus_frame)
        self.focus_curr_container.grid(row=0, column=1, sticky="nsew", padx=5, pady=5)
        self.focus_curr_container.pack_propagate(
            False
        )  # Stop label from resizing container
        self.focus_curr_container.grid_propagate(False)

        self.focus_curr_lbl = ttk.Label(
            self.focus_curr_container, text="No Image", anchor="center"
        )
        self.focus_curr_lbl.place(relx=0.5, rely=0.5, anchor="center")
        self.focus_curr_lbl.bind(
            "<Button-1>", lambda e: self.on_image_click(self.panel_curr.path)
        )

        self.focus_curr_lbl.container = self.focus_curr_container
        self.focus_curr_container.bind(
            "<Configure>", lambda e: self.on_focus_label_resize(e, self.focus_curr_lbl)
        )

    def _setup_focus_right_panel(self):
        # Right (Row 0, Col 2) - Controls
        self.focus_right_panel = ttk.Frame(self.focus_frame)
        self.focus_right_panel.grid(row=0, column=2, sticky="nsew", padx=10, pady=10)

        # Controls Stack
        self.focus_exit_btn = ttk.Button(
            self.focus_right_panel,
            text="Exit Focus Mode",
            command=self.toggle_focus_mode,
        )
        self.focus_exit_btn.pack(side="top", pady=10, fill="x")

        ttk.Separator(self.focus_right_panel, orient="horizontal").pack(
            fill="x", pady=10
        )

        # Navigation & Actions
        self.focus_prev_btn = ttk.Button(
            self.focus_right_panel, text="< Previous", command=self.prev_candidate
        )
        self.focus_prev_btn.pack(side="top", pady=5, fill="x")

        self.focus_next_btn = ttk.Button(
            self.focus_right_panel, text="Next >", command=self.next_candidate
        )
        self.focus_next_btn.pack(side="top", pady=5, fill="x")

        self.focus_del_btn = ttk.Button(
            self.focus_right_panel,
            text="DELETE (Trash)",
            command=self.delete_current_candidate,
        )
        self.focus_del_btn.pack(side="top", pady=20, fill="x")

        self.focus_move_btn = ttk.Button(
            self.focus_right_panel,
            text="Move to Selection",
            command=self.move_current_to_selection,
        )
        self.focus_move_btn.pack(side="top", pady=5, fill="x")

    def _setup_focus_bottom_panel(self):
        # --- Row 1: Bottom Strip ---
        self.focus_bottom_frame = ttk.Frame(self.focus_frame)
        self.focus_bottom_frame.grid(
            row=1, column=0, columnspan=3, sticky="nsew", pady=5
        )

        # Split 50/50
        self.focus_bottom_frame.rowconfigure(0, weight=1)
        self.focus_bottom_frame.columnconfigure(0, weight=1, uniform="bot_col")
        self.focus_bottom_frame.columnconfigure(1, weight=1, uniform="bot_col")

        # Bottom Left (Prev)
        self.focus_prev_container = ttk.Frame(self.focus_bottom_frame)
        self.focus_prev_container.grid(row=0, column=0, sticky="nsew", padx=5)
        self.focus_prev_container.pack_propagate(False)
        self.focus_prev_container.grid_propagate(False)

        self.focus_prev_lbl = ttk.Label(
            self.focus_prev_container, text="Prev", anchor="center"
        )
        self.focus_prev_lbl.place(relx=0.5, rely=0.5, anchor="center")
        self.focus_prev_lbl.bind(
            "<Button-1>", lambda e: self.on_image_click(self.panel_prev.path)
        )

        # Overlay for Prev
        self.focus_prev_overlay = ttk.Label(
            self.focus_prev_container,
            text="",
            font=("Helvetica", 9),
            background="black",
            foreground="white",
            padding=(4, 2),
        )
        self.focus_prev_overlay.place(relx=0.0, rely=0.0, anchor="nw")
        self.focus_prev_overlay.bind(
            "<Button-1>", lambda e: self.on_image_click(self.panel_prev.path)
        )

        # Bottom Right (Next)
        self.focus_next_container = ttk.Frame(self.focus_bottom_frame)
        self.focus_next_container.grid(row=0, column=1, sticky="nsew", padx=5)
        self.focus_next_container.pack_propagate(False)
        self.focus_next_container.grid_propagate(False)

        self.focus_next_lbl = ttk.Label(
            self.focus_next_container, text="Next", anchor="center"
        )
        self.focus_next_lbl.place(relx=0.5, rely=0.5, anchor="center")
        self.focus_next_lbl.bind(
            "<Button-1>", lambda e: self.on_image_click(self.panel_next.path)
        )

        # Overlay for Next
        self.focus_next_overlay = ttk.Label(
            self.focus_next_container,
            text="",
            font=("Helvetica", 9),
            background="black",
            foreground="white",
            padding=(4, 2),
        )
        self.focus_next_overlay.place(relx=0.0, rely=0.0, anchor="nw")
        self.focus_next_overlay.bind(
            "<Button-1>", lambda e: self.on_image_click(self.panel_next.path)
        )

        # Store container references on labels
        self.focus_prev_lbl.container = self.focus_prev_container
        self.focus_next_lbl.container = self.focus_next_container

        # Add resize handlers to containers
        self.focus_prev_container.bind(
            "<Configure>", lambda e: self.on_focus_label_resize(e, self.focus_prev_lbl)
        )
        self.focus_next_container.bind(
            "<Configure>", lambda e: self.on_focus_label_resize(e, self.focus_next_lbl)
        )

    def toggle_focus_mode(self):
        self.focus_mode = not self.focus_mode

        # Access MainApp to toggle sidebar
        # self.parent is content_area, self.parent.master is MainApp
        main_app = self.parent.master

        if self.focus_mode:
            # Enable Focus Mode
            if hasattr(main_app, "toggle_sidebar"):
                main_app.toggle_sidebar(False)

            self.notebook.pack_forget()
            self.focus_frame.pack(fill="both", expand=True)
            self.focus_frame.focus_set()  # Enable keyboard events

            # Reload images to ensure they are sized correctly for Focus Mode (Equal sizes)
            if self.panel_curr.path:
                self.load_triplet_view(self.panel_curr.path)
            else:
                self.refresh_active_view()
        else:
            # Disable Focus Mode
            self.focus_frame.pack_forget()
            self.notebook.pack(fill="both", expand=True)

            if hasattr(main_app, "toggle_sidebar"):
                main_app.toggle_sidebar(True)

            # Reload images to ensure they are sized correctly for Standard Mode (Large Main, Small Neighbors)
            if self.panel_curr.path:
                self.load_triplet_view(self.panel_curr.path)
            else:
                self.refresh_active_view()



    def open_fullscreen(self, path, mode, focus=(0.5, 0.5)):
        # Check if file exists
        if path and path.exists():
            # Pass sorted_files so FullscreenViewer can navigate via N/P keys
            file_list = getattr(self, "sorted_files", [])
            FullscreenViewer(
                self, path, initial_mode=mode, focus_point=focus, file_list=file_list
            )

    def browse_folder(self):
        folder = filedialog.askdirectory()
        if folder:
            self.folder_var.set(folder)
            self._load_folder_contents(folder)

    def _load_folder_contents(self, folder_path):
        """Finds all supported images in the selected folder and populates the Review tab."""
        # Block the UI briefly
        self.config(cursor="watch")
        self.update()

        p = Path(folder_path)
        from image_metadata_analyzer.reader import SUPPORTED_EXTENSIONS

        extensions = SUPPORTED_EXTENSIONS
        files = [f for f in p.rglob("*") if f.suffix.lower() in extensions]
        files.sort(key=lambda x: x.name)

        self.sorted_files = files
        self.candidates = files.copy()
        self.scan_results = []
        self.files_map = {}

        self.candidate_listbox.delete(0, "end")

        for f in self.sorted_files:
            # Initialize with N/A score and empty EXIF (fetch EXIF asynchronously if needed later)
            res = ScanResult(path=f)
            self.files_map[f] = res
            self.candidate_listbox.insert("end", f"{f.name} (Sharpness: N/A)")

        if self.candidates:
            self.notebook.tab(2, state="normal")
            self.log(f"Loaded {len(self.candidates)} images. Ready for review.")

            # Select first item
            self.candidate_listbox.selection_set(0)
            self.on_candidate_select(None)
        else:
            self.notebook.tab(2, state="disabled")
            self.log("No supported images found in the selected folder.")
            messagebox.showinfo(
                "Folder Load", "No supported images found in the selected folder."
            )

        # Restore UI cursor
        self.config(cursor="")
        self.update()

    def log(self, msg):
        self.log_queue.put(msg)

    def update_log_view(self):
        try:
            while True:
                msg = self.log_queue.get_nowait()
                self.log_text.config(state="normal")
                self.log_text.insert("end", msg + "\n")
                self.log_text.see("end")
                self.log_text.config(state="disabled")
        except queue.Empty:
            pass

        if self.is_scanning:
            self.after(100, self.update_log_view)

    def start_scan(self):
        folder = self.folder_var.get()
        if not folder or not Path(folder).exists():
            messagebox.showerror("Error", "Please select a valid folder.")
            return

        self.is_scanning = True
        self.stop_event.clear()

        # Switch to Review Tab (as requested)
        self.notebook.tab(2, state="normal")
        self.notebook.select(2)
        # We also enable Scan Tab so the user can look at the raw logs if they want to
        self.notebook.tab(1, state="normal")
        self.notebook.tab(0, state="disabled")

        self.log_text.config(state="normal")
        self.log_text.delete(1.0, "end")
        self.log_text.config(state="disabled")

        self.progress_var.set(0)
        self.review_progress_var.set(0)
        self.review_status_lbl.config(text="Scan Progress: 0%")
        # We don't reset these because they were populated during _load_folder_contents
        # self.scan_results = []
        # self.files_map = {}
        # self.candidates = []
        # self.candidate_listbox.delete(0, "end")
        self.has_switched_to_review = False

        # Reset cache
        self.cache_manager.clear()

        # Switch to review tab immediately as requested
        self.switch_to_review_mode()

        # Parse grid size in main thread
        grid_str = self.grid_size_var.get()
        try:
            # Extract first digit from "4x4" -> 4
            grid_size = int(grid_str.split("x")[0])
        except (ValueError, IndexError):
            grid_size = 1
            self.log(f"Warning: Invalid grid size '{grid_str}', defaulting to 1x1")

        # Pass the tool configuration
        tools = {
            "sharpness": self.tool_sharpness_var.get(),
            "noise": self.tool_noise_var.get(),
        }

        self.scan_controller.run_scan(
            files=self.sorted_files,
            grid_size=grid_size,
            tools=tools,
            progress_callback=self._on_scan_progress,
            finished_callback=self._on_scan_finished,
            log_callback=self.log,
        )
        self.after(100, self.update_log_view)

    def cancel_scan(self):
        self.scan_controller.cancel()
        self.log("Stopping scan...")

    def _on_scan_progress(self, result: ScanResult, current_idx: int, total_count: int):
        # Result arriving from background thread, update via parent.after for thread safety
        self.parent.after(
            0, lambda: self.process_scan_result(result, current_idx, total_count)
        )

    def _on_scan_finished(self):
        self.parent.after(0, self.scan_finished)

    def process_scan_result(
        self, result: ScanResult, current_idx: int, total_count: int
    ):
        if not self.is_scanning:
            return

        self._update_scan_state(result)
        self._update_scan_progress_ui(current_idx, total_count)
        self._update_candidate_listbox_ui(result)
        self._handle_review_lookahead(result.path)

        # Update button states
        self.update_button_states()

    def _update_scan_state(self, result: ScanResult):
        """Update internal collections with a new ScanResult."""
        path = result.path
        found = False
        for i, r in enumerate(self.scan_results):
            if r.path == path:
                self.scan_results[i] = result
                found = True
                break
        if not found:
            self.scan_results.append(result)

        self.files_map[path] = result

    def _update_scan_progress_ui(self, current_idx: int, total_count: int):
        """Update progress variables and labels based on scan progress."""
        pct = (current_idx / total_count) * 100
        self.progress_var.set(pct)
        self.review_progress_var.set(pct)
        self.review_status_lbl.config(
            text=f"Scan Progress: {int(pct)}% ({current_idx}/{total_count})"
        )

    def _update_candidate_listbox_ui(self, result: ScanResult):
        """Update the listbox display for a scanned candidate."""
        path = result.path
        if path in self.candidates:
            idx = self.candidates.index(path)
            score_text = format_score(result.score)
            noise_text = format_score(result.noise_score)

            # Delete and reinsert to update text, but maintain selection if it was selected
            is_selected = self.candidate_listbox.curselection() == (idx,)
            self.candidate_listbox.delete(idx)

            # Construct display string
            listbox_text = f"{path.name} (Sharpness: {score_text}, Noise: {noise_text})"
            self.candidate_listbox.insert(idx, listbox_text)

            if is_selected:
                self.candidate_listbox.selection_set(idx)
                # Refresh metadata label
                self.update_metadata_label(path)

    def _handle_review_lookahead(self, path):
        """Queue the candidate if it is within the lookahead window in Review Mode."""
        if self.has_switched_to_review:
            sel = self.candidate_listbox.curselection()
            if sel:
                cur_sel_idx = sel[0]
                if path in self.candidates:
                    new_idx = self.candidates.index(path)
                    # If the new candidate is within the lookahead window (next 3), queue it
                    if cur_sel_idx < new_idx <= cur_sel_idx + 3:
                        self.queue_candidate(new_idx)

    def switch_to_review_mode(self):
        self.has_switched_to_review = True
        self.notebook.tab(2, state="normal")
        self.notebook.select(2)
        self.log("Auto-switching to Review mode.")

        # Select the first one if nothing selected
        if not self.candidate_listbox.curselection():
            self.candidate_listbox.selection_set(0)
            self.on_candidate_select(None)

    def scan_finished(self):
        self.is_scanning = False
        self.notebook.tab(0, state="normal")

        self.review_status_lbl.config(text="Scan Complete.")

        if self.candidates:
            if not self.has_switched_to_review:
                self.switch_to_review_mode()
            self.log(f"Found {len(self.candidates)} images for review.")
        else:
            messagebox.showinfo(
                "Result",
                "No supported images found.",
            )
            self.notebook.select(0)

    def on_candidate_select(self, event):
        sel = self.candidate_listbox.curselection()
        if not sel:
            self.update_button_states()
            return

        idx = sel[0]
        current_path = self.candidates[idx]
        self.load_triplet_view(current_path)
        self.update_button_states()

        # Trigger preloader for next candidates
        self.preload_next_candidates(idx)

    def preload_next_candidates(self, current_idx):
        # Clear queue to prioritize new requests (user jumped to new location)
        self.cache_manager.clear_queues()

        # 1. Enqueue current and neighbors (prev 2, next 3) for full resolution loading IMMEDIATELY
        # This ensures the active image and neighbors are ready for fullscreen
        try:
            c_path = self.candidates[current_idx]
            self.queue_full_res_candidate(c_path)

            # Find neighbors for full res queue
            if c_path in self.sorted_files:
                f_idx = self.sorted_files.index(c_path)

                # Next 3 images
                for offset in range(1, 4):
                    if f_idx + offset < len(self.sorted_files):
                        self.queue_full_res_candidate(self.sorted_files[f_idx + offset])

                # Previous 2 images
                for offset in range(1, 3):
                    if f_idx - offset >= 0:
                        self.queue_full_res_candidate(self.sorted_files[f_idx - offset])
        except IndexError:
            pass

        # 2. Look ahead for next 3 candidates for preview loading
        count = 0
        for i in range(current_idx + 1, len(self.candidates)):
            if count >= 3:
                break
            self.queue_candidate(i)
            count += 1

    def queue_candidate(self, idx):
        try:
            c_path = self.candidates[idx]
            # Find neighbors
            if c_path in self.sorted_files:
                f_idx = self.sorted_files.index(c_path)

                # Prioritize: Candidate -> Next -> Prev
                self.cache_manager.queue_preview(c_path)

                if f_idx < len(self.sorted_files) - 1:
                    self.cache_manager.queue_preview(self.sorted_files[f_idx + 1])

                if f_idx > 0:
                    self.cache_manager.queue_preview(self.sorted_files[f_idx - 1])
        except IndexError:
            pass

    def queue_full_res_candidate(self, path):
        if path is None:
            return
        self.cache_manager.queue_full_res(path)

    # Threading methods removed, handled by ImageCacheManager

    def update_button_states(self):
        sel = self.candidate_listbox.curselection()
        if not sel:
            for btn in [
                "prev_btn", "next_btn", "del_btn", "move_btn",
                "focus_prev_btn", "focus_next_btn", "focus_del_btn", "focus_move_btn"
            ]:
                if hasattr(self, btn):
                    try:
                        getattr(self, btn).state(["disabled"])
                    except Exception:
                        pass
            return

        idx = sel[0]
        total = self.candidate_listbox.size()

        # Previous buttons
        prev_state = "!disabled" if idx > 0 else "disabled"
        for btn in ["prev_btn", "focus_prev_btn"]:
            if hasattr(self, btn):
                try:
                    getattr(self, btn).state([prev_state])
                except Exception:
                    pass

        # Next buttons
        next_state = "!disabled" if idx < total - 1 else "disabled"
        for btn in ["next_btn", "focus_next_btn"]:
            if hasattr(self, btn):
                try:
                    getattr(self, btn).state([next_state])
                except Exception:
                    pass

        # Action buttons
        for btn in ["del_btn", "focus_del_btn", "move_btn", "focus_move_btn"]:
            if hasattr(self, btn):
                try:
                    getattr(self, btn).state(["!disabled"])
                except Exception:
                    pass

    def load_triplet_view(self, current_path):
        # Find index in full sorted list
        if current_path not in self.sorted_files:
            return

        full_idx = self.sorted_files.index(current_path)

        prev_path = self.sorted_files[full_idx - 1] if full_idx > 0 else None
        next_path = (
            self.sorted_files[full_idx + 1]
            if full_idx < len(self.sorted_files) - 1
            else None
        )

        # Store paths in panels for fullscreen access
        self.panel_prev.path = prev_path
        self.panel_curr.path = current_path
        self.panel_next.path = next_path

        # Load Images in background to prevent UI freeze
        # Set placeholders first
        self.set_placeholder(self.panel_prev, prev_path)
        self.set_placeholder(self.panel_curr, current_path)
        self.set_placeholder(self.panel_next, next_path)

        # Update Metadata immediately
        self.update_metadata_label(current_path)

        # Clear current images to show loading state (avoids metadata/image mismatch)
        self.current_triplet_images = (None, None, None)
        self.refresh_active_view()

        # Ensure dimensions are up to date
        self.update_idletasks()

        # Get actual sizes from containers to load images at the correct size instantly
        if self.focus_mode:
            c_w = self.focus_curr_container.winfo_width()
            c_h = self.focus_curr_container.winfo_height()

            p_w = self.focus_prev_container.winfo_width()
            p_h = self.focus_prev_container.winfo_height()

            n_w = self.focus_next_container.winfo_width()
            n_h = self.focus_next_container.winfo_height()
        else:
            c_w = self.panel_curr.img_container.winfo_width()
            c_h = self.panel_curr.img_container.winfo_height()

            p_w = self.panel_prev.img_container.winfo_width()
            p_h = self.panel_prev.img_container.winfo_height()

            n_w = self.panel_next.img_container.winfo_width()
            n_h = self.panel_next.img_container.winfo_height()

        def _get_valid_size(w, h):
            if w < 10 or h < 10:
                return (800, 600)
            return (w, h)

        size_curr = _get_valid_size(c_w, c_h)
        size_prev = _get_valid_size(p_w, p_h)
        size_next = _get_valid_size(n_w, n_h)

        # Start background thread for loading images
        threading.Thread(
            target=self.load_images_background,
            args=(prev_path, current_path, next_path, size_curr, size_prev, size_next),
            daemon=True,
        ).start()



    def update_metadata_label(self, current_path):
        res = self.files_map.get(current_path)
        if res:
            exif = res.exif
            score_str = format_score(res.score)
            noise_str = format_score(res.noise_score)

            iso = format_meta(exif.iso if exif else None, "")
            shutter = format_meta(exif.shutter_speed if exif else None, "s")
            aperture = format_meta(exif.aperture if exif else None, "f/")
            focal = format_meta(exif.focal_length if exif else None, "mm")

            # ISO: 100 | 1/200s | f/2.8 | 50mm
            meta_str = f"ISO: {iso} | {shutter} | {aperture} | {focal}"

            txt = (
                f"File: {current_path.name}\n"
                f"Sharpness Score: {score_str}\n"
                f"Noise Level: {noise_str}\n"
                f"{meta_str}"
            )
            self.meta_lbl.config(text=txt)

            # Update Focus Mode labels if they exist
            if hasattr(self, "focus_score_lbl"):
                self.focus_score_lbl.config(
                    text=f"Sharpness Score: {score_str}"
                )
            if hasattr(self, "focus_noise_lbl"):
                self.focus_noise_lbl.config(
                    text=f"Noise Level: {noise_str}"
                )
            if hasattr(self, "focus_cat_lbl"):
                self.focus_cat_lbl.config(text="")
                self.focus_meta_lbl.config(text=meta_str)
                self.focus_filename_lbl.config(text=current_path.name)

        # Update previous and next overlay labels
        if hasattr(self, "focus_prev_overlay"):
            prev_path = self.panel_prev.path
            if prev_path:
                prev_res = self.files_map.get(prev_path)
                if prev_res:
                    prev_score_str = format_score(prev_res.score)
                    prev_noise_str = format_score(prev_res.noise_score)
                    prev_exif = prev_res.exif
                    p_iso = format_meta(prev_exif.iso if prev_exif else None, "")
                    p_shutter = format_meta(prev_exif.shutter_speed if prev_exif else None, "s")
                    p_aperture = format_meta(prev_exif.aperture if prev_exif else None, "f/")
                    p_focal = format_meta(prev_exif.focal_length if prev_exif else None, "mm")
                    p_meta = f"{p_iso} | {p_shutter} | {p_aperture} | {p_focal}"

                    self.focus_prev_overlay.config(
                        text=f"Previous\n{prev_path.name}\nSharpness: {prev_score_str}\nNoise: {prev_noise_str}\n{p_meta}"
                    )
                    self.focus_prev_overlay.place(relx=0.0, rely=0.0, anchor="nw")
            else:
                self.focus_prev_overlay.place_forget()

        if hasattr(self, "focus_next_overlay"):
            next_path = self.panel_next.path
            if next_path:
                next_res = self.files_map.get(next_path)
                if next_res:
                    next_score_str = format_score(next_res.score)
                    next_noise_str = format_score(next_res.noise_score)
                    next_exif = next_res.exif
                    n_iso = format_meta(next_exif.iso if next_exif else None, "")
                    n_shutter = format_meta(next_exif.shutter_speed if next_exif else None, "s")
                    n_aperture = format_meta(next_exif.aperture if next_exif else None, "f/")
                    n_focal = format_meta(next_exif.focal_length if next_exif else None, "mm")
                    n_meta = f"{n_iso} | {n_shutter} | {n_aperture} | {n_focal}"

                    self.focus_next_overlay.config(
                        text=f"Next\n{next_path.name}\nSharpness: {next_score_str}\nNoise: {next_noise_str}\n{n_meta}"
                    )
                    self.focus_next_overlay.place(relx=0.0, rely=0.0, anchor="nw")
            else:
                self.focus_next_overlay.place_forget()



    def prev_candidate(self):
        sel = self.candidate_listbox.curselection()
        if sel and sel[0] > 0:
            self.candidate_listbox.selection_clear(0, "end")
            self.candidate_listbox.selection_set(sel[0] - 1)
            self.candidate_listbox.event_generate("<<ListboxSelect>>")
            self.candidate_listbox.see(sel[0] - 1)

    def next_candidate(self):
        sel = self.candidate_listbox.curselection()
        if sel and sel[0] < self.candidate_listbox.size() - 1:
            self.candidate_listbox.selection_clear(0, "end")
            self.candidate_listbox.selection_set(sel[0] + 1)
            self.candidate_listbox.event_generate("<<ListboxSelect>>")
            self.candidate_listbox.see(sel[0] + 1)

    def delete_current_candidate(self):
        sel = self.candidate_listbox.curselection()
        if not sel:
            return

        idx = sel[0]
        path = self.candidates[idx]

        # Use our custom delete confirmation to support the <Delete> key
        self.show_delete_confirmation(path, idx)

    def show_delete_confirmation(self, path, idx):
        # Prevent multiple dialogs
        if (
            hasattr(self, "_delete_dialog")
            and self._delete_dialog
            and self._delete_dialog.winfo_exists()
        ):
            return

        dialog = tk.Toplevel(self)
        dialog.title("Confirm Delete")
        dialog.geometry("400x150")
        dialog.resizable(False, False)
        dialog.transient(self.winfo_toplevel())
        dialog.grab_set()

        # Center on parent
        parent = self.winfo_toplevel()
        x = parent.winfo_x() + (parent.winfo_width() - 400) // 2
        y = parent.winfo_y() + (parent.winfo_height() - 150) // 2
        dialog.geometry(f"+{x}+{y}")

        msg = f"Are you sure you want to move '{path.name}' and related files to trash?\n\n(Press Delete again to confirm)"
        ttk.Label(dialog, text=msg, justify="center", wraplength=350).pack(
            pady=20, padx=20
        )

        btn_frame = ttk.Frame(dialog)
        btn_frame.pack(fill="x", padx=20, pady=10)

        def on_confirm(*args):
            dialog.destroy()
            self.execute_delete(path, idx)

        def on_cancel(*args):
            dialog.destroy()

        yes_btn = ttk.Button(btn_frame, text="Yes", command=on_confirm)
        yes_btn.pack(side="left", expand=True, padx=5)
        no_btn = ttk.Button(btn_frame, text="No", command=on_cancel)
        no_btn.pack(side="right", expand=True, padx=5)

        # Bind Delete key to confirm
        dialog.bind("<Delete>", on_confirm)
        dialog.bind("<BackSpace>", on_confirm)
        dialog.bind("<Escape>", on_cancel)

        # Focus
        no_btn.focus_set()
        self._delete_dialog = dialog

    def execute_delete(self, path, idx):
        related = find_related_files(path)
        failed_trash = []

        for f in related:
            try:
                send2trash.send2trash(str(f))
                self.log(f"Moved to trash: {f}")
            except Exception as e:
                failed_trash.append(f)
                msg = f"Trash failed for {f}: {e}"
                self.log(msg)

        if failed_trash:
            msg = (
                f"Failed to move {len(failed_trash)} related file(s) to trash (e.g. network drive).\n"
                "Do you want to PERMANENTLY delete them?"
            )
            if messagebox.askyesno("Trash Failed", msg):
                for f in failed_trash:
                    try:
                        if f.exists():
                            f.unlink()
                            self.log(f"Permanently deleted: {f}")
                    except Exception as e:
                        msg = f"Delete failed for {f}: {e}"
                        self.log(msg)

        # Check if all files are gone
        remaining = [f for f in related if f.exists()]
        if not remaining:
            # Update UI
            self.candidates.pop(idx)
            self.candidate_listbox.delete(idx)
            if path in self.sorted_files:
                self.sorted_files.remove(path)
            if path in self.files_map:
                self.files_map.pop(path, None)

            # Select next if available, or prev
            if self.candidates:
                new_idx = idx if idx < len(self.candidates) else idx - 1
                self.candidate_listbox.selection_set(new_idx)
                self.on_candidate_select(None)
            else:
                self.panel_curr.img_lbl.config(image="", text="No Candidates")
                self.panel_prev.img_lbl.config(image="", text="")
                self.panel_next.img_lbl.config(image="", text="")

                self.panel_curr.path = None
                self.panel_prev.path = None
                self.panel_next.path = None

    def move_current_to_selection(self):
        sel = self.candidate_listbox.curselection()
        if not sel:
            return

        idx = sel[0]
        path = self.candidates[idx]
        self.execute_move_to_selection(path, idx)

    def execute_move_to_selection(self, path, idx):
        selected_dir_str = self.folder_var.get()
        if not selected_dir_str:
            return
        selected_dir = Path(selected_dir_str)
        selection_dir = selected_dir / "Selection"

        try:
            selection_dir.mkdir(parents=True, exist_ok=True)
        except Exception as e:
            messagebox.showerror("Error", f"Failed to create Selection directory: {e}")
            return

        related = find_related_files(path)
        moved_files = []
        failed_files = []

        for f in list(related):
            dest = selection_dir / f.name
            try:
                if dest.exists():
                    dest.unlink()
                f.rename(dest)
                moved_files.append(f)
                self.log(f"Moved to Selection: {f.name}")
            except Exception as e:
                failed_files.append((f, e))
                msg = f"Move failed for {f}: {e}"
                self.log(msg)

        if failed_files:
            err_msg = "\n".join([f"{f.name}: {e}" for f, e in failed_files])
            messagebox.showerror("Move Failed", f"Failed to move some files:\n{err_msg}")
            # If the main file failed to move, do not remove from internal list
            if path in [f for f, e in failed_files]:
                return

        # Update UI lists
        if idx < len(self.candidates) and self.candidates[idx] == path:
            self.candidates.pop(idx)
            self.candidate_listbox.delete(idx)
        else:
            if path in self.candidates:
                other_idx = self.candidates.index(path)
                self.candidates.remove(path)
                self.candidate_listbox.delete(other_idx)

        if path in self.sorted_files:
            self.sorted_files.remove(path)
        if path in self.files_map:
            self.files_map.pop(path, None)

        # Select next if available, or prev
        if self.candidates:
            new_idx = idx if idx < len(self.candidates) else len(self.candidates) - 1
            self.candidate_listbox.selection_clear(0, "end")
            self.candidate_listbox.selection_set(new_idx)
            self.on_candidate_select(None)
        else:
            self.panel_curr.img_lbl.config(image="", text="No Candidates")
            self.panel_prev.img_lbl.config(image="", text="")
            self.panel_next.img_lbl.config(image="", text="")

            self.panel_curr.path = None
            self.panel_prev.path = None
            self.panel_next.path = None

