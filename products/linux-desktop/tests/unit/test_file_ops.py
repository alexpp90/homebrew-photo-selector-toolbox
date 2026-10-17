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
    _apply_bundle_suffix,
    _copy_file,
    _move_no_overwrite,
    _resolve_bundle_destinations,
)
from photo_selector_linux.core.models import (
    CandidatePhoto,
    CullingActionType,
    PhotoStatus,
)
from photo_selector_linux.core.scanner import DirectoryScanner


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

    orig_move = _move_no_overwrite
    call_count = [0]

    def mock_move(src, dst):
        call_count[0] += 1
        if call_count[0] == 2:
            raise PermissionError("Simulated write failure on companion")
        return orig_move(src, dst)

    with patch("photo_selector_linux.core.file_ops._move_no_overwrite", side_effect=mock_move):
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

    def mock_stat(self_path, *args, **kwargs):
        st = real_stat(self_path, *args, **kwargs)
        if "Selection" in str(self_path):
            st_mock = MagicMock()
            st_mock.st_dev = 999  # Different filesystem device ID
            st_mock.st_size = getattr(st, "st_size", 0)
            st_mock.st_mode = getattr(st, "st_mode", 0)
            return st_mock
        return st

    call_copy = [0]
    orig_copyfileobj = shutil.copyfileobj

    def mock_copyfileobj(fsrc, fdst, *args, **kwargs):
        call_copy[0] += 1
        if call_copy[0] == 2:
            raise IOError("Simulated network/device disconnect during companion staging")
        return orig_copyfileobj(fsrc, fdst, *args, **kwargs)

    with patch.object(Path, "stat", mock_stat), \
         patch("shutil.copyfileobj", side_effect=mock_copyfileobj):
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
    orig_copyfileobj = shutil.copyfileobj

    def mock_copyfileobj(fsrc, fdst, *args, **kwargs):
        call_count[0] += 1
        if call_count[0] == 2:
            raise IOError("Simulated copy failure")
        return orig_copyfileobj(fsrc, fdst, *args, **kwargs)

    with patch("shutil.copyfileobj", side_effect=mock_copyfileobj):
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

    def mock_stat(self_path, *args, **kwargs):
        st = real_stat(self_path, *args, **kwargs)
        if "Selection" in str(self_path):
            st_mock = MagicMock()
            st_mock.st_dev = 999
            st_mock.st_size = getattr(st, "st_size", 0)
            st_mock.st_mode = getattr(st, "st_mode", 0)
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

    def mock_stat(self_path, *args, **kwargs):
        st = real_stat(self_path, *args, **kwargs)
        if "Selection" in str(self_path):
            st_mock = MagicMock()
            st_mock.st_dev = 999
            st_mock.st_size = getattr(st, "st_size", 0)
            st_mock.st_mode = getattr(st, "st_mode", 0)
            return st_mock
        return st

    orig_copyfileobj = shutil.copyfileobj
    call_copy = [0]

    def mock_copyfileobj_fail_midstream(fsrc, fdst, *args, **kwargs):
        call_copy[0] += 1
        if call_copy[0] == 2:
            fdst.write(b"PARTIAL_CORRUPT_BYTES")
            fdst.flush()
            raise OSError("No space left on device")
        return orig_copyfileobj(fsrc, fdst, *args, **kwargs)

    with patch.object(Path, "stat", mock_stat), \
         patch("shutil.copyfileobj", side_effect=mock_copyfileobj_fail_midstream):
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

    orig_copyfileobj = shutil.copyfileobj
    call_copy = [0]

    def mock_copyfileobj_fail_midstream(fsrc, fdst, *args, **kwargs):
        call_copy[0] += 1
        if call_copy[0] == 2:
            fdst.write(b"CORRUPT")
            fdst.flush()
            raise OSError("No space left on device")
        return orig_copyfileobj(fsrc, fdst, *args, **kwargs)

    with patch("shutil.copyfileobj", side_effect=mock_copyfileobj_fail_midstream):
        with pytest.raises(FileCullingError):
            mgr.copy_candidate(candidate, tmp_path)

    assert not (tmp_path / "Selection" / "DSC_012.ARW").exists()
    assert not (tmp_path / "Selection" / "DSC_012.JPG").exists()
    assert raw.exists() and jpg.exists()


