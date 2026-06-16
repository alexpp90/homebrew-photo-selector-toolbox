import pytest
from pathlib import Path
from PIL import Image
from unittest import mock
import tkinter as tk

from photo_selector_toolbox.utils import (
    calculate_dhash,
    group_files_by_similarity,
    select_representative,
)
from photo_selector_toolbox.models import ScanResult
from photo_selector_toolbox.sharpness_gui import SharpnessTool, ImageGroup


def test_calculate_dhash():
    # Create a solid image and a half-white/half-black image
    img_solid = Image.new("RGB", (100, 100), color="blue")
    img_half = Image.new("RGB", (100, 100), color="white")
    # Draw a black rectangle on the right half
    for x in range(50, 100):
        for y in range(100):
            img_half.putpixel((x, y), (0, 0, 0))

    hash_solid = calculate_dhash(img_solid)
    hash_half = calculate_dhash(img_half)

    assert isinstance(hash_solid, int)
    assert isinstance(hash_half, int)
    assert hash_solid != hash_half


def test_group_files_by_similarity():
    files = [
        Path("img1.jpg"),
        Path("img2.jpg"),
        Path("img3.jpg"),
        Path("img4.jpg"),
        Path("img5.jpg"),
    ]

    # Mock ScanResults
    # Let's say img1, img2, img3 are similar (dhash diff <= 10)
    # img4 is different
    # img5 is different
    files_map = {
        Path("img1.jpg"): ScanResult(
            Path("img1.jpg"), scores={"dhash": "0000000000000000"}
        ),
        Path("img2.jpg"): ScanResult(
            Path("img2.jpg"), scores={"dhash": "0000000000000001"}
        ),  # diff 1
        Path("img3.jpg"): ScanResult(
            Path("img3.jpg"), scores={"dhash": "0000000000000003"}
        ),  # diff 1 (total diff 2 from prev)
        Path("img4.jpg"): ScanResult(
            Path("img4.jpg"), scores={"dhash": "ffffffffffffffff"}
        ),  # diff 64
        Path("img5.jpg"): ScanResult(
            Path("img5.jpg"), scores={"dhash": "f000000000000000"}
        ),  # diff 4
    }

    groups = group_files_by_similarity(files, files_map, threshold=10)
    assert len(groups) == 3
    assert groups[0] == [Path("img1.jpg"), Path("img2.jpg"), Path("img3.jpg")]
    assert groups[1] == [Path("img4.jpg")]
    assert groups[2] == [Path("img5.jpg")]


def test_select_representative():
    group = [Path("img1.jpg"), Path("img2.jpg"), Path("img3.jpg")]
    files_map = {
        Path("img1.jpg"): ScanResult(Path("img1.jpg"), score=100.0),
        Path("img2.jpg"): ScanResult(Path("img2.jpg"), score=500.0),  # sharpest
        Path("img3.jpg"): ScanResult(Path("img3.jpg"), score=200.0),
    }

    rep = select_representative(group, files_map)
    assert rep == Path("img2.jpg")

    # Fallback if no scores
    files_map_no_score = {
        Path("img1.jpg"): ScanResult(Path("img1.jpg")),
        Path("img2.jpg"): ScanResult(Path("img2.jpg")),
        Path("img3.jpg"): ScanResult(Path("img3.jpg")),
    }
    rep_fallback = select_representative(group, files_map_no_score)
    assert rep_fallback == Path("img1.jpg")


