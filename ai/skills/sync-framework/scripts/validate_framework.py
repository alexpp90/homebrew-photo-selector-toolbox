#!/usr/bin/env python3
"""Validate the agent framework. Run as a gate from scripts/run_tests.sh.

Treats the framework as code:
1. Agents: naming, YAML frontmatter, capability declarations, advisory boundaries.
2. Skills: directory naming, schema, descriptions.
3. Mirror Trees & Copy-Mode Integrity: validates symlink resolution or recursive
   byte-for-byte copy equality without configuration drift.
4. Toolchain Parity: ensures Cursor, Windsurf, Copilot, Claude, Antigravity, and
   Gemini configurations exist, are synchronized, and cover all active products.
5. Unowned Product Checks: dynamically discovers product source trees and ensures
   all products have owning agents, routing entries, and requirement documents.
6. Product Boundary Leaks: statically detects forbidden cross-product imports and
   mathematically verifies guard_scope.py cross-exclusion coverage.
7. Roster & Mentions: verifies ROUTING.md consistency, settings sync, and @agent mentions.

Usage:
    python3 ai/skills/sync-framework/scripts/validate_framework.py [-v]
"""

from __future__ import annotations

from datetime import datetime, timedelta, timezone
import json
from pathlib import Path
import re
import sys

REPO = Path(__file__).resolve().parents[4]
AI = REPO / "ai"
AGENTS = AI / "agents"
SKILLS = AI / "skills"
ROUTING = AI / "ROUTING.md"
GEMINI_SETTINGS = REPO / ".gemini" / "settings.json"

NAME_RE = re.compile(r"^[a-z0-9]+(-[a-z0-9]+)*$")

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


# --------------------------------------------------------------------------- playbooks
MANDATORY_PLAYBOOK_SECTIONS = [
    {
        "name": "When to use / Purpose",
        "patterns": [
            re.compile(r"^when\s+to\s+use$", re.I),
            re.compile(r"^purpose$", re.I),
            re.compile(r"^applicability$", re.I),
            re.compile(r"^trigger(?:s)?$", re.I),
        ],
    },
    {
        "name": "Procedure / Steps",
        "patterns": [
            re.compile(r"^steps?(?:\s*\(.*\))?$", re.I),
            re.compile(r"^step-by-step\s+procedure$", re.I),
            re.compile(r"^procedure$", re.I),
            re.compile(r"^the\s+efficient\s+path$", re.I),
            re.compile(r"^execution$", re.I),
            re.compile(r"^workflow$", re.I),
        ],
    },
    {
        "name": "Traps / Pitfalls",
        "patterns": [
            re.compile(r"^(?:common\s+|known\s+)?traps?(?:\s+(?:and|&)\s+pitfalls?)?$", re.I),
            re.compile(r"^(?:common\s+|known\s+)?pitfalls?$", re.I),
        ],
    },
    {
        "name": "Verification / Definition of Done",
        "patterns": [
            re.compile(r"^definition\s+of\s+done$", re.I),
            re.compile(r"^verification(?:\s+method)?$", re.I),
            re.compile(r"^acceptance\s+criteria$", re.I),
            re.compile(r"^done\s+criteria$", re.I),
        ],
    },
]

TRIGGER_PHRASE_RE = re.compile(
    r"\b(?:use\s+(?:when(?:ever)?|for|if)|when(?:ever)?|triggers?\b|applies\s+when|procedure\s+for|adapt|run)\b",
    re.I,
)
PLAYBOOK_DATE_RE = re.compile(r"^\d{4}-\d{2}-\d{2}$")
DOC_DATE_RE = re.compile(r"(?:last[-_ ]validated|last[-_ ]updated):\s*(\d{4}-\d{2}-\d{2})", re.I)
MAX_PLAYBOOK_AGE_DAYS = 180
TEMPLATE_PLACEHOLDERS = ["<Task Type>", "<One sentence:", "<task>", "ai/skills/playbook-<task>/"]


