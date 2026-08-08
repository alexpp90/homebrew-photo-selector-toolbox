#!/usr/bin/env python3
"""Stop hook: end-of-session report on which AI definitions were actually used.

Answers "did the framework engage?" whenever something major happened — an
implementation (working-tree changes under products/, docs/ or ai/) or an
implementation plan (plan-mode tool use in the transcript, or plan artifacts in
Antigravity's artifact directory). Trivial sessions produce no report.

What it reports, per source of evidence:
- Hooks     — from the per-session usage ledger every guard writes via hooklib.record_use.
- Agents    — Claude Code transcript: Task/Agent subagent invocations, plus reads of
              ai/agents/*.md (a role adopted in a no-subagent host).
- Skills    — Claude Code transcript: Skill invocations, plus reads of ai/skills/*/SKILL.md.
- Products  — git status, mapped to the owning product.

Antigravity carries no transcript in its payload, so there the agent/skill sections say
so honestly and the report leans on the ledger and git; it is written into the session's
artifact directory (user-visible) as well as stderr. In Claude Code the report arrives
as a systemMessage. Advisory only: this hook never blocks and always fails open. A
fingerprint marker stops it repeating an identical report on every Stop.
"""

from __future__ import annotations

import hashlib
import json
import re
import sys
import tempfile
from collections import Counter
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from hooklib import (  # noqa: E402
    ANTIGRAVITY, bypassed, git, ledger_path, read_payload, session_key,
)

PRODUCTS = (
    ("products/desktop/", "Desktop"),
    ("products/android/android-desktop/", "Android Desktop"),
    ("products/android/phototok/", "PhotoTok"),
    ("products/android/core/", ":core (both Android products)"),
    ("docs/", "docs"),
    ("ai/", "ai framework"),
)
AGENT_READ = re.compile(r"ai/agents/([a-z0-9-]+)\.md")
SKILL_READ = re.compile(r"ai/skills/([a-z0-9-]+)/SKILL\.md")
PLAN_TOOLS = {"ExitPlanMode", "EnterPlanMode", "exit_plan_mode"}


def changed_files() -> list[str]:
    out = git("status", "--porcelain", "-uall")
    files = []
    for line in out.splitlines():
        if len(line) > 3:
            path = line[3:].strip()
            if " -> " in path:
                path = path.split(" -> ", 1)[1]
            files.append(path.strip('"'))
    return files


def scan_transcript(path: str) -> dict:
    """Tolerantly walk the JSONL transcript for tool_use blocks."""
    found = {"subagents": set(), "agent_reads": set(), "skills": set(),
             "skill_reads": set(), "plan": False}

    def walk(node) -> None:
        if isinstance(node, dict):
            if node.get("type") == "tool_use":
                name = str(node.get("name", ""))
                inp = node.get("input") or {}
                if name in ("Task", "Agent"):
                    found["subagents"].add(str(inp.get("subagent_type") or "general-purpose"))
                elif name == "Skill":
                    skill = str(inp.get("skill") or inp.get("command") or "").strip("/")
                    if skill:
                        found["skills"].add(skill)
                elif name in PLAN_TOOLS:
                    found["plan"] = True
                elif name == "Read":
                    fp = str(inp.get("file_path", ""))
                    m = AGENT_READ.search(fp)
                    if m:
                        found["agent_reads"].add(m.group(1))
                    m = SKILL_READ.search(fp)
                    if m:
                        found["skill_reads"].add(m.group(1))
            for value in node.values():
                walk(value)
        elif isinstance(node, list):
            for item in node:
                walk(item)

    try:
        with open(path, encoding="utf-8") as fh:
            for line in fh:
                try:
                    walk(json.loads(line))
                except (json.JSONDecodeError, ValueError):
                    continue
    except OSError:
        pass
    return found


