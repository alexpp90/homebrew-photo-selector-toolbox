#!/usr/bin/env python3
"""Retrospective Synthesis Engine (Feature F4.1).

Parses ai/memory/framework_retro.md, clusters recurring friction patterns
across products and domains, detects friction exceeding occurrence thresholds (default: >=2),
and generates actionable playbook proposals and code_health.md backlog items.

Treats retrospective reflections as actionable continuous-improvement data:
1. Parses markdown retro entries, timestamps, agents, efficiency/quality levels, root causes, and improvements.
2. Clusters friction points across product domains
   (desktop, macos-desktop, phototok, android-desktop, linux-desktop, ci, framework).
3. Identifies recurring patterns meeting or exceeding threshold (default: >=2 occurrences).
4. Verifies whether recurring patterns are already addressed by existing playbooks
   (ai/skills/playbook-*) or code_health backlog items.
5. Generates structured playbook proposals adhering to ai/skills/playbook-template/SKILL.md.
6. Generates formatted [OPEN] backlog items for ai/memory/code_health.md.

Usage:
    python3 ai/skills/retrospective/scripts/synthesize_retro.py [--check] [--dry-run]
                                                               [--apply] [--output FILE]
                                                               [--threshold N]

CLI Modes & Exit Codes:
    --dry-run               Print synthesis report to stdout without writing files (exit 0).
    --check                 Verify that all recurring friction is addressed; exit 1 if unaddressed patterns exist.
    --apply                 Append unaddressed backlog items to code_health.md (exit 0).
    --scaffold-playbooks    Used with --apply; scaffolds proposed playbook SKILL.md files.
    --output FILE           Write markdown synthesis report to FILE.
    --json                  Emit machine-readable JSON summary to stdout.
    --product DOMAIN        Filter synthesis to a specific domain (e.g. macos-desktop, phototok).
    --threshold N           Occurrence count to classify a pattern as recurring (default: 2).
"""

from __future__ import annotations

import argparse
from dataclasses import dataclass, field
import datetime
import json
from pathlib import Path
import re
import sys

if sys.stdout and hasattr(sys.stdout, "reconfigure"):
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    except Exception:
        pass
if sys.stderr and hasattr(sys.stderr, "reconfigure"):
    try:
        sys.stderr.reconfigure(encoding="utf-8", errors="replace")
    except Exception:
        pass


def find_repo_root() -> Path:
    """Find repository root by walking up to directory containing AGENTS.md or .git."""
    for parent in Path(__file__).resolve().parents:
        if (parent / "AGENTS.md").exists() or (parent / ".git").exists():
            return parent
    return Path.cwd()


REPO_ROOT = find_repo_root()
DEFAULT_RETRO_FILE = REPO_ROOT / "ai" / "memory" / "framework_retro.md"
DEFAULT_CODE_HEALTH_FILE = REPO_ROOT / "ai" / "memory" / "code_health.md"
DEFAULT_SKILLS_DIR = REPO_ROOT / "ai" / "skills"
DEFAULT_ROUTING_FILE = REPO_ROOT / "ai" / "ROUTING.md"

TRIGGER_PHRASE_RE = re.compile(
    r"\b(?:use\s+(?:when(?:ever)?|for|if)|when(?:ever)?|triggers?\b|applies\s+when|procedure\s+for|adapt|run)\b",
    re.I,
)

DEFAULT_CODE_HEALTH_HEADER = """# Code Health Backlog & Lessons

Refactoring candidates and structural lessons. Owned by `@shared-code-health-agent`;
any agent may append. Format for backlog items:

```markdown
## [OPEN|DONE] YYYY-MM-DD - Short title
**Where:** file paths / modules
**Debt:** what is wrong and why it matters
**Proposal:** the safe refactoring, referencing `ai/skills/refactoring-guide/SKILL.md` patterns
```

Lessons use the standard `ai/memory/` format (Learning/Action).
"""


@dataclass
class RetroEntry:
    """Parsed entry from ai/memory/framework_retro.md."""
    date: str
    title: str
    agents: list[str] = field(default_factory=list)
    fit_level: str = "Unknown"
    fit_detail: str = ""
    efficiency_level: str = "Unknown"
    efficiency_obs: str = ""
    quality_level: str = "Unknown"
    quality_obs: str = ""
    root_cause: str = ""
    actionable_improvement: str = ""
    domain: str = "unknown"
    is_friction: bool = False
    raw_markdown: str = ""