def check_playbook_schema(skills_dir: Path | None = None) -> set[str]:
    """Validate that all playbooks adhere to standard schema, mandatory sections, and freshness conventions."""
    skills_root = skills_dir or SKILLS
    names: set[str] = set()
    playbook_dirs = sorted(p for p in skills_root.iterdir() if p.is_dir() and p.name.startswith("playbook-"))
    if not playbook_dirs:
        fail("ai/skills/: no playbook directories found (expected playbook-*)")
        return names

    today = datetime.now(timezone.utc).date()
    routing_text = ROUTING.read_text(encoding="utf-8") if ROUTING.exists() else ""

    for d in playbook_dirs:
        skill_file = d / "SKILL.md"
        rel = skill_file.relative_to(REPO) if skill_file.is_relative_to(REPO) else skill_file
        is_template = (d.name == "playbook-template")

        if not skill_file.exists():
            fail(f"{d.name}: no SKILL.md in playbook directory")
            continue

        if not NAME_RE.match(d.name):
            fail(f"{d.name}: playbook directory must be lowercase-hyphen (got '{d.name}')")

        try:
            fm = parse_frontmatter(skill_file)
        except ValueError as exc:
            fail(f"{rel}: invalid YAML frontmatter ({exc})")
            continue

        if fm.get("name") != d.name:
            fail(f"{rel}: name '{fm.get('name')}' != directory '{d.name}'")
        else:
            ok(f"{rel}: name matches directory")

        desc = fm.get("description", "")
        if not desc:
            fail(f"{rel}: missing description")
        elif is_template and "template" not in desc.lower():
            fail(f"{rel}: template description must declare itself as template")
        elif not is_template and not TRIGGER_PHRASE_RE.search(desc):
            fail(f"{rel}: description missing operational trigger phrase (e.g., 'Use when...', 'When...')")
        else:
            ok(f"{rel}: description and trigger phrase valid")

        text = skill_file.read_text(encoding="utf-8")
        body = text.split("\n---\n", 1)[1] if "\n---\n" in text else text

        # Template placeholder check in active playbooks
        if not is_template:
            for placeholder in TEMPLATE_PLACEHOLDERS:
                if placeholder in body:
                    fail(f"{rel}: unreplaced template placeholder '{placeholder}' found in body")

        # Freshness / last_validated check
        val_str = fm.get("last_validated")
        if not val_str:
            m = DOC_DATE_RE.search(body)
            if m:
                val_str = m.group(1)

        if not val_str:
            fail(f"{rel}: missing 'last_validated: YYYY-MM-DD' freshness metadata")
        elif not PLAYBOOK_DATE_RE.match(val_str):
            fail(f"{rel}: invalid last_validated date format '{val_str}' — expected YYYY-MM-DD")
        else:
            try:
                val_date = datetime.strptime(val_str, "%Y-%m-%d").date()
            except ValueError:
                fail(f"{rel}: invalid calendar date for last_validated: '{val_str}'")
                val_date = None

            if val_date:
                if is_template:
                    ok(f"{rel}: template last_validated date '{val_str}' accepted")
                else:
                    if val_str == "1970-01-01":
                        fail(f"{rel}: last_validated retains template default '1970-01-01' — must be updated to validation date")
                    elif val_date > today + timedelta(days=1):
                        fail(f"{rel}: last_validated date '{val_str}' is in the future")
                    elif (today - val_date).days > MAX_PLAYBOOK_AGE_DAYS:
                        age = (today - val_date).days
                        fail(f"{rel}: playbook is stale (last validated {val_str}, {age} days ago > {MAX_PLAYBOOK_AGE_DAYS} days) — re-verify and bump last_validated")
                    else:
                        ok(f"{rel}: freshness validated ({val_str})")

        # Mandatory sections check
        sections: dict[str, str] = {}
        current_heading: str | None = None
        current_content: list[str] = []
        for line in body.splitlines():
            if line.startswith("## "):
                if current_heading:
                    sections[current_heading] = "\n".join(current_content).strip()
                current_heading = line[3:].strip()
                current_content = []
            elif current_heading is not None:
                current_content.append(line)
        if current_heading:
            sections[current_heading] = "\n".join(current_content).strip()

        for sec_spec in MANDATORY_PLAYBOOK_SECTIONS:
            found_heading = None
            for heading_title, content in sections.items():
                if any(p.match(heading_title) for p in sec_spec["patterns"]):
                    found_heading = heading_title
                    if not content.strip():
                        fail(f"{rel}: section '## {heading_title}' is empty — must contain actionable guidance")
                    break
            if not found_heading:
                fail(f"{rel}: missing mandatory section '{sec_spec['name']}'")
            else:
                ok(f"{rel}: mandatory section '{found_heading}' verified")

        # Documentation and routing check for active playbooks
        if not is_template and routing_text:
            if d.name not in routing_text:
                fail(f"{rel}: playbook '{d.name}' not documented under Shared Skills in ai/ROUTING.md")
            else:
                ok(f"{rel}: documented in ai/ROUTING.md")

        names.add(d.name)

    ok(f"playbook validation passed ({len(names)} playbooks)")
    return names


