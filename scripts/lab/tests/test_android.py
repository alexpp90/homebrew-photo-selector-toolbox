"""Unit tests for C-12, C-13, C-14 Android adb wrapper, tree dump, configs, and runner."""

from __future__ import annotations

import io
import shutil
import subprocess
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from lab.android.adb import AdbClient  # noqa: E402
from lab.android.configs import ConfigNotRunError, apply_config, record_device_state, reset_config  # noqa: E402
from lab.android.runner import run_android_journey  # noqa: E402
from lab.android.tree_android import parse_uiautomator_xml  # noqa: E402
from lab.schema import validate  # noqa: E402
from PIL import Image  # noqa: E402

DATA_DIR = Path(__file__).resolve().parent / "data"


class FakeAdbExec:
    def __init__(self, landscape_on_rot: int = 0) -> None:
        self.commands: list[list[str]] = []
        self.cur_rotation = 1
        self.landscape_on_rot = landscape_on_rot

    def __call__(self, cmd: list[str], timeout: int = 30) -> subprocess.CompletedProcess[bytes]:
        self.commands.append(cmd)
        joined = " ".join(cmd)
        if "user_rotation" in joined and "put" in joined:
            self.cur_rotation = int(cmd[-1])
        if "wm density" in joined and "reset" not in joined and cmd[-1] == "density":
            return subprocess.CompletedProcess(cmd, 0, stdout=b"Physical density: 320\n", stderr=b"")
        if "dumpsys window" in joined:
            if self.cur_rotation == self.landscape_on_rot:
                return subprocess.CompletedProcess(cmd, 0, stdout=b"sw924dp w1480dp h924dp\n", stderr=b"")
            return subprocess.CompletedProcess(cmd, 0, stdout=b"sw924dp w924dp h1480dp\n", stderr=b"")
        if "pull /sdcard/pst_dump.xml" in joined:
            dest = Path(cmd[-1])
            shutil.copyfile(DATA_DIR / "android_dump.xml", dest)
            return subprocess.CompletedProcess(cmd, 0, stdout=b"", stderr=b"")
        if "exec-out screencap -p" in joined:
            buf = io.BytesIO()
            Image.new("RGB", (100, 60), (20, 40, 60)).save(buf, format="PNG")
            return subprocess.CompletedProcess(cmd, 0, stdout=buf.getvalue(), stderr=b"")
        if "find . -type f" in joined:
            return subprocess.CompletedProcess(cmd, 0, stdout=b"1024 ./DSC_0001.JPG\n", stderr=b"")
        return subprocess.CompletedProcess(cmd, 0, stdout=b"0\n", stderr=b"")


class TestAndroidLayer(unittest.TestCase):
    def test_parse_uiautomator_xml_converts_px_to_dp(self) -> None:
        tree = parse_uiautomator_xml(DATA_DIR / "android_dump.xml", density_dpi=320)
        self.assertEqual(validate(tree.to_dict(), "tree"), [])
        by_id = {n.id: n for n in tree.nodes}
        self.assertIn("selector.frame.current", by_id)
        self.assertEqual(by_id["selector.frame.current"].bounds[0], 440.0)
        self.assertEqual(by_id["selector.frame.current"].bounds[2], 600.0)
        self.assertEqual(by_id["sidebar_open_folder"].role, "button")

    def test_adb_guards_paths_and_verifies_rotation(self) -> None:
        recorder = FakeAdbExec(landscape_on_rot=0)
        adb = AdbClient(serial="TEST1234", exec_fn=recorder)

        with self.assertRaises(ValueError):
            adb.rm_rf("/sdcard/DCIM/Camera")

        saved = record_device_state(adb)
        apply_config(adb, "tab-landscape", is_avd_kbd=False)
        self.assertEqual(recorder.cur_rotation, 0)
        reset_config(adb, saved)
        self.assertTrue(any("reset" in " ".join(c) for c in recorder.commands))

    def test_keyboard_config_raises_not_run_on_physical_device(self) -> None:
        recorder = FakeAdbExec()
        adb = AdbClient(serial="TEST1234", exec_fn=recorder)
        with self.assertRaises(ConfigNotRunError):
            apply_config(adb, "tab-landscape-kbd", is_avd_kbd=False)

    def test_android_runner_executes_segments_and_captures_checkpoints(self) -> None:
        run_dir = Path("/tmp/pstlab/test_android_runner")
        if run_dir.exists():
            shutil.rmtree(run_dir)
        try:
            recorder = FakeAdbExec(landscape_on_rot=1)
            adb = AdbClient(serial="TEST1234", exec_fn=recorder)
            manifest = {
                "id": "SMOKE-android-desktop",
                "product": "android-desktop",
                "title": "Smoke launch",
                "fixture": "smoke",
                "configs": ["tab-landscape"],
                "fresh_install": True,
                "steps": [
                    {"maestro": "products/android/android-desktop/tests/e2e/SMOKE/01_launch.yaml"},
                    {"checkpoint": "launched"},
                ],
                "layout_expect": {"launched": {"required": ["selector_sidebar"]}},
            }
            status, _ = run_android_journey(
                manifest=manifest,
                config_name="tab-landscape",
                run_dir=run_dir,
                adb=adb,
                maestro_fn=lambda serial, flow, junit: 0,
            )
            self.assertIn(status, ("pass", "fail"))
            png_file = run_dir / "android-desktop" / "tab-landscape" / "SMOKE-android-desktop" / "01_launched.png"
            self.assertTrue(png_file.is_file())
        finally:
            if run_dir.exists():
                shutil.rmtree(run_dir)


if __name__ == "__main__":
    unittest.main()
