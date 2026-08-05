#!/usr/bin/env python3
"""Validate the agent framework. Run as a gate from scripts/run_tests.sh.

Treats the framework as code: naming, registration, roster and cross-references must hold,
the same way tests must pass. The path check is the important one — it is what stops agent
instructions from rotting silently after a file move.

Usage:
    python3 ai/skills/sync-framework/scripts/validate_framework.py [-v]
"""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[4]
AGENTS = REPO / "ai" / "agents"
SKILLS = REPO / "ai" / "skills"
ROUTING = REPO / "ai" / "ROUTING.md"
GEMINI_SETTINGS = REPO / ".gemini" / "settings.json"

NAME_RE = re.compile(r"^[a-z0-9]+(-[a-z0-9]+)*$")

# Wordings an advisory agent uses to disclaim write access. Kept as a list rather than one
# regex so a new phrasing is a one-line addition.
ADVISORY_PHRASES = (
    "do not directly own or modify source files",
    "do not directly own source files",
    "no direct source code changes",
    "no implementation code changes",
    "you do not modify source files",
)
VERBOSE = "-v" in sys.argv or "--verbose" in sys.argv

errors: list[str] = []
checks = 0


def fail(msg: str) -> None:
    errors.append(msg)


def ok(msg: str) -> None:
    global checks
    checks += 1
    if VERBOSE:
        print(f"  ok  {msg}")


def parse_frontmatter(path: Path) -> dict[str, str]:
    text = path.read_text(encoding="utf-8")
    if not text.startswith("---\n"):
        raise ValueError("missing YAML frontmatter")
    end = text.find("\n---\n", 3)
    if end == -1:
        raise ValueError("unterminated YAML frontmatter")
    fields: dict[str, str] = {}
    for line in text[4:end].split("\n"):
        if not line.strip() or line.lstrip().startswith("#"):
            continue
        if line[0].isspace() or ":" not in line:
            continue
        key, _, value = line.partition(":")
        value = value.strip()
        if len(value) >= 2 and value[0] == value[-1] and value[0] in "\"'":
            value = value[1:-1]
        fields[key.strip()] = value
    return fields


# --------------------------------------------------------------------------- agents
def check_agents() -> set[str]:
    names: set[str] = set()
    for path in sorted(AGENTS.glob("*.md")):
        rel = path.relative_to(REPO)
        try:
            fm = parse_frontmatter(path)
        except ValueError as exc:
            fail(f"{rel}: {exc}")
            continue

        if not NAME_RE.match(path.stem):
            fail(f"{rel}: filename must be lowercase-hyphen (got '{path.stem}')")
        if fm.get("name") != path.stem:
            fail(f"{rel}: name '{fm.get('name')}' != filename stem '{path.stem}'")
        else:
            ok(f"{rel}: name matches stem")
        if not fm.get("description"):
            fail(f"{rel}: missing description")
        if not fm.get("tools"):
            fail(f"{rel}: missing 'tools:' — capability must be declared, not implied by prose")
        if not fm.get("model"):
            fail(f"{rel}: missing 'model:'")

        # An agent whose body disclaims write access must not hold write tools. The prose
        # and the frontmatter are two statements of one fact; this stops them diverging.
        body = path.read_text(encoding="utf-8").lower()
        disclaims = any(p in body for p in ADVISORY_PHRASES)
        tools = {t.strip() for t in fm.get("tools", "").split(",")}
        if disclaims and (tools & {"Edit", "Write"}):
            fail(
                f"{rel}: body disclaims write access but tools: grants Edit/Write — "
                f"either grant the capability in prose too, or drop it from tools:"
            )
        elif disclaims:
            ok(f"{rel}: advisory agent is read-only")

        names.add(path.stem)
    return names


