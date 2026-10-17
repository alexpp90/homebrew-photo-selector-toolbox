"""Pure-Python geometry solver for the 3-Up one-over-two workspace and 1:1 memory budget.

Adheres to:
- R-LINUX-UI-06: 3-Up Focus Mode (One-Over-Two layout: active on top, previous bottom-left, next bottom-right)
- R-LINUX-UI-08: 1:1 Actual-Pixel Zoom Inspection (memory-bounded 3-Up 1:1 development)
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Tuple

BYTES_PER_RGBA_PIXEL: int = 4
DEFAULT_RAW_1TO1_PIXELS: int = 6000 * 4000
MAX_CONCURRENT_1TO1_PANES: int = 2


@dataclass(frozen=True)
class Rect:
    """Axis-aligned pixel rectangle in canvas coordinates."""

    x: int
    y: int
    width: int
    height: int

    @property
    def right(self) -> int:
        return self.x + self.width

    @property
    def bottom(self) -> int:
        return self.y + self.height

    @property
    def center_x(self) -> float:
        return self.x + self.width / 2.0

    @property
    def center_y(self) -> float:
        return self.y + self.height / 2.0

    def intersects(self, other: Rect) -> bool:
        """True when ``self`` and ``other`` share positive area."""
        return not (
            self.right <= other.x
            or other.right <= self.x
            or self.bottom <= other.y
            or other.bottom <= self.y
        )


def fit_aspect_rect(bounds: Rect, aspect: float) -> Rect:
    """Fit a rectangle of ``aspect`` (width / height) centered inside ``bounds``."""
    bw = max(1, int(bounds.width))
    bh = max(1, int(bounds.height))
    if aspect <= 0:
        return Rect(bounds.x, bounds.y, bw, bh)

    bounds_aspect = bw / float(bh)
    if bounds_aspect >= aspect:
        fitted_h = bh
        fitted_w = max(1, min(bw, int(round(bh * aspect))))
    else:
        fitted_w = bw
        fitted_h = max(1, min(bh, int(round(bw / aspect))))

    offset_x = (bw - fitted_w) // 2
    offset_y = (bh - fitted_h) // 2
    return Rect(bounds.x + offset_x, bounds.y + offset_y, fitted_w, fitted_h)


def three_up_geometry(
    width: int,
    height: int,
    aspect: float = 1.5,
    top_ratio: float = 0.60,
    gap: int = 12,
) -> Tuple[Rect, Rect, Rect]:
    """Compute ``(top_rect, bottom_left_rect, bottom_right_rect)`` for the 3-Up one-over-two view.

    The active photo sits centered in the top band (default 60% of available height); the previous
    and next photos sit in the bottom-left and bottom-right cells without overlapping.
    """
    w = max(2, int(width))
    h = max(2, int(height))
    ratio = max(0.25, min(0.80, float(top_ratio)))
    eff_gap = max(0, min(int(gap), max(0, min(w, h) // 4)))

    avail_h = max(2, h - eff_gap)
    top_band_h = max(1, min(avail_h - 1, int(round(avail_h * ratio))))
    bottom_y = top_band_h + eff_gap
    bottom_band_h = max(1, h - bottom_y)

    avail_w = max(2, w - eff_gap)
    left_cell_w = max(1, avail_w // 2)
    right_cell_x = left_cell_w + eff_gap
    right_cell_w = max(1, w - right_cell_x)

    top_bounds = Rect(0, 0, w, top_band_h)
    bl_bounds = Rect(0, bottom_y, left_cell_w, bottom_band_h)
    br_bounds = Rect(right_cell_x, bottom_y, right_cell_w, bottom_band_h)

    top_rect = fit_aspect_rect(top_bounds, aspect)
    bl_rect = fit_aspect_rect(bl_bounds, aspect)
    br_rect = fit_aspect_rect(br_bounds, aspect)
    return top_rect, bl_rect, br_rect


def can_fit_1to1_development(
    width: int = 6000,
    height: int = 4000,
    cache_max_bytes: int = 512 * 1024 * 1024,
    active_1to1_count: int = 1,
) -> bool:
    """True when another full-resolution 1:1 RGBA texture fits within the 3-Up memory budget (U-07b).

    A 24 MP RAW development occupies ~96 MB as an RGBA texture. Against the 512 MB default cache
    ceiling, at most two panes (the active top pane plus one on-demand neighbour) may hold full
    1:1 textures at once — never all three panes simultaneously.
    """
    if active_1to1_count >= MAX_CONCURRENT_1TO1_PANES:
        return False
    pane_bytes = max(1, int(width) * int(height) * BYTES_PER_RGBA_PIXEL)
    projected_bytes = (max(0, int(active_1to1_count)) + 1) * pane_bytes
    return projected_bytes <= int(cache_max_bytes * 0.5)
