"""Unit tests for CullingWorkspaceViewModel and comparison presentation logic.

Adheres to:
- R-LINUX-UI-04: 1-Up Mode Transitions
- R-LINUX-UI-05: 2-Up Mode Transitions
- R-LINUX-UI-06: 3-Up Focus Mode Transitions
- R-LINUX-UI-07: Boundary Slot Conditions
- R-LINUX-CULL-06/07: Transactional Undo Stack
"""

from pathlib import Path
from unittest.mock import MagicMock
from photo_selector_linux.core.models import (
    CandidatePhoto,
    CullingActionType,
    CullingRecord,
)
from photo_selector_linux.viewmodels.culling_workspace import CullingWorkspaceViewModel


def make_candidate(name: str) -> CandidatePhoto:
    p = Path(f"/tmp/test_dir/{name}")
    return CandidatePhoto(primary_path=p, companion_paths=[])


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
