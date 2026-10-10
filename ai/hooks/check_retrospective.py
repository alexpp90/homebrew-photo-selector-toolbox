#!/usr/bin/env python3
"""Stop guard: don't let a session end without the retrospective.

Verifies that when product source was modified:
1. Pre-delivery intent and test suites are verified via verify_intent_delivery.
2. Observable outcomes are present: REQUIREMENTS.md sync, memory/playbook updates,
   and Agent Report summary.

The insecure /tmp single-retry bypass has been completely eliminated.
Emergency escape hatch: set PST_SKIP_HOOKS=1.
"""

from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from hooklib import bypassed, git, read_payload, stop_allow, stop_block  # noqa: E402
from verify_intent_delivery import verify_session  # noqa: E402

PRODUCT_PREFIXES = ("products/",)
REQUIREMENTS_HINT = "REQUIREMENTS.md"
MEMORY_PREFIX = "ai/memory/"
FRAMEWORK_PREFIXES = ("ai/agents/", "ai/skills/", "ai/hooks/", "ai/commands/", "ai/rules/")


def changed_files() -> list[str]:
    # -uall is required: plain --porcelain collapses untracked directories to "docs/",
    # which would hide a newly created REQUIREMENTS.md or ai/memory/ entry.
    out = git("status", "--porcelain", "-uall")
    files: list[str] = []
    for line in out.splitlines():
        if len(line) > 3:
            path = line[3:].strip()
            if " -> " in path:  # rename
                path = path.split(" -> ", 1)[1]
            files.append(path.strip('"'))
    return files


def main() -> int:
    payload = read_payload()
    if bypassed():
        stop_allow(payload)

    # Antigravity may stop with background work still running; wait for the real end.
    if payload.raw.get("fullyIdle") is False:
        stop_allow(payload)
    # Claude Code re-enters Stop after a block; never block twice.
    if payload.raw.get("stop_hook_active"):
        stop_allow(payload)

    # 1. Prerequisite: Pre-Delivery Intent & Test Execution Gate
    passed, intent_reason = verify_session(payload)
    if not passed:
        stop_block(payload, intent_reason)

    files = changed_files()
    if not files:
        stop_allow(payload)

    touched_product = [f for f in files if f.startswith(PRODUCT_PREFIXES)]
    if not touched_product:
        stop_allow(payload)

    synced_requirements = any(REQUIREMENTS_HINT in f for f in files)
    wrote_memory = any(f.startswith(MEMORY_PREFIX) for f in files)
    touched_framework = any(f.startswith(FRAMEWORK_PREFIXES) for f in files)

    # Retrospective check: must have at least one observable lifecycle outcome
    if not (synced_requirements or wrote_memory or touched_framework):
        sample = "\n".join(f"  - {f}" for f in touched_product[:8])
        more = f"\n  … and {len(touched_product) - 8} more" if len(touched_product) > 8 else ""

        stop_block(payload, (
            f"Retrospective not run. This session changed product source:\n{sample}{more}\n\n"
            f"but none of its expected outcomes are present — no REQUIREMENTS.md update, no "
            f"ai/memory/ entry, no framework fix.\n\n"
            f"Run the `retrospective` skill (ai/skills/retrospective/SKILL.md) now:\n"
            f"  1. ./scripts/run_tests.sh — and name every ⊘ SKIPPED gate in your summary\n"
            f"  2. sync REQUIREMENTS.md if observable behaviour changed\n"
            f"  3. reflect, then consult @shared-mentor-agent before writing any memory\n"
            f"  4. file out-of-scope debt in ai/memory/code_health.md\n"
            f"  5. create or improve a playbook if this task type will recur\n"
            f"  6. include the Agent Report table in your final task summary.\n\n"
            f"Emergency Manual Escape: Set PST_SKIP_HOOKS=1 if overriding manually."
        ))

    stop_allow(payload)
    return 0


if __name__ == "__main__":
    sys.exit(main())