@dataclass
class FrictionPatternDef:
    """Definition and metadata for a recurring friction pattern."""
    id: str
    title: str
    suggested_playbook: str
    description: str
    keywords: list[str]
    default_where: str
    debt_template: str
    proposal_template: str


PATTERNS: list[FrictionPatternDef] = [
    FrictionPatternDef(
        id="async_concurrency_timing",
        title="Asynchronous Concurrency & Timing Test Standardization",
        suggested_playbook="playbook-async-concurrency-testing",
        description=(
            "Use when handling asynchronous event loops, coroutine dispatchers in tests, "
            "polling loops vs hardcoded sleeps, and multi-threaded race conditions."
        ),
        keywords=[
            "async", "coroutine", "dispatcher", "sleep", "polling",
            "unconfined", "race condition", "thread", "barrier",
            "timing", "timeout", "sla"
        ],
        default_where="products/android/phototok/tests/, products/macos-desktop/tests/PhotoSelectorKitTests/",
        debt_template=(
            "Asynchronous tests rely on fragile sleep timers, unconfined dispatcher timing assumptions, "
            "or lack transactional barriers, causing false test failures under heavy CPU loads."
        ),
        proposal_template=(
            "Standardize asynchronous test execution using condition polling with SLA budgets rather than "
            "fixed sleeps, explicitly mock sequential coroutine launches, and enforce transactional write barriers."
        ),
    ),
    FrictionPatternDef(
        id="ui_focus_and_navigation",
        title="UI Focus Management & Keyboard Routing Supremacy",
        suggested_playbook="playbook-ui-focus-and-key-routing",
        description=(
            "Use when preventing native control focus theft, ensuring top-level keyboard shortcut "
            "routing supremacy, and suppressing background window activation."
        ),
        keywords=[
            "focus", "focusable", "arrow key", "key routing",
            "keyboard shortcut", "appkit", "swizzle", "focus stealing",
            "hijacking", "event monitor", "nsapplication"
        ],
        default_where="products/macos-desktop/src/PhotoSelectorApp/, products/desktop/tests/conftest.py",
        debt_template=(
            "Native desktop UI controls (SwiftUI buttons, Tkinter windows) hijack arrow keys from viewport containers "
            "or steal OS workstation focus during headless test execution."
        ),
        proposal_template=(
            "Enforce keyboard routing supremacy with .focusable(false) on clickable toolbar controls, route shortcuts "
            "via native event monitors (NSEvent), and swizzle Cocoa focus activation in test runners."
        ),
    ),
    FrictionPatternDef(
        id="ui_layout_and_occlusion",
        title="UI Layout Occlusion & Modal Presentation Architecture",
        suggested_playbook="playbook-modal-dialogs-and-occlusion",
        description=(
            "Use when presenting modal dialogs safely without colliding with system bars or obscuring "
            "underlying controls, and ensuring contrast compliance."
        ),
        keywords=[
            "occlusion", "dialog", "modal", "scrim", "toast",
            "floating card", "dark-on-dark", "contrast", "overlap",
            "bottom bar"
        ],
        default_where="products/android/phototok/src/com/phototok/ui/, products/macos-desktop/src/PhotoSelectorApp/",
        debt_template=(
            "In-layout notification cards collide with persistent navigation bars or obscure interactive controls, "
            "while dark mode palettes risk illegible dark-on-dark text contrast."
        ),
        proposal_template=(
            "Migrate multi-choice decision prompts from floating cards to centered, scrim-dimmed "
            "Dialog/AlertDialog containers, enforce explicit neutral options, and assert contrast and bounds "
            "non-intersection in tests."
        ),
    ),
    FrictionPatternDef(
        id="test_isolation_and_mocking",
        title="Test Isolation & Mock Lifecycle Management",
        suggested_playbook="playbook-test-isolation-and-mocking",
        description=(
            "Use when managing mock lifecycles, avoiding stale mock fallbacks during state transitions, "
            "and preventing module-level mock leakage."
        ),
        keywords=[
            "mock", "stub", "fake", "fixture", "isolation",
            "unmocked", "stale mock", "monkeypatch", "sys.modules"
        ],
        default_where="products/desktop/tests/unit/gui/, products/android/phototok/tests/",
        debt_template=(
            "Tests pollute global module state (sys.modules) or reuse stale mocks during multi-phase state "
            "transitions, causing ordering-dependent test failures and unmocked dialog hangs."
        ),
        proposal_template=(
            "Enforce strict fixture cleanup, replace whole-module sys.modules mocking with targeted attribute "
            "monkeypatching, and configure mock expectations sequentially for multi-step transitions."
        ),
    ),
    FrictionPatternDef(
        id="test_runner_and_ci_parity",
        title="Local CI Mirror & Test Runner Optimization",
        suggested_playbook="playbook-ci-parity-and-runner",
        description=(
            "Use when aligning local test execution with CI path filters and preventing unnecessary "
            "test runner overhead."
        ),
        keywords=[
            "path filter", "path-based change detection", "gate mirror",
            "ci parity", "headless gui", "unnecessary test execution",
            "ci mirror"
        ],
        default_where="scripts/run_tests.sh, docs/build/CI_PARITY.md",
        debt_template=(
            "Local test execution blindly runs all products regardless of what changed, introducing multi-minute "
            "test latency when working on localized subsystems."
        ),
        proposal_template=(
            "Maintain path-based change detection in scripts/run_tests.sh matching GitHub Actions path filters, "
            "ensuring every CI gate is faithfully mirrored locally."
        ),
    ),
    FrictionPatternDef(
        id="filesystem_and_storage_safety",
        title="Filesystem & External Storage Safety Verification",
        suggested_playbook="playbook-storage-safety-and-transactions",
        description=(
            "Use when safely interacting with external media, SAF file trees, sibling RAW+JPEG pairs, "
            "and transactional write barriers."
        ),
        keywords=[
            "saf", "documentfile", "transactional barrier", "sd card",
            "sibling discovery", "data loss", "moverelatedfiles"
        ],
        default_where=(
            "products/macos-desktop/src/PhotoSelectorKit/, "
            "products/android/phototok/src/com/phototok/data/"
        ),
        debt_template=(
            "File operations on external media (SD cards, SAF trees) risk race conditions, partial writes, "
            "or inadvertent deletion of sibling RAW+JPEG pairs without transactional barriers."
        ),
        proposal_template=(
            "Establish transactional barriers, confirm sibling pairing rules before dispatching mutations, "
            "and verify destructive workflows against rigorous rollback test fixtures."
        ),
    ),
    FrictionPatternDef(
        id="framework_memory_and_rules",
        title="Framework Memory Hygiene & Knowledge Retention",
        suggested_playbook="playbook-framework-memory-hygiene",
        description=(
            "Use when reviewing candidate memories, preventing noisy task diaries, maintaining "
            "playbook freshness, and curing framework drift."
        ),
        keywords=[
            "memory deduplication", "signal-to-noise", "candidate memories",
            "task diary", "stale instructions", "framework drift"
        ],
        default_where="ai/memory/, ai/skills/",
        debt_template=(
            "Unchecked memory logging accumulates task-diary noise or repeats existing lessons, diluting "
            "high-signal lessons and allowing playbooks to rot without validation dates."
        ),
        proposal_template=(
            "Enforce the shared-mentor-agent review gate before committing memory, prune redundant candidates, "
            "and require playbook freshness validation on every usage."
        ),
    ),
]