def test_trash_and_undo_restores_all_companions(tmp_path: Path):
    """Verify trash_candidate moves files to FreeDesktop Trash and undo_culling restores them (R-LINUX-CULL-05/07)."""
    trash_dir = tmp_path / "Trash"
    raw = tmp_path / "DSC_013.ARW"
    jpg = tmp_path / "DSC_013.JPG"
    xmp = tmp_path / "DSC_013.xmp"
    raw.write_bytes(b"raw-trash-payload")
    jpg.write_bytes(b"jpg-trash-payload")
    xmp.write_bytes(b"xmp-trash-payload")

    candidate = CandidatePhoto(primary_path=raw, companion_paths=[jpg, xmp])
    mgr = FileCullingManager(trash_dir=trash_dir)
    record = mgr.trash_candidate(candidate)

    assert candidate.status == PhotoStatus.TRASHED
    assert record.action_type == CullingActionType.TRASH
    assert not raw.exists()
    assert not jpg.exists()
    assert not xmp.exists()
    assert (trash_dir / "files" / "DSC_013.ARW").exists()
    assert (trash_dir / "files" / "DSC_013.JPG").exists()
    assert (trash_dir / "files" / "DSC_013.xmp").exists()
    assert (trash_dir / "info" / "DSC_013.ARW.trashinfo").exists()

    # Execute Trash Undo
    undone = mgr.undo_culling(record, candidate)
    assert undone is True
    assert candidate.status == PhotoStatus.CANDIDATE

    # Files physically restored to original locations and .trashinfo removed
    assert raw.exists() and raw.read_bytes() == b"raw-trash-payload"
    assert jpg.exists() and jpg.read_bytes() == b"jpg-trash-payload"
    assert xmp.exists() and xmp.read_bytes() == b"xmp-trash-payload"
    assert not (trash_dir / "files" / "DSC_013.ARW").exists()
    assert not (trash_dir / "info" / "DSC_013.ARW.trashinfo").exists()


def test_apply_bundle_suffix_companion_variants():
    """Verify _apply_bundle_suffix preserves companion pairing across all sidecar and edit forms."""
    assert _apply_bundle_suffix("IMG_0001.CR3", "IMG_0001", 2) == "IMG_0001-2.CR3"
    assert _apply_bundle_suffix("IMG_0001.JPG", "IMG_0001", 2) == "IMG_0001-2.JPG"
    assert _apply_bundle_suffix("IMG_0001.CR3.xmp", "IMG_0001", 2) == "IMG_0001-2.CR3.xmp"
    assert _apply_bundle_suffix("IMG_0001.xmp", "IMG_0001", 2) == "IMG_0001-2.xmp"
    assert _apply_bundle_suffix("IMG_0001-Edit.tif", "IMG_0001", 2) == "IMG_0001-2-Edit.tif"
    assert _apply_bundle_suffix("IMG_0001_Edit.tif", "IMG_0001", 2) == "IMG_0001-2_Edit.tif"
    assert _apply_bundle_suffix("IMG_0001-Enhanced-NR.dng", "IMG_0001", 2) == "IMG_0001-2-Enhanced-NR.dng"


