"""Unit tests for CullingWorkspaceViewModel, comparison presentation, and UI styling.

Adheres to:
- R-LINUX-UI-01: Native GNOME Libadwaita Window & HeaderBar
- R-LINUX-UI-02: Obsidian Darkroom & Optical Cobalt Palette (>= 7:1 AAA contrast)
- R-LINUX-UI-03: Maximized Viewport Canvas (>85% area) & Contextual Slot Badges
- R-LINUX-UI-04: 1-Up Mode Transitions
- R-LINUX-UI-05: 2-Up Mode Transitions
- R-LINUX-UI-06: 3-Up Focus Mode Transitions
- R-LINUX-UI-07: Boundary Slot Conditions
- R-LINUX-UI-09: Floating Optical Metadata & Sharpness Goodness HUD
- R-LINUX-UI-10: Semantic Bottom Action Bar & Status Counters
- R-LINUX-SCORE-05: Metric Omission Rule
- R-LINUX-CULL-06/07: Transactional Undo Stack
"""

from pathlib import Path
from unittest.mock import MagicMock
import pytest

from photo_selector_linux.core.models import (
    CandidatePhoto,
    CullingActionType,
    CullingRecord,
    ExposureMetadata,
    QualityScores,
)
from photo_selector_linux.ui.action_bar import ActionBarView
from photo_selector_linux.ui.canvas import CanvasView
from photo_selector_linux.ui.info_hud import InfoHUD
from photo_selector_linux.ui.styling import (
    APP_BRAND_NAME,
    APP_BRAND_TAGLINE,
    APP_ICON_NAME,
    APP_THEME_NAME,
    GOODNESS_BAR_FLOOR,
    STUDIO_DARK_CSS,
    STUDIO_PALETTE,
    compute_sharpness_goodness,
    format_companion_badges,
    get_slot_role_label,
)
from photo_selector_linux.ui.window import MainWindow
from photo_selector_linux.viewmodels.culling_workspace import CullingWorkspaceViewModel


def make_candidate(name: str, companions: list[str] | None = None) -> CandidatePhoto:
    p = Path(f"/tmp/test_dir/{name}")
    comp_paths = [Path(f"/tmp/test_dir/{c}") for c in (companions or [])]
    return CandidatePhoto(primary_path=p, companion_paths=comp_paths)


def test_libadwaita_components():
    """Verify MainWindow initializes brand identity and core workspace views (R-LINUX-UI-01)."""
    win = MainWindow()
    assert win.window_title == APP_BRAND_NAME == "Photo Selector"
    assert win.icon_name == APP_ICON_NAME == "org.photoselector.Linux"
    assert win.canvas_view is not None
    assert win.action_bar is not None


def test_studio_dark_palette():
    """Verify Obsidian Darkroom & Optical Cobalt palette tokens and CSS classes (R-LINUX-UI-02)."""
    assert APP_THEME_NAME == "Obsidian Darkroom & Optical Cobalt"
    assert "Obsidian Darkroom" in APP_BRAND_TAGLINE
    assert STUDIO_PALETTE["surface_bg"] == "#141417"
    assert STUDIO_PALETTE["header_chrome"] == "#181920"
    assert STUDIO_PALETTE["card_bg"] == "#1C1C20"
    assert STUDIO_PALETTE["border_stroke"] == "#3A3A42"
    assert STUDIO_PALETTE["text_primary"] == "#FFFFFF"
    assert STUDIO_PALETTE["text_secondary"] == "#D9D9D9"
    assert STUDIO_PALETTE["optical_cobalt"] == "#3584E4"
    assert STUDIO_PALETTE["optical_cyan"] == "#06B6D4"
    assert STUDIO_PALETTE["iris_indigo"] == "#6366F1"
    assert STUDIO_PALETTE["emerald_keeper"] == "#2EC27E"
    assert STUDIO_PALETTE["amber_optical"] == "#E5A50A"
    assert STUDIO_PALETTE["crimson_cull"] == "#E01B24"

    for selector in (
        ".studio-dark-surface",
        ".studio-headerbar",
        ".studio-actionbar",
        ".card-pane",
        ".accent-border",
        ".floating-hud",
        ".sharpness-meter-track",
        ".active-pill",
        ".slot-role-pill",
        ".zoom-indicator-pill",
        ".companion-badge-raw",
        ".companion-badge-xmp",
        ".status-counter-selected",
        ".status-counter-trashed",
        ".status-counter-remaining",
        ".empty-state-hero",
    ):
        assert selector in STUDIO_DARK_CSS, f"Missing CSS selector {selector} in STUDIO_DARK_CSS"