# --------------------------------------------------------------------------- skills
def check_skills() -> set[str]:
    names: set[str] = set()
    for d in sorted(p for p in SKILLS.iterdir() if p.is_dir()):
        skill_file = d / "SKILL.md"
        rel = skill_file.relative_to(REPO)
        if not skill_file.exists():
            fail(f"{d.relative_to(REPO)}: no SKILL.md")
            continue
        if not NAME_RE.match(d.name):
            fail(f"{d.relative_to(REPO)}: directory must be lowercase-hyphen (got '{d.name}')")
        try:
            fm = parse_frontmatter(skill_file)
        except ValueError as exc:
            fail(f"{rel}: {exc}")
            continue
        if fm.get("name") != d.name:
            fail(f"{rel}: name '{fm.get('name')}' != directory '{d.name}'")
        else:
            ok(f"{rel}: name matches directory")
        if not fm.get("description"):
            fail(f"{rel}: missing description")
        names.add(d.name)
    return names


# --------------------------------------------------------------------------- symlinks
def check_symlinks() -> None:
    expected = {
        ".claude/agents": "ai/agents",
        ".claude/skills": "ai/skills",
        ".claude/hooks": "ai/hooks",
        ".claude/commands": "ai/commands",
        ".agents/skills": "ai/skills",
        ".agents/workflows": "ai/commands",
        ".agents/rules": "ai/rules",
    }
    for link, target in expected.items():
        p = REPO / link
        if not p.is_symlink():
            fail(f"{link}: expected a symlink to {target}")
        elif not p.exists():
            fail(f"{link}: broken symlink")
        else:
            ok(f"{link} -> {target}")

    for root in (".gemini/agents", ".gemini/skills"):
        base = REPO / root
        if not base.is_dir():
            fail(f"{root}: missing")
            continue
        for entry in sorted(base.iterdir()):
            if not entry.is_symlink():
                fail(f"{root}/{entry.name}: expected a symlink into ai/")
            elif not entry.exists():
                fail(f"{root}/{entry.name}: broken symlink")
    ok(".gemini symlink trees resolve")


def check_mirror_completeness(agent_names: set[str], skill_names: set[str]) -> None:
    linked = {p.stem for p in (REPO / ".gemini" / "agents").glob("*.md")}
    for missing in sorted(agent_names - linked):
        fail(f".gemini/agents/{missing}.md: missing symlink for agent '{missing}'")
    for extra in sorted(linked - agent_names):
        fail(f".gemini/agents/{extra}.md: symlink for unknown agent '{extra}'")

    linked_skills = {p.name for p in (REPO / ".gemini" / "skills").iterdir()}
    for missing in sorted(skill_names - linked_skills):
        fail(f".gemini/skills/{missing}: missing symlink for skill '{missing}'")
    for extra in sorted(linked_skills - skill_names):
        fail(f".gemini/skills/{extra}: symlink for unknown skill '{extra}'")
    ok("mirror trees match the canonical sets")


# --------------------------------------------------------------------------- roster
def check_roster(agent_names: set[str]) -> None:
    if not ROUTING.exists():
        fail("ai/ROUTING.md: missing")
        return
    text = ROUTING.read_text(encoding="utf-8")
    mentioned = set(re.findall(r"@([a-z0-9]+(?:-[a-z0-9]+)*-agent)", text))
    for missing in sorted(agent_names - mentioned):
        fail(f"ai/ROUTING.md: agent '{missing}' exists but is not routed")
    for unknown in sorted(mentioned - agent_names):
        fail(f"ai/ROUTING.md: routes to '{unknown}', which has no ai/agents/ file")
    ok("ROUTING.md roster matches ai/agents/")