def test_move_two_same_named_bundles_in_sequence_preserves_both_and_pairing(tmp_path: Path):
    """Moving two different bundles named IMG_0001.* in sequence keeps both (IMG_0001.* and IMG_0001-2.*)."""
    cam_a = tmp_path / "cam_a"
    cam_b = tmp_path / "cam_b"
    cam_a.mkdir()
    cam_b.mkdir()

    a_cr3 = cam_a / "IMG_0001.CR3"
    a_jpg = cam_a / "IMG_0001.JPG"
    a_xmp = cam_a / "IMG_0001.CR3.xmp"
    a_edit = cam_a / "IMG_0001-Edit.tif"
    a_cr3.write_bytes(b"cam-a-cr3")
    a_jpg.write_bytes(b"cam-a-jpg")
    a_xmp.write_bytes(b"cam-a-xmp")
    a_edit.write_bytes(b"cam-a-edit")

    b_cr3 = cam_b / "IMG_0001.CR3"
    b_jpg = cam_b / "IMG_0001.JPG"
    b_xmp = cam_b / "IMG_0001.CR3.xmp"
    b_edit = cam_b / "IMG_0001-Edit.tif"
    b_cr3.write_bytes(b"cam-b-cr3")
    b_jpg.write_bytes(b"cam-b-jpg")
    b_xmp.write_bytes(b"cam-b-xmp")
    b_edit.write_bytes(b"cam-b-edit")

    cand_a = CandidatePhoto(primary_path=a_cr3, companion_paths=[a_jpg, a_xmp, a_edit])
    cand_b = CandidatePhoto(primary_path=b_cr3, companion_paths=[b_jpg, b_xmp, b_edit])

    mgr = FileCullingManager()
    mgr.move_candidate(cand_a, tmp_path)
    mgr.move_candidate(cand_b, tmp_path)

    sel_dir = tmp_path / "Selection"
    assert (sel_dir / "IMG_0001.CR3").read_bytes() == b"cam-a-cr3"
    assert (sel_dir / "IMG_0001.JPG").read_bytes() == b"cam-a-jpg"
    assert (sel_dir / "IMG_0001.CR3.xmp").read_bytes() == b"cam-a-xmp"
    assert (sel_dir / "IMG_0001-Edit.tif").read_bytes() == b"cam-a-edit"

    assert (sel_dir / "IMG_0001-2.CR3").read_bytes() == b"cam-b-cr3"
    assert (sel_dir / "IMG_0001-2.JPG").read_bytes() == b"cam-b-jpg"
    assert (sel_dir / "IMG_0001-2.CR3.xmp").read_bytes() == b"cam-b-xmp"
    assert (sel_dir / "IMG_0001-2-Edit.tif").read_bytes() == b"cam-b-edit"

    # Verify DirectoryScanner pairs both bundles cleanly when scanning Selection/ directly
    scanned = DirectoryScanner().scan_directory(sel_dir)
    scanned_by_name = {c.filename: c for c in scanned}
    assert set(scanned_by_name.keys()) == {"IMG_0001.CR3", "IMG_0001-2.CR3"}
    assert sorted(p.name for p in scanned_by_name["IMG_0001.CR3"].companion_paths) == [
        "IMG_0001-Edit.tif",
        "IMG_0001.CR3.xmp",
        "IMG_0001.JPG",
    ]
    assert sorted(p.name for p in scanned_by_name["IMG_0001-2.CR3"].companion_paths) == [
        "IMG_0001-2-Edit.tif",
        "IMG_0001-2.CR3.xmp",
        "IMG_0001-2.JPG",
    ]


def test_copy_when_companion_already_exists_suffixes_entire_bundle(tmp_path: Path):
    """When any member (e.g. IMG_0001.JPG) already exists in Selection/, the whole bundle gets -2."""
    sel_dir = tmp_path / "Selection"
    sel_dir.mkdir()
    existing_jpg = sel_dir / "IMG_0001.JPG"
    existing_jpg.write_bytes(b"preexisting-jpg")

    cr3 = tmp_path / "IMG_0001.CR3"
    jpg = tmp_path / "IMG_0001.JPG"
    xmp = tmp_path / "IMG_0001.CR3.xmp"
    cr3.write_bytes(b"new-cr3")
    jpg.write_bytes(b"new-jpg")
    xmp.write_bytes(b"new-xmp")

    candidate = CandidatePhoto(primary_path=cr3, companion_paths=[jpg, xmp])
    mgr = FileCullingManager()
    mgr.copy_candidate(candidate, tmp_path)

    assert existing_jpg.read_bytes() == b"preexisting-jpg"
    assert not (sel_dir / "IMG_0001.CR3").exists()
    assert not (sel_dir / "IMG_0001.CR3.xmp").exists()
    assert (sel_dir / "IMG_0001-2.CR3").read_bytes() == b"new-cr3"
    assert (sel_dir / "IMG_0001-2.JPG").read_bytes() == b"new-jpg"
    assert (sel_dir / "IMG_0001-2.CR3.xmp").read_bytes() == b"new-xmp"


