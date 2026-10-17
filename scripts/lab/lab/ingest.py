"""Ingest AI review outputs, de-duplicate, and manage known findings (C-21, C-22)."""

from __future__ import annotations

import datetime as dt
import json
import re
import sys
from pathlib import Path
from typing import Any

import yaml

from .model import Finding
from .paths import ensure_under, repo_root
from .report import generate_report
from .schema import validate


def extract_findings_from_raw(raw_path: Path, out_path: Path) -> list[dict[str, Any]]:
    """Extract JSON array from raw LLM output, validate each item, and write findings.json (C-21)."""
    text = raw_path.read_text(encoding="utf-8")
    start = text.find("[")
    end = text.rfind("]")
    if start == -1 or end == -1 or end < start:
        out_path.write_text("[]\n", encoding="utf-8")
        return []

    snippet = text[start : end + 1]
    try:
        parsed = json.loads(snippet)
    except json.JSONDecodeError as exc:
        print(f"warning: failed to parse JSON array from {raw_path}: {exc}", file=sys.stderr)
        out_path.write_text("[]\n", encoding="utf-8")
        return []

    if not isinstance(parsed, list):
        out_path.write_text("[]\n", encoding="utf-8")
        return []

    valid_items: list[dict[str, Any]] = []
    for idx, item in enumerate(parsed):
        if not isinstance(item, dict):
            continue
        normalized = dict(item)
        normalized.setdefault("source", "ai-review")
        normalized.setdefault("severity", "major")
        normalized.setdefault("confidence", "high")
        normalized.setdefault("product", "android-desktop")
        normalized.setdefault("config", "unknown")
        normalized.setdefault("journey", "unknown")
        normalized.setdefault("checkpoint", "unknown")
        normalized.setdefault("rule", "ai:mismatch")
        normalized.setdefault("region", None)
        if "message" not in normalized:
            normalized["message"] = str(normalized.pop("summary", "AI review finding"))
        if "evidence" not in normalized:
            shot = normalized.pop("screenshot", None)
            normalized["evidence"] = [str(shot)] if shot else []
        normalized.setdefault("new_since_last_run", True)
        if "id" not in normalized or not re.match(r"^[0-9a-f]{12}$", str(normalized.get("id", ""))):
            normalized["id"] = Finding.make_id(
                str(normalized["product"]),
                str(normalized["journey"]),
                str(normalized["checkpoint"]),
                str(normalized["rule"]),
                normalized.get("region"),
            )
        errs = validate(normalized, "finding")
        if errs:
            print(f"warning: dropping invalid finding #{idx} in {raw_path}: {errs}", file=sys.stderr)
            continue
        valid_items.append(normalized)

    out_path.write_text(json.dumps(valid_items, indent=2) + "\n", encoding="utf-8")
    return valid_items


def _load_active_known_findings(known_path: Path, today: dt.date | None = None) -> dict[str, dict[str, Any]]:
    today = today or dt.date.today()
    if not known_path.is_file():
        return {}
    raw = yaml.safe_load(known_path.read_text(encoding="utf-8"))
    if not isinstance(raw, list):
        return {}
    active: dict[str, dict[str, Any]] = {}
    for entry in raw:
        if not isinstance(entry, dict) or "id" not in entry:
            continue
        until_val = entry.get("until")
        if until_val:
            try:
                until_date = (
                    until_val
                    if isinstance(until_val, dt.date)
                    else dt.date.fromisoformat(str(until_val))
                )
                if until_date < today:
                    continue
            except ValueError:
                pass
        active[str(entry["id"])] = entry
    return active


def _find_previous_run_ids(run_dir: Path) -> set[str]:
    parent = run_dir.parent
    if not parent.is_dir():
        return set()
    siblings = sorted(
        d for d in parent.iterdir() if d.is_dir() and (d / "run.json").is_file() and d.name < run_dir.name
    )
    if not siblings:
        return set()
    prev_dir = siblings[-1]
    prev_ids: set[str] = set()
    for fname in ("findings.det.json", "findings.ai.json"):
        fpath = prev_dir / fname
        if fpath.is_file():
            try:
                items = json.loads(fpath.read_text(encoding="utf-8"))
                for item in items:
                    if isinstance(item, dict) and "id" in item:
                        prev_ids.add(str(item["id"]))
            except Exception:
                pass
    return prev_ids