def detect_domain(title: str, agents: list[str], content: str) -> str:
    """Attribute entry to a primary product or domain."""
    combined = (title + " " + " ".join(agents) + " " + content).lower()
    if any(a.startswith("phototok-") for a in agents) or "phototok" in combined:
        return "phototok"
    if (
        any(a.startswith("macos-desktop-") for a in agents)
        or any(k in combined for k in ("macos-desktop", "macos desktop", "swift"))
    ):
        return "macos-desktop"
    if (
        any(a.startswith("android-desktop-") for a in agents)
        or any(k in combined for k in ("android-desktop", "android desktop"))
    ):
        return "android-desktop"
    if "desktop" in combined and "tkinter" in combined:
        return "desktop"
    if "linux" in combined:
        return "linux-desktop"
    if "ci mirror" in combined or "test runner" in combined or "run_tests.sh" in combined:
        return "ci"
    return "framework"


def parse_retro_file(path: Path) -> list[RetroEntry]:
    """Parse markdown retro entries from framework_retro.md."""
    if not path.exists():
        return []
    try:
        text = path.read_text(encoding="utf-8")
    except UnicodeDecodeError as exc:
        print(f"Error: Failed to decode {path} as UTF-8 ({exc})", file=sys.stderr)
        return []
    except OSError as exc:
        print(f"Error reading retrospective file {path}: {exc}", file=sys.stderr)
        return []

    blocks = re.split(r"(?m)^(?=## \d{4}-\d{2}-\d{2})", text)
    entries: list[RetroEntry] = []

    for b in blocks:
        b_str = b.strip()
        if not re.match(r"^## \d{4}-\d{2}-\d{2}", b_str):
            continue
        first_line = b_str.splitlines()[0]
        m = re.match(r"^## (\d{4}-\d{2}-\d{2})\s*[-–—]\s*(.+)$", first_line)
        if not m:
            continue
        date, title = m.group(1), m.group(2).strip()

        # Agents
        agent_section = b_str.split("\n**Framework Fit")[0] if "**Framework Fit" in b_str else b_str
        agents = re.findall(r"@([a-zA-Z0-9_-]+)", agent_section)

        # Fit
        fit_m = re.search(
            r"-\s*\*\*Fit:\*\*\s*([A-Za-z]+)\s*(?:[-–—]\s*(.*?))?(?=\n-|\n\*\*|\Z)",
            b_str,
            re.DOTALL,
        )
        fit_level = fit_m.group(1).strip() if fit_m else "Unknown"
        fit_detail = fit_m.group(2).strip() if fit_m and fit_m.group(2) else ""

        # Efficiency
        eff_m = re.search(r"\*\*Session Efficiency:\*\*\s*([A-Za-z /]+)", b_str)
        eff_level = eff_m.group(1).strip() if eff_m else "Unknown"
        eff_obs_pat = r"\*\*Session Efficiency:\*\*.*?\n-\s*\*\*Observations:\*\*\s*(.*?)(?=\n\*\*|\Z)"
        eff_obs_m = re.search(eff_obs_pat, b_str, re.DOTALL)
        eff_obs = eff_obs_m.group(1).strip() if eff_obs_m else ""

        # Quality
        qual_m = re.search(r"\*\*Implementation Quality:\*\*\s*([A-Za-z /()]+)", b_str)
        qual_level = qual_m.group(1).strip() if qual_m else "Unknown"
        qual_obs_pat = r"\*\*Implementation Quality:\*\*.*?\n-\s*\*\*Observations:\*\*\s*(.*?)(?=\n\*\*|\Z)"
        qual_obs_m = re.search(qual_obs_pat, b_str, re.DOTALL)
        qual_obs = qual_obs_m.group(1).strip() if qual_obs_m else ""

        # Root cause
        rc_pat = r"\*\*Framework Root Cause:\*\*\s*(.*?)(?=\n\*\*Actionable Framework Improvement:|\Z)"
        rc_m = re.search(rc_pat, b_str, re.DOTALL)
        root_cause = rc_m.group(1).strip() if rc_m else ""

        # Actionable improvement
        act_pat = r"\*\*Actionable Framework Improvement:\*\*\s*(.*?)(?=\n---|(?:\n## \d{4}-\d{2}-\d{2})|\Z)"
        act_m = re.search(act_pat, b_str, re.DOTALL)
        act = act_m.group(1).strip() if act_m else ""

        # Domain
        domain = detect_domain(title, agents, b_str)

        # Friction classification
        is_friction = (
            eff_level.lower() in ["moderate", "low", "friction"]
            or qual_level.lower() in ["rework required", "defect caught late"]
            or ("no framework friction encountered" not in root_cause.lower() and len(root_cause) > 20)
        )

        entries.append(RetroEntry(
            date=date,
            title=title,
            agents=agents,
            fit_level=fit_level,
            fit_detail=fit_detail,
            efficiency_level=eff_level,
            efficiency_obs=eff_obs,
            quality_level=qual_level,
            quality_obs=qual_obs,
            root_cause=root_cause,
            actionable_improvement=act,
            domain=domain,
            is_friction=is_friction,
            raw_markdown=b_str
        ))

    return entries