def test_copy_then_undo_with_preexisting_destination_preserves_preexisting_file(tmp_path: Path):
    """Copy-then-undo when IMG_0001.JPG pre-existed in Selection/ deletes only IMG_0001-2.JPG."""
    sel_dir = tmp_path / "Selection"
    sel_dir.mkdir()
    preexisting_jpg = sel_dir / "IMG_0001.JPG"
    preexisting_jpg.write_bytes(b"preexisting-selection-jpg")

    src_jpg = tmp_path / "IMG_0001.JPG"
    src_jpg.write_bytes(b"candidate-source-jpg")

    candidate = CandidatePhoto(primary_path=src_jpg, companion_paths=[])
    mgr = FileCullingManager()
    record = mgr.copy_candidate(candidate, tmp_path)

    copied_dst = sel_dir / "IMG_0001-2.JPG"
    assert copied_dst.exists() and copied_dst.read_bytes() == b"candidate-source-jpg"
    assert preexisting_jpg.read_bytes() == b"preexisting-selection-jpg"

    assert mgr.undo_culling(record, candidate) is True

    assert not copied_dst.exists()
    assert preexisting_jpg.exists() and preexisting_jpg.read_bytes() == b"preexisting-selection-jpg"
    assert src_jpg.exists() and src_jpg.read_bytes() == b"candidate-source-jpg"


def test_move_then_undo_when_original_source_occupied_restores_with_suffix(tmp_path: Path):
    """When original source path is occupied before undo, existing file is kept and bundle restores as -2."""
    cr3 = tmp_path / "IMG_0001.CR3"
    jpg = tmp_path / "IMG_0001.JPG"
    xmp = tmp_path / "IMG_0001.CR3.xmp"
    cr3.write_bytes(b"moved-cr3")
    jpg.write_bytes(b"moved-jpg")
    xmp.write_bytes(b"moved-xmp")

    candidate = CandidatePhoto(primary_path=cr3, companion_paths=[jpg, xmp])
    mgr = FileCullingManager()
    record = mgr.move_candidate(candidate, tmp_path)

    # Simulate a new file imported at the original primary path before undo
    cr3.write_bytes(b"newly-imported-cr3-do-not-overwrite")

    assert mgr.undo_culling(record, candidate) is True

    # Existing occupying file is 100% preserved
    assert cr3.read_bytes() == b"newly-imported-cr3-do-not-overwrite"

    # Moved bundle restored under -2 suffix
    assert (tmp_path / "IMG_0001-2.CR3").read_bytes() == b"moved-cr3"
    assert (tmp_path / "IMG_0001-2.JPG").read_bytes() == b"moved-jpg"
    assert (tmp_path / "IMG_0001-2.CR3.xmp").read_bytes() == b"moved-xmp"
    assert candidate.primary_path == tmp_path / "IMG_0001-2.CR3"
    assert candidate.companion_paths == [tmp_path / "IMG_0001-2.JPG", tmp_path / "IMG_0001-2.CR3.xmp"]


def test_trash_then_undo_when_original_source_occupied_restores_with_suffix(tmp_path: Path):
    """When original path is occupied before trash undo, existing file is kept and trashed bundle restores as -2."""
    trash_dir = tmp_path / "Trash"
    cr3 = tmp_path / "IMG_0001.CR3"
    jpg = tmp_path / "IMG_0001.JPG"
    cr3.write_bytes(b"trashed-cr3")
    jpg.write_bytes(b"trashed-jpg")

    candidate = CandidatePhoto(primary_path=cr3, companion_paths=[jpg])
    mgr = FileCullingManager(trash_dir=trash_dir)
    record = mgr.trash_candidate(candidate)

    # Occupy original path before undo
    cr3.write_bytes(b"occupying-cr3")

    assert mgr.undo_culling(record, candidate) is True

    assert cr3.read_bytes() == b"occupying-cr3"
    assert (tmp_path / "IMG_0001-2.CR3").read_bytes() == b"trashed-cr3"
    assert (tmp_path / "IMG_0001-2.JPG").read_bytes() == b"trashed-jpg"
    assert candidate.primary_path == tmp_path / "IMG_0001-2.CR3"
    assert candidate.companion_paths == [tmp_path / "IMG_0001-2.JPG"]