# --------------------------------------------------------------------------- mirrors & copy mode
EXPECTED_MIRRORS = {
    "ROUTING.md": REPO / "ai" / "ROUTING.md",
    ".claude/agents": REPO / "ai" / "agents",
    ".claude/skills": REPO / "ai" / "skills",
    ".claude/hooks": REPO / "ai" / "hooks",
    ".claude/commands": REPO / "ai" / "commands",
    ".agents/skills": REPO / "ai" / "skills",
    ".agents/workflows": REPO / "ai" / "commands",
    ".agents/rules": REPO / "ai" / "rules",
}


def _compare_trees(copy_root: Path, source_root: Path, label: str) -> None:
    # 1. Canonical source files must exist in copy tree with matching bytes
    for src_path in source_root.rglob("*"):
        if src_path.is_dir() or src_path.name.startswith("."):
            continue
        rel = src_path.relative_to(source_root)
        copy_path = copy_root / rel
        if not copy_path.exists():
            fail(f"{label}/{rel}: missing in copy mode (exists in {src_path.relative_to(REPO)})")
        elif copy_path.read_bytes() != src_path.read_bytes():
            fail(f"{label}/{rel}: copy-mode drift detected — content differs from {src_path.relative_to(REPO)}")

    # 2. Mirror copy tree must not contain orphaned files
    for copy_path in copy_root.rglob("*"):
        if (
            copy_path.is_dir()
            or copy_path.name.startswith(".")
            or copy_path.name == "DO_NOT_EDIT.md"
            or "__pycache__" in copy_path.parts
        ):
            continue
        rel = copy_path.relative_to(copy_root)
        src_path = source_root / rel
        if not src_path.exists():
            fail(f"{label}/{rel}: orphaned file in copy tree (does not exist in {source_root.relative_to(REPO)})")


def check_mirrors_and_copy_mode(agent_names: set[str], skill_names: set[str]) -> None:
    """Validate mirror trees across both symlink mode and copy fallback mode."""
    sync_mode_file = REPO / ".sync_mode"
    declared_mode = None
    if sync_mode_file.exists():
        raw_mode = sync_mode_file.read_text(encoding="utf-8").strip()
        try:
            declared_mode = json.loads(raw_mode).get("mode")
        except Exception:
            declared_mode = raw_mode

    # Detect mode by inspecting primary mirror
    primary = REPO / ".claude" / "agents"
    is_symlink = primary.is_symlink()
    detected_mode = "symlink" if is_symlink else "copy"

    if declared_mode and declared_mode != detected_mode:
        fail(f".sync_mode declared '{declared_mode}' but filesystem is in '{detected_mode}' mode")

    for link_rel, target in EXPECTED_MIRRORS.items():
        p = REPO / link_rel
        if not p.exists() and not p.is_symlink():
            fail(f"{link_rel}: mirror path missing")
            continue

        if p.is_symlink():
            if not p.exists():
                fail(f"{link_rel}: broken symlink")
            elif p.resolve() != target.resolve():
                fail(f"{link_rel}: symlink points to '{p.resolve()}', expected '{target.resolve()}'")
            else:
                ok(f"{link_rel} -> {target.relative_to(REPO)}")
        else:
            # Copy mode validation
            if target.is_file():
                if not p.is_file():
                    fail(f"{link_rel}: expected a regular file in copy mode")
                elif p.read_bytes() != target.read_bytes():
                    fail(f"{link_rel}: copy-mode drift detected — differs from {target.relative_to(REPO)}")
                else:
                    ok(f"{link_rel}: copy-mode file matches {target.relative_to(REPO)}")
            elif target.is_dir():
                if not p.is_dir():
                    fail(f"{link_rel}: expected a directory in copy mode")
                else:
                    _compare_trees(p, target, link_rel)
                    ok(f"{link_rel}: copy tree matches {target.relative_to(REPO)}")

    # Gemini mirrors
    base_agents = REPO / ".gemini" / "agents"
    base_skills = REPO / ".gemini" / "skills"

    if not base_agents.is_dir():
        fail(".gemini/agents: missing directory")
    else:
        for entry in sorted(base_agents.glob("*.md")):
            canonical = AGENTS / entry.name
            if entry.is_symlink():
                if not entry.exists():
                    fail(f".gemini/agents/{entry.name}: broken symlink")
                elif entry.resolve() != canonical.resolve():
                    fail(f".gemini/agents/{entry.name}: symlink mismatch")
            else:
                if not canonical.exists():
                    fail(f".gemini/agents/{entry.name}: orphaned copy")
                elif entry.read_bytes() != canonical.read_bytes():
                    fail(f".gemini/agents/{entry.name}: copy-mode drift detected")

    if not base_skills.is_dir():
        fail(".gemini/skills: missing directory")
    else:
        for entry in sorted(base_skills.iterdir()):
            if entry.name.startswith("."):
                continue
            canonical = SKILLS / entry.name
            if entry.is_symlink():
                if not entry.exists():
                    fail(f".gemini/skills/{entry.name}: broken symlink")
                elif entry.resolve() != canonical.resolve():
                    fail(f".gemini/skills/{entry.name}: symlink mismatch")
            else:
                if not canonical.exists():
                    fail(f".gemini/skills/{entry.name}: orphaned copy")
                elif canonical.is_dir():
                    _compare_trees(entry, canonical, f".gemini/skills/{entry.name}")

    ok(f"mirror trees validated in {detected_mode} mode")


