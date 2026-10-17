"""Pure-Python decoupled ViewModel for culling workspace state machine.

Adheres to:
- R-LINUX-UI-04/05/06: 1-Up, 2-Up, and 3-Up Focus Mode transitions
- R-LINUX-CULL-03/04/05/06/07: Transactional Move, Copy, Trash, and Undo operations
"""

from __future__ import annotations

from pathlib import Path
from typing import Any, Callable, List, Optional, Tuple

from photo_selector_linux.core.file_ops import FileCullingManager
from photo_selector_linux.core.models import (
    CandidatePhoto,
    CullingActionType,
    CullingRecord,
)
from photo_selector_linux.core.scanner import DirectoryScanner


# A file operation, and what to do with its (result, error) once it finished.
Job = Callable[[], Any]
JobDone = Callable[[Any, Optional[BaseException]], None]
Dispatcher = Callable[[Job, JobDone], None]


def run_inline(job: Job, done: JobDone) -> None:
    """Dispatcher running the job on the calling thread (tests, headless use)."""
    try:
        result = job()
    except Exception as e:
        done(None, e)
        return
    done(result, None)


class CullingWorkspaceViewModel:
    """Decoupled presentation state machine driving navigation, comparison, and culling.

    File operations go through ``dispatcher``. The app passes one that runs them on a background
    worker and calls back on the main loop, so a Google Drive upload (16 s for a 25 MB RAW) does
    not freeze the window; one operation runs at a time, so the undo stack keeps its order.
    """

    def __init__(
        self,
        file_manager: Optional[FileCullingManager] = None,
        dispatcher: Optional[Dispatcher] = None,
    ):
        self.candidates: List[CandidatePhoto] = []
        self.current_index: int = 0
        self.comparison_mode: int = 1  # 1: 1-Up, 2: 2-Up, 3: 3-Up Focus Mode
        self.current_folder: Optional[Path] = None
        self.current_folder_name: str = ""
        self.selection_folder_name: str = "Selection"

        self.selected_count: int = 0
        self.copied_count: int = 0
        self.trashed_count: int = 0
        self.undo_stack: List[Tuple[CullingRecord, CandidatePhoto, int]] = []

        self.file_manager = file_manager or FileCullingManager()
        self.dispatcher: Dispatcher = dispatcher or run_inline
        # What the running file operation is doing ("Copying DSC0001.ARW…"), or None when idle.
        self.busy_message: Optional[str] = None
        self._folder_generation = 0

        self._state_callbacks: List[Callable[[CullingWorkspaceViewModel], None]] = []
        self._notification_callbacks: List[Callable[[CullingWorkspaceViewModel, str], None]] = []

    @property
    def candidate_count(self) -> int:
        return len(self.candidates)

    @property
    def remaining_count(self) -> int:
        return len(self.candidates)

    @property
    def is_busy(self) -> bool:
        return self.busy_message is not None

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

    def _run(
        self,
        busy_message: str,
        job: Job,
        on_success: Callable[[Any], None],
        failure: str,
        on_failure: Optional[Callable[[], None]] = None,
        on_error: Optional[Callable[[BaseException], None]] = None,
        failure_message: Optional[Callable[[BaseException], Optional[str]]] = None,
    ) -> bool:
        """Run a file operation through the dispatcher; False when one is already running.

        The outcome is applied when the job finishes, to the photo itself rather than to an
        index, because the user may browse meanwhile. A folder change drops the outcome.
        """
        if self.is_busy:
            return False
        self.busy_message = busy_message
        generation = self._folder_generation
        finished = []

        def done(result: Any, error: Optional[BaseException]) -> None:
            finished.append(True)
            self.busy_message = None
            if generation != self._folder_generation:
                pass
            elif error is not None:
                if on_error is not None:
                    on_error(error)
                if on_failure is not None:
                    on_failure()
                custom = failure_message(error) if failure_message is not None else None
                self._notify(custom if custom is not None else f"{failure}: {error}")
            else:
                on_success(result)
            self._notify_state_changed()

        self.dispatcher(job, done)
        if not finished:
            self._notify_state_changed()  # show the busy state while the job runs in the background
        return True

    def _index_of(self, photo: CandidatePhoto) -> Optional[int]:
        for i, candidate in enumerate(self.candidates):
            if candidate is photo:
                return i
        return None

    def _remove(self, photo: CandidatePhoto) -> int:
        """Drop a culled photo from the queue, keeping the view on the same or the next photo."""
        idx = self._index_of(photo)
        if idx is None:
            return self.current_index
        self.candidates.pop(idx)
        if idx < self.current_index:
            self.current_index -= 1
        if self.current_index >= len(self.candidates) and self.current_index > 0:
            self.current_index = len(self.candidates) - 1
        return idx

    def cull_move(self) -> None:
        """Move active candidate and all companions to Selection/ with auto-advance."""
        photo, folder = self.current_photo, self.current_folder
        if not photo or not folder:
            return
        selection = self.selection_folder_name

        def moved(record: CullingRecord) -> None:
            self.undo_stack.append((record, photo, self._remove(photo)))
            self.selected_count += 1
            self._notify(f"Moved {photo.filename} to {selection}/")

        def keep_partial_for_undo(error: BaseException) -> None:
            # Copy verified, original not removable: the copy is the user's data now. Keep the
            # record so it can be undone, and keep the photo (it is still where it was).
            partial_record = getattr(error, "partial_record", None)
            if partial_record is not None:
                idx = self._index_of(photo)
                self.undo_stack.append(
                    (partial_record, photo, idx if idx is not None else self.current_index)
                )

        self._run(
            f"Moving {photo.filename} to {selection}/…",
            lambda: self.file_manager.move_candidate(photo, folder, selection_folder=selection),
            moved,
            f"Could not move {photo.filename}",
            on_error=keep_partial_for_undo,
            failure_message=lambda error: (
                f"Copied {photo.filename} to {selection}/ — original could not be removed"
                if getattr(error, "partial_record", None) is not None
                else None
            ),
        )

    def cull_copy(self) -> None:
        """Copy active candidate and all companions to Selection/ with auto-advance."""
        photo, folder = self.current_photo, self.current_folder
        if not photo or not folder:
            return
        selection = self.selection_folder_name

        def copied(record: CullingRecord) -> None:
            idx = self._index_of(photo)
            self.undo_stack.append((record, photo, idx if idx is not None else self.current_index))
            self.copied_count += 1
            self.selected_count += 1
            if self.current_photo is photo and self.current_index < len(self.candidates) - 1:
                self.current_index += 1
            self._notify(f"Copied {photo.filename} to {selection}/")

        self._run(
            f"Copying {photo.filename} to {selection}/…",
            lambda: self.file_manager.copy_candidate(photo, folder, selection_folder=selection),
            copied,
            f"Could not copy {photo.filename}",
        )

    def cull_trash(self) -> None:
        """Send active candidate and all companions to Trash with auto-advance."""
        photo = self.current_photo
        if not photo:
            return

        def trashed(record: CullingRecord) -> None:
            self.undo_stack.append((record, photo, self._remove(photo)))
            self.trashed_count += 1
            self._notify(f"Moved {photo.filename} to Trash")

        def preserve_partial_on_error(error: BaseException) -> None:
            partial_record = getattr(error, "partial_record", None)
            if partial_record is not None:
                idx = self._index_of(photo)
                self.undo_stack.append(
                    (partial_record, photo, idx if idx is not None else self.current_index)
                )

        self._run(
            f"Moving {photo.filename} to Trash…",
            lambda: self.file_manager.trash_candidate(photo),
            trashed,
            f"Could not move {photo.filename} to Trash",
            on_error=preserve_partial_on_error,
        )

    def undo(self) -> bool:
        """Atomically reverse the last culling operation (R-LINUX-CULL-06/07).

        Returns False when there is nothing to undo, an operation is still running, or the undo
        failed synchronously; True once it succeeded or was started in the background.
        """
        if not self.undo_stack or self.is_busy:
            return False

        record, photo, orig_idx = self.undo_stack.pop()
        outcome = []

        def undone(success: bool) -> None:
            outcome.append(bool(success))
            if not success:
                return
            if record.action_type in (CullingActionType.MOVE, CullingActionType.TRASH):
                if self._index_of(photo) is None:
                    insert_pos = min(orig_idx, len(self.candidates))
                    self.candidates.insert(insert_pos, photo)
                    self.current_index = insert_pos
                    if record.action_type == CullingActionType.MOVE:
                        self.selected_count = max(0, self.selected_count - 1)
                    else:
                        self.trashed_count = max(0, self.trashed_count - 1)
            elif record.action_type == CullingActionType.COPY:
                self.selected_count = max(0, self.selected_count - 1)
                self.copied_count = max(0, self.copied_count - 1)
            self._notify(f"Undid {record.action_type.value} for {photo.filename}")

        def keep_for_retry() -> None:
            outcome.append(False)
            self.undo_stack.append((record, photo, orig_idx))

        self._run(
            f"Undoing {record.action_type.value} of {photo.filename}…",
            lambda: self.file_manager.undo_culling(record, photo),
            undone,
            f"Could not undo {record.action_type.value} for {photo.filename}",
            on_failure=keep_for_retry,
        )
        return outcome[0] if outcome else self.is_busy

    def load_directory(self, folder: Path) -> None:
        """Load and sort photographs from folder."""
        self.current_folder = folder.resolve()
        self._folder_generation += 1
        scanner = DirectoryScanner(custom_selection_folder=self.selection_folder_name)
        # GVFS cloud mounts list folders under opaque IDs; show the folder's real name.
        parent_names = scanner.name_resolver.names_in(self.current_folder.parent)
        self.current_folder_name = parent_names.get(self.current_folder.name, self.current_folder.name)
        self.candidates = scanner.scan_directory(self.current_folder)
        self.current_index = 0
        self.undo_stack.clear()
        self.selected_count = 0
        self.copied_count = 0
        self.trashed_count = 0
        self._notify(f"Loaded {len(self.candidates)} photographs from {self.current_folder_name}")
        self._notify_state_changed()