@mock.patch("photo_selector_toolbox.sharpness_gui.load_image_preview")
def test_gui_grouping_toggle(mock_load, monkeypatch):
    # Setup standard mock environment for tkinter
    root = tk.Tk()
    root.withdraw()

    # Prevent cache directory creation / sqlite operations
    monkeypatch.setattr(
        "photo_selector_toolbox.cache.ScoreCache._init_db", lambda self: None
    )

    tool = SharpnessTool(root)
    # Set grouping level to Time + Fast Similarity which uses dhash
    tool.group_level_var.set("Time + Fast Similarity")

    # Mock files with matching prefixes
    files = [Path("DSC_0001.JPG"), Path("DSC_0002.JPG"), Path("DSC_0003.JPG")]
    tool.sorted_files = files.copy()
    tool.candidates = files.copy()

    tool.files_map = {
        Path("DSC_0001.JPG"): ScanResult(
            Path("DSC_0001.JPG"),
            scores={
                "dhash": "0000000000000000",
                "dhash_8": "0000000000000000",
                "sharpness": 10.0,
            },
        ),
        Path("DSC_0002.JPG"): ScanResult(
            Path("DSC_0002.JPG"),
            scores={
                "dhash": "0000000000000001",
                "dhash_8": "0000000000000001",
                "sharpness": 50.0,
            },
        ),  # b is sharpest representative
        Path("DSC_0003.JPG"): ScanResult(
            Path("DSC_0003.JPG"),
            scores={
                "dhash": "0000000000000002",
                "dhash_8": "0000000000000002",
                "sharpness": 20.0,
            },
        ),
    }

    # By default, Group Similar is off
    assert len(tool.candidates) == 3
    assert tool.candidates == files

    # Toggle Group Similar ON
    tool.group_similar_var.set(True)
    tool.on_group_similar_change()

    # Under our algorithm, DSC_0001.JPG, DSC_0002.JPG, DSC_0003.JPG are grouped because hashes are similar.
    # The representative is DSC_0002.JPG because it has the highest sharpness score (50.0).
    # Since it is collapsed by default, tool.candidates should only contain the representative: [DSC_0002.JPG]
    assert len(tool.candidates) == 1
    assert tool.candidates[0] == Path("DSC_0002.JPG")

    # Expand the group
    tool.image_groups[0].expanded = True
    tool.apply_grouping_and_refresh()

    # Now that it's expanded, candidates should contain representative, followed by children excluding representative
    # So [DSC_0002.JPG, DSC_0001.JPG, DSC_0003.JPG]
    assert len(tool.candidates) == 3
    assert tool.candidates == [
        Path("DSC_0002.JPG"),
        Path("DSC_0001.JPG"),
        Path("DSC_0003.JPG"),
    ]

    # Delete DSC_0002.JPG (the representative)
    # DSC_0001.JPG (10.0) vs DSC_0003.JPG (20.0) -> DSC_0003.JPG is new representative
    tool.execute_delete(Path("DSC_0002.JPG"), 0)
    assert len(tool.image_groups) == 1
    assert tool.image_groups[0].representative == Path("DSC_0003.JPG")

    root.destroy()


def test_group_files_by_similarity_time_name():
    files = [
        Path("DSC_0001.JPG"),
        Path("DSC_0002.JPG"),
        Path("DSC_0003.JPG"),
        Path("Vacation_001.JPG"),
    ]
    files_map = {f: ScanResult(f) for f in files}

    # Mock st_mtime so that:
    # DSC_0001: 1000.0
    # DSC_0002: 1010.0 (diff 10s <= 30s)
    # DSC_0003: 1050.0 (diff 40s > 30s)
    # Vacation_001: 1055.0 (diff 5s, but prefix mismatch)
    mtimes = {
        Path("DSC_0001.JPG"): 1000.0,
        Path("DSC_0002.JPG"): 1010.0,
        Path("DSC_0003.JPG"): 1050.0,
        Path("Vacation_001.JPG"): 1055.0,
    }

    def mock_stat(self):
        return mock.Mock(st_mtime=mtimes.get(self, 0.0))

    with mock.patch.object(Path, "stat", mock_stat):
        # Test Level 1: Time & Filename
        groups = group_files_by_similarity(
            files, files_map, group_level="Time & Filename"
        )
        # DSC_0001 and DSC_0002 are grouped (diff 10s, same prefix)
        # DSC_0003 is separate (diff 40s from DSC_0002)
        # Vacation_001 is separate (diff 5s, but prefix mismatch)
        assert len(groups) == 3
        assert groups[0] == [Path("DSC_0001.JPG"), Path("DSC_0002.JPG")]
        assert groups[1] == [Path("DSC_0003.JPG")]
        assert groups[2] == [Path("Vacation_001.JPG")]


def test_group_files_by_similarity_time_fast():
    files = [
        Path("DSC_0001.JPG"),
        Path("DSC_0002.JPG"),
        Path("DSC_0003.JPG"),
    ]

    # DSC_0001 and DSC_0002: similar (diff 1)
    # DSC_0002 and DSC_0003: not similar (diff 64)
    files_map = {
        Path("DSC_0001.JPG"): ScanResult(
            Path("DSC_0001.JPG"), scores={"dhash_8": "0000000000000000"}
        ),
        Path("DSC_0002.JPG"): ScanResult(
            Path("DSC_0002.JPG"), scores={"dhash_8": "0000000000000001"}
        ),
        Path("DSC_0003.JPG"): ScanResult(
            Path("DSC_0003.JPG"), scores={"dhash_8": "ffffffffffffffff"}
        ),
    }

    mtimes = {
        Path("DSC_0001.JPG"): 1000.0,
        Path("DSC_0002.JPG"): 1010.0,  # diff 10s <= 30s
        Path("DSC_0003.JPG"): 1020.0,  # diff 10s <= 30s
    }

    def mock_stat(self):
        return mock.Mock(st_mtime=mtimes.get(self, 0.0))

    with mock.patch.object(Path, "stat", mock_stat):
        # Test Level 2: Time + Fast Similarity
        groups = group_files_by_similarity(
            files, files_map, group_level="Time + Fast Similarity"
        )
        assert len(groups) == 2
        assert groups[0] == [Path("DSC_0001.JPG"), Path("DSC_0002.JPG")]
        assert groups[1] == [Path("DSC_0003.JPG")]


