import logging
import threading
import tkinter as tk
from pathlib import Path
from typing import List, Optional
from tkinter import ttk
from PIL import Image, ImageTk

# Local imports
from photo_selector_toolbox.utils import load_image_preview

logger = logging.getLogger(__name__)


class FullscreenViewer(tk.Toplevel):
    def __init__(
        self, parent, path, initial_mode="fit", focus_point=(0.5, 0.5), file_list=None
    ):
        super().__init__(parent)
        self.parent = parent
        self.path = path
        self.initial_mode = initial_mode
        self.focus_point = focus_point  # (rel_x, rel_y) 0.0-1.0
        self.file_list = file_list or []

        # Track current index
        self.current_idx = -1
        if self.path in self.file_list:
            self.current_idx = self.file_list.index(self.path)

        self.title(f"Fullscreen - {path.name}")
        self.attributes("-fullscreen", True)
        self.geometry(f"{self.winfo_screenwidth()}x{self.winfo_screenheight()}")

        # UI Elements
        self.canvas = tk.Canvas(self, bg="black", highlightthickness=0)
        self.canvas.pack(fill="both", expand=True)

        self.loading_lbl = ttk.Label(
            self,
            text="Loading full resolution...",
            anchor="center",
            background="black",
            foreground="white",
        )
        self.loading_lbl.place(relx=0.5, rely=0.5, anchor="center")

        self.close_btn = ttk.Button(self, text="Close (Esc)", command=self.destroy)
        self.close_btn.place(relx=0.95, rely=0.05, anchor="ne")

        # Next / Previous buttons below the Close button
        self.next_btn = ttk.Button(self, text="Next (N)", command=self.next_image)
        self.next_btn.place(relx=0.95, rely=0.10, anchor="ne")

        self.prev_btn = ttk.Button(self, text="Previous (P)", command=self.prev_image)
        self.prev_btn.place(relx=0.95, rely=0.15, anchor="ne")

        self.del_btn = ttk.Button(self, text="Delete (Delete)", command=self.confirm_delete_image)
        self.del_btn.place(relx=0.95, rely=0.20, anchor="ne")

        self.move_btn = ttk.Button(self, text="Move to Selection (M)", command=self.move_to_selection)
        self.move_btn.place(relx=0.95, rely=0.25, anchor="ne")

        self.update_nav_buttons()

        # State
        self.pil_image = None  # Full resolution PIL image
        self.tk_image = None
        self.scale = 1.0
        self.min_scale = 0.1
        self.fit_scale = 1.0
        self.offset_x = 0
        self.offset_y = 0
        self.drag_start = None

        # Bindings
        self.bind("<Escape>", lambda e: self.destroy())
        self.canvas.bind("<ButtonPress-1>", self.on_drag_start)
        self.canvas.bind("<B1-Motion>", self.on_drag_move)

        # Zoom bindings
        self.canvas.bind("<MouseWheel>", self.on_zoom_wheel)  # Windows/MacOS
        self.canvas.bind("<Button-4>", self.on_zoom_wheel)  # Linux Scroll Up
        self.canvas.bind("<Button-5>", self.on_zoom_wheel)  # Linux Scroll Down

        # Key navigation
        self.bind("<Left>", lambda e: self.pan_key(50, 0))
        self.bind("<Right>", lambda e: self.pan_key(-50, 0))
        self.bind("<Up>", lambda e: self.pan_key(0, 50))
        self.bind("<Down>", lambda e: self.pan_key(0, -50))
        self.bind("<plus>", lambda e: self.zoom_key(1.2))
        self.bind("<equal>", lambda e: self.zoom_key(1.2))  # Shared key with plus
        self.bind("<minus>", lambda e: self.zoom_key(0.8))

        self.bind("<n>", self.next_image)
        self.bind("<N>", self.next_image)
        self.bind("<p>", self.prev_image)
        self.bind("<P>", self.prev_image)
        self.bind("<Delete>", lambda e: self.confirm_delete_image())
        self.bind("<BackSpace>", lambda e: self.confirm_delete_image())
        self.bind("<m>", self.move_to_selection)
        self.bind("<M>", self.move_to_selection)

        # Grab focus immediately to ensure key events work
        self.focus_set()

        # Start Loading
        self.after(100, self.load_image)

    def update_nav_buttons(self):
        if not self.file_list:
            self.next_btn.state(["disabled"])
            self.prev_btn.state(["disabled"])
            if hasattr(self, "move_btn"):
                self.move_btn.state(["disabled"])
            return

        if self.current_idx < len(self.file_list) - 1:
            self.next_btn.state(["!disabled"])
        else:
            self.next_btn.state(["disabled"])

        if self.current_idx > 0:
            self.prev_btn.state(["!disabled"])
        else:
            self.prev_btn.state(["disabled"])

        if hasattr(self, "move_btn"):
            self.move_btn.state(["!disabled"])

    def next_image(self, event=None):
        if self.file_list and self.current_idx < len(self.file_list) - 1:
            self.current_idx += 1
            self.load_new_path(self.file_list[self.current_idx])

    def prev_image(self, event=None):
        if self.file_list and self.current_idx > 0:
            self.current_idx -= 1
            self.load_new_path(self.file_list[self.current_idx])

    def load_new_path(self, new_path):
        self.path = new_path
        self.initial_mode = "fit"
        self.title(f"Fullscreen - {self.path.name}")
        self.update_nav_buttons()

        # Update parent's selection and trigger updates
        if hasattr(self.parent, "candidates") and self.path in self.parent.candidates:
            idx = self.parent.candidates.index(self.path)
            self.parent.candidate_listbox.selection_clear(0, "end")
            self.parent.candidate_listbox.selection_set(idx)
            self.parent.candidate_listbox.see(idx)
            self.parent.on_candidate_select(None)

        # Show loading indicator again
        self.canvas.delete("all")
        self.loading_lbl.config(text="Loading full resolution...")
        self.loading_lbl.place(relx=0.5, rely=0.5, anchor="center")

        # Clear current image state
        self.pil_image = None
        self.tk_image = None

        # Load new image
        self.load_image()

    def confirm_delete_image(self):
        if not self.path:
            return

        # Prevent multiple dialogs
        if (
            hasattr(self, "_delete_dialog")
            and self._delete_dialog
            and self._delete_dialog.winfo_exists()
        ):
            return

        dialog = tk.Toplevel(self)
        dialog.title("Confirm Delete")
        dialog.resizable(False, False)
        dialog.transient(self)
        dialog.grab_set()

        msg = f"Are you sure you want to move '{self.path.name}' and related files to trash?\n\n(Press Delete again to confirm)"
        ttk.Label(dialog, text=msg, justify="center", wraplength=350).pack(
            pady=(20, 10), padx=20
        )

        btn_frame = ttk.Frame(dialog)
        btn_frame.pack(fill="x", padx=20, pady=(10, 20))

        def on_confirm(*args):
            dialog.destroy()
            self.execute_delete_current()

        def on_cancel(*args):
            dialog.destroy()

        yes_btn = ttk.Button(btn_frame, text="Yes", command=on_confirm)
        yes_btn.pack(side="left", expand=True, padx=5)
        no_btn = ttk.Button(btn_frame, text="No", command=on_cancel)
        no_btn.pack(side="right", expand=True, padx=5)

        # Let Tkinter calculate the required size, setting a minimum geometry
        dialog.update_idletasks()
        try:
            width = max(400, int(dialog.winfo_reqwidth()))
            height = max(180, int(dialog.winfo_reqheight()))
        except (TypeError, ValueError):
            width = 400
            height = 180

        # Center on FullscreenViewer
        x = self.winfo_x() + (self.winfo_width() - width) // 2
        y = self.winfo_y() + (self.winfo_height() - height) // 2
        dialog.geometry(f"{width}x{height}+{x}+{y}")

        dialog.bind("<Delete>", on_confirm)
        dialog.bind("<BackSpace>", on_confirm)
        dialog.bind("<Escape>", on_cancel)

        no_btn.focus_set()
        self._delete_dialog = dialog

    def execute_delete_current(self):
        path = self.path
        if hasattr(self.parent, "candidates") and path in self.parent.candidates:
            idx = self.parent.candidates.index(path)
            self.parent.execute_delete(path, idx)

            if path in self.file_list:
                self.file_list.remove(path)

            if not self.file_list:
                self.destroy()
                return

            if self.current_idx >= len(self.file_list):
                self.current_idx = len(self.file_list) - 1

            self.load_new_path(self.file_list[self.current_idx])

    def move_to_selection(self, event=None):
        path = self.path
        if not path:
            return
        if hasattr(self.parent, "candidates") and path in self.parent.candidates:
            idx = self.parent.candidates.index(path)
            self.parent.execute_move_to_selection(path, idx)

            if path in self.file_list:
                self.file_list.remove(path)

            if not self.file_list:
                self.destroy()
                return

            if self.current_idx >= len(self.file_list):
                self.current_idx = len(self.file_list) - 1

            self.load_new_path(self.file_list[self.current_idx])

    def load_image(self):
        # Check cache
        img = self.parent.cache_manager.get_full_res(self.path)

        if img:
            self.pil_image = img
            self.on_image_loaded()
        else:
            # Load in thread
            threading.Thread(target=self.load_worker, daemon=True).start()

    def load_worker(self):
        try:
            img = load_image_preview(self.path, full_res=True)
            if img:
                self.pil_image = img
                self.parent.after(0, self.on_image_loaded)
                # Add to parent cache if possible
                with self.parent.cache_manager.full_res_lock:
                    self.parent.cache_manager.full_res_cache[self.path] = img
            else:
                self.parent.after(
                    0, lambda: self.loading_lbl.config(text="Failed to load.")
                )
        except Exception as e:
            msg = f"Error: {e}"
            self.parent.after(0, lambda: self.loading_lbl.config(text=msg))

    def on_image_loaded(self):
        self.loading_lbl.place_forget()
        if not self.pil_image:
            return

        sw = self.winfo_width()
        sh = self.winfo_height()
        iw, ih = self.pil_image.size

        # Avoid division by zero
        if iw == 0 or ih == 0:
            return

        # Casting to int for type safety
        sw_int = int(sw)
        sh_int = int(sh)

        scale_w = sw_int / iw
        scale_h = sh_int / ih
        self.fit_scale = min(scale_w, scale_h)
        self.min_scale = self.fit_scale

        if self.initial_mode == "fit":
            self.scale = self.fit_scale
            # Center image
            self.offset_x = (sw - iw * self.scale) / 2
            self.offset_y = (sh - ih * self.scale) / 2
        else:
            # 100%
            self.scale = 1.0
            # Center on focus point
            rel_x, rel_y = self.focus_point

            # Target center on screen
            cx = sw / 2
            cy = sh / 2

            # Image coordinate to be at cx, cy
            ix = rel_x * iw
            iy = rel_y * ih

            # offset_x + ix * scale = cx
            # offset_x = cx - ix * scale
            self.offset_x = cx - ix * self.scale
            self.offset_y = cy - iy * self.scale

            self.clamp_offsets()

        self.redraw()

    def redraw(self):
        if not self.pil_image:
            return

        # Optimized approach: Crop and Resize
        sw = self.winfo_width()
        sh = self.winfo_height()

        x1 = max(0, (0 - self.offset_x) / self.scale)
        y1 = max(0, (0 - self.offset_y) / self.scale)
        x2 = min(self.pil_image.width, (sw - self.offset_x) / self.scale)
        y2 = min(self.pil_image.height, (sh - self.offset_y) / self.scale)

        if x2 <= x1 or y2 <= y1:
            self.canvas.delete("all")
            return

        # Crop
        crop_box = (int(x1), int(y1), int(x2) + 1, int(y2) + 1)

        try:
            region = self.pil_image.crop(crop_box)

            # Resize region to screen pixels
            target_w = int(region.width * self.scale)
            target_h = int(region.height * self.scale)

            if target_w <= 0 or target_h <= 0:
                return

            # Use BILINEAR for quality
            region = region.resize((target_w, target_h), Image.Resampling.BILINEAR)

            self.tk_image = ImageTk.PhotoImage(region)

            # Place on canvas
            dest_x = self.offset_x + x1 * self.scale
            dest_y = self.offset_y + y1 * self.scale

            self.canvas.delete("all")
            self.canvas.create_image(dest_x, dest_y, anchor="nw", image=self.tk_image)

        except Exception as e:
            logger.error(f"Redraw error: {e}")

    def clamp_offsets(self):
        sw = self.winfo_width()
        sh = self.winfo_height()
        if not self.pil_image:
            return

        iw = self.pil_image.width * self.scale
        ih = self.pil_image.height * self.scale

        if iw <= sw:
            self.offset_x = (sw - iw) / 2
        else:
            # Constrain: offset_x cannot be > 0 (left gap) and cannot be < sw - iw (right gap)
            self.offset_x = min(0, max(sw - iw, self.offset_x))

        if ih <= sh:
            self.offset_y = (sh - ih) / 2
        else:
            self.offset_y = min(0, max(sh - ih, self.offset_y))

    def on_drag_start(self, event):
        self.drag_start = (event.x, event.y)

    def on_drag_move(self, event):
        if not self.drag_start:
            return
        dx = event.x - self.drag_start[0]
        dy = event.y - self.drag_start[1]

        self.offset_x += dx
        self.offset_y += dy
        self.drag_start = (event.x, event.y)

        self.clamp_offsets()
        self.redraw()

    def on_zoom_wheel(self, event):
        factor = 1.0
        if event.num == 4:  # Linux Scroll Up
            factor = 1.2
        elif event.num == 5:  # Linux Scroll Down
            factor = 0.8
        else:  # Windows/MacOS
            if event.delta > 0:
                factor = 1.2
            else:
                factor = 0.8

        self.zoom(factor, event.x, event.y)

    def zoom_key(self, factor):
        cx = self.winfo_width() / 2
        cy = self.winfo_height() / 2
        self.zoom(factor, cx, cy)

    def zoom(self, factor, center_x, center_y):
        if not self.pil_image:
            return

        old_scale = self.scale
        new_scale = old_scale * factor

        if new_scale < self.fit_scale:
            new_scale = self.fit_scale

        if new_scale > 4.0:
            new_scale = 4.0

        if new_scale == old_scale:
            return

        px = (center_x - self.offset_x) / old_scale
        py = (center_y - self.offset_y) / old_scale

        self.offset_x = center_x - px * new_scale
        self.offset_y = center_y - py * new_scale

        self.scale = new_scale
        self.clamp_offsets()
        self.redraw()

    def pan_key(self, dx, dy):
        self.offset_x += dx
        self.offset_y += dy
        self.clamp_offsets()
        self.redraw()
