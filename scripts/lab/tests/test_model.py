"""Unit tests for C-01 dataclasses and deterministic Finding.make_id."""

from __future__ import annotations

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from lab.model import Finding, Node, Tree  # noqa: E402
from lab.schema import validate  # noqa: E402


class TestModel(unittest.TestCase):
    def test_finding_id_deterministic_and_rounds_region_to_10(self) -> None:
        id1 = Finding.make_id(
            "android-desktop", "J-AD-02", "three-up", "overlap-interactive", [101.0, 204.0, 56.0, 62.0]
        )
        id2 = Finding.make_id(
            "android-desktop", "J-AD-02", "three-up", "overlap-interactive", [104.0, 196.0, 64.0, 58.0]
        )
        self.assertEqual(id1, id2)
        self.assertEqual(len(id1), 12)

        id_diff = Finding.make_id(
            "android-desktop", "J-AD-02", "three-up", "overlap-interactive", ["other_elem"]
        )
        self.assertNotEqual(id1, id_diff)

    def test_dataclass_round_trip(self) -> None:
        tree = Tree(
            platform="macos",
            window={"w": 1440, "h": 900, "scale": 2.0, "unit": "pt"},
            nodes=[
                Node(
                    id="slot_current",
                    parent=None,
                    role="image",
                    name="Current photo",
                    bounds=[100, 50, 800, 600],
                    interactive=False,
                    visible=True,
                )
            ],
        )
        d = tree.to_dict()
        self.assertEqual(validate(d, "tree"), [])
        restored = Tree.from_dict(d)
        self.assertEqual(restored.window_w, 1440)
        self.assertEqual(restored.nodes[0].id, "slot_current")

        f = Finding(
            id=Finding.make_id("macos", "J-MAC-01", "opened", "missing-or-zero-size", ["slot_current"]),
            source="layout-lint",
            product="macos",
            config="w1440",
            journey="J-MAC-01",
            checkpoint="opened",
            rule="missing-or-zero-size",
            severity="blocker",
            confidence="high",
            region=[0, 0, 0, 0],
            message="Missing slot_current",
            evidence=["macos/w1440/J-MAC-01/01_opened.png"],
        )
        self.assertEqual(validate(f.to_dict(), "finding"), [])
        self.assertEqual(Finding.from_dict(f.to_dict()).id, f.id)


if __name__ == "__main__":
    unittest.main()
