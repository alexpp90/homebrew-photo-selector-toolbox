"""
Visual regression test infrastructure.

Renders real tkinter windows on Xvfb, captures screenshots via Pillow,
and compares them against stored baselines using pixelmatch.

Run with:  pytest tests/visual/ -m visual
Update baselines:  UPDATE_BASELINES=1 pytest tests/visual/ -m visual
"""

import os
import sys
import time
import pytest
from pathlib import Path
from PIL import Image

BASELINES_DIR = Path(__file__).parent / "baselines"
DIFFS_DIR = Path(__file__).parent / "diffs"
# Percentage of pixels that may differ before a test fails (accounts for
# minor antialiasing / font-rendering differences across environments).
DEFAULT_THRESHOLD = 0.5  # 0.5 %


def _display_available():
    if sys.platform in ("win32", "darwin"):
        return True
    return bool(os.environ.get("DISPLAY"))


def capture_widget_screenshot(widget, width=800, height=600, settle_ms=200):
    """Capture a screenshot of a tkinter widget as a PIL Image.

    Forces geometry, updates all pending events, then grabs
    the widget's pixel contents via Tk's built-in photo mechanism.
    """
    root = widget.winfo_toplevel()

    # Force a specific size so baselines are reproducible
    root.geometry(f"{width}x{height}")
    root.update_idletasks()
    root.update()
    # Let the window manager settle (important under Xvfb)
    time.sleep(settle_ms / 1000)
    root.update_idletasks()
    root.update()

    # Tk doesn't expose a direct "grab this window" API usable without
    # platform-specific extensions, but we can use the x/y/width/height
    # approach with ImageGrab or subprocess scrot.  Since we run on Linux
    # CI with scrot installed, use that as a reliable fallback.
    x = root.winfo_rootx()
    y = root.winfo_rooty()
    w = root.winfo_width()
    h = root.winfo_height()

    try:
        # Try pyscreenshot / PIL.ImageGrab first
        from PIL import ImageGrab
        img = ImageGrab.grab(bbox=(x, y, x + w, y + h))
        return img
    except Exception:
        pass

    # Fallback: use scrot (installed in CI)
    import subprocess
    import tempfile
    with tempfile.NamedTemporaryFile(suffix=".png", delete=False) as tmp:
        tmp_path = tmp.name
    try:
        subprocess.run(
            ["scrot", "-a", f"{x},{y},{w},{h}", tmp_path],
            check=True, timeout=5,
        )
        return Image.open(tmp_path).copy()
    finally:
        if os.path.exists(tmp_path):
            os.unlink(tmp_path)


def assert_screenshot_matches(
    screenshot: Image.Image,
    name: str,
    threshold: float = DEFAULT_THRESHOLD,
):
    """Compare *screenshot* against the stored baseline named *name*.

    If the env var ``UPDATE_BASELINES`` is set, overwrite the baseline
    instead of comparing.  This lets developers regenerate baselines
    locally (on a Linux machine with Xvfb) whenever an intentional UI
    change is made.

    On mismatch the diff image is written to ``tests/visual/diffs/``
    and the assertion error includes the pixel difference percentage.
    """
    BASELINES_DIR.mkdir(parents=True, exist_ok=True)
    DIFFS_DIR.mkdir(parents=True, exist_ok=True)

    baseline_path = BASELINES_DIR / f"{name}.png"

    if os.environ.get("UPDATE_BASELINES"):
        screenshot.save(str(baseline_path))
        pytest.skip(f"Baseline updated: {baseline_path}")
        return

    if not baseline_path.exists():
        # First run — store the baseline automatically
        screenshot.save(str(baseline_path))
        pytest.skip(
            f"Baseline created: {baseline_path}. "
            "Re-run to compare against it."
        )
        return

    baseline = Image.open(str(baseline_path))

    # Resize screenshot to match baseline dimensions if they differ
    # (guards against minor window-manager geometry differences)
    if screenshot.size != baseline.size:
        screenshot = screenshot.resize(baseline.size, Image.LANCZOS)

    # Ensure both are RGBA for pixelmatch
    screenshot_rgba = screenshot.convert("RGBA")
    baseline_rgba = baseline.convert("RGBA")

    try:
        from pixelmatch.contrib.PIL import pixelmatch as pm
        diff_img = Image.new("RGBA", baseline_rgba.size)
        num_diff = pm(
            baseline_rgba, screenshot_rgba, diff_img,
            threshold=0.1,  # per-pixel colour sensitivity
            includeAA=True,
        )
    except ImportError:
        import numpy as np
        arr_a = np.array(screenshot_rgba)
        arr_b = np.array(baseline_rgba)
        num_diff = int((arr_a != arr_b).any(axis=-1).sum())
        diff_img = None

    total_pixels = baseline_rgba.size[0] * baseline_rgba.size[1]
    diff_pct = (num_diff / total_pixels) * 100

    if diff_pct > threshold:
        diff_path = DIFFS_DIR / f"{name}_diff.png"
        actual_path = DIFFS_DIR / f"{name}_actual.png"
        screenshot.save(str(actual_path))
        if diff_img is not None:
            diff_img.save(str(diff_path))
        pytest.fail(
            f"Visual regression: {name} differs by {diff_pct:.2f}% "
            f"({num_diff}/{total_pixels} pixels). "
            f"Diff saved to {diff_path}"
        )


@pytest.fixture
def visual_root():
    """Create and yield a real tk.Tk root, then destroy it."""
    if not _display_available():
        pytest.skip("No display available for visual tests")

    import tkinter as tk
    root = tk.Tk()
    root.withdraw()  # start hidden
    yield root
    try:
        root.destroy()
    except Exception:
        pass
