#!/usr/bin/env python3
"""PostToolUse hook: lint an edited Desktop Python file immediately.

Feedback, not enforcement. The CI mirror (scripts/run_tests.sh) runs
`flake8 src/ tests/` from products/desktop/ as a blocking gate; discovering those
findings only at /verify wastes a whole loop. This hook runs the same linter, with
the same config (products/desktop/.flake8), on just the file that changed, the
moment it changes.

Exit codes follow the PostToolUse contract: 0 = silence, 2 = stderr is shown to the
model so it can fix the findings while the file is still in context. A missing
flake8 is silence, never an error — the CI mirror remains the gate of record.

Android lint is Gradle-based and too slow for a per-edit loop; Kotlin stays covered
by run_tests.sh. Registered in .claude/settings.json (fires inside subagents too).
"""

from __future__ import annotations

import importlib.util
import shutil
import subprocess
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from hooklib import bypassed, read_payload, record_use, repo_root  # noqa: E402

DESKTOP = "products/desktop/"
LINT_ROOTS = (DESKTOP + "src/", DESKTOP + "tests/", DESKTOP + "benchmarks/")


def main() -> int:
    if bypassed():
        return 0
    payload = read_payload()
    if not payload.is_write:
        return 0

    path = payload.repo_relative()
    if not path.endswith(".py") or not path.startswith(LINT_ROOTS):
        return 0

    root = repo_root()
    abs_path = root / path
    if not abs_path.is_file():
        return 0
    exe = shutil.which("flake8")
    if exe:
        flake8 = [exe]
    elif importlib.util.find_spec("flake8") is not None:
        flake8 = [sys.executable, "-m", "flake8"]  # installed but not on PATH
    else:
        return 0  # advisory hook: no linter, no noise; CI mirror still gates

    try:
        result = subprocess.run(
            [*flake8, "--show-source", str(abs_path.relative_to(root / DESKTOP))],
            cwd=root / DESKTOP, capture_output=True, text=True, timeout=30,
        )
    except (OSError, subprocess.SubprocessError):
        return 0

    if result.returncode != 0:
        findings = (result.stdout or result.stderr).strip()
        print(
            f"flake8 findings in {path} (same gate scripts/run_tests.sh will enforce "
            f"— fix now while the file is in context):\n{findings}",
            file=sys.stderr,
        )
        record_use(payload, "findings")
        return 2
    record_use(payload, "clean")
    return 0


if __name__ == "__main__":
    sys.exit(main())
