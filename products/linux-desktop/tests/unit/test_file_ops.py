"""Unit tests for transactional file operations, atomic moves, copy, and trash.

Adheres to:
- R-LINUX-CULL-03: Atomic Multi-File Move & Cross-Filesystem Rollback Safety
- R-LINUX-CULL-04: Atomic Multi-File Copy to Selection & Staged Rollback
- R-LINUX-CULL-05: FreeDesktop Safe Trash Integration
- R-LINUX-CULL-06: Transactional Copy-Undo Safety Invariant
- R-LINUX-CULL-07: Transactional Move and Trash Undo
"""

import os
import shutil
from pathlib import Path
from unittest.mock import MagicMock, patch
import pytest

from photo_selector_linux.core.file_ops import (
    FileCullingError,
    FileCullingManager,
    InsufficientDiskSpaceError,
)
from photo_selector_linux.core.models import (
    CandidatePhoto,
    CullingActionType,
    PhotoStatus,
)


def test_intra_filesystem_atomic_move(tmp_path: Path):
    """Verify intra-filesystem move moves primary + companions to Selection/ (R-LINUX-CULL-03)."""
    raw = tmp_path / "DSC_001.ARW"
    jpg = tmp_path / "DSC_001.JPG"
    xmp = tmp_path / "DSC_001.xmp"
    raw.write_bytes(b"raw-data")
    jpg.write_bytes(b"jpg-data")
    xmp.write_bytes(b"xmp-data")

    candidate = CandidatePhoto(primary_path=raw, companion_paths=[jpg, xmp])
    mgr = FileCullingManager()
    record = mgr.move_candidate(candidate, tmp_path)

    assert candidate.status == PhotoStatus.SELECTED
    assert record.action_type == CullingActionType.MOVE
    assert not raw.exists()
    assert not jpg.exists()
    assert not xmp.exists()

    sel_dir = tmp_path / "Selection"
    assert (sel_dir / "DSC_001.ARW").exists()
    assert (sel_dir / "DSC_001.JPG").exists()
    assert (sel_dir / "DSC_001.xmp").exists()


def test_self_move_guard(tmp_path: Path):
    """Verify self-move is an idempotent no-op preserving file without deletion (R-LINUX-CULL-03)."""
    sel_dir = tmp_path / "Selection"
    sel_dir.mkdir()
    target = sel_dir / "DSC_002.ARW"
    target.write_bytes(b"keep-content")

    candidate = CandidatePhoto(primary_path=target, companion_paths=[])
    mgr = FileCullingManager()
    record = mgr.move_candidate(candidate, tmp_path)
    assert record.action_type == CullingActionType.MOVE

    assert target.exists()
    assert target.read_bytes() == b"keep-content"
    assert candidate.status == PhotoStatus.SELECTED


def test_intra_filesystem_move_rollback_on_failure(tmp_path: Path):
    """Verify atomic rollback restores already-moved companions if subsequent move fails (R-LINUX-CULL-03)."""
    raw = tmp_path / "DSC_003.ARW"
    jpg = tmp_path / "DSC_003.JPG"
    raw.write_bytes(b"raw")
    jpg.write_bytes(b"jpg")

    candidate = CandidatePhoto(primary_path=raw, companion_paths=[jpg])
    mgr = FileCullingManager()

    orig_replace = os.replace
    call_count = [0]

    def mock_replace(src, dst):
        call_count[0] += 1
        if call_count[0] == 2:
            raise PermissionError("Simulated write failure on companion")
        return orig_replace(src, dst)

    with patch("os.replace", side_effect=mock_replace):
        with pytest.raises(FileCullingError):
            mgr.move_candidate(candidate, tmp_path)

    # Rollback must restore all files to original source locations
    assert raw.exists()
    assert jpg.exists()
    assert not (tmp_path / "Selection" / "DSC_003.ARW").exists()


