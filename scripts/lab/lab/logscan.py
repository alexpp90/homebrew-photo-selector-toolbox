"""Platform log and crash report scanner for Android, macOS, and Linux."""

from __future__ import annotations

from datetime import datetime
from pathlib import Path

from .model import Finding


def _excerpt(lines: list[str], start_idx: int, max_lines: int = 20) -> str:
    return "\n".join(lines[start_idx: start_idx + max_lines]).strip()


def scan_android_log(
    log_text: str,
    app_id: str,
    *,
    product: str = "android-desktop",
    config: str = "default",
    journey: str = "manual",
    checkpoint: str = "logs",
    evidence: list[str] | None = None,
) -> list[Finding]:
    """Scan Android logcat output for crashes or ANRs matching `app_id`."""
    lines = log_text.splitlines()
    findings: list[Finding] = []
    ev = list(evidence or [])

    for idx, line in enumerate(lines):
        if f"ANR in {app_id}" in line:
            msg = _excerpt(lines, idx, 20)
            fid = Finding.make_id(product, journey, checkpoint, "log-anr", [app_id])
            findings.append(
                Finding(
                    id=fid,
                    source="log",
                    product=product,
                    config=config,
                    journey=journey,
                    checkpoint=checkpoint,
                    rule="log-anr",
                    severity="blocker",
                    confidence="high",
                    region=None,
                    message=msg,
                    evidence=ev,
                    new_since_last_run=True,
                )
            )
            break

    for idx, line in enumerate(lines):
        if "FATAL EXCEPTION" in line:
            window_text = "\n".join(lines[idx: idx + 25])
            if f"Process: {app_id}" in window_text or app_id in window_text:
                msg = _excerpt(lines, idx, 20)
                fid = Finding.make_id(product, journey, checkpoint, "log-crash", [app_id])
                findings.append(
                    Finding(
                        id=fid,
                        source="log",
                        product=product,
                        config=config,
                        journey=journey,
                        checkpoint=checkpoint,
                        rule="log-crash",
                        severity="blocker",
                        confidence="high",
                        region=None,
                        message=msg,
                        evidence=ev,
                        new_since_last_run=True,
                    )
                )
                break

    return findings


def scan_linux_log(
    log_text: str,
    *,
    product: str = "linux",
    config: str = "default",
    journey: str = "manual",
    checkpoint: str = "logs",
    evidence: list[str] | None = None,
) -> list[Finding]:
    """Scan Linux stderr output for Python tracebacks and GTK/GLib critical warnings."""
    lines = log_text.splitlines()
    findings: list[Finding] = []
    ev = list(evidence or [])

    for idx, line in enumerate(lines):
        if "Traceback (most recent call last)" in line:
            msg = _excerpt(lines, idx, 20)
            fid = Finding.make_id(product, journey, checkpoint, "log-traceback", ["traceback"])
            findings.append(
                Finding(
                    id=fid,
                    source="log",
                    product=product,
                    config=config,
                    journey=journey,
                    checkpoint=checkpoint,
                    rule="log-traceback",
                    severity="blocker",
                    confidence="high",
                    region=None,
                    message=msg,
                    evidence=ev,
                    new_since_last_run=True,
                )
            )
            break

    for idx, line in enumerate(lines):
        if "Gtk-CRITICAL" in line or "GLib-GObject-CRITICAL" in line:
            rule = "log-gtk-critical" if "Gtk-CRITICAL" in line else "log-glib-critical"
            msg = _excerpt(lines, idx, 20)
            fid = Finding.make_id(product, journey, checkpoint, rule, [rule])
            findings.append(
                Finding(
                    id=fid,
                    source="log",
                    product=product,
                    config=config,
                    journey=journey,
                    checkpoint=checkpoint,
                    rule=rule,
                    severity="minor",
                    confidence="high",
                    region=None,
                    message=msg,
                    evidence=ev,
                    new_since_last_run=True,
                )
            )
            break

    return findings


def scan_macos_crash_reports(
    reports_dir: Path | str,
    started_at: datetime | float,
    *,
    product: str = "macos",
    config: str = "default",
    journey: str = "manual",
    checkpoint: str = "logs",
) -> list[Finding]:
    """Scan `~/Library/Logs/DiagnosticReports` for `PhotoSelector*.ips` newer than `started_at`."""
    rdir = Path(reports_dir).expanduser()
    if not rdir.is_dir():
        return []
    threshold = started_at.timestamp() if isinstance(started_at, datetime) else float(started_at)
    findings: list[Finding] = []

    for ips in sorted(rdir.glob("PhotoSelector*.ips")):
        try:
            st = ips.stat()
        except OSError:
            continue
        if st.st_mtime >= threshold:
            lines = ips.read_text(encoding="utf-8", errors="replace").splitlines()
            msg = _excerpt(lines, 0, 20)
            fid = Finding.make_id(product, journey, checkpoint, "log-macos-crash", ["ips"])
            findings.append(
                Finding(
                    id=fid,
                    source="log",
                    product=product,
                    config=config,
                    journey=journey,
                    checkpoint=checkpoint,
                    rule="log-macos-crash",
                    severity="blocker",
                    confidence="high",
                    region=None,
                    message=msg,
                    evidence=[ips.name],
                    new_since_last_run=True,
                )
            )
    return findings
