"""Linux Desktop journey runner under private Xvfb + D-Bus session (C-41, C-42)."""

from __future__ import annotations

import json
import os
import shutil
import subprocess
import sys
import time
from pathlib import Path
from typing import Any, Callable

from ..fixtures import generate_fixtures
from ..fs_oracle import evaluate_fs_oracle, snapshot_directory
from ..layout_lint import lint, load_rules
from ..logscan import scan_linux_log
from ..model import Finding, Tree
from ..paths import ensure_under, repo_root


def preflight_linux() -> tuple[bool, str | None]:
    """Verify Linux host prerequisites (Linux OS, Xvfb, xdotool, gapplication/gdbus)."""
    if sys.platform != "linux":
        return False, f"Linux Desktop requires Linux host (current: {sys.platform})"
    for binary in ("Xvfb", "xdotool", "dbus-run-session"):
        if shutil.which(binary) is None:
            return False, f"Missing required binary '{binary}' (install xvfb xdotool dbus-x11)"
    return True, None


def build_journey_commands(journey_manifest: dict[str, Any], out_dir: Path) -> list[str]:
    """Convert journey manifest steps into shell commands executed inside session.sh."""
    cmds: list[str] = []
    for step in journey_manifest.get("steps", []):
        if "keys" in step:
            for k in step["keys"]:
                from .keys import normalize_xdotool_key

                xk = normalize_xdotool_key(str(k))
                cmds.append(f"xdotool key --clearmodifiers {xk}")
                cmds.append("sleep 0.2")
        elif "wait_ms" in step:
            sec = max(0.05, float(step["wait_ms"]) / 1000.0)
            cmds.append(f"sleep {sec:.2f}")
        elif "checkpoint" in step:
            cp = str(step["checkpoint"])
            cmds.append(
                f"gapplication action org.photoselector.Linux lab-checkpoint \"'{cp}'\" || "
                f"gdbus call --session --dest org.photoselector.Linux "
                f"--object-path /org/photoselector/Linux "
                f"--method org.freedesktop.Application.ActivateAction lab-checkpoint \"[<\'{cp}\'>]\" \"{{}}\""
            )
            cmds.append("sleep 0.3")
    return cmds