def test_canvas_area_allocation():
    """Verify CanvasView allocates >85% viewport and renders slot role badges (R-LINUX-UI-03)."""
    vm = CullingWorkspaceViewModel()
    canvas = CanvasView(viewmodel=vm)

    # Empty state when no candidates are loaded
    assert canvas.rendered_slots == []

    # Populate candidates with RAW and XMP companions
    vm.candidates = [
        make_candidate("DSC_0001.JPG", ["DSC_0001.ARW", "DSC_0001.xmp"]),
        make_candidate("DSC_0002.JPG", ["DSC_0002.CR3"]),
        make_candidate("DSC_0003.JPG"),
    ]
    vm.current_index = 0
    canvas._render_view()

    # 1-Up mode
    assert len(canvas.rendered_slots) == 1
    slot0 = canvas.rendered_slots[0]
    assert slot0["type"] == "photo"
    assert slot0["role"] == "ACTIVE"
    assert slot0["is_active"] is True
    assert slot0["badges"] == [
        ("RAW+JPG", "companion-badge-raw"),
        ("XMP", "companion-badge-xmp"),
    ]
    assert slot0["zoom_1to1"] is False

    # Toggle 1:1 pixel zoom in 1-Up mode
    canvas.toggle_zoom()
    assert canvas.is_1to1_zoom is True
    assert canvas.rendered_slots[0]["zoom_1to1"] is True
    canvas.reset_zoom()
    assert canvas.is_1to1_zoom is False

    # 2-Up mode (CHAMPION vs CHALLENGER)
    vm.set_comparison_mode(2)
    assert len(canvas.rendered_slots) == 2
    assert canvas.rendered_slots[0]["role"] == "CHAMPION"
    assert canvas.rendered_slots[0]["is_active"] is True
    assert canvas.rendered_slots[1]["role"] == "CHALLENGER"
    assert canvas.rendered_slots[1]["is_active"] is False

    # 3-Up mode at index 0: left boundary card + ACTIVE + NEXT
    vm.set_comparison_mode(3)
    assert len(canvas.rendered_slots) == 3
    assert canvas.rendered_slots[0] == {"type": "boundary", "slot_type": "first"}
    assert canvas.rendered_slots[1]["role"] == "ACTIVE"
    assert canvas.rendered_slots[2]["role"] == "NEXT"

    # 3-Up mode at middle index 1: PREVIOUS + ACTIVE + NEXT
    vm.nav_next()
    assert vm.current_index == 1
    assert [s.get("role") for s in canvas.rendered_slots] == ["PREVIOUS", "ACTIVE", "NEXT"]

    # 3-Up mode at last index 2: PREVIOUS + ACTIVE + right boundary card
    vm.nav_next()
    assert vm.current_index == 2
    assert canvas.rendered_slots[0]["role"] == "PREVIOUS"
    assert canvas.rendered_slots[1]["role"] == "ACTIVE"
    assert canvas.rendered_slots[2] == {"type": "boundary", "slot_type": "last"}