def test_cross_filesystem_preflight_space_check(tmp_path: Path):
    """Verify pre-flight capacity check raises InsufficientDiskSpaceError before disk mutation (R-LINUX-CULL-03)."""
    raw = tmp_path / "DSC_004.ARW"
    raw.write_bytes(b"x" * 1024)
    candidate = CandidatePhoto(primary_path=raw, companion_paths=[])
    mgr = FileCullingManager()

    mock_stat = MagicMock()
    mock_stat.f_bavail = 1
    mock_stat.f_frsize = 10  # 10 bytes available < 1024 bytes required

    with patch("os.statvfs", return_value=mock_stat):
        with pytest.raises(InsufficientDiskSpaceError):
            mgr.move_candidate(candidate, tmp_path)

    # Original file remains completely untouched
    assert raw.exists()


def test_cross_filesystem_two_phase_staging_and_rollback(tmp_path: Path):
    """Verify cross-mount staging failure cleans destination replicas, keeping sources intact (R-LINUX-CULL-03)."""
    raw = tmp_path / "DSC_005.ARW"
    jpg = tmp_path / "DSC_005.JPG"
    raw.write_bytes(b"raw")
    jpg.write_bytes(b"jpg")

    candidate = CandidatePhoto(primary_path=raw, companion_paths=[jpg])
    mgr = FileCullingManager()

    # Simulate cross-mount by faking different st_dev
    real_stat = Path.stat

    def mock_stat(self_path):
        st = real_stat(self_path)
        if "Selection" in str(self_path):
            st_mock = MagicMock()
            st_mock.st_dev = 999  # Different filesystem device ID
            st_mock.st_size = getattr(st, "st_size", 0)
            return st_mock
        return st

    call_copy = [0]
    orig_copy2 = shutil.copy2

    def mock_copy2(src, dst):
        call_copy[0] += 1
        if call_copy[0] == 2:
            raise IOError("Simulated network/device disconnect during companion staging")
        return orig_copy2(src, dst)

    with patch.object(Path, "stat", mock_stat), \
         patch("shutil.copy2", side_effect=mock_copy2):
        with pytest.raises(FileCullingError):
            mgr.move_candidate(candidate, tmp_path)

    # Invariant: All sources must remain intact! Destination replicas must be unlinked!
    assert raw.exists()
    assert jpg.exists()
    sel_raw = tmp_path / "Selection" / "DSC_005.ARW"
    assert not sel_raw.exists()


def test_atomic_copy_to_selection(tmp_path: Path):
    """Verify copy replicates primary + companions to Selection/ while preserving sources (R-LINUX-CULL-04)."""
    raw = tmp_path / "DSC_006.ARW"
    jpg = tmp_path / "DSC_006.JPG"
    raw.write_bytes(b"raw-data")
    jpg.write_bytes(b"jpg-data")

    candidate = CandidatePhoto(primary_path=raw, companion_paths=[jpg])
    mgr = FileCullingManager()
    record = mgr.copy_candidate(candidate, tmp_path)

    assert candidate.status == PhotoStatus.COPIED
    assert record.action_type == CullingActionType.COPY
    # Sources preserved
    assert raw.exists()
    assert jpg.exists()
    # Destination created
    assert (tmp_path / "Selection" / "DSC_006.ARW").exists()
    assert (tmp_path / "Selection" / "DSC_006.JPG").exists()


def test_copy_rollback_on_failure(tmp_path: Path):
    """Verify failed copy unlinks destination replicas without touching sources (R-LINUX-CULL-04)."""
    raw = tmp_path / "DSC_007.ARW"
    jpg = tmp_path / "DSC_007.JPG"
    raw.write_bytes(b"raw")
    jpg.write_bytes(b"jpg")

    candidate = CandidatePhoto(primary_path=raw, companion_paths=[jpg])
    mgr = FileCullingManager()

    call_count = [0]
    orig_copy2 = shutil.copy2

    def mock_copy2(src, dst):
        call_count[0] += 1
        if call_count[0] == 2:
            raise IOError("Simulated copy failure")
        return orig_copy2(src, dst)

    with patch("shutil.copy2", side_effect=mock_copy2):
        with pytest.raises(FileCullingError):
            mgr.copy_candidate(candidate, tmp_path)

    assert raw.exists()
    assert jpg.exists()
    assert not (tmp_path / "Selection" / "DSC_007.ARW").exists()


