"""Install the weekly systemd user timer that runs the lab unattended on this checkout."""

from __future__ import annotations

import os
import shutil
import subprocess
import sys
from pathlib import Path

from .paths import repo_root

UNIT = "pstlab-weekly"


def render_units(root: Path | None = None, path_env: str | None = None) -> tuple[str, str]:
    """Return `(service, timer)` unit text for the checkout at `root`.

    The service gets this shell's tool directories on `PATH` (adb, Maestro, Xvfb), because a
    systemd user unit does not read the shell rc files the SDK and Maestro installers edit.
    The weekly run uses dedicated devices only — never `--with-personal`.
    """
    r = (root or repo_root()).resolve()
    extra = [d for d in (
        str(Path(shutil.which("adb")).parent) if shutil.which("adb") else "",
        str(Path.home() / ".maestro" / "bin"),
    ) if d]
    path = ":".join(dict.fromkeys(extra + (path_env or os.environ.get("PATH", "/usr/bin:/bin")).split(":")))
    service = f"""[Unit]
Description=Photo Selector Toolbox weekly device lab run (Linux + dedicated Android devices)

[Service]
Type=oneshot
WorkingDirectory={r}
Environment=PATH={path}
Environment=MAESTRO_CLI_NO_ANALYTICS=1
ExecStartPre=-/usr/bin/git -C {r} pull --ff-only
ExecStart={sys.executable} scripts/lab/lab.py run --all
"""
    timer = """[Unit]
Description=Run Photo Selector Toolbox device lab every Sunday at 03:00

[Timer]
OnCalendar=Sun 03:00
Persistent=true

[Install]
WantedBy=timers.target
"""
    return service, timer


def install_timer(dry_run: bool = False) -> int:
    service, timer = render_units()
    if dry_run:
        print(f"# ~/.config/systemd/user/{UNIT}.service\n{service}\n# ~/.config/systemd/user/{UNIT}.timer\n{timer}")
        return 0
    unit_dir = Path.home() / ".config" / "systemd" / "user"
    unit_dir.mkdir(parents=True, exist_ok=True)
    (unit_dir / f"{UNIT}.service").write_text(service, encoding="utf-8")
    (unit_dir / f"{UNIT}.timer").write_text(timer, encoding="utf-8")
    for cmd in (["daemon-reload"], ["enable", "--now", f"{UNIT}.timer"]):
        res = subprocess.run(["systemctl", "--user", *cmd], check=False)
        if res.returncode != 0:
            return res.returncode
    subprocess.run(["systemctl", "--user", "list-timers", f"{UNIT}.timer"], check=False)
    return 0
