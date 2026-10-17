"""Unit tests for C-04 filesystem oracle."""

from __future__ import annotations

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from lab.fs_oracle import evaluate_fs_oracle  # noqa: E402


class TestFsOracle(unittest.TestCase):
    def setUp(self) -> None:
        self.initial = {
            "root": "PSTLab/burst-sharpness",
            "files": [
                {"path": "DSC_0001.JPG", "size": 100},
                {"path": "DSC_0002.JPG", "size": 100},
                {"path": "DSC_0003.JPG", "size": 100},
            ],
            "trash": [],
        }

    def test_unchanged_passes_and_fails(self) -> None:
        self.assertEqual(evaluate_fs_oracle(self.initial, "unchanged", initial_fs=self.initial), [])

        modified = {
            "root": "PSTLab/burst-sharpness",
            "files": [
                {"path": "DSC_0001.JPG", "size": 100},
                {"path": "DSC_0002.JPG", "size": 100},
            ],
            "trash": [],
        }
        findings = evaluate_fs_oracle(modified, {"unchanged": True}, initial_fs=self.initial)
        self.assertEqual(len(findings), 1)
        self.assertEqual(findings[0].rule, "fs-unchanged")

    def test_present_absent_and_trashed(self) -> None:
        after_move = {
            "root": "PSTLab/burst-sharpness",
            "files": [
                {"path": "DSC_0001.JPG", "size": 100},
                {"path": "Selection/DSC_0003.JPG", "size": 100},
            ],
            "trash": [{"path": "DSC_0002.JPG", "size": 100}],
        }
        expect = {
            "present": ["Selection/DSC_0003.JPG"],
            "absent": ["DSC_0003.JPG", "DSC_0002.JPG"],
            "trashed": ["*DSC_0002.JPG"],
        }
        self.assertEqual(evaluate_fs_oracle(after_move, expect, initial_fs=self.initial), [])

        bad_after = {
            "root": "PSTLab/burst-sharpness",
            "files": [{"path": "DSC_0003.JPG", "size": 100}],
            "trash": [],
        }
        findings = evaluate_fs_oracle(bad_after, expect, initial_fs=self.initial)
        rules = {f.rule for f in findings}
        self.assertIn("fs-present", rules)
        self.assertIn("fs-absent", rules)
        self.assertIn("fs-trashed", rules)


if __name__ == "__main__":
    unittest.main()