def test_copy_undo_source_preservation_invariant(tmp_path: Path):
    """Verify copy undo deletes ONLY destination replicas in Selection/; sources NEVER deleted (R-LINUX-CULL-06)."""
    raw = tmp_path / "SOURCE_008.ARW"
    jpg = tmp_path / "SOURCE_008.JPG"
    raw.write_bytes(b"precious-source-raw")
    jpg.write_bytes(b"precious-source-jpg")

    candidate = CandidatePhoto(primary_path=raw, companion_paths=[jpg])
    mgr = FileCullingManager()
    record = mgr.copy_candidate(candidate, tmp_path)

    sel_raw = tmp_path / "Selection" / "SOURCE_008.ARW"
    sel_jpg = tmp_path / "Selection" / "SOURCE_008.JPG"
    assert sel_raw.exists()
    assert sel_jpg.exists()

    # Execute Undo
    undone = mgr.undo_culling(record, candidate)
    assert undone is True
    assert candidate.status == PhotoStatus.CANDIDATE

    # Invariant: Selection replicas deleted
    assert not sel_raw.exists()
    assert not sel_jpg.exists()

    # ABSOLUTE SAFETY INVARIANT: Original source files MUST BE INTACT!
    assert raw.exists()
    assert jpg.exists()
    assert raw.read_bytes() == b"precious-source-raw"
    assert jpg.read_bytes() == b"precious-source-jpg"


def test_move_undo_restores_all_companions(tmp_path: Path):
    """Verify move undo returns all files from Selection/ back to source paths (R-LINUX-CULL-07)."""
    raw = tmp_path / "ORIG_009.ARW"
    jpg = tmp_path / "ORIG_009.JPG"
    raw.write_bytes(b"raw-data")
    jpg.write_bytes(b"jpg-data")

    candidate = CandidatePhoto(primary_path=raw, companion_paths=[jpg])
    mgr = FileCullingManager()
    record = mgr.move_candidate(candidate, tmp_path)

    assert not raw.exists()
    assert not jpg.exists()
    assert (tmp_path / "Selection" / "ORIG_009.ARW").exists()

    # Execute Move Undo
    undone = mgr.undo_culling(record, candidate)
    assert undone is True
    assert candidate.status == PhotoStatus.CANDIDATE

    # Files restored to original locations
    assert raw.exists()
    assert jpg.exists()
    assert not (tmp_path / "Selection" / "ORIG_009.ARW").exists()
    assert not (tmp_path / "Selection" / "ORIG_009.JPG").exists()


def test_cross_filesystem_move_commit_partial_failure_preserves_destinations(tmp_path: Path):
    """Verify that if source unlinking fails during Phase 2 Commit, destination replicas are NEVER deleted."""
    raw = tmp_path / "DSC_010.ARW"
    jpg = tmp_path / "DSC_010.JPG"
    raw.write_bytes(b"precious-raw-bytes" * 50)
    jpg.write_bytes(b"precious-jpg-bytes" * 50)

    candidate = CandidatePhoto(primary_path=raw, companion_paths=[jpg])
    mgr = FileCullingManager()

    real_stat = Path.stat

    def mock_stat(self_path):
        st = real_stat(self_path)
        if "Selection" in str(self_path):
            st_mock = MagicMock()
            st_mock.st_dev = 999
            st_mock.st_size = getattr(st, "st_size", 0)
            return st_mock
        return st

    orig_unlink = Path.unlink
    unlink_count = [0]

    def fail_on_second_source_unlink(self_path):
        unlink_count[0] += 1
        if unlink_count[0] == 2:
            raise PermissionError("Simulated second companion source unlink error (e.g. read-only SD-card lock)")
        return orig_unlink(self_path)

    with patch.object(Path, "stat", mock_stat), \
         patch.object(Path, "unlink", fail_on_second_source_unlink):
        with pytest.raises(FileCullingError) as exc_info:
            mgr.move_candidate(candidate, tmp_path)
        assert "destination replicas preserved" in str(exc_info.value)

    # CRITICAL ZERO DATA LOSS INVARIANT:
    # 1. Primary RAW destination replica must exist and have full content
    sel_raw = tmp_path / "Selection" / "DSC_010.ARW"
    assert sel_raw.exists()
    assert sel_raw.read_bytes() == b"precious-raw-bytes" * 50

    # 2. Companion JPG destination replica must exist and have full content
    sel_jpg = tmp_path / "Selection" / "DSC_010.JPG"
    assert sel_jpg.exists()
    assert sel_jpg.read_bytes() == b"precious-jpg-bytes" * 50

    # 3. First source was unlinked, second source remains intact
    assert not raw.exists()
    assert jpg.exists()


