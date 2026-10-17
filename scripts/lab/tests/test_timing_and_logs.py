"""Unit tests for C-05 timing and log oracles."""

from __future__ import annotations

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from lab.logscan import scan_android_log, scan_linux_log  # noqa: E402
from lab.timing import evaluate_timing  # noqa: E402


class TestTimingAndLogs(unittest.TestCase):
    def test_timing_regression(self) -> None:
        self.assertEqual(
            evaluate_timing({"checkpoint": "moved", "step_ms": 1000}, {"checkpoint": "moved", "step_ms": 950}),
            [],
        )
        # > 1.5x and > +300 ms
        f_trend = evaluate_timing({"checkpoint": "moved", "step_ms": 1600}, {"checkpoint": "moved", "step_ms": 950})
        self.assertEqual(len(f_trend), 1)
        self.assertEqual(f_trend[0].rule, "timing-regression")

    def test_logscan_android_and_linux(self) -> None:
        clean = "I/ActivityManager: Displayed com.photoselectortoolbox.lab/.MainActivity\n"
        self.assertEqual(scan_android_log(clean, "com.photoselectortoolbox.lab"), [])

        crash_log = (
            "E/ActivityManager: ANR in com.photoselectortoolbox.lab\n"
            "E/AndroidRuntime: FATAL EXCEPTION: main\n"
            "E/AndroidRuntime: Process: com.photoselectortoolbox.lab, PID: 1234\n"
        )
        findings = scan_android_log(crash_log, "com.photoselectortoolbox.lab")
        rules = {f.rule: f.severity for f in findings}
        self.assertEqual(rules.get("log-anr"), "blocker")
        self.assertEqual(rules.get("log-crash"), "blocker")

        linux_err = (
            "Traceback (most recent call last):\n"
            "  File \"app.py\", line 10, in <module>\n"
            "Gtk-CRITICAL **: gtk_widget_compute_bounds: assertion failed\n"
        )
        l_findings = scan_linux_log(linux_err)
        l_rules = {f.rule: f.severity for f in l_findings}
        self.assertEqual(l_rules.get("log-traceback"), "blocker")
        self.assertEqual(l_rules.get("log-gtk-critical"), "minor")


if __name__ == "__main__":
    unittest.main()