@dataclass
class ClusterResult:
    """Clustered occurrences and status for a friction pattern."""
    pattern: FrictionPatternDef
    entries: list[RetroEntry]
    occurrences_count: int
    domains: set[str]
    first_seen: str
    last_seen: str
    playbook_exists: bool
    backlog_exists: bool
    is_recurring: bool
    is_addressed: bool


def cluster_entries(
    entries: list[RetroEntry],
    skills_dir: Path,
    code_health_file: Path,
    threshold: int = 2,
    domain_filter: str | None = None
) -> list[ClusterResult]:
    """Cluster entries against pattern definitions and verify remediation status."""
    # Discover existing active playbooks
    existing_playbooks = set()
    if skills_dir.exists():
        for p in skills_dir.glob("playbook-*"):
            if p.is_dir() and p.name != "playbook-template":
                existing_playbooks.add(p.name)

    # Discover existing backlog items
    existing_backlog_titles: list[str] = []
    if code_health_file.exists():
        try:
            ch_text = code_health_file.read_text(encoding="utf-8", errors="replace")
            existing_backlog_titles = re.findall(
                r"^## \[(?:OPEN|DONE)\]\s*\d{4}-\d{2}-\d{2}\s*[-–—]\s*(.+)$",
                ch_text,
                re.MULTILINE
            )
        except OSError:
            existing_backlog_titles = []

    results: list[ClusterResult] = []

    for pat in PATTERNS:
        matching_entries: list[RetroEntry] = []
        for e in entries:
            if domain_filter and e.domain.lower() != domain_filter.lower():
                continue
            friction_text = (
                e.root_cause + " " + e.actionable_improvement + " " + e.efficiency_obs + " " + e.quality_obs
            ).lower()
            matched_kws = [kw for kw in pat.keywords if kw in friction_text]
            if len(matched_kws) >= 1:
                matching_entries.append(e)

        if not matching_entries:
            continue

        matching_entries.sort(key=lambda x: x.date)
        domains = {e.domain for e in matching_entries}
        first_seen = matching_entries[0].date
        last_seen = matching_entries[-1].date
        occ_count = len(matching_entries)

        playbook_exists = pat.suggested_playbook in existing_playbooks
        backlog_exists = any(
            pat.title.lower() in t.lower() or pat.id.replace("_", " ") in t.lower()
            for t in existing_backlog_titles
        )

        is_recurring = occ_count >= threshold
        is_addressed = playbook_exists or backlog_exists

        results.append(ClusterResult(
            pattern=pat,
            entries=matching_entries,
            occurrences_count=occ_count,
            domains=domains,
            first_seen=first_seen,
            last_seen=last_seen,
            playbook_exists=playbook_exists,
            backlog_exists=backlog_exists,
            is_recurring=is_recurring,
            is_addressed=is_addressed
        ))

    results.sort(key=lambda r: r.occurrences_count, reverse=True)
    return results