def test_cross_filesystem_move_staging_failure_cleans_inflight_and_staged_replicas(tmp_path: Path):
    """Verify failed cross-mount staging unlinks in-flight partial file and staged files (R-LINUX-CULL-03)."""
    raw = tmp_path / "DSC_011.ARW"
    jpg = tmp_path / "DSC_011.JPG"
    raw.write_bytes(b"raw-data-complete")
    jpg.write_bytes(b"jpg-data-complete")

    candidate = CandidatePhoto(primary_path=raw, companion_paths=[jpg])
    mgr = FileCullingManager()

    real_stat = Path.stat

    def mock_stat(self_path):
        st = real_stat(self_path)
        if "Selection" in str(self_path):
            st_mock = MagicMock()
            st_mock.st_dev = 999
            st_mock.st_size = getattr(st, "st_size", 0)
            return st_mock
        return st

    orig_copy2 = shutil.copy2
    call_copy = [0]

    def mock_copy2_fail_midstream(src, dst):
        call_copy[0] += 1
        if call_copy[0] == 2:
            Path(dst).write_bytes(b"PARTIAL_CORRUPT_BYTES")
            raise OSError("No space left on device")
        return orig_copy2(src, dst)

    with patch.object(Path, "stat", mock_stat), \
         patch("shutil.copy2", side_effect=mock_copy2_fail_midstream):
        with pytest.raises(FileCullingError):
            mgr.move_candidate(candidate, tmp_path)

    # Invariant: Neither staged RAW nor partial in-flight JPG may remain in Selection/
    assert not (tmp_path / "Selection" / "DSC_011.ARW").exists()
    assert not (tmp_path / "Selection" / "DSC_011.JPG").exists()

    # Sources remain 100% intact
    assert raw.exists() and raw.read_bytes() == b"raw-data-complete"
    assert jpg.exists() and jpg.read_bytes() == b"jpg-data-complete"


def test_copy_staging_failure_cleans_inflight_and_staged_replicas(tmp_path: Path):
    """Verify failed copy unlinks in-flight partial file and staged files (R-LINUX-CULL-04)."""
    raw = tmp_path / "DSC_012.ARW"
    jpg = tmp_path / "DSC_012.JPG"
    raw.write_bytes(b"raw-data")
    jpg.write_bytes(b"jpg-data")

    candidate = CandidatePhoto(primary_path=raw, companion_paths=[jpg])
    mgr = FileCullingManager()

    orig_copy2 = shutil.copy2
    call_copy = [0]

    def mock_copy2_fail_midstream(src, dst):
        call_copy[0] += 1
        if call_copy[0] == 2:
            Path(dst).write_bytes(b"CORRUPT")
            raise OSError("No space left on device")
        return orig_copy2(src, dst)

    with patch("shutil.copy2", side_effect=mock_copy2_fail_midstream):
        with pytest.raises(FileCullingError):
            mgr.copy_candidate(candidate, tmp_path)

    assert not (tmp_path / "Selection" / "DSC_012.ARW").exists()
    assert not (tmp_path / "Selection" / "DSC_012.JPG").exists()
    assert raw.exists() and jpg.exists()
