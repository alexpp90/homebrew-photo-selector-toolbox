"""Unit tests for pure-Python three_up_geometry (one-over-two, UD-4) and 1:1 zoom in 3-Up.

Adheres to:
- R-LINUX-UI-06: 3-Up Focus Mode (One-over-Two Layout)
- R-LINUX-UI-08: 1:1 Actual-Pixel Zoom Inspection in 1-Up, 2-Up, and 3-Up
"""

from pathlib import Path
from unittest.mock import MagicMock
import pytest

from photo_selector_linux.core.models import CandidatePhoto
from photo_selector_linux.ui.canvas import CanvasView
from photo_selector_linux.ui.layout import (
    Rect,
    can_fit_1to1_development,
    three_up_geometry,
)
from photo_selector_linux.ui.photo_loader import PhotoTextureLoader
from photo_selector_linux.viewmodels.culling_workspace import CullingWorkspaceViewModel


def _rects_overlap(a: Rect, b: Rect) -> bool:
    return not (
        a.right <= b.x
        or b.right <= a.x
        or a.bottom <= b.y
        or b.bottom <= a.y
    )


def test_layout_module_has_no_gtk_dependency():
    """Verify ui.layout is pure Python and does not import gi.repository at module level."""
    import inspect
    from photo_selector_linux.ui import layout

    src = inspect.getsource(layout)
    assert "gi.repository" not in src


@pytest.mark.parametrize(
    "win_w, win_h",
    [
        (1920, 1080),  # 16:9 window
        (1920, 1200),  # 16:10 window
        (1800, 1200),  # 3:2 window
        (320, 240),    # minimum window
    ],
)
@pytest.mark.parametrize(
    "aspect",
    [
        3.0 / 2.0,  # 3:2 landscape
        4.0 / 3.0,  # 4:3 landscape
        2.0 / 3.0,  # 2:3 portrait
    ],
)
def test_three_up_geometry_across_windows_and_photo_aspects(
    win_w: int,
    win_h: int,
    aspect: float,
):
    """Verify three_up_geometry places current photo centered on top and neighbours below without overlap (UD-4)."""
    top, bottom_left, bottom_right = three_up_geometry(
        width=win_w,
        height=win_h,
        aspect=aspect,
        top_ratio=0.60,
        gap=12,
    )

    for r in (top, bottom_left, bottom_right):
        assert r.width >= 1 and r.height >= 1
        assert 0 <= r.x and r.right <= win_w
        assert 0 <= r.y and r.bottom <= win_h

    # Top rectangle is above both bottom rectangles with no overlap
    assert top.bottom <= bottom_left.y
    assert top.bottom <= bottom_right.y
    assert bottom_left.right <= bottom_right.x
    assert not _rects_overlap(top, bottom_left)
    assert not _rects_overlap(top, bottom_right)
    assert not _rects_overlap(bottom_left, bottom_right)

    # Top rectangle is horizontally centered within the window
    left_margin = top.x
    right_margin = win_w - top.right
    assert abs(left_margin - right_margin) <= 1


def test_canvas_3up_renders_one_over_two_positions_and_1to1_zoom():
    """Verify CanvasView in 3-Up mode assigns bottom-left/top/bottom-right positions and bounds 1:1 RAW developments."""
    vm = CullingWorkspaceViewModel()
    vm.candidates = [
        CandidatePhoto(primary_path=Path("/tmp/DSC_0001.ARW")),
        CandidatePhoto(primary_path=Path("/tmp/DSC_0002.ARW")),
        CandidatePhoto(primary_path=Path("/tmp/DSC_0003.ARW")),
    ]
    vm.current_index = 1
    vm.set_comparison_mode(3)

    loader = PhotoTextureLoader(max_mb=512.0)
    loader.request = MagicMock()
    loader.evict_full_resolution = MagicMock(return_value=0)
    canvas = CanvasView(viewmodel=vm, loader=loader)
    canvas._render_view()

    assert canvas.last_three_up_geometry is not None
    top_rect, bl_rect, br_rect = canvas.last_three_up_geometry
    assert [s["role"] for s in canvas.rendered_slots] == ["PREVIOUS", "ACTIVE", "NEXT"]
    assert [s["position"] for s in canvas.rendered_slots] == ["bottom-left", "top", "bottom-right"]
    assert canvas.rendered_slots[0]["rect"] == bl_rect
    assert canvas.rendered_slots[1]["rect"] == top_rect
    assert canvas.rendered_slots[2]["rect"] == br_rect

    # Toggle 1:1 zoom in 3-Up mode: only the active (top) pane develops 1:1 initially
    loader.request.reset_mock()
    canvas.toggle_zoom()
    assert canvas.is_1to1_zoom is True
    assert [s["zoom_1to1"] for s in canvas.rendered_slots] == [False, True, False]

    # Requesting 1:1 on one neighbour (-1) fits within the 2-pane 1:1 budget
    assert canvas.request_neighbour_1to1(-1) is True
    assert [s["zoom_1to1"] for s in canvas.rendered_slots] == [True, True, False]

    # Requesting 1:1 on the second neighbour (+1) replaces the first so at most 2 full 1:1 panes are active
    assert canvas.request_neighbour_1to1(1) is True
    assert [s["zoom_1to1"] for s in canvas.rendered_slots] == [False, True, True]

    # Linked pan updates normalized pan coordinates across panes
    canvas.set_linked_pan(0.25, 0.75)
    assert canvas.pan_offset == (0.25, 0.75)
    canvas.set_linked_pan(-5.0, 9.0)
    assert canvas.pan_offset == (0.0, 1.0)


def test_1to1_development_memory_budget_helper():
    """Verify can_fit_1to1_development prevents holding three full 24 MP RGBA textures against a 512 MB cache."""
    cache_512mb = 512 * 1024 * 1024
    assert can_fit_1to1_development(6000, 4000, cache_512mb, active_1to1_count=0) is True
    assert can_fit_1to1_development(6000, 4000, cache_512mb, active_1to1_count=1) is True
    assert can_fit_1to1_development(6000, 4000, cache_512mb, active_1to1_count=2) is False
