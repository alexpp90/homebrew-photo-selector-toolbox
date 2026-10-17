"""Unit tests for C-03 layout lint rules R1-R8."""

from __future__ import annotations

import json
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from lab.layout_lint import lint, load_rules  # noqa: E402
from lab.model import Tree  # noqa: E402

DATA_DIR = Path(__file__).resolve().parent / "data"


class TestLayoutLint(unittest.TestCase):
    def test_tree_ok_produces_zero_findings(self) -> None:
        tree = Tree.from_dict(json.loads((DATA_DIR / "tree_ok.json").read_text(encoding="utf-8")))
        rules = load_rules("android-desktop")
        expect = {
            "required": ["image_region", "selector_sidebar", "position_counter"],
            "frames": ["image_region"],
        }
        findings = lint(tree, rules, expect, config="tab-landscape")
        self.assertEqual(findings, [])

    def test_tree_overlap_triggers_r1_or_r2(self) -> None:
        tree = Tree.from_dict(json.loads((DATA_DIR / "tree_overlap.json").read_text(encoding="utf-8")))
        rules = load_rules("android-desktop")
        findings = lint(tree, rules, config="tab-landscape")
        rule_names = {f.rule for f in findings}
        self.assertTrue(bool(rule_names & {"overlap-interactive", "occludes-frame"}))

    def test_tree_zero_size_triggers_r3(self) -> None:
        tree = Tree.from_dict(json.loads((DATA_DIR / "tree_zero_size.json").read_text(encoding="utf-8")))
        rules = load_rules("android-desktop")
        expect = {"required": ["coach_overlay_dismiss"]}
        findings = lint(tree, rules, expect, config="tab-landscape")
        rule_names = {f.rule for f in findings}
        self.assertIn("missing-or-zero-size", rule_names)

    def test_r4_r5_r6_r7_r8(self) -> None:
        raw_tree = {
            "platform": "android",
            "window": {"w": 1480, "h": 924, "scale": 2.0, "unit": "dp"},
            "nodes": [
                {
                    "id": "off_btn",
                    "parent": None,
                    "role": "button",
                    "name": "Off",
                    "bounds": [1450, 10, 60, 50],
                    "interactive": True,
                    "visible": True,
                    "overlay": False,
                    "text": "",
                    "clipped": False,
                },
                {
                    "id": "small_unnamed_btn",
                    "parent": None,
                    "role": "button",
                    "name": "",
                    "bounds": [10, 10, 30, 30],
                    "interactive": True,
                    "visible": True,
                    "overlay": False,
                    "text": "",
                    "clipped": False,
                },
                {
                    "id": "clip_lbl",
                    "parent": None,
                    "role": "text",
                    "name": "",
                    "bounds": [10, 150, 80, 20],
                    "interactive": False,
                    "visible": True,
                    "overlay": False,
                    "text": "Truncated...",
                    "clipped": True,
                },
                {
                    "id": "image_region",
                    "parent": None,
                    "role": "image",
                    "name": "Image region",
                    "bounds": [400, 100, 300, 200],
                    "interactive": False,
                    "visible": True,
                    "overlay": False,
                    "text": "",
                    "clipped": False,
                },
            ],
        }
        tree = Tree.from_dict(raw_tree)
        rules = load_rules("android-desktop")
        findings = lint(tree, rules, {"frames": ["image_region"]}, config="tab-landscape")
        rule_names = {f.rule for f in findings}
        self.assertIn("off-screen", rule_names)
        self.assertIn("touch-target", rule_names)
        self.assertIn("unnamed-control", rule_names)
        self.assertIn("clipped-text", rule_names)
        self.assertIn("frame-size", rule_names)


if __name__ == "__main__":
    unittest.main()
