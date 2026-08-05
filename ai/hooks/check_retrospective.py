#!/usr/bin/env python3
"""Stop guard: don't let a session end without the retrospective.

The lifecycle used to be enforced by printing a reminder and hoping. This checks. If the
session changed product source but shows none of the retrospective's observable side
effects, the agent is sent back into the loop with the specific steps it still owes.

Deliberately evidence-based rather than perfect: it looks at the working tree, so it cannot
tell a thorough retrospective from a cursory one. It reliably catches the case that
actually happens — finishing without doing it at all.

Fires once per session; a second stop is allowed through so the agent can never be trapped.
"""

from __future__ import annotations

import os
import sys
import tempfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from hooklib import bypassed, git, read_payload, stop_allow, stop_block  # noqa: E402

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


def already_fired(conversation_id: str) -> bool:
    """One nudge per session. Without this the agent could be blocked indefinitely."""
    if not conversation_id:
        return False
    marker = Path(tempfile.gettempdir()) / f"pst-retro-{conversation_id}.marker"
    if marker.exists():
        return True
    try:
        marker.write_text("1", encoding="utf-8")
    except OSError:
        return True  # cannot track state -> fail open
    return False


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

    files = changed_files()
    if not files:
        stop_allow(payload)

    touched_product = [f for f in files if f.startswith(PRODUCT_PREFIXES)]
    if not touched_product:
        stop_allow(payload)

    synced_requirements = any(REQUIREMENTS_HINT in f for f in files)
    wrote_memory = any(f.startswith(MEMORY_PREFIX) for f in files)
    touched_framework = any(f.startswith(FRAMEWORK_PREFIXES) for f in files)

    if synced_requirements or wrote_memory or touched_framework:
        stop_allow(payload)

    conversation_id = (
        payload.raw.get("conversationId")
        or payload.raw.get("session_id")
        or os.environ.get("CLAUDE_SESSION_ID", "")
    )
    if already_fired(str(conversation_id)):
        stop_allow(payload)

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
        f"  5. file out-of-scope debt in ai/memory/code_health.md\n"
        f"  7. create or improve a playbook if this task type will recur\n\n"
        f"If a step genuinely does not apply, say so explicitly in your summary and finish — "
        f"this check will not fire again in this session."
    ))
    return 0


if __name__ == "__main__":
    sys.exit(main())