def test_group_files_by_similarity_detailed():
    files = [
        Path("DSC_0001.JPG"),
        Path("DSC_0002.JPG"),
        Path("DSC_0003.JPG"),
    ]

    # DSC_0001 and DSC_0002: similar (diff 1)
    # DSC_0002 and DSC_0003: not similar (diff 64)
    files_map = {
        Path("DSC_0001.JPG"): ScanResult(
            Path("DSC_0001.JPG"),
            scores={
                "dhash_16": "0000000000000000000000000000000000000000000000000000000000000000"
            },
        ),
        Path("DSC_0002.JPG"): ScanResult(
            Path("DSC_0002.JPG"),
            scores={
                "dhash_16": "0000000000000000000000000000000000000000000000000000000000000001"
            },
        ),
        Path("DSC_0003.JPG"): ScanResult(
            Path("DSC_0003.JPG"),
            scores={
                "dhash_16": "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"
            },
        ),
    }

    mtimes = {
        Path("DSC_0001.JPG"): 1000.0,
        Path("DSC_0002.JPG"): 1010.0,  # diff 10s <= 30s
        Path("DSC_0003.JPG"): 1020.0,  # diff 10s <= 30s
    }

    def mock_stat(self):
        return mock.Mock(st_mtime=mtimes.get(self, 0.0))

    with mock.patch.object(Path, "stat", mock_stat):
        # Test Level 3: Detailed Similarity
        groups = group_files_by_similarity(
            files, files_map, group_level="Detailed Similarity"
        )
        assert len(groups) == 2
        assert groups[0] == [Path("DSC_0001.JPG"), Path("DSC_0002.JPG")]
        assert groups[1] == [Path("DSC_0003.JPG")]


def test_gui_grouping_cancellation(monkeypatch):
    import threading
    from unittest.mock import MagicMock
    from photo_selector_toolbox.sharpness_gui import SharpnessTool
    from photo_selector_toolbox.models import ScanResult

    root = tk.Tk()
    root.withdraw()

    # Prevent cache directory creation / sqlite operations
    monkeypatch.setattr(
        "photo_selector_toolbox.cache.ScoreCache._init_db", lambda self: None
    )

    # Mock load_config / save_config
    mock_config = {"group_similar": False, "group_level": "Time & Filename"}
    monkeypatch.setattr(
        "photo_selector_toolbox.sharpness_gui.load_config", lambda: mock_config
    )
    monkeypatch.setattr(
        "photo_selector_toolbox.sharpness_gui.save_config",
        lambda cfg: mock_config.update(cfg),
    )

    # Mock parent.after to call immediately
    def mock_after(ms, func, *args):
        func(*args)

    monkeypatch.setattr(root, "after", mock_after)

    tool = SharpnessTool(root)

    # Mock UI elements
    tool.group_progress_frame = MagicMock()
    tool.group_progress_bar = MagicMock()
    tool.group_status_lbl = MagicMock()
    tool.group_similar_chk = MagicMock()
    tool.group_level_combo = MagicMock()
    tool.scan_options_btn = MagicMock()

    # Setup files
    files = [Path("DSC_0001.JPG"), Path("DSC_0002.JPG")]
    tool.sorted_files = files.copy()
    tool.candidates = files.copy()
    tool.files_map = {
        Path("DSC_0001.JPG"): ScanResult(
            Path("DSC_0001.JPG"), scores={"sharpness": 10.0}
        ),
        Path("DSC_0002.JPG"): ScanResult(
            Path("DSC_0002.JPG"), scores={"sharpness": 50.0}
        ),
    }

    # Set initial applied states
    tool._last_applied_group_similar = False
    tool._last_applied_group_level = "Time & Filename"

    # Mock Thread.start to capture the thread instance but do not run it
    started_thread = None

    def dummy_thread_start(self):
        nonlocal started_thread
        started_thread = self

    monkeypatch.setattr(threading.Thread, "start", dummy_thread_start)

    # Enable grouping and trigger change
    tool.group_similar_var.set(True)
    tool.group_level_var.set("Time + Fast Similarity")
    tool.on_group_similar_change()

    # Assert start_grouping_analysis was triggered
    assert tool.is_grouping is True
    assert started_thread is not None

    # Call cancel_grouping
    tool.cancel_grouping()
    assert tool.grouping_stop_event.is_set()

    # Run the target logic synchronously
    started_thread._target()

    # Verify that everything reverted to last applied state
    assert tool.is_grouping is False
    assert tool.group_similar_var.get() is False
    assert tool.group_level_var.get() == "Time & Filename"

    # Verify widgets were re-enabled
    tool.group_similar_chk.state.assert_any_call(["!disabled"])
    tool.scan_options_btn.state.assert_any_call(["!disabled"])

    root.destroy()