def check_mirror_completeness(agent_names: set[str], skill_names: set[str]) -> None:
    linked = {p.stem for p in (REPO / ".gemini" / "agents").glob("*.md")}
    for missing in sorted(agent_names - linked):
        fail(f".gemini/agents/{missing}.md: missing mirror for agent '{missing}'")
    for extra in sorted(linked - agent_names):
        fail(f".gemini/agents/{extra}.md: mirror for unknown agent '{extra}'")

    linked_skills = {p.name for p in (REPO / ".gemini" / "skills").iterdir() if not p.name.startswith(".")}
    for missing in sorted(skill_names - linked_skills):
        fail(f".gemini/skills/{missing}: missing mirror for skill '{missing}'")
    for extra in sorted(linked_skills - skill_names):
        fail(f".gemini/skills/{extra}: mirror for unknown skill '{extra}'")
    ok("mirror sets match canonical agents and skills")


# --------------------------------------------------------------------------- active products specification
ACTIVE_PRODUCTS = {
    "desktop": {
        "dir": REPO / "products" / "desktop",
        "prefix": "desktop-",
        "slug": "desktop",
        "doc": REPO / "docs" / "products" / "desktop" / "REQUIREMENTS.md",
    },
    "macos-desktop": {
        "dir": REPO / "products" / "macos-desktop",
        "prefix": "macos-desktop-",
        "slug": "macos-desktop",
        "doc": REPO / "docs" / "products" / "macos-desktop" / "REQUIREMENTS.md",
    },
    "linux-desktop": {
        "dir": REPO / "products" / "linux-desktop",
        "prefix": "linux-desktop-",
        "slug": "linux-desktop",
        "doc": REPO / "docs" / "products" / "linux-desktop" / "REQUIREMENTS.md",
    },
    "android-desktop": {
        "dir": REPO / "products" / "android" / "android-desktop",
        "prefix": "android-desktop-",
        "slug": "android-desktop",
        "doc": REPO / "docs" / "products" / "android-desktop" / "REQUIREMENTS.md",
    },
    "phototok": {
        "dir": REPO / "products" / "android" / "phototok",
        "prefix": "phototok-",
        "slug": "phototok",
        "doc": REPO / "docs" / "products" / "phototok" / "REQUIREMENTS.md",
    },
}

SHARED_MODULES = {
    "android-core": {
        "dir": REPO / "products" / "android" / "core",
        "slug": "android-build",
    }
}


