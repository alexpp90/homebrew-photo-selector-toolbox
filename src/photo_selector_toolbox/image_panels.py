import logging
from PIL import Image, ImageTk
from tkinter import ttk

# Local imports
from photo_selector_toolbox.utils import load_image_preview, create_placeholder_image
from photo_selector_toolbox.formatting import format_score

logger = logging.getLogger(__name__)


class ImagePanelsMixin:
    """Mixin class for SharpnessTool to manage image panel scaling and background loading."""

    def create_image_panel(self, parent, title):
        frame = ttk.LabelFrame(parent, text=title)

        # Image Container Frame
        img_container = ttk.Frame(frame)
        img_container.pack(fill="both", expand=True)
        img_container.pack_propagate(
            False
        )  # Stop the label from resizing the container

        # Image Label (Placeholder)
        lbl = ttk.Label(img_container, text="No Image", anchor="center", cursor="hand2")
        lbl.place(relx=0.5, rely=0.5, anchor="center")

        # Details
        details = ttk.Label(frame, text="", font=("Helvetica", 9))
        details.pack(fill="x")

        frame.img_container = img_container  # Store ref
        frame.img_lbl = lbl  # Store ref
        frame.details_lbl = details  # Store ref
        frame.path = None  # Initialize path
        frame.pil_image = None  # Reference to unscaled base image
        frame.tk_image = None

        # Responsive resize handler
        img_container.bind("<Configure>", lambda e: self.on_panel_resize(e, frame))

        # Bind click to fullscreen
        lbl.bind("<Button-1>", lambda e: self.on_thumbnail_single_click(e, frame))
        lbl.bind(
            "<Double-Button-1>", lambda e: self.on_thumbnail_double_click(e, frame)
        )

        return frame

    def on_panel_resize(self, event, panel):
        """Called when a panel resizes. Triggers image rescaling if available."""
        if (
            hasattr(panel, "_last_width")
            and panel._last_width == event.width
            and panel._last_height == event.height
        ):
            return

        panel._last_width = event.width
        panel._last_height = event.height

        if hasattr(self, "_resize_timer_" + str(id(panel))):
            self.after_cancel(getattr(self, "_resize_timer_" + str(id(panel))))

        # Debounce the resize to prevent lag (increased for optimization)
        timer_id = self.after(300, lambda: self.scale_image_to_panel(panel))
        setattr(self, "_resize_timer_" + str(id(panel)), timer_id)

    def scale_image_to_panel(self, panel):
        """Scales the panel's PIL image to fit its current label dimensions."""
        if not hasattr(panel, "pil_image") or not panel.pil_image:
            return

        img_container = panel.img_container
        lbl = panel.img_lbl
        img_container.update_idletasks()  # Ensure dimensions are correct

        # Get dimensions of the image container
        w = img_container.winfo_width()
        h = img_container.winfo_height()

        # Fallback to sensible default if container is uninitialized (e.g. 1x1)
        if w < 10 or h < 10:
            w, h = panel.pil_image.size

        # Calculate maximum scale factor that fits image into the container dimensions
        img_w, img_h = panel.pil_image.size

        # Avoid division by zero
        if img_w == 0 or img_h == 0:
            return

        scale = min(w / img_w, h / img_h)
        opt_w = int(img_w * scale)
        opt_h = int(img_h * scale)

        try:
            img_copy = panel.pil_image.copy()
            img_copy.thumbnail((opt_w, opt_h), Image.Resampling.LANCZOS)
            tk_img = ImageTk.PhotoImage(img_copy)

            lbl.config(image=tk_img, text="")
            lbl.image = tk_img  # Keep reference to prevent garbage collection
        except Exception as e:
            logger.error(f"Error scaling panel image: {e}")

    def on_focus_label_resize(self, event, lbl):
        """Called when a focus mode label resizes."""
        if (
            hasattr(lbl, "_last_width")
            and lbl._last_width == event.width
            and lbl._last_height == event.height
        ):
            return

        lbl._last_width = event.width
        lbl._last_height = event.height

        if hasattr(self, "_resize_timer_f_" + str(id(lbl))):
            self.after_cancel(getattr(self, "_resize_timer_f_" + str(id(lbl))))

        # Debounce the resize to prevent lag
        timer_id = self.after(300, lambda: self.scale_image_to_focus_label(lbl))
        setattr(self, "_resize_timer_f_" + str(id(lbl)), timer_id)

    def scale_image_to_focus_label(self, lbl):
        """Scales the PIL image stored on a focus label to fit its dimensions."""
        if not hasattr(lbl, "pil_image") or not lbl.pil_image:
            return

        container = lbl.container
        container.update_idletasks()
        w = container.winfo_width()
        h = container.winfo_height()

        if w < 10 or h < 10:
            w, h = lbl.pil_image.size

        # Calculate maximum scale factor that fits image into the container dimensions
        img_w, img_h = lbl.pil_image.size

        # Avoid division by zero
        if img_w == 0 or img_h == 0:
            return

        scale = min(w / img_w, h / img_h)
        opt_w = int(img_w * scale)
        opt_h = int(img_h * scale)

        try:
            img_copy = lbl.pil_image.copy()
            img_copy.thumbnail((opt_w, opt_h), Image.Resampling.LANCZOS)
            tk_img = ImageTk.PhotoImage(img_copy)

            lbl.config(image=tk_img, text="")
            lbl.image = tk_img
        except Exception as e:
            logger.error(f"Error scaling focus label image: {e}")

    def on_thumbnail_single_click(self, event, frame):
        if not frame.path:
            return
        self._pending_click_path = frame.path
        # Delay to detect double click
        self._pending_click_id = self.after(
            250, lambda: self.open_fullscreen(frame.path, "fit")
        )

    def on_thumbnail_double_click(self, event, frame):
        if not frame.path:
            return
        # Cancel single click
        if hasattr(self, "_pending_click_id"):
            self.after_cancel(self._pending_click_id)
            del self._pending_click_id

        # Calculate coordinates
        lbl = event.widget
        rx, ry = 0.5, 0.5

        if hasattr(lbl, "image") and lbl.image:
            img_w = lbl.image.width()
            img_h = lbl.image.height()
            lbl_w = lbl.winfo_width()
            lbl_h = lbl.winfo_height()

            # Image is centered
            x_start = (lbl_w - img_w) // 2
            y_start = (lbl_h - img_h) // 2

            click_x = event.x - x_start
            click_y = event.y - y_start

            rx = click_x / img_w
            ry = click_y / img_h

            # Clamp
            rx = max(0.0, min(1.0, rx))
            ry = max(0.0, min(1.0, ry))

        self.open_fullscreen(frame.path, "100%", (rx, ry))

    def on_image_click(self, path):
        if path and path.exists():
            self.open_fullscreen(path, "fit")

    def set_placeholder(self, panel, path):
        lbl = panel.img_lbl
        details = panel.details_lbl

        panel.img_container.update_idletasks()
        w = panel.img_container.winfo_width()
        h = panel.img_container.winfo_height()
        if w < 10 or h < 10:
            w, h = 400, 300

        if path is None:
            placeholder_img = create_placeholder_image(w, h, "No Image Selected")
            tk_img = ImageTk.PhotoImage(placeholder_img)
            lbl.config(image=tk_img, text="")
            lbl.image = tk_img
            details.config(text="")
            panel.pil_image = placeholder_img
            panel.path = None
            return

        res = self.files_map.get(path)
        lines = [path.name]

        if res:
            score_txt = format_score(res.score)
            noise_txt = format_score(res.noise_score)
            if score_txt != "N/A":
                lines.append(f"Sharpness: {score_txt}")
            if noise_txt != "N/A":
                lines.append(f"Noise: {noise_txt}")

        details.config(
            text="\n".join(lines),
        )
        placeholder_img = create_placeholder_image(w, h, f"Loading: {path.name}")
        tk_img = ImageTk.PhotoImage(placeholder_img)
        lbl.config(image=tk_img, text="")
        lbl.image = tk_img
        panel.pil_image = placeholder_img

    def load_images_background(
        self, prev_path, curr_path, next_path, size_curr, size_prev, size_next
    ):
        CACHE_SIZE = (1200, 900)

        def get_image(path, requested_size):
            if path is None:
                return None

            img = None

            # 1. Try Cache
            img = self.cache_manager.get_preview(path)

            # 2. Load if not in cache
            if not img:
                try:
                    img = load_image_preview(path, max_size=CACHE_SIZE)
                    if img:
                        with self.cache_manager.preview_lock:
                            self.cache_manager.preview_cache[path] = img
                except Exception as e:
                    logger.error(f"Error loading {path}: {e}")

            if not img:
                return None

            # 3. Return the base unscaled PIL image.
            # We scale it dynamically in the main thread to fit the UI panel perfectly.
            try:
                img_copy = img.copy()
                img_copy.thumbnail(requested_size, Image.Resampling.LANCZOS)
                return img_copy
            except Exception as e:
                logger.error(f"Error preparing {path}: {e}")
                return None

        p_img = get_image(prev_path, size_prev)
        c_img = get_image(curr_path, size_curr)
        n_img = get_image(next_path, size_next)

        # Update UI in main thread
        self.parent.after(0, lambda: self.update_panels_final(p_img, c_img, n_img, prev_path, curr_path, next_path))

    def update_panels_final(self, p_img, c_img, n_img, prev_path, curr_path, next_path):
        if (
            self.panel_prev.path != prev_path
            or self.panel_curr.path != curr_path
            or self.panel_next.path != next_path
        ):
            return  # Stale load, ignore
        self.current_triplet_images = (p_img, c_img, n_img)
        self.refresh_active_view()

    def refresh_active_view(self):
        p_img, c_img, n_img = self.current_triplet_images

        if self.focus_mode:
            # Update Focus Mode
            def set_lbl(lbl, img, default_text):
                lbl.pil_image = img  # Store unscaled image for resize events

                if img:
                    self.scale_image_to_focus_label(lbl)
                else:
                    lbl.container.update_idletasks()
                    w = lbl.container.winfo_width()
                    h = lbl.container.winfo_height()
                    if w < 10 or h < 10:
                        w, h = 400, 300
                    placeholder_img = create_placeholder_image(w, h, default_text)
                    tk_img = ImageTk.PhotoImage(placeholder_img)
                    lbl.config(image=tk_img, text="")
                    lbl.image = tk_img
                    lbl.pil_image = placeholder_img

            set_lbl(self.focus_prev_lbl, p_img, "Previous Image")
            set_lbl(self.focus_curr_lbl, c_img, "No Image Selected")
            set_lbl(self.focus_next_lbl, n_img, "Next Image")
        else:
            # Helper to set image on a label
            def set_panel_img(panel, img):
                lbl = panel.img_lbl
                panel.pil_image = img  # Store raw PIL image

                if img:
                    # Initial display before resize event fires
                    self.scale_image_to_panel(panel)
                else:
                    panel.img_container.update_idletasks()
                    w = panel.img_container.winfo_width()
                    h = panel.img_container.winfo_height()
                    if w < 10 or h < 10:
                        w, h = 400, 300
                    p_name = panel.path.name if panel.path else "No Image Selected"
                    p_text = f"Preview Unavailable: {p_name}" if panel.path else "No Image Selected"
                    placeholder_img = create_placeholder_image(w, h, p_text)
                    tk_img = ImageTk.PhotoImage(placeholder_img)
                    lbl.config(image=tk_img, text="")
                    lbl.image = tk_img
                    panel.pil_image = placeholder_img

            set_panel_img(self.panel_prev, p_img)
            set_panel_img(self.panel_curr, c_img)
            set_panel_img(self.panel_next, n_img)
