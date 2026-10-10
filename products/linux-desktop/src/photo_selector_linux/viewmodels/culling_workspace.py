"""Pure-Python decoupled ViewModel for culling workspace state machine.

Adheres to:
- R-LINUX-UI-04/05/06: 1-Up, 2-Up, and 3-Up Focus Mode transitions
- R-LINUX-CULL-03/04/05/06/07: Transactional Move, Copy, Trash, and Undo operations
"""

from __future__ import annotations

from pathlib import Path
from typing import Callable, List, Optional, Tuple

from photo_selector_linux.core.file_ops import FileCullingManager
from photo_selector_linux.core.models import (
    CandidatePhoto,
    CullingActionType,
    CullingRecord,
)
from photo_selector_linux.core.scanner import DirectoryScanner


class CullingWorkspaceViewModel:
    """Decoupled presentation state machine driving navigation, comparison, and culling."""

    def __init__(self, file_manager: Optional[FileCullingManager] = None):
        self.candidates: List[CandidatePhoto] = []
        self.current_index: int = 0
        self.comparison_mode: int = 1  # 1: 1-Up, 2: 2-Up, 3: 3-Up Focus Mode
        self.current_folder: Optional[Path] = None
        self.selection_folder_name: str = "Selection"

        self.selected_count: int = 0
        self.copied_count: int = 0
        self.trashed_count: int = 0
        self.undo_stack: List[Tuple[CullingRecord, CandidatePhoto, int]] = []

        self.file_manager = file_manager or FileCullingManager()

        self._state_callbacks: List[Callable[[CullingWorkspaceViewModel], None]] = []
        self._notification_callbacks: List[Callable[[CullingWorkspaceViewModel, str], None]] = []

    @property
    def candidate_count(self) -> int:
        return len(self.candidates)

    @property
    def remaining_count(self) -> int:
        return len(self.candidates)

    @property
    def current_photo(self) -> Optional[CandidatePhoto]:
        if 0 <= self.current_index < len(self.candidates):
            return self.candidates[self.current_index]
        return None

    def connect(self, signal: str, callback: Callable) -> None:
        """Register callbacks for state changes or notifications."""
        if signal == "state-changed":
            self._state_callbacks.append(callback)
        elif signal == "notification":
            self._notification_callbacks.append(callback)

    def _notify_state_changed(self) -> None:
        for cb in self._state_callbacks:
            try:
                cb(self)
            except Exception:
                pass

    def _notify(self, message: str) -> None:
        for cb in self._notification_callbacks:
            try:
                cb(self, message)
            except Exception:
                pass

    def set_comparison_mode(self, mode: int) -> None:
        """Switch comparison mode (1: 1-Up, 2: 2-Up, 3: 3-Up Focus Mode)."""
        if mode in (1, 2, 3) and mode != self.comparison_mode:
            self.comparison_mode = mode
            self._notify_state_changed()

    def nav_next(self) -> None:
        """Navigate to the next candidate photo."""
        if self.current_index < len(self.candidates) - 1:
            self.current_index += 1
            self._notify_state_changed()

    def nav_previous(self) -> None:
        """Navigate to the previous candidate photo."""
        if self.current_index > 0:
            self.current_index -= 1
            self._notify_state_changed()

    def cull_move(self) -> None:
        """Move active candidate and all companions to Selection/ with auto-advance."""
        photo = self.current_photo
        if not photo or not self.current_folder:
            return

        idx = self.current_index
        record = self.file_manager.move_candidate(
            photo,
            self.current_folder,
            selection_folder=self.selection_folder_name,
        )
        self.undo_stack.append((record, photo, idx))
        self.selected_count += 1

        self.candidates.pop(idx)
        if self.current_index >= len(self.candidates) and self.current_index > 0:
            self.current_index -= 1

        self._notify(f"Moved {photo.primary_path.name} to {self.selection_folder_name}/")
        self._notify_state_changed()

    def cull_copy(self) -> None:
        """Copy active candidate and all companions to Selection/ with auto-advance."""
        photo = self.current_photo
        if not photo or not self.current_folder:
            return

        idx = self.current_index
        record = self.file_manager.copy_candidate(
            photo,
            self.current_folder,
            selection_folder=self.selection_folder_name,
        )
        self.undo_stack.append((record, photo, idx))
        self.copied_count += 1
        self.selected_count += 1

        self.nav_next()
        self._notify(f"Copied {photo.primary_path.name} to {self.selection_folder_name}/")
        self._notify_state_changed()

    def cull_trash(self) -> None:
        """Send active candidate and all companions to Trash with auto-advance."""
        photo = self.current_photo
        if not photo:
            return

        idx = self.current_index
        record = self.file_manager.trash_candidate(photo)
        self.undo_stack.append((record, photo, idx))
        self.trashed_count += 1

        self.candidates.pop(idx)
        if self.current_index >= len(self.candidates) and self.current_index > 0:
            self.current_index -= 1

        self._notify(f"Moved {photo.primary_path.name} to Trash")
        self._notify_state_changed()

    def undo(self) -> bool:
        """Atomically reverse the last culling operation (R-LINUX-CULL-06/07)."""
        if not self.undo_stack:
            return False

        record, photo, orig_idx = self.undo_stack.pop()
        success = self.file_manager.undo_culling(record, photo)
        if not success:
            return False

        if record.action_type == CullingActionType.MOVE:
            insert_pos = min(orig_idx, len(self.candidates))
            self.candidates.insert(insert_pos, photo)
            self.current_index = insert_pos
            self.selected_count = max(0, self.selected_count - 1)
        elif record.action_type == CullingActionType.COPY:
            self.selected_count = max(0, self.selected_count - 1)
            self.copied_count = max(0, self.copied_count - 1)
        elif record.action_type == CullingActionType.TRASH:
            insert_pos = min(orig_idx, len(self.candidates))
            self.candidates.insert(insert_pos, photo)
            self.current_index = insert_pos
            self.trashed_count = max(0, self.trashed_count - 1)

        self._notify(f"Undid {record.action_type.value} for {photo.primary_path.name}")
        self._notify_state_changed()
        return True

    def load_directory(self, folder: Path) -> None:
        """Load and sort photographs from folder."""
        self.current_folder = folder.resolve()
        scanner = DirectoryScanner(custom_selection_folder=self.selection_folder_name)
        self.candidates = scanner.scan_directory(self.current_folder)
        self.current_index = 0
        self.undo_stack.clear()
        self.selected_count = 0
        self.copied_count = 0
        self.trashed_count = 0
        self._notify(f"Loaded {len(self.candidates)} photographs from {self.current_folder.name}")
        self._notify_state_changed()