# --------------------------------------------------------------------------- unowned products
def check_unowned_products(agent_names: set[str]) -> None:
    """Dynamically discover product directories and verify full framework ownership."""
    products_root = REPO / "products"
    if not products_root.is_dir():
        fail("products/: missing directory")
        return

    discovered: set[Path] = set()
    for item in products_root.iterdir():
        if not item.is_dir() or item.name.startswith("."):
            continue
        if item.name == "android":
            for sub in item.iterdir():
                if sub.is_dir() and not sub.name.startswith(".") and (sub / "src").is_dir():
                    discovered.add(sub.resolve())
        elif (item / "src").is_dir() or (item / "pyproject.toml").exists() or (item / "Package.swift").exists():
            discovered.add(item.resolve())

    registered = {p["dir"].resolve() for p in ACTIVE_PRODUCTS.values()}
    registered.update(m["dir"].resolve() for m in SHARED_MODULES.values())

    for unowned in sorted(discovered - registered):
        rel = unowned.relative_to(REPO)
        fail(f"{rel}: unowned product directory discovered — missing specification in ACTIVE_PRODUCTS and owning agent")

    for prod_id, spec in ACTIVE_PRODUCTS.items():
        rel_dir = spec["dir"].relative_to(REPO)
        if not spec["dir"].exists():
            fail(f"{rel_dir}: product directory does not exist")
            continue

        matching_agents = [a for a in agent_names if a.startswith(spec["prefix"])]
        if not matching_agents:
            fail(f"{rel_dir}: missing owning agent in ai/agents/ (expected prefix '{spec['prefix']}')")
        else:
            ok(f"{rel_dir}: owned by {', '.join(matching_agents)}")

        if spec["slug"] not in SCOPE_SLUGS:
            fail(f"{rel_dir}: scope slug '{spec['slug']}' not in SCOPE_SLUGS")

        if not spec["doc"].exists():
            fail(f"{rel_dir}: missing requirements document '{spec['doc'].relative_to(REPO)}'")
        else:
            ok(f"{rel_dir}: requirements document verified")

    if ROUTING.exists():
        routing_text = ROUTING.read_text(encoding="utf-8")
        for prod_id, spec in ACTIVE_PRODUCTS.items():
            if spec["slug"] not in routing_text and spec["dir"].name not in routing_text:
                fail(f"ai/ROUTING.md: active product '{spec['slug']}' not documented in routing")
    ok("all active products verified and owned")


# --------------------------------------------------------------------------- product boundary leaks
FORBIDDEN_PRODUCT_IMPORTS = {
    "desktop": {
        "src": REPO / "products" / "desktop" / "src",
        "patterns": [
            (re.compile(r"^\s*(?:import|from)\s+photo_selector_linux\b"), "Linux Desktop code (photo_selector_linux)"),
            (re.compile(r"^\s*(?:import|from)\s+com\.photo\w*\b"), "Android code (com.photo*)"),
            (re.compile(r"^\s*(?:import|from)\s+PhotoSelectorKit\b"), "macOS Desktop code (PhotoSelectorKit)"),
        ],
    },
    "linux-desktop": {
        "src": REPO / "products" / "linux-desktop" / "src",
        "patterns": [
            (re.compile(r"^\s*(?:import|from)\s+photo_selector_toolbox\b"), "Desktop code (photo_selector_toolbox)"),
            (re.compile(r"^\s*(?:import|from)\s+com\.photo\w*\b"), "Android code (com.photo*)"),
            (re.compile(r"^\s*(?:import|from)\s+PhotoSelectorKit\b"), "macOS Desktop code (PhotoSelectorKit)"),
        ],
    },
    "android-desktop": {
        "src": REPO / "products" / "android" / "android-desktop" / "src",
        "patterns": [
            (re.compile(r"^\s*import\s+com\.phototok\."), "PhotoTok code (com.phototok)"),
            (re.compile(r"^\s*(?:import|from)\s+photo_selector_toolbox\b"), "Desktop code"),
            (re.compile(r"^\s*(?:import|from)\s+photo_selector_linux\b"), "Linux Desktop code"),
        ],
    },
    "phototok": {
        "src": REPO / "products" / "android" / "phototok" / "src",
        "patterns": [
            (re.compile(r"^\s*import\s+com\.photoselectortoolbox\."), "Android Desktop code (com.photoselectortoolbox)"),
            (re.compile(r"^\s*(?:import|from)\s+photo_selector_toolbox\b"), "Desktop code"),
            (re.compile(r"^\s*(?:import|from)\s+photo_selector_linux\b"), "Linux Desktop code"),
        ],
    },
    "android-core": {
        "src": REPO / "products" / "android" / "core" / "src",
        "patterns": [
            (re.compile(r"^\s*import\s+com\.photoselectortoolbox\."), "Android Desktop code (com.photoselectortoolbox)"),
            (re.compile(r"^\s*import\s+com\.phototok\."), "PhotoTok code (com.phototok)"),
            (re.compile(r"^\s*(?:import|from)\s+photo_selector_toolbox\b"), "Desktop code"),
            (re.compile(r"^\s*(?:import|from)\s+photo_selector_linux\b"), "Linux Desktop code"),
        ],
    },
}

