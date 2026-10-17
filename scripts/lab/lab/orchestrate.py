"""End-to-end device lab orchestrator across Android, macOS, and Linux (C-60)."""

from __future__ import annotations

import datetime as dt
import json
import subprocess
import sys
from pathlib import Path
from typing import Any, Callable

import yaml

from .android.adb import AdbClient
from .android.runner import run_android_journey
from .linux.runner import preflight_linux, run_linux_journey
from .macos.remote import preflight_remote_mac, trigger_remote_mac_run
from .macos.runner import default_app_binary, run_macos_journey
from .model import Finding
from .paths import ensure_under, new_run_dir, repo_root
from .report import generate_report

ALL_PRODUCTS = ["android-desktop", "phototok", "macos", "linux"]


def get_git_commit() -> str:
    try:
        res = subprocess.run(
            ["git", "-C", str(repo_root()), "rev-parse", "--short", "HEAD"],
            capture_output=True,
            text=True,
            check=False,
        )
        commit = (res.stdout or "").strip()
        if len(commit) >= 7:
            return commit
    except Exception:
        pass
    return "0000000"


def load_configs_for_product(product: str) -> dict[str, dict[str, Any]]:
    cfg_dir = repo_root() / "scripts" / "lab" / "configs"
    cfg_file = cfg_dir / ("android.yaml" if product in ("android-desktop", "phototok") else f"{product}.yaml")
    if not cfg_file.is_file():
        return {}
    data = yaml.safe_load(cfg_file.read_text(encoding="utf-8"))
    return data if isinstance(data, dict) else {}


def load_journeys_for_product(
    product: str,
    journey_filter: str | None = None,
) -> list[dict[str, Any]]:
    journeys: list[dict[str, Any]] = []
    j_dir = repo_root() / "scripts" / "lab" / "journeys"
    if not j_dir.is_dir():
        return journeys
    for ypath in sorted(j_dir.glob("*.yaml")):
        data = yaml.safe_load(ypath.read_text(encoding="utf-8"))
        if not isinstance(data, dict) or data.get("product") != product:
            continue
        jid = str(data.get("id", ""))
        if journey_filter and jid != journey_filter and not jid.startswith(journey_filter):
            continue
        journeys.append(data)
    return journeys


def _record_not_run(
    run_dir: Path,
    product: str,
    journeys: list[dict[str, Any]],
    config_filter: str | None,
    reason: str,
) -> list[dict[str, Any]]:
    records: list[dict[str, Any]] = []
    for j in journeys:
        jid = str(j["id"])
        for cfg in j.get("configs", []):
            if config_filter and cfg != config_filter:
                continue
            j_dir = run_dir / product / cfg / jid
            ensure_under(j_dir, run_dir)
            j_dir.mkdir(parents=True, exist_ok=True)
            res = {
                "id": jid,
                "product": product,
                "config": cfg,
                "status": "not-run",
                "reason": reason,
                "duration_s": 0.0,
                "checkpoints": [],
            }
            (j_dir / "result.json").write_text(json.dumps(res, indent=2) + "\n", encoding="utf-8")
            records.append(
                {
                    "product": product,
                    "config": cfg,
                    "journey": jid,
                    "result": "not-run",
                    "reason": reason,
                    "dir": f"{product}/{cfg}/{jid}",
                }
            )
    return records


def _load_devices_inventory(devices_yaml: Path | None = None) -> dict[str, Any]:
    path = devices_yaml or (repo_root() / "private" / "lab" / "devices.yaml")
    if not path.is_file():
        return {}
    data = yaml.safe_load(path.read_text(encoding="utf-8"))
    return data if isinstance(data, dict) else {}


