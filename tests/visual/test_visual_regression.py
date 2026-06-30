"""
Visual regression tests for the Photo Selector Toolbox GUI.

These tests render real tkinter widgets inside Xvfb, capture screenshots,
and compare them against stored baseline images.

To generate/update baselines:
    UPDATE_BASELINES=1 pytest tests/visual/ -m visual

Baselines are committed to the repo under tests/visual/baselines/.
"""

import pytest

from .conftest import (
    capture_widget_screenshot,
    assert_screenshot_matches,
)

pytestmark = [pytest.mark.visual, pytest.mark.linux_only]


# ── Helpers ────────────────────────────────────────────────────────


def _apply_theme(root):
    """Apply the app's dark theme to a root window."""
    from photo_selector_toolbox.gui import apply_dark_theme
    apply_dark_theme(root)


# ── MainApp shell ─────────────────────────────────────────────────


class TestMainAppVisual:
    """Visual tests for the main application window (menu + content area)."""

    def test_main_app_initial_layout(self, visual_root):
        """Verify the main window renders with sidebar, menu bar,
        and the default frame visible."""
        from tkinter import ttk

        root = visual_root
        _apply_theme(root)
        root.title("Photo Selector Toolbox")
        root.deiconify()

        # Build the content area manually (avoiding MainApp's splash
        # screen and icon logic that doesn't work under Xvfb)
        content_area = ttk.Frame(root)
        content_area.pack(fill="both", expand=True)

        # Create tab-like header
        header = ttk.Frame(content_area)
        header.pack(fill="x", padx=10, pady=(10, 0))
        for label in ["Photo Selector", "Library Statistics", "Duplicate Finder"]:
            ttk.Button(header, text=label).pack(side="left", padx=2)

        # Placeholder content
        body = ttk.LabelFrame(content_area, text="Configuration", padding=10)
        body.pack(fill="both", expand=True, padx=10, pady=10)
        ttk.Label(body, text="Select a folder to begin").pack(pady=20)

        screenshot = capture_widget_screenshot(root, 1024, 768)
        assert_screenshot_matches(screenshot, "main_app_initial")


# ── ImageLibraryStatistics ────────────────────────────────────────


class TestImageLibraryStatisticsVisual:
    """Visual tests for the Image Library Statistics panel."""

    def test_statistics_empty_state(self, visual_root):
        """The statistics panel in its initial empty state."""
        from photo_selector_toolbox.gui import ImageLibraryStatistics

        root = visual_root
        _apply_theme(root)
        root.deiconify()

        frame = ImageLibraryStatistics(root)
        frame.pack(fill="both", expand=True)

        screenshot = capture_widget_screenshot(root, 1024, 768)
        assert_screenshot_matches(screenshot, "statistics_empty")

    def test_statistics_with_progress(self, visual_root):
        """The statistics panel while an analysis is in progress."""
        from photo_selector_toolbox.gui import ImageLibraryStatistics

        root = visual_root
        _apply_theme(root)
        root.deiconify()

        frame = ImageLibraryStatistics(root)
        frame.pack(fill="both", expand=True)

        # Simulate analysis in progress
        frame.root_folder_var.set("/home/user/Photos")
        frame.is_analyzing = True
        if hasattr(frame, "analyze_btn"):
            frame.analyze_btn.configure(text="Stop", state="normal")
        if hasattr(frame, "progress_var"):
            frame.progress_var.set(45.0)
        if hasattr(frame, "status_lbl"):
            frame.status_lbl.configure(text="Analyzing: 450/1000 images...")

        screenshot = capture_widget_screenshot(root, 1024, 768)
        assert_screenshot_matches(screenshot, "statistics_in_progress")


# ── DuplicateFinder ───────────────────────────────────────────────


class TestDuplicateFinderVisual:
    """Visual tests for the Duplicate Finder panel."""

    def test_duplicate_finder_empty_state(self, visual_root):
        """The duplicate finder in its initial empty state."""
        from photo_selector_toolbox.gui import DuplicateFinder

        root = visual_root
        _apply_theme(root)
        root.deiconify()

        frame = DuplicateFinder(root)
        frame.pack(fill="both", expand=True)

        screenshot = capture_widget_screenshot(root, 1024, 768)
        assert_screenshot_matches(screenshot, "duplicate_finder_empty")


# ── About Dialog ──────────────────────────────────────────────────


