"""Android journey runner executing Maestro segments, adb keys, checkpoints, and oracles."""

from __future__ import annotations

import json
import os
import shutil
import subprocess
import time
from pathlib import Path
from typing import Any, Callable, Optional

from ..fixtures import generate_fixtures
from ..fs_oracle import evaluate_fs_oracle
from ..layout_lint import lint, load_rules
from ..logscan import scan_android_log
from ..model import Finding
from ..paths import repo_root
from ..timing import evaluate_timing, find_previous_timing
from .adb import AdbClient
from .configs import (
    ConfigNotRunError,
    apply_config,
    load_android_configs,
    record_device_state,
    reset_config,
    resize_app_window,
    set_verified_rotation,
    window_mode_mismatch,
)
from .tree_android import parse_uiautomator_xml


# Optional[], not `str | None`: aliases are evaluated at runtime and the Mac runs Python 3.9.
MaestroFn = Callable[[Optional[str], Path, Path], int]


MAESTRO_TIMEOUT_S = 300
REMOTE_WORK_DIR = "/sdcard/PSTLab/work"
REMOTE_WORK_ROOT = "PSTLab/work"
# Per-product flow (products/android/<product>/tests/e2e/) that makes sure the app has the
# work folder open before a journey that does not start from a fresh install.
ENSURE_FOLDER_FLOW = "_ensure_folder.yaml"
PICKER_PACKAGES = ("com.google.android.documentsui", "com.android.documentsui")


def maestro_binary() -> str:
    """`maestro` from PATH, else the official installer's `~/.maestro/bin/maestro`.

    The fallback matters for the systemd timer and other non-login shells, which do not read
    the PATH line the installer appends to the shell rc file.
    """
    found = shutil.which("maestro")
    if found:
        return found
    home_bin = Path.home() / ".maestro" / "bin" / "maestro"
    return str(home_bin) if home_bin.is_file() else "maestro"


# Maestro failures that say nothing about the app or the flow: the on-device driver or the
# wireless adb link dropped. One retry separates these from a broken flow.
INFRA_FAILURE_MARKERS = (
    "DeviceServerDiedException",
    "DEADLINE_EXCEEDED",
    "was requested, but it is not connected",
    "Unable to launch app",
    "UNAVAILABLE: io exception",
)


def is_infra_failure(log_text: str) -> bool:
    return any(marker in log_text for marker in INFRA_FAILURE_MARKERS)


def default_maestro_runner(serial: str | None, flow_file: Path, junit_out: Path) -> int:
    """Run one Maestro flow; retry once when the failure is infrastructure, not the flow."""
    rc = _run_maestro_once(serial, flow_file, junit_out)
    log = junit_out.with_suffix(".log")
    xml_text = junit_out.read_text(encoding="utf-8", errors="replace") if junit_out.is_file() else ""
    log_text = log.read_text(encoding="utf-8", errors="replace") if log.is_file() else ""
    if rc != 0 and is_infra_failure(log_text + xml_text):
        first = log_text + "\n" + xml_text
        rc = _run_maestro_once(serial, flow_file, junit_out)
        with log.open("a", encoding="utf-8") as fh:
            fh.write("\n--- first attempt failed on infrastructure; retried once ---\n" + first[-2000:])
    return rc


def _run_maestro_once(serial: str | None, flow_file: Path, junit_out: Path) -> int:
    """`maestro [--device <serial>] test <flow_file> --format junit --output <junit_out>`.

    Maestro's console output goes to `<junit_out>.log` so a `flow-broken` result names the
    failing command instead of just a return code.
    """
    cmd = [maestro_binary()]
    if serial:
        cmd.extend(["--device", serial])
    cmd.extend(["test", str(flow_file), "--format", "junit", "--output", str(junit_out)])
    env = dict(os.environ)
    env.setdefault("MAESTRO_CLI_NO_ANALYTICS", "1")
    env.setdefault("MAESTRO_CLI_ANALYSIS_NOTIFICATION_DISABLED", "true")
    try:
        res = subprocess.run(
            cmd, capture_output=True, text=True, check=False, env=env, timeout=MAESTRO_TIMEOUT_S
        )
        rc, out, err = res.returncode, res.stdout, res.stderr
    except subprocess.TimeoutExpired as exc:
        rc = 124
        out = exc.stdout.decode() if isinstance(exc.stdout, bytes) else (exc.stdout or "")
        err = f"maestro timed out after {MAESTRO_TIMEOUT_S}s"
    junit_out.with_suffix(".log").write_text(f"$ {' '.join(cmd)}\n{out}\n{err}", encoding="utf-8")
    return rc


