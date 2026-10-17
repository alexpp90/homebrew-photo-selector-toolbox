"""Unit tests for C-01 JSON schemas and validator."""

from __future__ import annotations

import json
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from lab.paths import repo_root  # noqa: E402
from lab.schema import validate  # noqa: E402
import yaml  # noqa: E402

DATA_DIR = Path(__file__).resolve().parent / "data"
JOURNEYS_DIR = repo_root() / "scripts" / "lab" / "journeys"


class TestSchema(unittest.TestCase):
    def test_sample_trees_validate(self) -> None:
        for name in ("tree_ok.json", "tree_overlap.json", "tree_zero_size.json"):
            data = json.loads((DATA_DIR / name).read_text(encoding="utf-8"))
            self.assertEqual(validate(data, "tree"), [])

    def test_invalid_tree_rejected(self) -> None:
        bad_tree = {"platform": "invalid", "window": {"w": 100, "h": 900, "scale": 1.0, "unit": "dp"}, "nodes": []}
        self.assertNotEqual(validate(bad_tree, "tree"), [])

    def test_finding_schema_valid_and_invalid(self) -> None:
        good = {
            "id": "a1b2c3d4e5f6",
            "source": "layout-lint",
            "product": "android-desktop",
            "config": "tab-landscape",
            "journey": "J-AD-02",
            "checkpoint": "three-up",
            "rule": "overlap-interactive",
            "severity": "blocker",
            "confidence": "high",
            "region": [10, 20, 30, 40],
            "message": "Sidebar overlaps frame",
            "evidence": ["android-desktop/tab-landscape/J-AD-02/01_three-up.png"],
            "new_since_last_run": True,
        }
        self.assertEqual(validate(good, "finding"), [])

        bad = dict(good, severity="unknown-severity")
        self.assertNotEqual(validate(bad, "finding"), [])

    def test_fs_schema_valid_and_invalid(self) -> None:
        good = {
            "root": "cull-basic",
            "files": [{"path": "DSC_0001.JPG", "size": 1024}],
            "trash": [],
        }
        self.assertEqual(validate(good, "fs"), [])

        bad = {"root": "cull-basic", "files": [{"path": "a.jpg", "size": "not-int"}], "trash": []}
        self.assertNotEqual(validate(bad, "fs"), [])

    def test_all_journey_manifests_validate(self) -> None:
        manifests = sorted(JOURNEYS_DIR.glob("*.yaml"))
        self.assertGreaterEqual(len(manifests), 10)
        for ypath in manifests:
            data = yaml.safe_load(ypath.read_text(encoding="utf-8"))
            self.assertEqual(validate(data, "journey"), [], f"Schema errors in {ypath.name}")

    def test_run_schema_valid(self) -> None:
        good_run = {
            "started": "2026-10-17T09:00:00Z",
            "finished": "2026-10-17T09:05:00Z",
            "commit": "526fb59",
            "products": {"android-desktop": {"status": "ran"}},
            "tools": {"python": "3.13"},
            "journeys": [
                {
                    "product": "android-desktop",
                    "config": "tab-landscape",
                    "journey": "J-AD-02",
                    "result": "pass",
                    "dir": "android-desktop/tab-landscape/J-AD-02",
                }
            ],
        }
        self.assertEqual(validate(good_run, "run"), [])


if __name__ == "__main__":
    unittest.main()
