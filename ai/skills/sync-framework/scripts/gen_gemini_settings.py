#!/usr/bin/env python3
"""Generate .gemini/settings.json from the canonical agent definitions.

.gemini/settings.json is a build artifact of ai/agents/*.md. Hand-editing it is what let
its roster drift from the agent files; regenerate it instead.

Usage:
    python3 ai/skills/sync-framework/scripts/gen_gemini_settings.py           # write
    python3 ai/skills/sync-framework/scripts/gen_gemini_settings.py --check   # exit 1 on drift
"""

from __future__ import annotations

import json
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[4]
AGENTS = REPO / "ai" / "agents"
SETTINGS = REPO / ".gemini" / "settings.json"


def parse_frontmatter(path: Path) -> dict[str, str]:
    """Minimal YAML frontmatter reader: flat `key: value` pairs only.

    Deliberately not PyYAML — this must run with a bare interpreter in CI and in every
    agent tool's sandbox, without a dependency install step.
    """
    text = path.read_text(encoding="utf-8")
    if not text.startswith("---\n"):
        raise ValueError(f"{path}: missing YAML frontmatter")
    end = text.find("\n---\n", 3)
    if end == -1:
        raise ValueError(f"{path}: unterminated YAML frontmatter")

    fields: dict[str, str] = {}
    for line in text[4:end].split("\n"):
        if not line.strip() or line.lstrip().startswith("#"):
            continue
        if line[0].isspace() or ":" not in line:
            continue  # nested/continuation lines are not used by this framework
        key, _, value = line.partition(":")
        value = value.strip()
        if len(value) >= 2 and value[0] == value[-1] and value[0] in "\"'":
            value = value[1:-1]
        fields[key.strip()] = value
    return fields


def build() -> dict:
    agents: dict[str, dict] = {}
    for path in sorted(AGENTS.glob("*.md")):
        fm = parse_frontmatter(path)
        name = fm.get("name")
        if name != path.stem:
            raise ValueError(f"{path}: name '{name}' does not match filename stem '{path.stem}'")
        if not fm.get("description"):
            raise ValueError(f"{path}: missing description")

        entry: dict[str, object] = {"description": fm["description"]}
        if "tools" in fm:
            tools = [t.strip() for t in fm["tools"].split(",") if t.strip()]
            entry["tools"] = tools
            # Advisory agents must not be able to modify the repository. Mirror the
            # capability into a flag Gemini/Antigravity-style subagent configs understand.
            entry["enable_write_tools"] = any(t in tools for t in ("Edit", "Write"))
        if "model" in fm and fm["model"] != "inherit":
            entry["model"] = fm["model"]
        agents[name] = entry

    return {
        "_generated_by": "ai/skills/sync-framework/scripts/gen_gemini_settings.py",
        "_do_not_edit": "Edit ai/agents/*.md and regenerate. See the sync-framework skill.",
        "agents": agents,
    }


def main() -> int:
    check = "--check" in sys.argv
    generated = build()
    rendered = json.dumps(generated, indent=2, ensure_ascii=False) + "\n"

    if check:
        current = SETTINGS.read_text(encoding="utf-8") if SETTINGS.exists() else ""
        if current != rendered:
            print(
                "DRIFT: .gemini/settings.json does not match ai/agents/*.md\n"
                "  fix: python3 ai/skills/sync-framework/scripts/gen_gemini_settings.py",
                file=sys.stderr,
            )
            return 1
        print("OK: .gemini/settings.json is in sync")
        return 0

    SETTINGS.parent.mkdir(parents=True, exist_ok=True)
    SETTINGS.write_text(rendered, encoding="utf-8")
    print(f"wrote {SETTINGS.relative_to(REPO)} ({len(generated['agents'])} agents)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
