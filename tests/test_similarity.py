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
        Path("img1.jpg"): ScanResult(Path("img1.jpg"), scores={"dhash": "0000000000000000"}),
        Path("img2.jpg"): ScanResult(Path("img2.jpg"), scores={"dhash": "0000000000000001"}), # diff 1
        Path("img3.jpg"): ScanResult(Path("img3.jpg"), scores={"dhash": "0000000000000003"}), # diff 1 (total diff 2 from prev)
        Path("img4.jpg"): ScanResult(Path("img4.jpg"), scores={"dhash": "ffffffffffffffff"}), # diff 64
        Path("img5.jpg"): ScanResult(Path("img5.jpg"), scores={"dhash": "f000000000000000"}), # diff 4
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
        Path("img2.jpg"): ScanResult(Path("img2.jpg"), score=500.0), # sharpest
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
    monkeypatch.setattr("photo_selector_toolbox.cache.ScoreCache._init_db", lambda self: None)

    tool = SharpnessTool(root)
    
    # Mock files
    files = [Path("a.jpg"), Path("b.jpg"), Path("c.jpg")]
    tool.sorted_files = files.copy()
    tool.candidates = files.copy()
    
    tool.files_map = {
        Path("a.jpg"): ScanResult(Path("a.jpg"), scores={"dhash": "0000000000000000", "sharpness": 10.0}),
        Path("b.jpg"): ScanResult(Path("b.jpg"), scores={"dhash": "0000000000000001", "sharpness": 50.0}), # b is sharpest representative
        Path("c.jpg"): ScanResult(Path("c.jpg"), scores={"dhash": "0000000000000002", "sharpness": 20.0}),
    }

    # By default, Group Similar is off
    assert len(tool.candidates) == 3
    assert tool.candidates == files

    # Toggle Group Similar ON
    tool.group_similar_var.set(True)
    tool.on_group_similar_change()

    # Under our algorithm, a.jpg, b.jpg, c.jpg are grouped because hashes are similar.
    # The representative is b.jpg because it has the highest sharpness score (50.0).
    # Since it is collapsed by default, tool.candidates should only contain the representative: [b.jpg]
    assert len(tool.candidates) == 1
    assert tool.candidates[0] == Path("b.jpg")

    # Expand the group
    tool.image_groups[0].expanded = True
    tool.apply_grouping_and_refresh()

    # Now that it's expanded, candidates should contain representative, followed by children excluding representative
    # So [b.jpg, a.jpg, c.jpg]
    assert len(tool.candidates) == 3
    assert tool.candidates == [Path("b.jpg"), Path("a.jpg"), Path("c.jpg")]

    # Delete b.jpg (the representative)
    # a.jpg (10.0) vs c.jpg (20.0) -> c.jpg is new representative
    tool.execute_delete(Path("b.jpg"), 0)
    assert len(tool.image_groups) == 1
    assert tool.image_groups[0].representative == Path("c.jpg")
    
    root.destroy()