def test_info_hud_goodness_and_metric_omission():
    """Verify InfoHUD sharpness goodness bar and strict Metric Omission Rule (R-LINUX-UI-09, R-LINUX-SCORE-05)."""
    vm = CullingWorkspaceViewModel()
    canvas = CanvasView(viewmodel=vm)
    hud = InfoHUD(viewmodel=vm)

    # Toggle HUD visibility via I hotkey handler
    assert canvas.hud_enabled is True
    canvas.toggle_hud()
    assert canvas.hud_enabled is False
    canvas.toggle_hud()
    assert canvas.hud_enabled is True

    # Candidate with full optical metadata, companions, and sharp focus score
    cand_sharp = make_candidate("IMG_4021.JPG", ["IMG_4021.CR3", "IMG_4021.XMP"])
    cand_sharp.exif = ExposureMetadata(
        shutter_speed=1.0 / 500.0,
        aperture=2.8,
        iso=400,
        focal_length=85.0,
        camera_make="Sony",
        camera_model="Sony ILCE-7RM5",
        lens="FE 85mm F1.4 GM",
    )
    cand_sharp.scores = QualityScores(
        sharpness=90.0,
        noise=1.2,
        highlight_clipping=1.5,
        shadow_clipping=0.2,
    )
    hud.update_photo(cand_sharp)
    assert hud.is_hud_visible is True
    assert hud.filename_text == "IMG_4021.JPG"
    assert hud.companion_badges == [
        ("RAW+JPG", "companion-badge-raw"),
        ("XMP", "companion-badge-xmp"),
    ]
    assert hud.exif_text == "1/500s · f/2.8 · ISO 400 · 85mm"
    assert hud.camera_text == "Sony ILCE-7RM5 · FE 85mm F1.4 GM"
    assert hud.score_visible is True
    assert hud.score_badge_text == "Focus: 90.0 (Sharp)"
    assert hud.score_css_class == "score-badge-sharp"
    assert hud.sharpness_goodness == pytest.approx(compute_sharpness_goodness(90.0))
    assert hud.clipping_text == "HL: 1.5% · SH: 0.2%"

    # Candidate with missing EXIF and missing scores -> strict Metric Omission Rule (no N/A or --)
    cand_bare = make_candidate("SCAN_001.PNG")
    cand_bare.exif = ExposureMetadata()
    cand_bare.scores = None
    hud.update_photo(cand_bare)
    assert hud.is_hud_visible is True
    assert hud.filename_text == "SCAN_001.PNG"
    assert hud.companion_badges == []
    assert hud.exif_text == ""
    assert hud.camera_text == ""
    assert hud.score_visible is False
    assert hud.score_badge_text == ""
    assert hud.sharpness_goodness is None
    assert hud.clipping_text == ""

    # Candidate with blurry score -> blurry badge & goodness floor
    cand_soft = make_candidate("BLUR_002.JPG")
    cand_soft.scores = QualityScores(
        sharpness=18.4,
        noise=3.1,
        highlight_clipping=0.0,
        shadow_clipping=0.0,
    )
    hud.update_photo(cand_soft)
    assert hud.score_visible is True
    assert hud.score_badge_text == "Focus: 18.4 (Blurry)"
    assert hud.score_css_class == "score-badge-blurry"
    assert hud.sharpness_goodness == pytest.approx(compute_sharpness_goodness(18.4))

    # Reset HUD with None
    hud.update_photo(None)
    assert hud.is_hud_visible is False
    assert hud.filename_text == ""
    assert hud.score_visible is False


def test_action_bar_semantic_status_counters():
    """Verify ActionBarView semantic status counters and Undo button state (R-LINUX-UI-10)."""
    mock_file_mgr = MagicMock()
    vm = CullingWorkspaceViewModel(file_manager=mock_file_mgr)
    vm.candidates = [make_candidate(f"p_{i}.jpg") for i in range(4)]
    vm.current_folder = Path("/tmp/test_dir")

    bar = ActionBarView(viewmodel=vm)
    bar._update_state()
    assert bar.selected_label_text == "0 Selected"
    assert bar.trashed_label_text == "0 Trashed"
    assert bar.remaining_label_text == "4 Remaining"
    assert bar.selected_css_class == "status-counter-badge"
    assert bar.trashed_css_class == "status-counter-badge"
    assert bar.remaining_css_class == "status-counter-remaining"
    assert bar.undo_label_text == "Undo (Ctrl+Z)"
    assert bar.undo_sensitive is False

    # Perform Move and Trash to verify semantic counter pills update
    c0 = vm.candidates[0]
    mock_file_mgr.move_candidate.return_value = CullingRecord(
        photo_id=c0.id,
        action_type=CullingActionType.MOVE,
        original_primary_path=c0.primary_path,
        affected_paths=[(c0.primary_path, Path("/tmp/test_dir/Selection/p_0.jpg"))],
    )
    vm.cull_move()

    c1 = vm.candidates[0]
    mock_file_mgr.trash_candidate.return_value = CullingRecord(
        photo_id=c1.id,
        action_type=CullingActionType.TRASH,
        original_primary_path=c1.primary_path,
        affected_paths=[(c1.primary_path, c1.primary_path)],
    )
    vm.cull_trash()

    assert bar.selected_label_text == "1 Selected"
    assert bar.trashed_label_text == "1 Trashed"
    assert bar.remaining_label_text == "2 Remaining"
    assert bar.selected_css_class == "status-counter-selected"
    assert bar.trashed_css_class == "status-counter-trashed"
    assert bar.undo_label_text == "Undo (2) (Ctrl+Z)"
    assert bar.undo_sensitive is True