def check_gemini_settings(agent_names: set[str]) -> None:
    if not GEMINI_SETTINGS.exists():
        fail(".gemini/settings.json: missing")
        return
    try:
        data = json.loads(GEMINI_SETTINGS.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        fail(f".gemini/settings.json: invalid JSON ({exc})")
        return
    listed = set(data.get("agents", {}))
    if listed != agent_names:
        for missing in sorted(agent_names - listed):
            fail(f".gemini/settings.json: missing agent '{missing}'")
        for extra in sorted(listed - agent_names):
            fail(f".gemini/settings.json: unknown agent '{extra}'")
    else:
        ok(".gemini/settings.json roster matches")


# --------------------------------------------------------------------------- paths
PATH_RE = re.compile(r"`([A-Za-z0-9_./-]+/[A-Za-z0-9_./-]+)`")
# Placeholders and globs are intentionally un-checkable.
SKIP = re.compile(r"[<>*]|^https?:|^@")
# A path may be referenced before it exists, provided the same line says so. This keeps
# deliberate "write this when release work resumes" pointers from blocking the gate, while
# still catching paths that rotted silently.
PLANNED = re.compile(
    r"not yet (?:been )?(?:written|created)|does not exist yet|"
    r"\bplanned\b|\bto be (?:written|created)\b|create (?:it|them) when",
    re.I,
)


def check_referenced_paths() -> None:
    """Every repo path named in an agent or skill body must exist, or be marked planned.

    This is the check that catches instructions rotting after a file move — the class of
    defect that had agents reading a root REQUIREMENTS.md that no longer existed.
    """
    docs = sorted(AGENTS.glob("*.md")) + sorted(SKILLS.glob("*/SKILL.md"))
    seen: set[tuple[str, str]] = set()
    planned = 0
    for doc in docs:
        rel = doc.relative_to(REPO)
        for line in doc.read_text(encoding="utf-8").split("\n"):
            for raw in PATH_RE.findall(line):
                if SKIP.search(raw):
                    continue
                candidate = raw.rstrip("/")
                # Only validate things that look like in-repo paths.
                head = candidate.split("/", 1)[0]
                if not (REPO / head).exists():
                    continue
                if (REPO / candidate).exists():
                    continue
                key = (str(rel), candidate)
                if key in seen:
                    continue
                seen.add(key)
                if PLANNED.search(line):
                    planned += 1
                    if VERBOSE:
                        print(f"  --  {rel}: '{candidate}' marked planned")
                    continue
                fail(f"{rel}: references '{candidate}', which does not exist")
    ok(f"referenced repository paths resolve ({planned} marked planned)")


MENTION_RE = re.compile(r"@([a-z0-9]+(?:[-_][a-z0-9]+)*_agent|[a-z0-9]+(?:-[a-z0-9]+)*-agent)")


def check_mentions(agent_names: set[str]) -> None:
    """Every @agent mentioned anywhere in the framework must exist.

    Catches delegation pointers left behind by a rename or a roster change — e.g. a
    consultant still handing work to an agent that was split in two.
    """
    docs = (
        sorted(AGENTS.glob("*.md"))
        + sorted(SKILLS.glob("*/SKILL.md"))
        + sorted((REPO / "ai" / "commands").glob("*.md"))
        + sorted((REPO / "ai" / "rules").glob("*.md"))
        + [REPO / "AGENTS.md", REPO / "CLAUDE.md", REPO / "GEMINI.md", ROUTING]
    )
    seen: set[tuple[str, str]] = set()
    for doc in docs:
        if not doc.exists():
            continue
        rel = doc.relative_to(REPO)
        for mention in MENTION_RE.findall(doc.read_text(encoding="utf-8")):
            if mention in agent_names:
                continue
            key = (str(rel), mention)
            if key in seen:
                continue
            seen.add(key)
            fail(f"{rel}: mentions '@{mention}', which has no ai/agents/ file")
    ok("every @agent mention resolves")


def main() -> int:
    agent_names = check_agents()
    skill_names = check_skills()
    check_symlinks()
    check_mirror_completeness(agent_names, skill_names)
    check_roster(agent_names)
    check_gemini_settings(agent_names)
    check_referenced_paths()
    check_mentions(agent_names)

    print(
        f"framework validation: {len(agent_names)} agents, {len(skill_names)} skills, "
        f"{checks} checks"
    )
    if errors:
        print(f"\nFAILED ({len(errors)}):", file=sys.stderr)
        for e in errors:
            print(f"  - {e}", file=sys.stderr)
        print(
            "\nfix at the canonical source under ai/, then rerun. See the sync-framework skill.",
            file=sys.stderr,
        )
        return 1
    print("framework validation: OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())