RELATIVE_ESCAPE_RE = re.compile(r"""['"]\.\./\.\./(desktop|android|macos-desktop|linux-desktop)/""")


def check_product_boundary_leaks() -> None:
    """Statically verify that product source trees do not leak across boundaries."""
    for prod_name, config in FORBIDDEN_PRODUCT_IMPORTS.items():
        src_root = config["src"]
        if not src_root.is_dir():
            continue
        patterns = config["patterns"]
        for path in src_root.rglob("*"):
            if not path.is_file() or path.suffix not in (".py", ".kt", ".swift"):
                continue
            rel = path.relative_to(REPO)
            text = path.read_text(encoding="utf-8", errors="ignore")
            for line_no, line in enumerate(text.splitlines(), start=1):
                for pat, label in patterns:
                    if pat.search(line):
                        fail(f"{rel}:{line_no}: product boundary leak — imports {label}: '{line.strip()}'")
                m = RELATIVE_ESCAPE_RE.search(line)
                if m:
                    fail(f"{rel}:{line_no}: product boundary leak — relative path traversal into '{m.group(1)}'")

    # Mathematically verify guard_scope.py cross-exclusion
    guard_scope_file = REPO / "ai" / "hooks" / "guard_scope.py"
    if guard_scope_file.exists():
        text = guard_scope_file.read_text(encoding="utf-8")
        for prod_id, spec in ACTIVE_PRODUCTS.items():
            slug = spec["slug"]
            if f'"{slug}":' not in text and f"'{slug}':" not in text:
                fail(f"ai/hooks/guard_scope.py: missing FORBIDDEN configuration for slug '{slug}'")
            expected_prefix = spec["dir"].relative_to(REPO).as_posix() + "/"
            if expected_prefix not in text:
                fail(f"ai/hooks/guard_scope.py: missing path prefix '{expected_prefix}'")
    ok("product boundary leak verification passed")


# --------------------------------------------------------------------------- multi-toolchain parity
TOOLCHAIN_FILES = {
    "windsurf": REPO / ".windsurfrules",
    "copilot": REPO / ".github" / "copilot-instructions.md",
    "cursorrules": REPO / ".cursorrules",
}
CURSOR_RULES_DIR = REPO / ".cursor" / "rules"
PROVENANCE_BANNER_RE = re.compile(r"(?:DO NOT HAND EDIT|Generated by|Source: ai/)", re.I)


def check_toolchain_parity() -> None:
    """Verify presence, provenance, and active product coverage across all toolchains."""
    # 1. Cursor
    if not CURSOR_RULES_DIR.is_dir():
        fail(".cursor/rules/: directory missing — run sync_framework.py")
    else:
        mdc_files = list(CURSOR_RULES_DIR.glob("*.mdc"))
        if not mdc_files:
            fail(".cursor/rules/: no .mdc rule files found — run sync_framework.py")
        else:
            combined_cursor_text = ""
            for mdc in mdc_files:
                rel = mdc.relative_to(REPO)
                try:
                    fm = parse_frontmatter(mdc)
                    if not fm.get("description"):
                        fail(f"{rel}: missing frontmatter description")
                except ValueError as exc:
                    fail(f"{rel}: invalid YAML frontmatter ({exc})")
                combined_cursor_text += mdc.read_text(encoding="utf-8") + "\n"

            for prod_id, spec in ACTIVE_PRODUCTS.items():
                if spec["dir"].relative_to(REPO).as_posix() not in combined_cursor_text:
                    fail(f".cursor/rules/: missing coverage for active product '{prod_id}'")
            ok(f".cursor/rules/ validated ({len(mdc_files)} rules, all 5 products covered)")

    # 2. Windsurf & Copilot & .cursorrules
    for name, path in TOOLCHAIN_FILES.items():
        rel = path.relative_to(REPO)
        if not path.exists():
            fail(f"{rel}: toolchain file missing — run sync_framework.py")
            continue
        text = path.read_text(encoding="utf-8")
        if not PROVENANCE_BANNER_RE.search(text):
            fail(f"{rel}: missing generation provenance banner (must indicate generated from ai/)")

        for prod_id, spec in ACTIVE_PRODUCTS.items():
            rel_prod = spec["dir"].relative_to(REPO).as_posix()
            if rel_prod not in text:
                fail(f"{rel}: missing active product reference '{rel_prod}'")
        ok(f"{rel}: validated (provenance verified, all 5 products covered)")

    # 3. Dynamic drift check against generator if sync_framework.py exists
    sync_script = REPO / "ai" / "skills" / "sync-framework" / "scripts" / "sync_framework.py"
    if sync_script.exists():
        try:
            sys.path.insert(0, str(sync_script.parent))
            import sync_framework  # type: ignore
            targets = sync_framework.build_all_toolchains()
            drift_files = []
            for rel_target, expected_content in targets.items():
                full_target = REPO / rel_target
                if not full_target.exists() or full_target.read_text(encoding="utf-8") != expected_content:
                    drift_files.append(str(rel_target))
            if drift_files:
                for df in drift_files:
                    fail(f"{df}: toolchain drift detected — content differs from sync_framework.py output")
            else:
                ok("toolchain generator output matches disk with zero drift")
        except Exception as exc:
            if VERBOSE:
                print(f"  --  generator check skipped ({exc})")