def _maestro_failure_line(log_path: Path) -> str:
    """The most telling line of a Maestro log: the first failure marker, else the last line."""
    if not log_path.is_file():
        return "no maestro log"
    lines = [ln.strip() for ln in log_path.read_text(encoding="utf-8", errors="replace").splitlines() if ln.strip()]
    for ln in lines:
        if ln.startswith("[Failed]"):
            return ln[:240]
    for ln in lines:
        if "FAILED" in ln or "not found" in ln.lower() or "Exception" in ln or "Error" in ln:
            return ln[:240]
    return lines[-1][:240] if lines else "empty maestro log"


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
    label: str | None = None,
) -> tuple[str, list[Finding]]:
    """Run one `(journey, config)` pair on Android and return `(result, findings)`.

    `label` names the run directory and the findings' `config` (default: `config_name`); the
    orchestrator passes `<config>@<device>` so several devices can run the same config.
    """
    r = root or repo_root()
    m_fn = maestro_fn or default_maestro_runner
    product = str(manifest["product"])
    journey_id = str(manifest["id"])
    fixture_set = str(manifest["fixture"])
    app_id = "com.phototok.lab" if product == "phototok" else "com.photoselectortoolbox.lab"

    cfg_label = label or config_name
    out_dir = run_dir / product / cfg_label / journey_id
    out_dir.mkdir(parents=True, exist_ok=True)

    saved_state = record_device_state(adb)
    rules = load_rules(product)
    overlay_ids = {str(x) for x in rules.get("overlay_ids", [])}
    findings: list[Finding] = []
    result_status = "pass"
    broken_reason: str | None = None

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
        # Every journey's fixture goes into the same folder, so the app's one persisted SAF
        # grant (to PSTLab/work) stays valid across journeys; the app is stopped first so it
        # re-reads the folder on launch instead of showing the previous journey's photos.
        adb.force_stop(app_id)
        # A flow that broke inside the system folder picker leaves it on top of the next journey.
        for picker in PICKER_PACKAGES:
            adb.force_stop(picker)
        remote_fixture = REMOTE_WORK_DIR
        adb.rm_rf(remote_fixture)
        adb.push_dir(local_stage, remote_fixture)

        initial_files = adb.list_files(remote_fixture)
        initial_fs = {"root": REMOTE_WORK_ROOT, "files": initial_files, "trash": []}
        (out_dir / "00_initial.fs.json").write_text(
            json.dumps(initial_fs, indent=2), encoding="utf-8"
        )

        if manifest.get("fresh_install"):
            adb.clear(app_id, user=user_id)
        adb.start(app_id)
        cfg_spec = load_android_configs().get(config_name, {})
        try:
            mismatch = window_mode_mismatch(adb, app_id, cfg_spec)
            if mismatch:
                raise ConfigNotRunError(mismatch)
            if cfg_spec.get("window"):
                resize_app_window(adb, app_id, str(cfg_spec["window"]))
        except ConfigNotRunError as exc:
            res_obj = {"status": "not-run", "reason": str(exc), "findings": 0}
            (out_dir / "result.json").write_text(json.dumps(res_obj, indent=2), encoding="utf-8")
            return "not-run", []

        ensure_flow = r / "products" / "android" / product / "tests" / "e2e" / ENSURE_FOLDER_FLOW
        if not manifest.get("fresh_install") and ensure_flow.is_file():
            junit_out = out_dir / "maestro_00_ensure_folder.xml"
            if m_fn(adb.serial, ensure_flow, junit_out) != 0:
                result_status = "flow-broken"
                broken_reason = f"{ENSURE_FOLDER_FLOW}: {_maestro_failure_line(junit_out.with_suffix('.log'))}"

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
            fs_obj = {"root": REMOTE_WORK_ROOT, "files": cur_files, "trash": []}
            fs_path.write_text(json.dumps(fs_obj, indent=2), encoding="utf-8")
            timing_path.write_text(json.dumps(timing_obj, indent=2), encoding="utf-8")

            ev_rel = f"{product}/{cfg_label}/{journey_id}/{stem}.png"
            l_expect = (manifest.get("layout_expect") or {}).get(cp_name)
            findings.extend(
                lint(
                    tree,
                    rules,
                    l_expect,
                    product=product,
                    config=cfg_label,
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
                    config=cfg_label,
                    journey=journey_id,
                    checkpoint=cp_name,
                    evidence=[ev_rel],
                )
            )
            prev_t = find_previous_timing(run_dir, product, cfg_label, journey_id, stem)
            findings.extend(
                evaluate_timing(
                    timing_obj,
                    prev_t,
                    product=product,
                    config=cfg_label,
                    journey=journey_id,
                    checkpoint=cp_name,
                    evidence=[ev_rel],
                )
            )

        for step in manifest.get("steps", []) if result_status == "pass" else []:
            if "maestro" in step:
                seg_index += 1
                flow_rel = Path(str(step["maestro"]))
                flow_path = flow_rel if flow_rel.is_absolute() else (r / flow_rel)
                junit_out = out_dir / f"maestro_{seg_index:02d}.xml"
                rc = m_fn(adb.serial, flow_path, junit_out)
                if rc != 0:
                    result_status = "flow-broken"
                    broken_reason = f"{flow_rel.name} rc={rc}: {_maestro_failure_line(junit_out.with_suffix('.log'))}"
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
            elif "resize" in step:
                resize_app_window(adb, app_id, str(step["resize"]))
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

        if broken_reason and broken_reason.startswith(ENSURE_FOLDER_FLOW):
            capture_checkpoint("broken")

        log_path = out_dir / "logcat.txt"
        adb.logcat_dump(log_path)
        log_text = log_path.read_text(encoding="utf-8", errors="replace")
        findings.extend(
            scan_android_log(
                log_text,
                app_id,
                product=product,
                config=cfg_label,
                journey=journey_id,
                checkpoint="logs",
                evidence=[f"{product}/{cfg_label}/{journey_id}/logcat.txt"],
            )
        )

        if result_status == "pass" and any(f.severity in ("blocker", "major") for f in findings):
            result_status = "fail"

        res_obj: dict[str, Any] = {
            "status": result_status,
            "findings": len(findings),
        }
        if broken_reason:
            res_obj["reason"] = broken_reason
        (out_dir / "result.json").write_text(json.dumps(res_obj, indent=2), encoding="utf-8")
        return result_status, findings
    finally:
        reset_config(adb, saved_state)