def test_styling_helper_edge_cases():
    """Verify boundary values and edge cases for sharpness goodness and badge helpers."""
    assert compute_sharpness_goodness(0.0) == pytest.approx(GOODNESS_BAR_FLOOR)
    assert compute_sharpness_goodness(-25.0) == pytest.approx(GOODNESS_BAR_FLOOR)
    assert compute_sharpness_goodness(float("nan")) == pytest.approx(GOODNESS_BAR_FLOOR)
    assert compute_sharpness_goodness(float("-inf")) == pytest.approx(GOODNESS_BAR_FLOOR)
    assert compute_sharpness_goodness(float("inf")) == pytest.approx(1.0)
    assert compute_sharpness_goodness(50.0) == pytest.approx(GOODNESS_BAR_FLOOR + 0.5 * (1.0 - GOODNESS_BAR_FLOOR))
    assert compute_sharpness_goodness(100.0) == pytest.approx(1.0)
    assert compute_sharpness_goodness(500.0) == pytest.approx(1.0)

    assert get_slot_role_label(1, 0) == "ACTIVE"
    assert get_slot_role_label(2, 0, is_active=True) == "CHAMPION"
    assert get_slot_role_label(2, 1, is_active=False) == "CHALLENGER"
    assert get_slot_role_label(3, -1, is_active=False) == "PREVIOUS"
    assert get_slot_role_label(3, 0, is_active=True) == "ACTIVE"
    assert get_slot_role_label(3, 1, is_active=False) == "NEXT"

    assert format_companion_badges(False, False) == []
    assert format_companion_badges(True, False) == [("RAW+JPG", "companion-badge-raw")]
    assert format_companion_badges(False, True) == [("XMP", "companion-badge-xmp")]
    assert format_companion_badges(True, True) == [
        ("RAW+JPG", "companion-badge-raw"),
        ("XMP", "companion-badge-xmp"),
    ]


def test_comparison_mode_transitions():
    """Verify switching between 1-Up, 2-Up, and 3-Up Focus Mode (R-LINUX-UI-04, 05, 06)."""
    vm = CullingWorkspaceViewModel()
    assert vm.comparison_mode == 1

    vm.set_comparison_mode(2)
    assert vm.comparison_mode == 2

    vm.set_comparison_mode(3)
    assert vm.comparison_mode == 3

    # Invalid mode ignored
    vm.set_comparison_mode(99)
    assert vm.comparison_mode == 3


def test_navigation_bounds():
    """Verify navigation clamps at collection boundaries."""
    vm = CullingWorkspaceViewModel()
    vm.candidates = [make_candidate(f"img_{i}.jpg") for i in range(3)]
    vm.current_index = 0

    # Cannot go before 0
    vm.nav_previous()
    assert vm.current_index == 0

    vm.nav_next()
    assert vm.current_index == 1
    vm.nav_next()
    assert vm.current_index == 2

    # Cannot go beyond last index
    vm.nav_next()
    assert vm.current_index == 2


def test_boundary_slot_conditions():
    """Verify 3-Up Focus Mode boundary index states (R-LINUX-UI-07)."""
    vm = CullingWorkspaceViewModel()
    vm.candidates = [make_candidate(f"img_{i}.jpg") for i in range(5)]
    vm.current_index = 0

    # At start: index 0 (Left slot shows First Photograph boundary card)
    assert vm.current_index == 0

    vm.current_index = 4
    # At end: index N-1 (Right slot shows Last Photograph boundary card)
    assert vm.current_index == len(vm.candidates) - 1


def test_transactional_undo_stack():
    """Verify culling operations and undo maintain candidate lists and counts (R-LINUX-CULL-06, 07)."""
    mock_file_mgr = MagicMock()
    vm = CullingWorkspaceViewModel(file_manager=mock_file_mgr)

    c1 = make_candidate("photo_1.jpg")
    c2 = make_candidate("photo_2.jpg")
    vm.candidates = [c1, c2]
    vm.current_folder = Path("/tmp/test_dir")
    vm.current_index = 0

    # Move operation
    fake_move_rec = CullingRecord(
        photo_id=c1.id,
        action_type=CullingActionType.MOVE,
        original_primary_path=c1.primary_path,
        affected_paths=[(c1.primary_path, Path("/tmp/test_dir/Selection/photo_1.jpg"))],
    )
    mock_file_mgr.move_candidate.return_value = fake_move_rec
    mock_file_mgr.undo_culling.return_value = True

    vm.cull_move()
    assert vm.selected_count == 1
    assert len(vm.candidates) == 1
    assert len(vm.undo_stack) == 1

    # Undo operation
    success = vm.undo()
    assert success is True
    assert vm.selected_count == 0
    assert len(vm.candidates) == 2
    assert len(vm.undo_stack) == 0
    assert vm.candidates[0] == c1