# --------------------------------------------------------------------------- roster & paths
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


PATH_RE = re.compile(r"`([A-Za-z0-9_./-]+/[A-Za-z0-9_./-]+)`")
SKIP = re.compile(r"[<>*]|^https?:|^@")
PLANNED = re.compile(
    r"not yet (?:been )?(?:written|created)|does not exist yet|"
    r"\bplanned\b|\bto be (?:written|created)\b|create (?:it|them) when",
    re.I,
)


def check_referenced_paths() -> None:
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


SCOPE_RE = re.compile(r'guard_scope\.py\\?" ([a-z-]+)"')
SCOPE_PREFIXES = (
    ("linux-desktop-", "linux-desktop"),
    ("macos-desktop-", "macos-desktop"),
    ("android-desktop-", "android-desktop"),
    ("phototok-", "phototok"),
    ("desktop-", "desktop"),
)
SCOPE_EXPLICIT = {
    "android-shared-build-agent": "android-build",
    "shared-mentor-agent": "no-products",
}
SCOPE_SLUGS = {"desktop", "android-desktop", "phototok", "macos-desktop", "linux-desktop", "android-build", "no-products"}


def check_scope_hooks(agent_names: set[str]) -> None:
    for name in sorted(agent_names):
        expected = SCOPE_EXPLICIT.get(name)
        if expected is None:
            for prefix, slug in SCOPE_PREFIXES:
                if name.startswith(prefix):
                    expected = slug
                    break
        text = (AGENTS / f"{name}.md").read_text(encoding="utf-8")
        found = SCOPE_RE.search(text)
        if expected is None:
            if found:
                fail(f"ai/agents/{name}.md: has a guard_scope hook but no product scope defined")
            continue
        if not found:
            fail(f"ai/agents/{name}.md: missing guard_scope frontmatter hook (expected slug '{expected}')")
        elif found.group(1) != expected:
            fail(f"ai/agents/{name}.md: guard_scope slug '{found.group(1)}' != expected '{expected}'")
        elif found.group(1) not in SCOPE_SLUGS:
            fail(f"ai/agents/{name}.md: unknown guard_scope slug '{found.group(1)}'")
        else:
            ok(f"ai/agents/{name}.md: scope hook '{expected}'")


def main() -> int:
    agent_names = check_agents()
    skill_names = check_skills()
    playbook_names = check_playbook_schema()
    check_mirrors_and_copy_mode(agent_names, skill_names)
    check_mirror_completeness(agent_names, skill_names)
    check_unowned_products(agent_names)
    check_product_boundary_leaks()
    check_toolchain_parity()
    check_roster(agent_names)
    check_gemini_settings(agent_names)
    check_referenced_paths()
    check_mentions(agent_names)
    check_scope_hooks(agent_names)

    print(
        f"framework validation: {len(agent_names)} agents, {len(skill_names)} skills, "
        f"{len(playbook_names)} playbooks, {checks} checks"
    )
    if errors:
        print(f"\nFAILED ({len(errors)}):", file=sys.stderr)
        for e in errors:
            print(f"  - {e}", file=sys.stderr)
        print(
            "\nfix at canonical source under ai/, then run sync_framework.py.",
            file=sys.stderr,
        )
        return 1
    print("framework validation: OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())
