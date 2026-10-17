"""Unit tests for streaming scan ingestion, culls during active scan, and folder cancellation.

Adheres to:
- R-LINUX-CULL-01: Progressive Asynchronous Streaming Ingestion
- R-LINUX-CULL-02: Natural Alphanumeric Sorting (depth-first per folder, UD-9)
"""

import threading
import time
from pathlib import Path
from unittest.mock import MagicMock

from photo_selector_linux.core.models import CullingActionType, CullingRecord
from photo_selector_linux.core.scanner import DirectoryScanner
from photo_selector_linux.viewmodels.culling_workspace import CullingWorkspaceViewModel


class SlowStreamingScanner(DirectoryScanner):
    """Test scanner that pauses between the first photo batch and subsequent batches."""

    def __init__(self, first_published: threading.Event, release_rest: threading.Event):
        super().__init__()
        self._first_published = first_published
        self._release_rest = release_rest

    def scan_stream(self, root_dir: Path, batch_size: int = 30, first_batch_size: int = 1):
        gen = super().scan_stream(root_dir, batch_size=batch_size, first_batch_size=first_batch_size)
        first = next(gen, None)
        if first is not None:
            yield first
            self._first_published.set()
            self._release_rest.wait(timeout=5.0)
        for batch in gen:
            yield batch


def test_first_photo_published_before_scan_ends(tmp_path: Path):
    """Verify the first candidate is published to the workspace before scan completion (R-LINUX-CULL-01)."""
    for i in range(5):
        (tmp_path / f"DSC_{i:04d}.JPG").write_bytes(b"img")

    first_published = threading.Event()
    release_rest = threading.Event()
    scanner = SlowStreamingScanner(first_published, release_rest)

    vm = CullingWorkspaceViewModel(
        scanner=scanner,
        scan_idle_add=lambda fn, *args: fn(*args),
    )
    vm.load_directory(tmp_path)

    assert first_published.wait(timeout=3.0)
    # Wait briefly for scan_idle_add to deliver the first batch
    deadline = time.monotonic() + 2.0
    while len(vm.candidates) == 0 and time.monotonic() < deadline:
        time.sleep(0.01)

    assert len(vm.candidates) == 1
    assert vm.candidates[0].filename == "DSC_0000.JPG"
    assert vm.is_scanning is True

    release_rest.set()
    if vm._scan_thread is not None:
        vm._scan_thread.join(timeout=3.0)

    assert len(vm.candidates) == 5
    assert vm.is_scanning is False


def test_cull_during_scan_executes(tmp_path: Path):
    """Verify a cull triggered while the scan is still streaming executes immediately without waiting for scan end."""
    for i in range(4):
        (tmp_path / f"DSC_{i:04d}.JPG").write_bytes(b"img")

    first_published = threading.Event()
    release_rest = threading.Event()
    scanner = SlowStreamingScanner(first_published, release_rest)

    file_mgr = MagicMock()
    file_mgr.move_candidate.side_effect = lambda photo, folder, selection_folder="Selection", **kwargs: CullingRecord(
        photo_id=photo.id,
        action_type=CullingActionType.MOVE,
        original_primary_path=photo.primary_path,
        affected_paths=[(photo.primary_path, folder / selection_folder / photo.filename)],
    )

    vm = CullingWorkspaceViewModel(
        scanner=scanner,
        file_manager=file_mgr,
        scan_idle_add=lambda fn, *args: fn(*args),
    )
    vm.load_directory(tmp_path)
    assert first_published.wait(timeout=3.0)
    deadline = time.monotonic() + 2.0
    while len(vm.candidates) == 0 and time.monotonic() < deadline:
        time.sleep(0.01)

    # Trigger Move while scan is still paused on remaining batches
    assert vm.is_scanning is True
    assert vm.cull_move() is True
    assert file_mgr.move_candidate.called
    assert vm.selected_count == 1

    release_rest.set()
    if vm._scan_thread is not None:
        vm._scan_thread.join(timeout=3.0)

    assert [c.filename for c in vm.candidates] == ["DSC_0001.JPG", "DSC_0002.JPG", "DSC_0003.JPG"]


def test_folder_change_cancels_old_scan(tmp_path: Path):
    """Verify opening a second folder cancels batches from the previous folder's scan thread."""
    folder_a = tmp_path / "folder_a"
    folder_b = tmp_path / "folder_b"
    folder_a.mkdir()
    folder_b.mkdir()
    for i in range(4):
        (folder_a / f"A_{i:04d}.JPG").write_bytes(b"a")
    (folder_b / "B_0001.JPG").write_bytes(b"b")

    first_published = threading.Event()
    release_rest = threading.Event()
    slow_scanner = SlowStreamingScanner(first_published, release_rest)

    vm = CullingWorkspaceViewModel(
        scanner=slow_scanner,
        scan_idle_add=lambda fn, *args: fn(*args),
    )
    vm.load_directory(folder_a)
    assert first_published.wait(timeout=3.0)
    old_thread = vm._scan_thread

    # Switch to normal scanner for folder_b while folder_a scan is still mid-flight
    vm.scanner = DirectoryScanner()
    vm. _scan_idle_add = None
    vm.load_directory(folder_b)

    # Release old scan thread and ensure none of folder_a's remaining photos leak into folder_b
    release_rest.set()
    if old_thread is not None:
        old_thread.join(timeout=3.0)

    assert vm.current_folder == folder_b
    assert [c.filename for c in vm.candidates] == ["B_0001.JPG"]


def test_scan_directory_and_scan_stream_share_depth_first_per_folder_order(tmp_path: Path):
    """Verify scan_directory and scan_stream yield the identical depth-first per-folder natural order (UD-9)."""
    (tmp_path / "z_root.jpg").write_bytes(b"root")
    sub2 = tmp_path / "day2"
    sub10 = tmp_path / "day10"
    sub2.mkdir()
    sub10.mkdir()
    (sub2 / "a_sub2.jpg").write_bytes(b"s2")
    (sub10 / "a_sub10.jpg").write_bytes(b"s10")

    scanner = DirectoryScanner()
    from_dir = [c.primary_path.relative_to(tmp_path).as_posix() for c in scanner.scan_directory(tmp_path)]
    from_stream = [
        c.primary_path.relative_to(tmp_path).as_posix()
        for batch in scanner.scan_stream(tmp_path, batch_size=2, first_batch_size=1)
        for c in batch
    ]
    assert from_dir == ["z_root.jpg", "day2/a_sub2.jpg", "day10/a_sub10.jpg"]
    assert from_stream == from_dir
