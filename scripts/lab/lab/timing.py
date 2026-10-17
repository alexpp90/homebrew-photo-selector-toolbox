"""Timing trend oracle comparing checkpoint `step_ms` against the previous run."""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any

from .model import Finding


def evaluate_timing(
    current_timing: dict[str, Any],
    previous_timing: dict[str, Any] | None,
    *,
    product: str = "android-desktop",
    config: str = "default",
    journey: str = "manual",
    checkpoint: str | None = None,
    evidence: list[str] | None = None,
) -> list[Finding]:
    """Compare `step_ms` against `previous_timing`; report `timing` (minor) on regression."""
    if previous_timing is None:
        return []
    cp = checkpoint or str(current_timing.get("checkpoint", "checkpoint"))
    cur_ms = float(current_timing.get("step_ms", 0))
    prev_ms = float(previous_timing.get("step_ms", 0))
    if prev_ms <= 0:
        return []
    if cur_ms > 1.5 * prev_ms and (cur_ms - prev_ms) > 300.0:
        fid = Finding.make_id(product, journey, cp, "timing-regression", [cp])
        return [
            Finding(
                id=fid,
                source="timing",
                product=product,
                config=config,
                journey=journey,
                checkpoint=cp,
                rule="timing-regression",
                severity="minor",
                confidence="high",
                region=None,
                message=f"step_ms regressed from {prev_ms:.0f} ms to {cur_ms:.0f} ms "
                f"(+{cur_ms - prev_ms:.0f} ms, {cur_ms / prev_ms:.2f}x)",
                evidence=list(evidence or []),
                new_since_last_run=True,
            )
        ]
    return []


def find_previous_timing(
    run_dir: Path,
    product: str,
    config: str,
    journey: str,
    stem: str,
) -> dict[str, Any] | None:
    """Find the most recent previous run directory that has `<product>/<config>/<journey>/<stem>.timing.json`."""
    parent = run_dir.parent
    if not parent.is_dir():
        return None
    candidates = sorted(
        [d for d in parent.iterdir() if d.is_dir() and d.name < run_dir.name],
        key=lambda d: d.name,
        reverse=True,
    )
    for prev_dir in candidates:
        tfile = prev_dir / product / config / journey / f"{stem}.timing.json"
        if tfile.is_file():
            try:
                return json.loads(tfile.read_text(encoding="utf-8"))
            except (OSError, json.JSONDecodeError):
                continue
    return None
