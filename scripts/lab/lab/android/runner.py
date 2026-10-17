"""Android journey runner executing Maestro segments, adb keys, checkpoints, and oracles."""

from __future__ import annotations

import json
import subprocess
import time
from pathlib import Path
from typing import Any, Callable

from ..fixtures import generate_fixtures
from ..fs_oracle import evaluate_fs_oracle
from ..layout_lint import lint, load_rules
from ..logscan import scan_android_log
from ..model import Finding
from ..paths import repo_root
from ..timing import evaluate_timing, find_previous_timing
from .adb import AdbClient
from .configs import ConfigNotRunError, apply_config, record_device_state, reset_config, set_verified_rotation
from .tree_android import parse_uiautomator_xml


MaestroFn = Callable[[str | None, Path, Path], int]


def default_maestro_runner(serial: str | None, flow_file: Path, junit_out: Path) -> int:
    """Run `maestro [--device <serial>] test <flow_file> --format junit --output <junit_out>`."""
    cmd = ["maestro"]
    if serial:
        cmd.extend(["--device", serial])
    cmd.extend(["test", str(flow_file), "--format", "junit", "--output", str(junit_out)])
    res = subprocess.run(cmd, capture_output=True, text=True, check=False)
    return res.returncode


def run_android_journey(
    manifest: dict[str, Any],
    config_name: str,
    run_dir: Path,
    adb: AdbClient,
    *,
    maestro_fn: MaestroFn | None = None,
    user_id: int = 0,
    is_avd_kbd: bool = False,
    root: Path | None = None,
    staging_root: Path | None = None,
) -> tuple[str, list[Finding]]:
    """Run one `(journey, config)` pair on Android and return `(result, findings)`."""
    r = root or repo_root()
    m_fn = maestro_fn or default_maestro_runner
    product = str(manifest["product"])
    journey_id = str(manifest["id"])
    fixture_set = str(manifest["fixture"])
    app_id = "com.phototok.lab" if product == "phototok" else "com.photoselectortoolbox.lab"

    out_dir = run_dir / product / config_name / journey_id
    out_dir.mkdir(parents=True, exist_ok=True)

    saved_state = record_device_state(adb)
    rules = load_rules(product)
    overlay_ids = {str(x) for x in rules.get("overlay_ids", [])}
    findings: list[Finding] = []
    result_status = "pass"

    try:
        try:
            apply_config(adb, config_name, is_avd_kbd=is_avd_kbd)
        except ConfigNotRunError as exc:
            res_obj = {"status": "not-run", "reason": str(exc), "findings": 0}
            (out_dir / "result.json").write_text(json.dumps(res_obj, indent=2), encoding="utf-8")
            return "not-run", []

        adb.logcat_clear()

        # Generate fixtures locally and push to /sdcard/PSTLab/<set>
        local_stage = (staging_root or (out_dir / "_fixture_stage")) / fixture_set
        generate_fixtures(fixture_set, local_stage, root=r)
        remote_fixture = f"/sdcard/PSTLab/{fixture_set}"
        adb.rm_rf(remote_fixture)
        adb.push_dir(local_stage, remote_fixture)

        initial_files = adb.list_files(remote_fixture)
        initial_fs = {"root": f"PSTLab/{fixture_set}", "files": initial_files, "trash": []}
        (out_dir / "00_initial.fs.json").write_text(
            json.dumps(initial_fs, indent=2), encoding="utf-8"
        )

        if manifest.get("fresh_install"):
            adb.clear(app_id, user=user_id)
        adb.start(app_id)

        start_ms = int(time.monotonic() * 1000)
        last_cp_ms = start_ms
        cp_index = 0
        seg_index = 0
        density_dpi = adb.density()

        def capture_checkpoint(cp_name: str) -> None:
            nonlocal cp_index, last_cp_ms
            cp_index += 1
            stem = f"{cp_index:02d}_{cp_name}"
            now_ms = int(time.monotonic() * 1000)
            timing_obj = {
                "checkpoint": cp_name,
                "t_ms": max(0, now_ms - start_ms),
                "step_ms": max(0, now_ms - last_cp_ms),
            }
            last_cp_ms = now_ms

            png_path = out_dir / f"{stem}.png"
            xml_path = out_dir / f"{stem}.xml"
            tree_path = out_dir / f"{stem}.tree.json"
            fs_path = out_dir / f"{stem}.fs.json"
            timing_path = out_dir / f"{stem}.timing.json"

            adb.screencap(png_path)
            adb.dump_tree(xml_path)
            tree = parse_uiautomator_xml(xml_path, density_dpi=density_dpi, overlay_ids=overlay_ids)
            tree_path.write_text(json.dumps(tree.to_dict(), indent=2), encoding="utf-8")

            cur_files = adb.list_files(remote_fixture)
            fs_obj = {"root": f"PSTLab/{fixture_set}", "files": cur_files, "trash": []}
            fs_path.write_text(json.dumps(fs_obj, indent=2), encoding="utf-8")
            timing_path.write_text(json.dumps(timing_obj, indent=2), encoding="utf-8")

            ev_rel = f"{product}/{config_name}/{journey_id}/{stem}.png"
            l_expect = (manifest.get("layout_expect") or {}).get(cp_name)
            findings.extend(
                lint(
                    tree,
                    rules,
                    l_expect,
                    product=product,
                    config=config_name,
                    journey=journey_id,
                    checkpoint=cp_name,
                    evidence=[ev_rel],
                )
            )
            f_expect = (manifest.get("fs_expect") or {}).get(cp_name)
            findings.extend(
                evaluate_fs_oracle(
                    fs_obj,
                    f_expect,
                    initial_fs=initial_fs,
                    product=product,
                    config=config_name,
                    journey=journey_id,
                    checkpoint=cp_name,
                    evidence=[ev_rel],
                )
            )
            prev_t = find_previous_timing(run_dir, product, config_name, journey_id, stem)
            findings.extend(
                evaluate_timing(
                    timing_obj,
                    prev_t,
                    product=product,
                    config=config_name,
                    journey=journey_id,
                    checkpoint=cp_name,
                    evidence=[ev_rel],
                )
            )

        for step in manifest.get("steps", []):
            if "maestro" in step:
                seg_index += 1
                flow_rel = Path(str(step["maestro"]))
                flow_path = flow_rel if flow_rel.is_absolute() else (r / flow_rel)
                junit_out = out_dir / f"maestro_{seg_index:02d}.xml"
                rc = m_fn(adb.serial, flow_path, junit_out)
                if rc != 0:
                    result_status = "flow-broken"
                    capture_checkpoint("broken")
                    break
            elif "keys" in step:
                for idx, key_code in enumerate(step["keys"]):
                    if idx > 0:
                        time.sleep(0.15)
                    if isinstance(key_code, list):
                        adb.key_combo(*[str(k) for k in key_code])
                    else:
                        adb.key(str(key_code))
            elif "checkpoint" in step:
                capture_checkpoint(str(step["checkpoint"]))
            elif "rotate" in step:
                set_verified_rotation(adb, str(step["rotate"]))
            elif "background" in step:
                bg_ms = int(step["background"])
                adb.home()
                if bg_ms > 0:
                    time.sleep(min(bg_ms / 1000.0, 10.0))
                adb.start(app_id)
            elif "wait_ms" in step:
                w_ms = int(step["wait_ms"])
                if w_ms > 0:
                    time.sleep(min(w_ms / 1000.0, 10.0))

        log_path = out_dir / "logcat.txt"
        adb.logcat_dump(log_path)
        log_text = log_path.read_text(encoding="utf-8", errors="replace")
        findings.extend(
            scan_android_log(
                log_text,
                app_id,
                product=product,
                config=config_name,
                journey=journey_id,
                checkpoint="logs",
                evidence=[f"{product}/{config_name}/{journey_id}/logcat.txt"],
            )
        )

        if result_status == "pass" and any(f.severity in ("blocker", "major") for f in findings):
            result_status = "fail"

        res_obj = {
            "status": result_status,
            "findings": len(findings),
        }
        (out_dir / "result.json").write_text(json.dumps(res_obj, indent=2), encoding="utf-8")
        return result_status, findings
    finally:
        reset_config(adb, saved_state)