def read_ledger(payload) -> Counter:
    counts: Counter = Counter()
    try:
        with ledger_path(payload).open(encoding="utf-8") as fh:
            for line in fh:
                try:
                    entry = json.loads(line)
                    counts[(entry.get("hook", "?"), entry.get("decision", "?"))] += 1
                except (json.JSONDecodeError, ValueError):
                    continue
    except OSError:
        pass
    return counts


def fmt(names: set) -> str:
    return ", ".join(sorted(names)) if names else "none"


def build_report(files: list[str], transcript: dict, hooks: Counter,
                 host: str, plan: bool) -> str:
    per_product: Counter = Counter()
    for f in files:
        for prefix, label in PRODUCTS:
            if f.startswith(prefix):
                per_product[label] += 1
                break
    kinds = [k for k, present in
             (("implementation", bool(files)), ("plan", plan)) if present]

    lines = [f"AI framework usage report — major work: {', '.join(kinds) or 'n/a'}"]
    if per_product:
        lines.append("Changed: " + ", ".join(
            f"{label} ({n} file{'s' if n > 1 else ''})"
            for label, n in per_product.most_common()))
    if host == ANTIGRAVITY:
        lines.append("Agents/skills: not observable in this host (no transcript in "
                     "hook payload) — verify the lifecycle ran per AGENTS.md.")
    else:
        lines.append(f"Agents — subagents invoked: {fmt(transcript['subagents'])}; "
                     f"roles read: {fmt(transcript['agent_reads'])}")
        lines.append(f"Skills — invoked: {fmt(transcript['skills'])}; "
                     f"read as markdown: {fmt(transcript['skill_reads'])}")
    if hooks:
        per_hook: dict[str, list[str]] = {}
        for (hook, decision), n in sorted(hooks.items()):
            per_hook.setdefault(hook, []).append(f"{n} {decision}")
        lines.append("Hooks fired: " + "; ".join(
            f"{hook} ({', '.join(parts)})" for hook, parts in sorted(per_hook.items())))
    else:
        lines.append("Hooks fired: none recorded — guards may not be active in this host.")
    return "\n".join(lines)


def already_reported(payload, fingerprint: str) -> bool:
    marker = (Path(tempfile.gettempdir())
              / f"pst-usage-report-{session_key(payload)}.marker")
    try:
        if marker.exists() and marker.read_text(encoding="utf-8") == fingerprint:
            return True
        marker.write_text(fingerprint, encoding="utf-8")
    except OSError:
        return True  # cannot track state -> stay silent rather than spam
    return False


def emit(payload, report: str) -> None:
    if payload.dialect == ANTIGRAVITY:
        art_dir = payload.raw.get("artifactDirectoryPath")
        if art_dir:
            try:
                (Path(art_dir) / "ai-usage-report.md").write_text(
                    report + "\n", encoding="utf-8")
            except OSError:
                pass
        print(report, file=sys.stderr)
        json.dump({"decision": "allow"}, sys.stdout)
    else:
        json.dump({"systemMessage": report}, sys.stdout)
    sys.stdout.write("\n")


def main() -> int:
    payload = read_payload()
    if bypassed():
        return 0
    if payload.raw.get("fullyIdle") is False:  # Antigravity: not the real end yet
        return 0

    files = changed_files()
    transcript_path = payload.raw.get("transcript_path")
    transcript = scan_transcript(transcript_path) if transcript_path else {
        "subagents": set(), "agent_reads": set(), "skills": set(),
        "skill_reads": set(), "plan": False}

    plan = transcript["plan"]
    if payload.dialect == ANTIGRAVITY:
        art_dir = payload.raw.get("artifactDirectoryPath")
        if art_dir and any(Path(art_dir).glob("*plan*")):
            plan = True

    if not files and not plan:
        return 0  # nothing major happened: no report

    hooks = read_ledger(payload)
    report = build_report(files, transcript, hooks, payload.dialect, plan)
    if already_reported(payload, hashlib.sha256(report.encode()).hexdigest()):
        return 0
    emit(payload, report)
    return 0


if __name__ == "__main__":
    sys.exit(main())
