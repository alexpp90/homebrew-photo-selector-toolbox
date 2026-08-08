#!/usr/bin/env python3
"""Agent-scoped PreToolUse guard: keep a product agent inside its own product.

AGENTS.md rule 1 — "identify the product first, and stay inside it" — was prose-only
because hook payloads do not carry the invoking subagent's identity. Claude Code's
per-agent frontmatter hooks close that gap: each product agent registers this guard in
its own `hooks:` block and names its product on the command line, so no payload
introspection is needed.

Usage (in ai/agents/<agent>.md frontmatter):

    hooks:
      PreToolUse:
        - matcher: "Write|Edit|MultiEdit|NotebookEdit"
          hooks:
            - type: command
              command: "python3 \"$CLAUDE_PROJECT_DIR/ai/hooks/guard_scope.py\" <slug>"

Slugs: desktop, android-desktop, phototok, android-build (spans both Android
products), no-products (consultants that must never touch product source).

Claude Code only — Antigravity and Gemini have no per-subagent hooks, so there the
rule stays enforced by prose in AGENTS.md and review. Denials explain the routing so
the model can hand the work to the owning agent instead of retrying.
"""

from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from hooklib import allow, bypassed, deny, read_payload  # noqa: E402

# Directories each slug must never write into. A deny-list, not an allow-list: the
# lifecycle obliges every agent to touch docs/, ai/ and scripts/ (requirements sync,
# retrospective, framework fixes), so only the *other products' source* is off-limits.
FORBIDDEN: dict[str, tuple[str, ...]] = {
    "desktop": ("products/android/",),
    "android-desktop": ("products/desktop/", "products/android/phototok/"),
    "phototok": ("products/desktop/", "products/android/android-desktop/"),
    "android-build": ("products/desktop/",),
    "no-products": ("products/",),
}

# Who to hand the work to when a write is denied.
OWNERS = {
    "products/desktop/": "a desktop-* agent (see ai/ROUTING.md)",
    "products/android/android-desktop/": "@android-desktop-ui-agent or @android-desktop-core-agent",
    "products/android/phototok/": "@phototok-ui-agent or @phototok-core-agent",
    "products/android/": "the owning Android agent (see ai/ROUTING.md)",
    "products/": "the owning product agent (see ai/ROUTING.md)",
}


def main() -> int:
    payload = read_payload()
    if bypassed() or not payload.is_write:
        allow(payload)

    slug = sys.argv[1] if len(sys.argv) > 1 else ""
    forbidden = FORBIDDEN.get(slug)
    if forbidden is None:
        # Unknown slug: never brick the agent over a registration typo; the
        # validator catches bad slugs at build time.
        allow(payload)

    path = payload.repo_relative()
    if not path or path.startswith("..") or path.startswith("/"):
        allow(payload)  # outside the repository: not this guard's concern

    for prefix in forbidden:
        if path.startswith(prefix):
            owner = OWNERS.get(prefix, "the owning agent (see ai/ROUTING.md)")
            deny(payload, (
                f"Blocked: '{path}' is outside your product scope ('{slug}'). "
                f"AGENTS.md rule 1 — every agent stays inside its own product; copying "
                f"or editing across products is a defect, not reuse.\n"
                f"This file belongs to {owner}. If the feature is wanted in more than "
                f"one product, split it into one subtask per product and delegate per "
                f"ai/ROUTING.md."
            ))

    allow(payload)
    return 0


if __name__ == "__main__":
    sys.exit(main())