def generate_playbook_content(cluster: ClusterResult, today: str) -> str:
    """Generate a complete playbook SKILL.md adhering to playbook-template."""
    pat = cluster.pattern
    domains_str = ", ".join(sorted(cluster.domains))

    traps: list[str] = []
    for e in cluster.entries:
        if e.root_cause and "no framework friction" not in e.root_cause.lower():
            traps.append(f"- **{e.title}** ({e.date}): {e.root_cause}")

    if not traps:
        traps.append(
            f"- Avoid recurring friction documented in `ai/memory/framework_retro.md` regarding {pat.title.lower()}."
        )

    traps_block = "\n".join(traps)

    steps: list[str] = []
    for i, e in enumerate(cluster.entries, 1):
        if e.actionable_improvement:
            steps.append(f"{i}. {e.actionable_improvement}")

    if not steps:
        steps = [
            f"1. Review target domain specifications across: {domains_str}.",
            "2. Execute local CI mirror validation via `./scripts/run_tests.sh`.",
            "3. Verify non-regression before milestone sign-off."
        ]
    steps_block = "\n".join(steps)

    # Ensure description strictly complies with validate_framework TRIGGER_PHRASE_RE
    desc = pat.description.strip()
    if not TRIGGER_PHRASE_RE.search(desc):
        desc = f"Use when {desc[:1].lower() + desc[1:] if desc else 'handling recurring friction.'}"

    return f"""---
name: {pat.suggested_playbook}
description: "{desc}"
last_validated: {today}
---

# Playbook: {pat.title}

Standardized operating procedure to eliminate recurring friction observed across {domains_str} sessions.

## When to use

- Triggered when encountering {pat.title.lower()} patterns across {domains_str}.
- Required whenever implementing or debugging areas identified in `ai/memory/framework_retro.md`.

## Steps (the efficient path)

{steps_block}

## Traps

{traps_block}

## Definition of done

- Automated tests cover all relevant transition states and edge cases.
- All gates in `./scripts/run_tests.sh` pass cleanly without timing flake or focus theft.
- No regression to sibling products or existing patterns.

---
Maintenance: every use must either improve this file or bump `last_validated` — see the `create-playbook` skill.
`@shared-code-health-agent` prunes playbooks that go stale or reference deleted files.
"""


