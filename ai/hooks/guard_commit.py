#!/usr/bin/env python3
"""PreToolUse guard on shell commands: catch what slips past the write guard.

Two things this stops that nothing else can:

1. Committing a scratch artifact that reached the index by some route other than a
   guarded write (git add -A after an untracked file appeared, a script that wrote it).
2. `git push` on a branch whose recent history is a chain of fix(ci)/fix(test) commits —
   the "push a guess and let CI adjudicate it" anti-pattern AGENTS.md names but could not
   previously prevent.

Registered for shell tools in both .claude/settings.json and .agents/hooks.json.
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from hooklib import allow, bypassed, deny, git, read_payload  # noqa: E402

GIT_COMMIT = re.compile(r"\bgit\s+(?:-\S+\s+|--\S+(?:=\S+)?\s+)*commit\b")
GIT_PUSH = re.compile(r"\bgit\s+(?:-\S+\s+|--\S+(?:=\S+)?\s+)*push\b")
NO_VERIFY = re.compile(r"--no-verify\b")
SPECULATIVE = re.compile(r"^fix\((?:ci|test|tests|build)\)", re.I)

# Writes through a mirrored directory that bypass the Write/Edit tools entirely.
MIRROR_WRITE = re.compile(
    r"(?:>>?|tee\s+|sed\s+-i[^|]*?|cp\s+[^|]*?|mv\s+[^|]*?)\s*\.?/?\.(claude|gemini|agents)/",
)

SCRATCH_STAGED = [
    re.compile(r"(^|/)scratch[^/]*\.", re.I),
    re.compile(r"(^|/)(pr_desc|pr_description|pr-body|pr_body)", re.I),
    re.compile(r"(^|/)(debug|tmp|temp|test_output)_", re.I),
    re.compile(r"(_report|-report|_dump|-dump)\.(txt|json|log|csv|md)$", re.I),
]


def staged_files() -> list[str]:
    return [p for p in git("diff", "--cached", "--name-only").splitlines() if p.strip()]


def recent_subjects(count: int = 3) -> list[str]:
    return [s for s in git("log", f"-{count}", "--format=%s").splitlines() if s.strip()]


def main() -> int:
    payload = read_payload()
    if bypassed() or not payload.is_shell:
        allow(payload)

    command = payload.command
    if not command:
        allow(payload)

    if MIRROR_WRITE.search(command):
        deny(payload, (
            "Blocked: this command writes into .claude/, .gemini/ or .agents/, which are "
            "symlinks to the canonical tree under ai/. Edit the file under ai/ instead, then "
            "run: python3 ai/skills/sync-framework/scripts/validate_framework.py"
        ))

    if GIT_COMMIT.search(command):
        offenders = [
            f for f in staged_files()
            if any(p.search(f) for p in SCRATCH_STAGED)
        ]
        if offenders:
            listing = "\n".join(f"  - {f}" for f in offenders)
            deny(payload, (
                f"Blocked: scratch artifacts are staged. AGENTS.md rule 6 — these must not be "
                f"committed:\n{listing}\n"
                f"Unstage them (git restore --staged <path>) and delete or move them. "
                f"Benchmarks belong in products/desktop/benchmarks/."
            ))
        if NO_VERIFY.search(command):
            deny(payload, (
                "Blocked: --no-verify skips the repository's commit checks. If a check is "
                "wrong, fix the check; if it is right, fix the change. Set PST_SKIP_HOOKS=1 "
                "deliberately if you have a genuine emergency."
            ))

    if GIT_PUSH.search(command):
        subjects = recent_subjects(3)
        speculative = [s for s in subjects if SPECULATIVE.match(s)]
        if len(speculative) >= 2:
            listing = "\n".join(f"  - {s}" for s in speculative)
            deny(payload, (
                f"Blocked: the last commits on this branch are speculative CI fixes:\n{listing}\n"
                f"This is the pattern AGENTS.md rule 5 exists to prevent — never push a guess "
                f"and let CI adjudicate it. Reproduce the failure locally first:\n"
                f"  ./scripts/run_tests.sh   (add --all with a device attached)\n"
                f"If the gate genuinely cannot run locally, docs/build/CI_PARITY.md explains "
                f"why and how to read the Actions log. Consider squashing the fix chain."
            ))

    allow(payload)
    return 0


if __name__ == "__main__":
    sys.exit(main())
