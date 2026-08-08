#!/usr/bin/env python3
"""PreToolUse guard: block writes that violate the repository's structural rules.

Enforces mechanically what AGENTS.md previously only asked for. Each denial returns a
reason written so the model can correct itself and retry, rather than a bare refusal.

Registered for write tools in both .claude/settings.json and .agents/hooks.json.
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from hooklib import bypassed, deny, allow, read_payload  # noqa: E402

# Rule 6 — no scratch files, report dumps or PR-description drafts in the repository.
SCRATCH = [
    (re.compile(r"(^|/)scratch[^/]*\.(py|js|ts|kt|sh|txt|json|md)$", re.I),
     "scratch file"),
    (re.compile(r"(^|/)(pr_desc|pr_description|pr-body|pr_body)[^/]*$", re.I),
     "PR-description draft"),
    (re.compile(r"(^|/)(debug|tmp|temp|test_output|scratch)_[^/]*$", re.I),
     "temporary working file"),
    (re.compile(r"(^|/)[^/]*(_report|-report|_dump|-dump)\.(txt|json|log|csv|md)$", re.I),
     "report dump"),
    (re.compile(r"(^|/)(flake8|lint|coverage|analysis)[-_]?(report|out|output)[^/]*$", re.I),
     "lint/analysis output"),
]

# The canonical-source rule: these trees are symlinks into ai/.
MIRROR = re.compile(r"^\.(claude|gemini|agents)/")

# Real files that legitimately live inside the mirror directories: per-host hook
# registration is canonical *at* these paths (see ai/README.md), not mirrored from ai/.
MIRROR_REAL = {".claude/settings.json", ".agents/hooks.json"}

MIRROR_TARGET = {
    "agents": "ai/agents/", "skills": "ai/skills/", "hooks": "ai/hooks/",
    "commands": "ai/commands/", "workflows": "ai/commands/", "rules": "ai/rules/",
}

# Nothing product-specific lives at the repository root.
ROOT_ALLOWED = {
    "AGENTS.md", "CLAUDE.md", "GEMINI.md", "README.md", "CHANGELOG.md", "LICENSE",
    "CONTRIBUTING.md", "CODE_OF_CONDUCT.md", "SECURITY.md", "THIRDPARTY_NOTICES.txt",
    ".gitignore", ".firebaserc", ".gitattributes", ".editorconfig",
}
ROOT_CODE = re.compile(r"^[^/]+\.(py|kt|java|js|ts|ipynb)$", re.I)

# Generated artifacts must be regenerated, not hand-edited.
GENERATED = {
    ".gemini/settings.json":
        "Regenerate it: python3 ai/skills/sync-framework/scripts/gen_gemini_settings.py "
        "(edit ai/agents/*.md instead).",
}


def main() -> int:
    payload = read_payload()
    if bypassed() or not payload.is_write:
        allow(payload)

    path = payload.repo_relative()
    literal = payload.literal_relative()
    if not path or path.startswith("..") or path.startswith("/"):
        allow(payload)  # outside the repository: not ours to police

    # The mirror check must run on the path as written: .claude/, .gemini/ and .agents/ are
    # symlinks into ai/, so a resolved path never shows the violation.
    if MIRROR.match(literal) and literal not in MIRROR_REAL:
        top = literal.split("/")[1] if "/" in literal else ""
        target = MIRROR_TARGET.get(top)
        tail = literal.split("/", 2)[-1] if literal.count("/") >= 2 else ""
        hint = f" Edit {target}{tail} instead." if target else ""
        deny(payload, (
            f"Blocked: '{literal}' is inside a mirrored directory. Everything under .claude/, "
            f".gemini/ and .agents/ is a symlink to the canonical tree under ai/.{hint}\n"
            f"Edit the canonical file, then run: "
            f"python3 ai/skills/sync-framework/scripts/validate_framework.py"
        ))

    for pattern, kind in SCRATCH:
        if pattern.search(path):
            deny(payload, (
                f"Blocked: '{path}' looks like a {kind}. AGENTS.md rule 6 — no scratch files, "
                f"report dumps or PR-description drafts in the repository.\n"
                f"Benchmarks belong in products/desktop/benchmarks/. For genuinely temporary "
                f"work, write outside the repository (e.g. /tmp). If this file really is a "
                f"deliverable, give it a name that says so."
            ))

    if path in GENERATED:
        deny(payload, f"Blocked: '{path}' is a generated artifact. {GENERATED[path]}")

    if "/" not in path and path not in ROOT_ALLOWED and ROOT_CODE.match(path):
        deny(payload, (
            f"Blocked: '{path}' would put code at the repository root. Nothing "
            f"product-specific lives there — every product owns a directory under products/.\n"
            f"Desktop: products/desktop/src/ or products/desktop/scripts/. "
            f"Android: products/android/<product>/src/. "
            f"Cross-product tooling: scripts/."
        ))

    allow(payload)
    return 0


if __name__ == "__main__":
    sys.exit(main())