def generate_backlog_item(cluster: ClusterResult, today: str) -> str:
    """Generate a formatted [OPEN] item for code_health.md."""
    pat = cluster.pattern
    where_str = pat.default_where
    sessions_str = ", ".join(f"{e.date} ('{e.title}')" for e in cluster.entries)

    return f"""## [OPEN] {today} - {pat.title}
**Where:** {where_str}
**Debt:** Recurring friction detected across {cluster.occurrences_count} sessions ({sessions_str}). {pat.debt_template}
**Proposal:** {pat.proposal_template} Reference playbook `{pat.suggested_playbook}`.
"""


def format_report(
    entries: list[RetroEntry],
    clusters: list[ClusterResult],
    threshold: int,
    today: str
) -> str:
    """Format full markdown synthesis report."""
    total_entries = len(entries)
    friction_entries = [e for e in entries if e.is_friction]
    recurring = [c for c in clusters if c.is_recurring]
    unaddressed = [c for c in recurring if not c.is_addressed]

    lines = [
        "# AI Framework Retrospective Synthesis Report",
        "",
        f"Generated: {today}",
        "",
        "## Executive Summary",
        f"- **Total Retro Entries Analyzed:** {total_entries}",
        f"- **Friction / Rework Entries:** {len(friction_entries)}",
        f"- **Pattern Clusters Identified:** {len(clusters)}",
        f"- **Recurring Patterns (>= {threshold} occurrences):** {len(recurring)}",
        f"- **Unaddressed Recurring Patterns:** {len(unaddressed)}",
        "",
        "## Clustered Friction Patterns",
        "",
        "| Pattern | Occurrences | Domains | Date Range | Playbook | Backlog | Status |",
        "|---|---|---|---|---|---|---|",
    ]

    for c in clusters:
        pb_status = "✔ Present" if c.playbook_exists else "✘ Missing"
        bl_status = "✔ Logged" if c.backlog_exists else "✘ Missing"
        rec_status = "⚠️ RECURRING" if c.is_recurring else "Monitored"
        if not c.is_addressed and c.is_recurring:
            rec_status = "🚨 ACTION REQUIRED"
        lines.append(
            f"| **{c.pattern.title}** | {c.occurrences_count} | {', '.join(sorted(c.domains))} | "
            f"{c.first_seen} .. {c.last_seen} | {pb_status} | {bl_status} | {rec_status} |"
        )

    lines.append("")
    lines.append("## Actionable Playbook Proposals")
    lines.append("")

    for c in recurring:
        if not c.playbook_exists:
            domains_list = ", ".join(sorted(c.domains))
            lines.append(f"### Proposed New Playbook: `{c.pattern.suggested_playbook}`")
            lines.append(f"- **Title:** {c.pattern.title}")
            lines.append(f"- **Target Directory:** `ai/skills/{c.pattern.suggested_playbook}/`")
            lines.append(f"- **Rationale:** Encountered {c.occurrences_count} times across {domains_list}.")
            lines.append("")
            lines.append("```markdown")
            lines.append(generate_playbook_content(c, today).strip())
            lines.append("```")
            lines.append("")
        else:
            lines.append(f"### Existing Playbook to Refresh: `{c.pattern.suggested_playbook}`")
            lines.append(
                f"- Playbook exists; bump `last_validated` to `{today}` and incorporate recent session insights."
            )
            lines.append("")

    lines.append("## Code Health Backlog Recommendations")
    lines.append("")

    for c in recurring:
        if not c.backlog_exists:
            lines.append(f"### Recommended `[OPEN]` Item: {c.pattern.title}")
            lines.append("")
            lines.append("```markdown")
            lines.append(generate_backlog_item(c, today).strip())
            lines.append("```")
            lines.append("")
        else:
            lines.append(f"### Backlog Item Already Present: {c.pattern.title}")
            lines.append("- Already tracked in `ai/memory/code_health.md`.")
            lines.append("")

    return "\n".join(lines)