def ingest_review_bundles(
    run_dir: Path,
    private_lab_dir: Path | None = None,
    today: dt.date | None = None,
) -> list[Finding]:
    """Ingest review/bundle-*/findings.json into <run_dir>/findings.ai.json and re-render report (C-22)."""
    run_dir = run_dir.resolve()
    priv_dir = private_lab_dir or (repo_root() / "private" / "lab")
    known_path = priv_dir / "review" / "known_findings.yaml"
    active_known = _load_active_known_findings(known_path, today=today)
    prev_ids = _find_previous_run_ids(run_dir)

    # Write known_ids.json in run_dir so report.py knows which IDs are triaged
    (run_dir / "known_ids.json").write_text(
        json.dumps(sorted(active_known.keys()), indent=2) + "\n",
        encoding="utf-8",
    )

    review_root = run_dir / "review"
    ingested_by_id: dict[str, Finding] = {}

    if review_root.is_dir():
        for b_dir in sorted(review_root.glob("bundle-*")):
            f_json = b_dir / "findings.json"
            if not f_json.is_file():
                continue
            manifest_map: dict[str, dict[str, Any]] = {}
            m_json = b_dir / "manifest.json"
            if m_json.is_file():
                for entry in json.loads(m_json.read_text(encoding="utf-8")):
                    manifest_map[entry["image"]] = entry
                    manifest_map[entry["image_basename"]] = entry

            raw_items = json.loads(f_json.read_text(encoding="utf-8"))
            if not isinstance(raw_items, list):
                continue

            for item in raw_items:
                if not isinstance(item, dict):
                    continue
                if item.get("confidence") == "low":
                    continue

                ev_list = item.get("evidence") or []
                shot_ref = str(item.get("screenshot") or (ev_list[0] if ev_list else ""))
                meta = manifest_map.get(shot_ref) or manifest_map.get(Path(shot_ref).name)
                product = meta["product"] if meta else str(item.get("product", "android-desktop"))
                config = meta["config"] if meta else str(item.get("config", "unknown"))
                journey = meta["journey"] if meta else str(item.get("journey", "unknown"))
                checkpoint = meta["checkpoint"] if meta else str(item.get("checkpoint", "unknown"))
                prefix = meta["prefix"] if meta else "01"

                region = item.get("region")
                scaled_region: list[float] | None = None
                if isinstance(region, list) and len(region) == 4:
                    rx, ry, rw, rh = [float(v) for v in region]
                    tree_path = run_dir / product / config / journey / f"{prefix}_{checkpoint}.tree.json"
                    if meta and tree_path.is_file():
                        try:
                            tree_data = json.loads(tree_path.read_text(encoding="utf-8"))
                            tw, th = float(tree_data["window"]["w"]), float(tree_data["window"]["h"])
                            bw, bh = float(meta["bundle_size"][0]), float(meta["bundle_size"][1])
                            if bw > 0 and bh > 0:
                                rx = rx * tw / bw
                                ry = ry * th / bh
                                rw = rw * tw / bw
                                rh = rh * th / bh
                        except Exception:
                            pass
                    scaled_region = [float(max(0, int(round(v)))) for v in (rx, ry, rw, rh)]

                rule = str(item.get("rule", "ai:mismatch"))
                fid = Finding.make_id(product, journey, checkpoint, rule, scaled_region)
                if fid in active_known:
                    continue

                rel_shot = f"{product}/{config}/{journey}/{prefix}_{checkpoint}.png"
                msg = str(item.get("message") or item.get("summary") or "AI review finding")
                finding = Finding(
                    id=fid,
                    source="ai-review",
                    product=product,
                    config=config,
                    journey=journey,
                    checkpoint=checkpoint,
                    rule=rule,
                    severity=str(item.get("severity", "major")),
                    confidence=str(item.get("confidence", "high")),
                    region=scaled_region,
                    message=msg,
                    evidence=[rel_shot],
                    new_since_last_run=(fid not in prev_ids),
                )
                ingested_by_id[fid] = finding

    findings_list = list(ingested_by_id.values())
    out_ai = run_dir / "findings.ai.json"
    ensure_under(out_ai, run_dir)
    out_ai.write_text(
        json.dumps([f.to_dict() for f in findings_list], indent=2) + "\n",
        encoding="utf-8",
    )
    generate_report(run_dir)
    return findings_list


def triage_finding(
    run_dir: Path,
    fid: str,
    reason: str,
    until: str = "2027-01-01",
    private_lab_dir: Path | None = None,
) -> Path:
    """Append a finding ID to known_findings.yaml and update report (C-22)."""
    priv_dir = private_lab_dir or (repo_root() / "private" / "lab")
    known_path = priv_dir / "review" / "known_findings.yaml"
    known_path.parent.mkdir(parents=True, exist_ok=True)
    existing: list[dict[str, Any]] = []
    if known_path.is_file():
        loaded = yaml.safe_load(known_path.read_text(encoding="utf-8"))
        if isinstance(loaded, list):
            existing = loaded
    existing = [e for e in existing if isinstance(e, dict) and e.get("id") != fid]
    existing.append({"id": fid, "reason": reason, "until": until})
    known_path.write_text(yaml.safe_dump(existing, sort_keys=False), encoding="utf-8")

    active_known = _load_active_known_findings(known_path)
    (run_dir / "known_ids.json").write_text(
        json.dumps(sorted(active_known.keys()), indent=2) + "\n",
        encoding="utf-8",
    )
    if (run_dir / "run.json").is_file():
        generate_report(run_dir)
    return known_path