def test_cross_mount_staging_with_preexisting_destination_name_stages_to_suffix_and_fsyncs(tmp_path: Path):
    """Cross-mount move with pre-existing destination name stages to -2, calls fsync, and preserves existing file."""
    sel_dir = tmp_path / "Selection"
    sel_dir.mkdir()
    preexisting = sel_dir / "IMG_0001.CR3"
    preexisting.write_bytes(b"preexisting-cr3")

    cr3 = tmp_path / "IMG_0001.CR3"
    jpg = tmp_path / "IMG_0001.JPG"
    cr3.write_bytes(b"cross-mount-cr3")
    jpg.write_bytes(b"cross-mount-jpg")

    candidate = CandidatePhoto(primary_path=cr3, companion_paths=[jpg])
    mgr = FileCullingManager()

    real_stat = Path.stat

    def mock_stat(self_path, *args, **kwargs):
        st = real_stat(self_path, *args, **kwargs)
        if "Selection" in str(self_path):
            st_mock = MagicMock()
            st_mock.st_dev = 999
            st_mock.st_size = getattr(st, "st_size", 0)
            st_mock.st_mode = getattr(st, "st_mode", 0)
            return st_mock
        return st

    fsync_calls = []
    orig_fsync = os.fsync

    def record_fsync(fd):
        fsync_calls.append(fd)
        return orig_fsync(fd)

    with patch.object(Path, "stat", mock_stat), \
         patch("os.fsync", side_effect=record_fsync):
        record = mgr.move_candidate(candidate, tmp_path)

    assert len(fsync_calls) == 2
    assert preexisting.read_bytes() == b"preexisting-cr3"
    assert (sel_dir / "IMG_0001-2.CR3").read_bytes() == b"cross-mount-cr3"
    assert (sel_dir / "IMG_0001-2.JPG").read_bytes() == b"cross-mount-jpg"
    assert not cr3.exists()
    assert not jpg.exists()
    assert record.affected_paths == [
        (cr3, (sel_dir / "IMG_0001-2.CR3").resolve()),
        (jpg, (sel_dir / "IMG_0001-2.JPG").resolve()),
    ]


def test_copy_file_and_move_no_overwrite_raise_file_exists_error(tmp_path: Path):
    """_copy_file (O_CREAT | O_EXCL) and _move_no_overwrite never overwrite an existing destination file."""
    src = tmp_path / "src.jpg"
    dst = tmp_path / "dst.jpg"
    src.write_bytes(b"new-bytes")
    dst.write_bytes(b"existing-precious-bytes")

    with pytest.raises(FileExistsError):
        _copy_file(src, dst)
    assert dst.read_bytes() == b"existing-precious-bytes"

    with pytest.raises(FileExistsError):
        _move_no_overwrite(src, dst)
    assert dst.read_bytes() == b"existing-precious-bytes"
    assert src.read_bytes() == b"new-bytes"


def test_no_os_replace_used_in_file_ops():
    """Verify file_ops.py contains no os.replace calls that could overwrite existing user files."""
    import ast
    import inspect
    from photo_selector_linux.core import file_ops

    tree = ast.parse(inspect.getsource(file_ops))
    replace_calls = [
        node for node in ast.walk(tree)
        if isinstance(node, ast.Call)
        and isinstance(node.func, ast.Attribute)
        and node.func.attr == "replace"
        and isinstance(node.func.value, ast.Name)
        and node.func.value.id == "os"
    ]
    assert replace_calls == []


def test_resolve_bundle_destinations_skips_occupied_suffixes(tmp_path: Path):
    """If both unsuffixed and -2 are occupied for any member, _resolve_bundle_destinations selects -3."""
    sel_dir = tmp_path / "Selection"
    sel_dir.mkdir()
    (sel_dir / "IMG_0001.CR3").write_bytes(b"v1")
    (sel_dir / "IMG_0001-2.JPG").write_bytes(b"v2-jpg")

    cr3 = tmp_path / "IMG_0001.CR3"
    jpg = tmp_path / "IMG_0001.JPG"
    cr3.write_bytes(b"v3-cr3")
    jpg.write_bytes(b"v3-jpg")

    candidate = CandidatePhoto(primary_path=cr3, companion_paths=[jpg])
    resolved = _resolve_bundle_destinations(candidate, sel_dir)
    assert resolved == [
        (cr3, (sel_dir / "IMG_0001-3.CR3").resolve()),
        (jpg, (sel_dir / "IMG_0001-3.JPG").resolve()),
    ]