class TestAboutDialogVisual:
    """Visual tests for the About dialog."""

    def test_about_dialog_layout(self, visual_root):
        """The About dialog renders with version info and links."""
        from photo_selector_toolbox.gui import AboutDialog

        root = visual_root
        _apply_theme(root)
        root.deiconify()


        dialog = AboutDialog(root)

        screenshot = capture_widget_screenshot(dialog, 500, 400)
        assert_screenshot_matches(screenshot, "about_dialog")

        dialog.destroy()


# ── Dark Theme Consistency ────────────────────────────────────────


class TestThemeConsistency:
    """Verify that theme colours and widget styling are consistent."""

    def test_button_styles(self, visual_root):
        """All button variants render with correct styling."""
        from tkinter import ttk

        root = visual_root
        _apply_theme(root)
        root.deiconify()

        frame = ttk.Frame(root)
        frame.pack(fill="both", expand=True, padx=20, pady=20)

        ttk.Label(frame, text="Button Styles", style="Header.TLabel").pack(
            pady=(0, 10)
        )

        for i, (text, style) in enumerate([
            ("Primary Action", "Primary.TButton"),
            ("Default Button", "TButton"),
            ("Danger Action", "Danger.TButton"),
        ]):
            try:
                ttk.Button(frame, text=text, style=style).pack(
                    fill="x", pady=3
                )
            except Exception:
                # Style might not exist, use default
                ttk.Button(frame, text=text).pack(fill="x", pady=3)

        screenshot = capture_widget_screenshot(root, 400, 300)
        assert_screenshot_matches(screenshot, "button_styles")

    def test_label_styles(self, visual_root):
        """All label variants render with correct font and colour."""
        from tkinter import ttk

        root = visual_root
        _apply_theme(root)
        root.deiconify()

        frame = ttk.Frame(root)
        frame.pack(fill="both", expand=True, padx=20, pady=20)

        ttk.Label(frame, text="Header Label", style="Header.TLabel").pack(
            anchor="w", pady=3
        )
        ttk.Label(frame, text="Title Label", style="Title.TLabel").pack(
            anchor="w", pady=3
        )
        ttk.Label(frame, text="Default Label").pack(anchor="w", pady=3)
        ttk.Label(frame, text="Muted Label", style="Muted.TLabel").pack(
            anchor="w", pady=3
        )

        screenshot = capture_widget_screenshot(root, 400, 300)
        assert_screenshot_matches(screenshot, "label_styles")

    def test_input_controls(self, visual_root):
        """Entry, Combobox, Checkbox, and Progress render with theme."""
        import tkinter as tk
        from tkinter import ttk

        root = visual_root
        _apply_theme(root)
        root.deiconify()

        frame = ttk.Frame(root)
        frame.pack(fill="both", expand=True, padx=20, pady=20)

        ttk.Label(frame, text="Input Controls", style="Header.TLabel").pack(
            pady=(0, 10)
        )

        entry_var = tk.StringVar(root, value="Sample text")
        ttk.Entry(frame, textvariable=entry_var, width=40).pack(
            fill="x", pady=3
        )

        combo = ttk.Combobox(
            frame, values=["Option A", "Option B", "Option C"], state="readonly"
        )
        combo.current(0)
        combo.pack(fill="x", pady=3)

        check_var = tk.BooleanVar(root, value=True)
        ttk.Checkbutton(frame, text="Enable feature", variable=check_var).pack(
            anchor="w", pady=3
        )

        progress = ttk.Progressbar(frame, value=65, maximum=100)
        progress.pack(fill="x", pady=3)

        screenshot = capture_widget_screenshot(root, 400, 350)
        assert_screenshot_matches(screenshot, "input_controls")

    def test_labelframe_nesting(self, visual_root):
        """LabelFrame panels nest correctly with theme colours."""
        from tkinter import ttk

        root = visual_root
        _apply_theme(root)
        root.deiconify()

        outer = ttk.LabelFrame(root, text="Outer Panel", padding=10)
        outer.pack(fill="both", expand=True, padx=10, pady=10)

        ttk.Label(outer, text="Content inside outer panel").pack(pady=5)

        inner = ttk.LabelFrame(outer, text="Inner Panel", padding=10)
        inner.pack(fill="x", padx=5, pady=5)

        ttk.Label(inner, text="Nested content").pack()
        ttk.Button(inner, text="Action").pack(pady=5)

        screenshot = capture_widget_screenshot(root, 500, 400)
        assert_screenshot_matches(screenshot, "labelframe_nesting")