def main() -> int:
    parser = argparse.ArgumentParser(description="Retrospective Synthesis Engine (Feature F4.1)")
    parser.add_argument(
        "--retro-file",
        type=Path,
        default=DEFAULT_RETRO_FILE,
        help="Path to framework_retro.md",
    )
    parser.add_argument(
        "--code-health-file",
        type=Path,
        default=DEFAULT_CODE_HEALTH_FILE,
        help="Path to code_health.md",
    )
    parser.add_argument(
        "--skills-dir",
        type=Path,
        default=DEFAULT_SKILLS_DIR,
        help="Path to skills directory",
    )
    parser.add_argument(
        "--routing-file",
        type=Path,
        default=DEFAULT_ROUTING_FILE,
        help="Path to ai/ROUTING.md",
    )
    parser.add_argument(
        "--threshold",
        type=int,
        default=2,
        help="Occurrence threshold for recurring patterns (default: 2)",
    )
    parser.add_argument(
        "--product",
        type=str,
        default=None,
        help="Filter by product domain (e.g. macos-desktop, phototok)",
    )
    parser.add_argument(
        "--check",
        action="store_true",
        help="Validate whether all recurring friction is addressed",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Print synthesis report without modifying files",
    )
    parser.add_argument(
        "--apply",
        action="store_true",
        help="Apply proposals to code_health.md and create playbooks",
    )
    parser.add_argument(
        "--scaffold-playbooks",
        action="store_true",
        help="With --apply, write proposed playbooks to ai/skills/ and register in ai/ROUTING.md",
    )
    parser.add_argument(
        "--output",
        "-o",
        type=Path,
        help="Write report to specified file",
    )
    parser.add_argument(
        "--json",
        action="store_true",
        help="Output results in JSON format",
    )
    parser.add_argument(
        "--verbose",
        "-v",
        action="store_true",
        help="Verbose output",
    )

    args = parser.parse_args()
    today = datetime.date.today().isoformat()

    entries = parse_retro_file(args.retro_file)
    if not entries:
        print(f"Error: No entries found in {args.retro_file}", file=sys.stderr)
        return 1

    clusters = cluster_entries(
        entries,
        args.skills_dir,
        args.code_health_file,
        threshold=args.threshold,
        domain_filter=args.product
    )
    recurring = [c for c in clusters if c.is_recurring]
    unaddressed = [c for c in recurring if not c.is_addressed]

    report_md = format_report(entries, clusters, args.threshold, today)

    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(report_md, encoding="utf-8")
        if args.verbose:
            print(f"Report written to {args.output}")

    if args.json:
        payload = {
            "generated_at": today,
            "total_entries": len(entries),
            "clusters": [
                {
                    "pattern_id": c.pattern.id,
                    "title": c.pattern.title,
                    "occurrences": c.occurrences_count,
                    "domains": sorted(list(c.domains)),
                    "first_seen": c.first_seen,
                    "last_seen": c.last_seen,
                    "is_recurring": c.is_recurring,
                    "is_addressed": c.is_addressed,
                    "playbook_exists": c.playbook_exists,
                    "backlog_exists": c.backlog_exists,
                    "suggested_playbook": c.pattern.suggested_playbook,
                }
                for c in clusters
            ],
            "unaddressed_count": len(unaddressed),
        }
        print(json.dumps(payload, indent=2))
        return 1 if (args.check and len(unaddressed) > 0) else 0

    if args.apply:
        applied_items = 0
        if not args.code_health_file.exists():
            print(
                f"Warning: Backlog file {args.code_health_file} does not exist; initializing new file.",
                file=sys.stderr,
            )
            args.code_health_file.parent.mkdir(parents=True, exist_ok=True)
            ch_content = DEFAULT_CODE_HEALTH_HEADER
        else:
            try:
                ch_content = args.code_health_file.read_text(encoding="utf-8", errors="replace")
            except OSError as exc:
                print(f"Error reading {args.code_health_file}: {exc}", file=sys.stderr)
                return 1

        to_append: list[str] = []
        for c in recurring:
            if not c.backlog_exists:
                item_text = generate_backlog_item(c, today)
                # Deduplicate: check if pattern title is already present in code_health.md
                if c.pattern.title not in ch_content:
                    to_append.append(item_text)
                    applied_items += 1
        if to_append:
            new_content = ch_content.rstrip() + "\n\n" + "\n".join(to_append) + "\n"
            try:
                args.code_health_file.write_text(new_content, encoding="utf-8")
                print(f"Appended {len(to_append)} new backlog items to {args.code_health_file}")
            except OSError as exc:
                print(f"Error writing to {args.code_health_file}: {exc}", file=sys.stderr)
                return 1

        if args.scaffold_playbooks:
            created_playbooks = 0
            for c in recurring:
                if not c.playbook_exists:
                    pb_dir = args.skills_dir / c.pattern.suggested_playbook
                    pb_dir.mkdir(parents=True, exist_ok=True)
                    pb_skill = pb_dir / "SKILL.md"
                    pb_content = generate_playbook_content(c, today)
                    try:
                        pb_skill.write_text(pb_content, encoding="utf-8")
                        created_playbooks += 1
                        print(f"Scaffolded playbook: {pb_skill}")
                    except OSError as exc:
                        print(f"Error writing {pb_skill}: {exc}", file=sys.stderr)
                        return 1

                    # Register in ai/ROUTING.md if routing file exists and playbook not documented
                    if args.routing_file and args.routing_file.exists():
                        try:
                            r_text = args.routing_file.read_text(encoding="utf-8")
                            if c.pattern.suggested_playbook not in r_text:
                                pb_row = (
                                    f"| [`{c.pattern.suggested_playbook}`]"
                                    f"(skills/{c.pattern.suggested_playbook}/SKILL.md) | "
                                    f"{c.pattern.title.lower()} |\n"
                                )
                                if "| `playbook-*` |" in r_text:
                                    r_text = r_text.replace("| `playbook-*` |", pb_row + "| `playbook-*` |")
                                else:
                                    r_text = r_text.rstrip() + "\n" + pb_row
                                args.routing_file.write_text(r_text, encoding="utf-8")
                                print(f"Registered playbook in {args.routing_file}")
                        except OSError as exc:
                            print(f"Warning: Could not update {args.routing_file}: {exc}", file=sys.stderr)

        print(f"Apply complete: {applied_items} backlog item(s) logged.")
        return 0

    if args.check:
        if unaddressed:
            print(
                f"FAIL: Found {len(unaddressed)} recurring friction pattern(s) "
                f"(>= {args.threshold} occurrences) requiring remediation:\n"
            )
            for c in unaddressed:
                print(f"  - [{c.pattern.id}] {c.pattern.title} ({c.occurrences_count} occurrences)")
                print(f"    Domains: {', '.join(sorted(c.domains))}")
                print(f"    Suggested Playbook: {c.pattern.suggested_playbook} (Missing)")
                print("    Backlog Item in code_health.md: (Missing)")
                print()
            print("Run with --dry-run to inspect proposals, or --apply to log backlog items.")
            return 1
        else:
            print("OK: All recurring friction patterns in framework_retro.md are addressed.")
            return 0

    # Default / dry-run output
    print(report_md)
    return 0


if __name__ == "__main__":
    sys.exit(main())