def run_lab(
    products: list[str],
    journey_filter: str | None = None,
    config_filter: str | None = None,
    out_dir: Path | None = None,
    installed: bool = False,
    devices_inventory: dict[str, Any] | None = None,
    preflight_overrides: dict[str, tuple[bool, str | None]] | None = None,
    runner_overrides: dict[str, Callable[..., tuple[str, list[Finding]]]] | None = None,
) -> Path:
    """Orchestrate a lab run across the requested products and generate report.html."""
    now = dt.datetime.now(dt.timezone.utc)
    commit = get_git_commit()

    if out_dir is None:
        run_dir = new_run_dir(commit, now=now)
    else:
        run_dir = out_dir.resolve()
        run_dir.mkdir(parents=True, exist_ok=True)

    inventory = devices_inventory if devices_inventory is not None else _load_devices_inventory()
    all_findings: list[Finding] = []
    products_meta: dict[str, Any] = {}
    journey_records: list[dict[str, Any]] = []

    for product in products:
        journeys = load_journeys_for_product(product, journey_filter)
        configs_map = load_configs_for_product(product)
        if not journeys:
            continue

        if preflight_overrides and product in preflight_overrides:
            ok, reason = preflight_overrides[product]
        elif product in ("android-desktop", "phototok"):
            dev_key = "tablet" if product == "android-desktop" else "phone"
            dev_cfg = inventory.get(dev_key, {})
            serial = str(dev_cfg.get("serial", ""))
            if not serial or serial.startswith(("R52X", "RF8X")):
                ok, reason = False, f"{dev_key} device not attached or serial not configured in devices.yaml"
            else:
                adb = AdbClient(serial=serial)
                try:
                    adb.ensure_ready()
                    ok, reason = True, None
                except Exception as exc:
                    ok, reason = False, str(exc)
        elif product == "macos":
            if sys.platform == "darwin":
                bin_path = default_app_binary()
                if bin_path.is_file():
                    ok, reason = True, None
                else:
                    ok, reason = False, f"macOS app binary not built at {bin_path}"
            else:
                mac_cfg = inventory.get("mac", {})
                host = str(mac_cfg.get("host", ""))
                user = str(mac_cfg.get("user", "pst-lab"))
                if not host:
                    ok, reason = False, "Mac host not configured in devices.yaml"
                else:
                    ok, reason = preflight_remote_mac(host, user=user)
        elif product == "linux":
            ok, reason = preflight_linux()
        else:
            ok, reason = False, f"Unknown product '{product}'"

        if not ok:
            products_meta[product] = {"status": "not-run", "reason": reason or "preflight failed"}
            journey_records.extend(
                _record_not_run(run_dir, product, journeys, config_filter, reason or "preflight failed")
            )
            continue

        products_meta[product] = {"status": "ran"}
        for j in journeys:
            jid = str(j["id"])
            for cfg_name in j.get("configs", []):
                if config_filter and cfg_name != config_filter:
                    continue
                cfg_spec = configs_map.get(cfg_name, {})

                if runner_overrides and product in runner_overrides:
                    status, f_list = runner_overrides[product](j, cfg_name, cfg_spec, run_dir)
                    all_findings.extend(f_list)
                elif product in ("android-desktop", "phototok"):
                    dev_key = "tablet" if product == "android-desktop" else "phone"
                    dev_cfg = inventory.get(dev_key, {})
                    adb = AdbClient(serial=str(dev_cfg.get("serial", "")))
                    status, f_list = run_android_journey(
                        manifest=j,
                        config_name=cfg_name,
                        run_dir=run_dir,
                        adb=adb,
                        user_id=int(dev_cfg.get("lab_user_id", 10)),
                    )
                    all_findings.extend(f_list)
                elif product == "macos":
                    if sys.platform == "darwin":
                        status, f_list = run_macos_journey(j, cfg_name, cfg_spec, run_dir)
                        all_findings.extend(f_list)
                    else:
                        mac_cfg = inventory.get("mac", {})
                        ok_rem, _ = trigger_remote_mac_run(
                            host=str(mac_cfg.get("host", "")),
                            run_id=run_dir.name,
                            commit=commit,
                            journeys=[jid],
                            configs=[cfg_name],
                            local_run_dir=run_dir,
                            user=str(mac_cfg.get("user", "pst-lab")),
                        )
                        status = "pass" if ok_rem else "not-run"
                elif product == "linux":
                    status, f_list = run_linux_journey(
                        j,
                        cfg_name,
                        cfg_spec,
                        run_dir,
                        installed=installed,
                    )
                    all_findings.extend(f_list)
                else:
                    status = "not-run"

                journey_records.append(
                    {
                        "product": product,
                        "config": cfg_name,
                        "journey": jid,
                        "result": status,
                        "dir": f"{product}/{cfg_name}/{jid}",
                    }
                )

    run_meta = {
        "started": now.isoformat(),
        "finished": dt.datetime.now(dt.timezone.utc).isoformat(),
        "commit": commit,
        "products": products_meta,
        "tools": {"python": sys.version.split()[0]},
        "journeys": journey_records,
    }
    (run_dir / "run.json").write_text(json.dumps(run_meta, indent=2) + "\n", encoding="utf-8")
    (run_dir / "findings.det.json").write_text(
        json.dumps([f.to_dict() for f in all_findings], indent=2) + "\n",
        encoding="utf-8",
    )

    _, html_path = generate_report(run_dir)
    print(str(html_path))
    return run_dir