def run_linux_journey(
    journey_manifest: dict[str, Any],
    config_name: str,
    config_spec: dict[str, Any],
    run_dir: Path,
    fixture_base_dir: Path | None = None,
    installed: bool = False,
    exec_fn: Callable[[list[str], dict[str, str]], subprocess.CompletedProcess[str]] | None = None,
) -> tuple[str, list[Finding]]:
    """Run a Linux Desktop journey inside a private Xvfb + D-Bus session."""
    product = "linux"
    r = repo_root()
    session_sh = r / "products" / "linux-desktop" / "tests" / "e2e" / "session.sh"
    jid = str(journey_manifest["id"])
    fset = str(journey_manifest.get("fixture") or journey_manifest.get("fixture_set") or "cull-basic")
    j_dir = run_dir / product / config_name / jid
    ensure_under(j_dir, run_dir)
    j_dir.mkdir(parents=True, exist_ok=True)

    fbase = (fixture_base_dir or Path("/tmp/pstlab")).resolve()
    fbase.mkdir(parents=True, exist_ok=True)
    lab_work = fbase / f"linux_{jid}_{config_name}"
    ensure_under(lab_work, fbase)
    if lab_work.exists():
        shutil.rmtree(lab_work)
    work_fixture = lab_work / fset
    generate_fixtures(fset, work_fixture, root=r)

    xdg_data = lab_work / "xdg-data"
    xdg_config = lab_work / "xdg-config"
    xdg_data.mkdir(parents=True, exist_ok=True)
    xdg_config.mkdir(parents=True, exist_ok=True)
    trash_files = xdg_data / "Trash" / "files"

    initial_fs = snapshot_directory(work_fixture, fbase, trash_dir=trash_files)
    (j_dir / "00_initial.fs.json").write_text(
        json.dumps(initial_fs, indent=2) + "\n",
        encoding="utf-8",
    )

    steps_script = j_dir / "steps.sh"
    step_lines = ["#!/usr/bin/env bash", "set -e"] + build_journey_commands(journey_manifest, j_dir)
    steps_script.write_text("\n".join(step_lines) + "\n", encoding="utf-8")
    steps_script.chmod(0o755)

    cmd = [
        str(session_sh),
        "--fixture",
        str(work_fixture),
        "--out",
        str(j_dir),
        "--lab-dir",
        str(lab_work),
        "--steps",
        str(steps_script),
        "--size",
        str(config_spec.get("size", "1920x1080")),
        "--scale",
        str(config_spec.get("scale", 1)),
    ]
    if config_spec.get("color_scheme"):
        cmd.extend(["--color-scheme", str(config_spec["color_scheme"])])
    if config_spec.get("text_scaling"):
        cmd.extend(["--text-scaling", str(config_spec["text_scaling"])])
    if installed:
        cmd.append("--installed")

    env = dict(os.environ)
    t0 = time.monotonic()
    if exec_fn is not None:
        proc = exec_fn(cmd, env)
    else:
        proc = subprocess.run(cmd, env=env, capture_output=True, text=True, check=False)
    duration_s = round(time.monotonic() - t0, 2)

    log_path = j_dir / "journey.log"
    log_text = (proc.stdout or "") + "\n" + (proc.stderr or "")
    if log_path.is_file():
        log_text = log_path.read_text(encoding="utf-8") + "\n" + log_text
    log_path.write_text(log_text, encoding="utf-8")

    after_fs = snapshot_directory(work_fixture, fbase, trash_dir=trash_files)
    checkpoints: list[str] = []
    for step in journey_manifest.get("steps", []):
        if "checkpoint" in step:
            checkpoints.append(str(step["checkpoint"]))

    for idx, cp_name in enumerate(checkpoints, start=1):
        prefix = f"{idx:02d}"
        fs_p = j_dir / f"{prefix}_{cp_name}.fs.json"
        if not fs_p.exists():
            fs_path_data = json.dumps(after_fs, indent=2) + "\n"
            fs_p.write_text(fs_path_data, encoding="utf-8")

    status = "pass" if proc.returncode == 0 else "flow-broken"
    rules_cfg = load_rules(product)
    layout_expect_all = journey_manifest.get("layout_expect", {})
    fs_expect_all = journey_manifest.get("fs_expect", {})

    findings: list[Finding] = []
    for idx, cp_name in enumerate(checkpoints, start=1):
        prefix = f"{idx:02d}"
        rel_shot = f"{product}/{config_name}/{jid}/{prefix}_{cp_name}.png"
        tree_p = j_dir / f"{prefix}_{cp_name}.tree.json"
        fs_p = j_dir / f"{prefix}_{cp_name}.fs.json"
        if tree_p.is_file():
            tree_obj = Tree.from_dict(json.loads(tree_p.read_text(encoding="utf-8")))
            findings.extend(
                lint(
                    tree_obj,
                    rules_cfg,
                    layout_expect_all.get(cp_name),
                    product=product,
                    config=config_name,
                    journey=jid,
                    checkpoint=cp_name,
                    evidence=[rel_shot],
                )
            )
        if fs_p.is_file() and cp_name in fs_expect_all:
            cur_fs = json.loads(fs_p.read_text(encoding="utf-8"))
            findings.extend(
                evaluate_fs_oracle(
                    cur_fs,
                    fs_expect_all[cp_name],
                    initial_fs=initial_fs,
                    product=product,
                    config=config_name,
                    journey=jid,
                    checkpoint=cp_name,
                    evidence=[rel_shot],
                )
            )

    findings.extend(scan_linux_log(log_text, product=product, config=config_name, journey=jid))
    if status == "pass" and any(f.severity in ("blocker", "major") for f in findings):
        status = "fail"

    res_obj = {
        "id": jid,
        "product": product,
        "config": config_name,
        "status": status,
        "duration_s": duration_s,
        "checkpoints": checkpoints,
        "findings": len(findings),
    }
    (j_dir / "result.json").write_text(json.dumps(res_obj, indent=2) + "\n", encoding="utf-8")
    return status, findings
