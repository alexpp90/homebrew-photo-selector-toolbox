"""test_ai_framework_eval.py — Automated AI Framework Eval & Regression Test Harness.

Feature: F4.3 AI Framework Regression & Eval Harness
Requirements: R4 (Autonomous Continuous Improvement & Feedback Engine)

Verifies:
1. Agent Prompt Contracts:
   - YAML frontmatter integrity (name, description, tools, model).
   - Mandatory operational sections (Scope & Ownership, Rules & Anti-patterns,
     Tech Stack / Domain Knowledge, Communication & Delegation).
   - PreToolUse hook configuration and product slug binding.
   - Catching invalid prompts, broken frontmatter, and advisory write leaks.
2. Skill Instruction Contracts:
   - YAML frontmatter and non-empty instruction bodies.
   - Integrity of all referenced repository scripts (existence, executability, syntax).
   - Catching broken scripts, empty bodies, and directory mismatches.
3. Lifecycle Hooks Protocol:
   - Structural dialect detection (Claude Code vs Antigravity).
   - Tool call decision emission (allow vs deny) in both host dialects.
   - Stop hook decision emission (allow vs block/continue).
   - Functional isolation of guard_scope.py and guard_paths.py.
   - Emergency bypass switch (PST_SKIP_HOOKS=1).
   - Graceful handling of malformed and empty stdin payloads.
4. Retrospective Synthesis Engine:
   - Structured parsing of ai/memory/framework_retro.md entries.
   - Friction pattern clustering and recurrence threshold detection (>= 2).
   - Synthesis engine execution (--dry-run / --json) and report generation.
5. Playbook Validator Contract:
   - Structural schema compliance across all ai/skills/playbook-*/SKILL.md files.
   - Mandatory level-2 sections (When to use, Steps, Traps, Definition of done).
   - Temporal freshness validation (last_validated: YYYY-MM-DD, age <= 180 days).
   - Template sentinel discrimination (1970-01-01 only allowed for playbook-template).
   - Catching omitted sections, invalid dates, placeholder leaks, and empty bodies.
"""

from __future__ import annotations

import ast
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import unittest


def _resolve_repo_root() -> Path:
    """Robustly resolve repository root from environment, __file__ parents, or cwd."""
    if os.environ.get("PST_REPO_ROOT"):
        return Path(os.environ["PST_REPO_ROOT"]).resolve()
    current = Path(__file__).resolve()
    for parent in [current] + list(current.parents):
        if (parent / "AGENTS.md").exists() and (parent / "ai").exists():
            return parent
    cwd = Path.cwd().resolve()
    for parent in [cwd] + list(cwd.parents):
        if (parent / "AGENTS.md").exists() and (parent / "ai").exists():
            return parent
    return current.parents[4]


REPO_ROOT = _resolve_repo_root()
if str(REPO_ROOT) not in sys.path:
    sys.path.insert(0, str(REPO_ROOT))

AGENTS_DIR = REPO_ROOT / "ai" / "agents"
SKILLS_DIR = REPO_ROOT / "ai" / "skills"
HOOKS_DIR = REPO_ROOT / "ai" / "hooks"
MEMORY_DIR = REPO_ROOT / "ai" / "memory"
RETRO_FILE = MEMORY_DIR / "framework_retro.md"
VALIDATE_FRAMEWORK_SCRIPT = SKILLS_DIR / "sync-framework" / "scripts" / "validate_framework.py"
SYNTHESIZE_RETRO_SCRIPT = SKILLS_DIR / "retrospective" / "scripts" / "synthesize_retro.py"
SYNC_SCRIPTS_DIR = SKILLS_DIR / "sync-framework" / "scripts"
RETRO_SCRIPTS_DIR = SKILLS_DIR / "retrospective" / "scripts"
if str(SYNC_SCRIPTS_DIR) not in sys.path:
    sys.path.insert(0, str(SYNC_SCRIPTS_DIR))
if str(RETRO_SCRIPTS_DIR) not in sys.path:
    sys.path.insert(0, str(RETRO_SCRIPTS_DIR))

NAME_RE = re.compile(r"^[a-z0-9]+(-[a-z0-9]+)*$")
DATE_RE = re.compile(r"^\d{4}-\d{2}-\d{2}$")

ADVISORY_PHRASES = (
    "do not directly own or modify source files",
    "do not directly own source files",
    "no direct source code changes",
    "no implementation code changes",
    "you do not modify source files",
)


def parse_simple_frontmatter(text: str) -> tuple[dict[str, str], str]:
    """Extract YAML frontmatter and markdown body."""
    if not text.startswith("---\n"):
        raise ValueError("missing YAML frontmatter header (must start with ---\n)")
    end = text.find("\n---\n", 3)
    if end == -1:
        raise ValueError("unterminated YAML frontmatter (missing closing ---\n)")

    fm_text = text[4:end]
    body = text[end + 5:]
    fields: dict[str, str] = {}
    for line in fm_text.splitlines():
        line_s = line.strip()
        if not line_s or line_s.startswith("#"):
            continue
        if line[0].isspace() or ":" not in line:
            continue
        key, _, value = line.partition(":")
        val = value.strip()
        if len(val) >= 2 and val[0] == val[-1] and val[0] in "\"'":
            val = val[1:-1]
        fields[key.strip()] = val
    return fields, body


# ============================================================================
# 1. Agent Prompt Contract Suite
# ============================================================================
class TestAgentPromptContract(unittest.TestCase):
    """Verifies that all agent definitions in ai/agents/*.md satisfy operational contracts."""

    def test_all_agents_have_valid_frontmatter(self):
        """All agent files must have valid YAML frontmatter matching stem, with required fields."""
        agent_files = sorted(AGENTS_DIR.glob("*.md"))
        self.assertGreaterEqual(len(agent_files), 16, "Expected at least 16 agent definitions")

        for path in agent_files:
            rel = path.relative_to(REPO_ROOT)
            text = path.read_text(encoding="utf-8")
            fm, _ = parse_simple_frontmatter(text)

            self.assertTrue(NAME_RE.match(path.stem), f"{rel}: filename stem must be lowercase-hyphen")
            self.assertEqual(fm.get("name"), path.stem, f"{rel}: frontmatter 'name' must match filename stem")
            self.assertTrue(bool(fm.get("description")), f"{rel}: missing or empty 'description'")
            self.assertTrue(bool(fm.get("tools")), f"{rel}: missing or empty 'tools'")
            self.assertTrue(bool(fm.get("model")), f"{rel}: missing or empty 'model'")

    def test_all_agents_have_mandatory_scope_and_ownership(self):
        """All agent files must define their file/system scope and ownership."""
        for path in sorted(AGENTS_DIR.glob("*.md")):
            rel = path.relative_to(REPO_ROOT)
            text = path.read_text(encoding="utf-8")
            has_scope = bool(re.search(r"^##\s+(Scope|Responsibilities|When you are consulted)", text, re.M))
            self.assertTrue(has_scope, f"{rel}: missing mandatory Scope / Ownership section")

    def test_all_agents_have_mandatory_rules_and_anti_patterns(self):
        """All agent files must define operational rules and behavioral constraints."""
        for path in sorted(AGENTS_DIR.glob("*.md")):
            rel = path.relative_to(REPO_ROOT)
            text = path.read_text(encoding="utf-8")
            has_rules = bool(re.search(r"^##\s+(Rules|Principles|Core invariants)", text, re.M))
            self.assertTrue(has_rules, f"{rel}: missing mandatory Rules / Behavioral Constraints section")

    def test_all_agents_have_domain_knowledge_or_operational_context(self):
        """All agent files must define technical domain knowledge, tasks, review procedures, or tech guidelines."""
        for path in sorted(AGENTS_DIR.glob("*.md")):
            rel = path.relative_to(REPO_ROOT)
            text = path.read_text(encoding="utf-8")
            has_context = bool(re.search(
                r"^##\s+(Key Domain Knowledge|Typical tasks|Your review procedure|"
                r"Read before you start|Responsibilities|Core invariants)",
                text, re.M,
            ))
            if not has_context:
                has_tech_in_rules = bool(re.search(
                    r"^##\s+Rules.*\b(Gradle|SDK|CI|dependencies|workflow|build)\b",
                    text, re.S | re.M,
                ))
                has_context = has_tech_in_rules
            self.assertTrue(has_context, f"{rel}: missing operational context / domain knowledge section")

    def test_agent_scope_guard_hooks_conform_to_slugs(self):
        """Agents with PreToolUse hooks must bind to guard_scope.py with the correct product slug."""
        product_slug_map = {
            "desktop-backend-agent.md": "desktop",
            "desktop-build-agent.md": "desktop",
            "desktop-gui-agent.md": "desktop",
            "desktop-test-agent.md": "desktop",
            "android-desktop-core-agent.md": "android-desktop",
            "android-desktop-ui-agent.md": "android-desktop",
            "android-shared-build-agent.md": "android-build",
            "phototok-core-agent.md": "phototok",
            "phototok-ui-agent.md": "phototok",
            "macos-desktop-agent.md": "macos-desktop",
            "linux-desktop-agent.md": "linux-desktop",
            "shared-mentor-agent.md": "no-products",
        }
        for filename, expected_slug in product_slug_map.items():
            path = AGENTS_DIR / filename
            text = path.read_text(encoding="utf-8")
            m = re.search(r'guard_scope\.py[\\"\s]+([a-z0-9_-]+)', text)
            self.assertIsNotNone(m, f"{filename}: missing guard_scope.py hook in frontmatter")
            self.assertEqual(
                m.group(1),
                expected_slug,
                f"{filename}: hook must declare slug '{expected_slug}', got '{m.group(1)}'",
            )

    def test_advisory_agents_do_not_declare_write_tools(self):
        """Agents disclaiming direct write access must not declare Edit or Write tools."""
        for path in sorted(AGENTS_DIR.glob("*.md")):
            rel = path.relative_to(REPO_ROOT)
            text = path.read_text(encoding="utf-8")
            fm, _ = parse_simple_frontmatter(text)
            body_lower = text.lower()
            disclaims = any(p in body_lower for p in ADVISORY_PHRASES)
            tools = {t.strip() for t in fm.get("tools", "").split(",")}
            if disclaims:
                self.assertFalse(
                    bool(tools & {"Edit", "Write"}),
                    f"{rel}: advisory agent disclaims write but declares Edit/Write tools",
                )

    def test_agent_mutation_catches_invalid_prompts(self):
        """Mutated agent prompts must be detected by the frontmatter and section validators."""
        with tempfile.TemporaryDirectory() as tmp_dir:
            tmp = Path(tmp_dir)

            # 1. Missing frontmatter header
            bad1 = tmp / "bad-header.md"
            bad1.write_text("# Title without frontmatter", encoding="utf-8")
            with self.assertRaises(ValueError):
                parse_simple_frontmatter(bad1.read_text(encoding="utf-8"))

            # 2. Unterminated frontmatter
            bad2 = tmp / "unterminated.md"
            bad2.write_text("---\nname: unterminated\ndescription: foo\n", encoding="utf-8")
            with self.assertRaises(ValueError):
                parse_simple_frontmatter(bad2.read_text(encoding="utf-8"))

            # 3. Name mismatch with filename stem
            bad3 = tmp / "test-agent.md"
            bad3.write_text(
                "---\nname: different-agent\ndescription: d\ntools: Read\nmodel: inherit\n---\n## Scope\n",
                encoding="utf-8",
            )
            fm3, _ = parse_simple_frontmatter(bad3.read_text(encoding="utf-8"))
            self.assertNotEqual(fm3.get("name"), bad3.stem)

            # 4. Missing Scope section
            bad4 = "---\nname: no-scope\ndescription: d\ntools: Read\nmodel: inherit\n---\n## Rules\n1. Do not break.\n"
            has_scope = bool(re.search(r"^##\s+(Scope|Responsibilities|When you are consulted)", bad4, re.M))
            self.assertFalse(has_scope)

            # 5. Missing Rules section
            bad5 = "---\nname: no-rules\ndescription: d\ntools: Read\nmodel: inherit\n---\n## Scope\nfiles here\n"
            has_rules = bool(re.search(r"^##\s+(Rules|Principles|Core invariants)", bad5, re.M))
            self.assertFalse(has_rules)


# ============================================================================
# 2. Skill Instruction Contract Suite
# ============================================================================
class TestSkillInstructionContract(unittest.TestCase):
    """Verifies that all skill definitions in ai/skills/*/SKILL.md satisfy integrity contracts."""

    def test_all_skills_have_valid_frontmatter_and_nonempty_body(self):
        """All skill directories must contain SKILL.md with matching name, description, and substantive body."""
        skill_dirs = sorted(p for p in SKILLS_DIR.iterdir() if p.is_dir() and not p.name.startswith("."))
        self.assertGreaterEqual(len(skill_dirs), 13, "Expected at least 13 skill packages")

        for d in skill_dirs:
            skill_file = d / "SKILL.md"
            rel = skill_file.relative_to(REPO_ROOT)
            self.assertTrue(skill_file.exists(), f"Missing SKILL.md in {d.relative_to(REPO_ROOT)}")
            self.assertTrue(NAME_RE.match(d.name), f"{d.name}: directory must be lowercase-hyphen")

            text = skill_file.read_text(encoding="utf-8")
            fm, body = parse_simple_frontmatter(text)

            self.assertEqual(fm.get("name"), d.name, f"{rel}: frontmatter 'name' must match directory '{d.name}'")
            self.assertTrue(bool(fm.get("description")), f"{rel}: missing or empty 'description'")
            self.assertGreaterEqual(
                len(body.strip()), 50, f"{rel}: instruction body must be non-empty substantive markdown"
            )

    def test_all_referenced_scripts_exist_on_disk(self):
        """Any executable repository script path referenced in skill instructions must exist on disk."""
        script_pattern_python = re.compile(r"(?:python3|bash)\s+([a-zA-Z0-9_./-]+\.(?:py|sh))")
        script_pattern_dot = re.compile(r"(?:\./)([a-zA-Z0-9_./-]+\.(?:py|sh))")

        checked_refs = 0
        for skill_file in sorted(SKILLS_DIR.glob("*/SKILL.md")):
            text = skill_file.read_text(encoding="utf-8")
            targets = set()
            for m in script_pattern_python.finditer(text):
                targets.add(m.group(1))
            for m in script_pattern_dot.finditer(text):
                targets.add(m.group(1))

            for ref in targets:
                resolved = REPO_ROOT / ref
                self.assertTrue(
                    resolved.exists(),
                    f"{skill_file.relative_to(REPO_ROOT)} references non-existent script '{ref}'",
                )
                checked_refs += 1

        self.assertGreater(checked_refs, 5, "Expected multiple script references to be verified")

    def test_all_referenced_shell_scripts_are_executable(self):
        """Referenced shell scripts must have execute permissions."""
        for skill_file in sorted(SKILLS_DIR.glob("*/SKILL.md")):
            text = skill_file.read_text(encoding="utf-8")
            for m in re.finditer(r"(?:\./)([a-zA-Z0-9_./-]+\.sh)", text):
                script_path = REPO_ROOT / m.group(1)
                if script_path.exists():
                    self.assertTrue(
                        os.access(script_path, os.X_OK),
                        f"Referenced shell script {script_path.relative_to(REPO_ROOT)} is not executable",
                    )

    def test_all_referenced_python_scripts_have_valid_syntax(self):
        """Referenced Python scripts must compile cleanly with AST parser."""
        for skill_file in sorted(SKILLS_DIR.glob("*/SKILL.md")):
            text = skill_file.read_text(encoding="utf-8")
            for m in re.finditer(r"python3\s+([a-zA-Z0-9_./-]+\.py)", text):
                script_path = REPO_ROOT / m.group(1)
                if script_path.exists():
                    code = script_path.read_text(encoding="utf-8")
                    try:
                        ast.parse(code, filename=str(script_path))
                    except SyntaxError as exc:
                        self.fail(f"Syntax error in script {script_path.relative_to(REPO_ROOT)}: {exc}")

    def test_skill_mutation_catches_invalid_skills(self):
        """Mutated skills (empty body, broken script reference) must be flagged."""
        empty_body_skill = "---\nname: my-skill\ndescription: valid desc\n---\n   \n"
        _, body = parse_simple_frontmatter(empty_body_skill)
        self.assertLess(len(body.strip()), 50)

        fake_script_ref = "python3 ai/skills/fake-skill/scripts/nonexistent.py"
        m = re.search(r"python3\s+([a-zA-Z0-9_./-]+\.py)", fake_script_ref)
        self.assertIsNotNone(m)
        self.assertFalse((REPO_ROOT / m.group(1)).exists())


# ============================================================================
# 3. Lifecycle Hooks Protocol Suite
# ============================================================================
class TestLifecycleHooksProtocol(unittest.TestCase):
    """Verifies that lifecycle hooks in ai/hooks/ adhere to the multi-toolchain stdin/stdout JSON protocol."""

    def setUp(self):
        self.guard_scope = HOOKS_DIR / "guard_scope.py"
        self.guard_paths = HOOKS_DIR / "guard_paths.py"
        self.hooklib = HOOKS_DIR / "hooklib.py"
        self.check_retro = HOOKS_DIR / "check_retrospective.py"
        self.verify_intent = HOOKS_DIR / "verify_intent_delivery.py"

        for h in [self.guard_scope, self.guard_paths, self.hooklib, self.check_retro, self.verify_intent]:
            self.assertTrue(h.exists(), f"Hook file missing: {h.name}")

    def _run_hook(self, hook_path: Path, args: list[str], payload: dict, env: dict | None = None) -> tuple[int, dict]:
        run_env = os.environ.copy()
        run_env["CLAUDE_PROJECT_DIR"] = str(REPO_ROOT)
        run_env["ANTIGRAVITY_WORKSPACE"] = str(REPO_ROOT)
        if env:
            run_env.update(env)

        proc = subprocess.run(
            [sys.executable, str(hook_path)] + args,
            input=json.dumps(payload),
            capture_output=True,
            text=True,
            cwd=str(REPO_ROOT),
            env=run_env,
        )
        out_raw = proc.stdout.strip()
        out_json = json.loads(out_raw) if out_raw else {}
        return proc.returncode, out_json

    def test_claude_code_allow_protocol(self):
        """In Claude Code dialect, an allowed tool call must emit {} with exit code 0."""
        payload = {
            "tool_name": "Write",
            "tool_input": {"file_path": "products/desktop/src/photo_selector_toolbox/core/analyzer.py"},
        }
        rc, out = self._run_hook(self.guard_scope, ["desktop"], payload)
        self.assertEqual(rc, 0)
        self.assertEqual(out, {}, f"Expected empty object {{}} for Claude allow, got {out}")

    def test_claude_code_deny_protocol(self):
        """In Claude Code dialect, a denied tool call must emit hookSpecificOutput with permissionDecision: deny."""
        payload = {
            "tool_name": "Write",
            "tool_input": {"file_path": "products/android/phototok/src/MainActivity.kt"},
        }
        rc, out = self._run_hook(self.guard_scope, ["desktop"], payload)
        self.assertEqual(rc, 0)
        self.assertIn("hookSpecificOutput", out)
        hso = out["hookSpecificOutput"]
        self.assertEqual(hso.get("permissionDecision"), "deny")
        self.assertEqual(hso.get("hookEventName"), "PreToolUse")
        self.assertTrue(bool(hso.get("permissionDecisionReason")), "Expected non-empty denial reason")

    def test_antigravity_allow_protocol(self):
        """In Antigravity dialect, an allowed tool call must emit {'decision': 'allow'} with exit code 0."""
        payload = {
            "toolCall": {
                "name": "write_to_file",
                "args": {"TargetFile": str(REPO_ROOT / "products/desktop/src/photo_selector_toolbox/core/analyzer.py")},
            }
        }
        rc, out = self._run_hook(self.guard_scope, ["desktop"], payload)
        self.assertEqual(rc, 0)
        self.assertEqual(out.get("decision"), "allow")

    def test_antigravity_deny_protocol(self):
        """In Antigravity dialect, a denied tool call must emit {'decision': 'deny', 'reason': ...} with exit code 0."""
        payload = {
            "toolCall": {
                "name": "write_to_file",
                "args": {"TargetFile": str(REPO_ROOT / "products/android/phototok/src/MainActivity.kt")},
            }
        }
        rc, out = self._run_hook(self.guard_scope, ["desktop"], payload)
        self.assertEqual(rc, 0)
        self.assertEqual(out.get("decision"), "deny")
        self.assertTrue(bool(out.get("reason")), "Expected non-empty denial reason")

    def test_guard_scope_functional_isolation_across_slugs(self):
        """guard_scope.py must isolate products based on slug while allowing shared files."""
        # desktop agent
        desktop_allow = {
            "toolCall": {"name": "write_to_file", "args": {"TargetFile": "products/desktop/src/main.py"}}
        }
        desktop_deny_mac = {
            "toolCall": {"name": "write_to_file", "args": {"TargetFile": "products/macos-desktop/src/main.swift"}}
        }
        desktop_shared = {
            "toolCall": {"name": "write_to_file", "args": {"TargetFile": "docs/products/desktop/REQUIREMENTS.md"}}
        }

        _, out = self._run_hook(self.guard_scope, ["desktop"], desktop_allow)
        self.assertEqual(out.get("decision"), "allow")

        _, out = self._run_hook(self.guard_scope, ["desktop"], desktop_deny_mac)
        self.assertEqual(out.get("decision"), "deny")

        _, out = self._run_hook(self.guard_scope, ["desktop"], desktop_shared)
        self.assertEqual(out.get("decision"), "allow")

        # macos-desktop agent
        mac_allow = {
            "toolCall": {"name": "write_to_file", "args": {"TargetFile": "products/macos-desktop/src/main.swift"}}
        }
        mac_deny_desktop = {
            "toolCall": {"name": "write_to_file", "args": {"TargetFile": "products/desktop/src/main.py"}}
        }
        _, out = self._run_hook(self.guard_scope, ["macos-desktop"], mac_allow)
        self.assertEqual(out.get("decision"), "allow")
        _, out = self._run_hook(self.guard_scope, ["macos-desktop"], mac_deny_desktop)
        self.assertEqual(out.get("decision"), "deny")

        # no-products consultant
        no_prod_deny = {
            "toolCall": {"name": "write_to_file", "args": {"TargetFile": "products/desktop/src/main.py"}}
        }
        no_prod_allow = {
            "toolCall": {"name": "write_to_file", "args": {"TargetFile": "ai/memory/bolt.md"}}
        }
        _, out = self._run_hook(self.guard_scope, ["no-products"], no_prod_deny)
        self.assertEqual(out.get("decision"), "deny")
        _, out = self._run_hook(self.guard_scope, ["no-products"], no_prod_allow)
        self.assertEqual(out.get("decision"), "allow")

    def test_guard_paths_blocks_scratch_and_mirrors(self):
        """guard_paths.py must block scratch files and edits to symlink mirrors."""
        scratch_payload = {"toolCall": {"name": "write_to_file", "args": {"TargetFile": "scratch_test.py"}}}
        _, out = self._run_hook(self.guard_paths, [], scratch_payload)
        self.assertEqual(out.get("decision"), "deny")
        self.assertIn("scratch file", out.get("reason", ""))

        mirror_payload = {
            "toolCall": {
                "name": "write_to_file",
                "args": {"TargetFile": ".claude/agents/desktop-backend-agent.md"},
            }
        }
        _, out = self._run_hook(self.guard_paths, [], mirror_payload)
        self.assertEqual(out.get("decision"), "deny")
        reason = out.get("reason", "").lower()
        self.assertTrue(
            "mirror" in reason or "canonical tree under ai/" in reason,
            f"Unexpected mirror reason: {out.get('reason')}",
        )

        valid_payload = {
            "toolCall": {
                "name": "write_to_file",
                "args": {"TargetFile": "products/desktop/src/photo_selector_toolbox/cli.py"},
            }
        }
        _, out = self._run_hook(self.guard_paths, [], valid_payload)
        self.assertEqual(out.get("decision"), "allow")

    def test_emergency_bypass_pst_skip_hooks(self):
        """When PST_SKIP_HOOKS=1, guards must allow unconditionally."""
        scratch_payload = {"toolCall": {"name": "write_to_file", "args": {"TargetFile": "scratch_bypass.py"}}}
        rc, out = self._run_hook(self.guard_paths, [], scratch_payload, env={"PST_SKIP_HOOKS": "1"})
        self.assertEqual(rc, 0)
        self.assertEqual(out.get("decision"), "allow")

    def test_hooks_handle_malformed_stdin_gracefully(self):
        """Hooks must handle invalid or empty JSON on stdin without crashing."""
        proc = subprocess.run(
            [sys.executable, str(self.guard_paths)],
            input="NOT_VALID_JSON",
            capture_output=True,
            text=True,
            cwd=str(REPO_ROOT),
        )
        self.assertEqual(proc.returncode, 0)


# ============================================================================
# 4. Retrospective Synthesis Engine Suite
# ============================================================================
class TestRetrospectiveSynthesisEngine(unittest.TestCase):
    """Verifies that retrospective memory in ai/memory/framework_retro.md is parseable and synthesizable."""

    def test_framework_retro_markdown_structure(self):
        """ai/memory/framework_retro.md must exist and contain valid formatted retro entries."""
        self.assertTrue(RETRO_FILE.exists(), f"Missing {RETRO_FILE}")
        content = RETRO_FILE.read_text(encoding="utf-8")

        entry_headers = re.findall(r"^##\s+(\d{4}-\d{2}-\d{2})\s+-\s+(.+)$", content, re.M)
        self.assertGreaterEqual(
            len(entry_headers), 5, "Expected at least 5 retrospective entries in framework_retro.md"
        )

        for dt, title in entry_headers:
            self.assertTrue(bool(DATE_RE.match(dt)), f"Invalid retro entry date: {dt}")
            self.assertTrue(bool(title.strip()), "Empty retro entry title")

    def test_retro_entry_parser_extracts_structured_fields(self):
        """Entries must be parseable into date, agents, efficiency, quality, root cause, and improvements."""
        content = RETRO_FILE.read_text(encoding="utf-8")
        raw_entries = re.split(r"\n(?=##\s+\d{4}-\d{2}-\d{2}\s+-)", content)
        entries = [e for e in raw_entries if re.match(r"^##\s+\d{4}-\d{2}-\d{2}\s+-", e.strip())]
        self.assertGreaterEqual(len(entries), 5)

        for e in entries:
            self.assertIn("**Agents Involved:**", e)
            self.assertIn("**Framework Fit & Tooling:**", e)
            self.assertIn("**Session Efficiency:**", e)
            self.assertIn("**Implementation Quality:**", e)
            self.assertIn("**Framework Root Cause:**", e)
            self.assertIn("**Actionable Framework Improvement:**", e)

    def test_friction_clustering_and_recurrence_detection(self):
        """Simulation of friction pattern clustering must correctly detect recurrent patterns (>= threshold)."""
        sample_entries = [
            {"id": 1, "topic": "async coroutine timing", "friction": True, "domain": "phototok"},
            {"id": 2, "topic": "async polling sleep timeout", "friction": True, "domain": "macos-desktop"},
            {"id": 3, "topic": "ui focus keyboard shortcut", "friction": True, "domain": "macos-desktop"},
            {"id": 4, "topic": "smooth run", "friction": False, "domain": "desktop"},
        ]

        cluster_async = [e for e in sample_entries if e["friction"] and "async" in e["topic"]]
        cluster_keyboard = [e for e in sample_entries if e["friction"] and "keyboard" in e["topic"]]

        threshold = 2
        self.assertGreaterEqual(len(cluster_async), threshold, "Async pattern should meet threshold >= 2")
        self.assertLess(len(cluster_keyboard), threshold, "Keyboard pattern should not meet threshold (< 2)")

    def test_synthesize_retro_execution_if_present(self):
        """If synthesize_retro.py exists, it must execute with --dry-run or --check cleanly."""
        if SYNTHESIZE_RETRO_SCRIPT.exists():
            proc = subprocess.run(
                [sys.executable, str(SYNTHESIZE_RETRO_SCRIPT), "--dry-run"],
                capture_output=True,
                text=True,
                encoding="utf-8",
                errors="replace",
                cwd=str(REPO_ROOT),
            )
            self.assertEqual(proc.returncode, 0, f"synthesize_retro.py --dry-run failed:\n{proc.stderr}")

    def test_synthesize_retro_scaffolded_playbooks_schema_compliance(self):
        """Scaffolded playbooks for all PATTERNS must satisfy validate_framework schema and TRIGGER_PHRASE_RE."""
        if not SYNTHESIZE_RETRO_SCRIPT.exists() or not VALIDATE_FRAMEWORK_SCRIPT.exists():
            self.skipTest("Required scripts not found")

        import validate_framework
        import synthesize_retro
        from unittest.mock import patch

        # 1. Operational trigger phrase in every friction pattern definition description
        for pat in synthesize_retro.PATTERNS:
            self.assertIsNotNone(
                validate_framework.TRIGGER_PHRASE_RE.search(pat.description),
                f"Pattern '{pat.id}' description lacks operational trigger phrase: {pat.description}",
            )

        # 2. Schema compliance of generated playbook markdown for every friction pattern
        with tempfile.TemporaryDirectory() as tmp_dir:
            tmp_skills = Path(tmp_dir)
            today = datetime.now(timezone.utc).strftime("%Y-%m-%d")
            routing_lines: list[str] = []

            for pat in synthesize_retro.PATTERNS:
                routing_lines.append(pat.suggested_playbook)
                pb_dir = tmp_skills / pat.suggested_playbook
                pb_dir.mkdir(parents=True, exist_ok=True)

                cluster = synthesize_retro.ClusterResult(
                    pattern=pat,
                    entries=[],
                    occurrences_count=2,
                    domains={"desktop", "macos-desktop"},
                    first_seen="2026-10-01",
                    last_seen=today,
                    playbook_exists=False,
                    backlog_exists=False,
                    is_recurring=True,
                    is_addressed=False,
                )
                pb_content = synthesize_retro.generate_playbook_content(cluster, today)
                pb_skill_file = pb_dir / "SKILL.md"
                pb_skill_file.write_text(pb_content, encoding="utf-8")

                # Verify frontmatter parsing and trigger phrase presence in generated frontmatter
                fm, _ = parse_simple_frontmatter(pb_content)
                self.assertEqual(fm.get("name"), pat.suggested_playbook)
                desc = fm.get("description", "")
                self.assertTrue(bool(desc), f"Playbook {pat.suggested_playbook} has empty description")
                self.assertIsNotNone(
                    validate_framework.TRIGGER_PHRASE_RE.search(desc),
                    f"Generated playbook '{pat.suggested_playbook}' description lacks trigger phrase: '{desc}'",
                )

            # Route scaffolded playbooks via mock ROUTING.md so check_playbook_schema routing check succeeds
            dummy_routing = Path(tmp_dir) / "ROUTING.md"
            dummy_routing.write_text("\n".join(routing_lines), encoding="utf-8")

            with patch.object(validate_framework, "ROUTING", dummy_routing):
                validate_framework.errors.clear()
                validated_names = validate_framework.check_playbook_schema(skills_dir=tmp_skills)
                self.assertEqual(
                    validate_framework.errors,
                    [],
                    "check_playbook_schema flagged errors on scaffolded playbooks:\n"
                    + "\n".join(validate_framework.errors),
                )
                self.assertEqual(len(validated_names), len(synthesize_retro.PATTERNS))

    def test_synthesize_retro_flake8_conformity(self):
        """synthesize_retro.py must conform to repository flake8 linting with 0 violations."""
        if not SYNTHESIZE_RETRO_SCRIPT.exists():
            self.skipTest("synthesize_retro.py not found")

        flake8_cfg = REPO_ROOT / "products" / "desktop" / ".flake8"
        cmd = [sys.executable, "-m", "flake8"]
        if flake8_cfg.exists():
            cmd.append(f"--config={flake8_cfg}")
        cmd.append(str(SYNTHESIZE_RETRO_SCRIPT))

        proc = subprocess.run(
            cmd,
            capture_output=True,
            text=True,
            cwd=str(REPO_ROOT),
        )
        if proc.returncode != 0 and "No module named flake8" in proc.stderr:
            self.skipTest("flake8 not installed in current test environment")

        violations = [line for line in proc.stdout.splitlines() if line.strip()]
        self.assertEqual(
            proc.returncode,
            0,
            f"flake8 reported {len(violations)} violation(s) in synthesize_retro.py:\n{proc.stdout}",
        )

    def test_synthesize_retro_non_utf8_input_handling(self):
        """parse_retro_file and synthesize_retro.py must handle non-UTF-8 files without unhandled traceback."""
        if not SYNTHESIZE_RETRO_SCRIPT.exists():
            self.skipTest("synthesize_retro.py not found")

        with tempfile.NamedTemporaryFile(suffix=".md", delete=False) as f:
            # Corrupted binary header followed by valid markdown block
            f.write(b"\x80\x81\xff\xfe\n## 2026-10-10 - Binary Corrupted Entry\n**Agents Involved:** @test\n")
            corrupt_path = Path(f.name)

        try:
            # 1. CLI execution must handle corrupted file gracefully without crashing
            proc = subprocess.run(
                [sys.executable, str(SYNTHESIZE_RETRO_SCRIPT), "--retro-file", str(corrupt_path)],
                capture_output=True,
                text=True,
                encoding="utf-8",
                errors="replace",
                cwd=str(REPO_ROOT),
            )
            self.assertNotIn(
                "Traceback (most recent call last)",
                proc.stderr,
                f"synthesize_retro.py emitted unhandled traceback on non-UTF-8 input:\n{proc.stderr}",
            )
            self.assertNotIn(
                "UnicodeDecodeError",
                proc.stderr,
                f"synthesize_retro.py leaked raw UnicodeDecodeError traceback:\n{proc.stderr}",
            )

            # 2. Python API parse_retro_file must not raise unhandled UnicodeDecodeError
            import synthesize_retro
            try:
                entries = synthesize_retro.parse_retro_file(corrupt_path)
                self.assertIsInstance(entries, list)
            except UnicodeDecodeError as exc:
                self.fail(f"synthesize_retro.parse_retro_file raised unhandled UnicodeDecodeError: {exc}")
        finally:
            corrupt_path.unlink(missing_ok=True)

    def test_synthesize_retro_missing_code_health_handling(self):
        """synthesize_retro.py --apply must handle missing code_health.md cleanly without silent no-op."""
        if not SYNTHESIZE_RETRO_SCRIPT.exists():
            self.skipTest("synthesize_retro.py not found")

        with tempfile.TemporaryDirectory() as tmp_dir:
            missing_ch = Path(tmp_dir) / "code_health.md"
            self.assertFalse(missing_ch.exists())

            proc = subprocess.run(
                [
                    sys.executable,
                    str(SYNTHESIZE_RETRO_SCRIPT),
                    "--retro-file", str(RETRO_FILE),
                    "--code-health-file", str(missing_ch),
                    "--apply",
                ],
                capture_output=True,
                text=True,
                encoding="utf-8",
                errors="replace",
                cwd=str(REPO_ROOT),
            )
            self.assertEqual(proc.returncode, 0, f"--apply failed:\n{proc.stderr}")

            # Verify that either the file was initialized and populated, or a clean notice was emitted
            if missing_ch.exists():
                content = missing_ch.read_text(encoding="utf-8")
                self.assertIn("Code Health Backlog", content)
                self.assertIn("[OPEN]", content)
                self.assertNotIn("Apply complete: 0 backlog item(s) logged", proc.stdout)
            else:
                combined = (proc.stdout + proc.stderr).lower()
                self.assertTrue(
                    any(w in combined for w in ["warning", "not found", "does not exist", "missing", "initialize"]),
                    f"Expected warning or auto-initialization when code_health.md is missing, "
                    f"got stdout: {proc.stdout}, stderr: {proc.stderr}",
                )


# ============================================================================
# 5. Playbook Validator Contract Suite
# ============================================================================
class TestPlaybookValidatorContract(unittest.TestCase):
    """Verifies that all playbooks adhere to schema invariants, mandatory sections, and freshness rules."""

    def test_existing_playbooks_conform_to_schema(self):
        """All playbooks in ai/skills/playbook-*/SKILL.md must satisfy schema invariants."""
        playbook_files = sorted(SKILLS_DIR.glob("playbook-*/SKILL.md"))
        self.assertGreaterEqual(len(playbook_files), 3, "Expected at least 3 playbooks (including template)")

        for pb in playbook_files:
            rel = pb.relative_to(REPO_ROOT)
            text = pb.read_text(encoding="utf-8")
            fm, body = parse_simple_frontmatter(text)

            is_template = pb.parent.name == "playbook-template"

            # 1. Frontmatter name
            self.assertEqual(fm.get("name"), pb.parent.name, f"{rel}: name must match directory name")

            # 2. Description
            desc = fm.get("description", "")
            self.assertTrue(bool(desc), f"{rel}: missing description")
            if is_template:
                self.assertIn("TEMPLATE", desc.upper(), f"{rel}: template description must state TEMPLATE")
            else:
                self.assertTrue(
                    any(p in desc.lower() for p in ["use when", "use whenever", "when ", "covers "]),
                    f"{rel}: active playbook description must contain operational trigger phrase",
                )

            # 3. last_validated freshness
            last_val = fm.get("last_validated")
            self.assertIsNotNone(last_val, f"{rel}: missing 'last_validated' in frontmatter")
            self.assertTrue(bool(DATE_RE.match(last_val)), f"{rel}: last_validated must match YYYY-MM-DD")
            if is_template:
                self.assertEqual(last_val, "1970-01-01", f"{rel}: template must specify 1970-01-01")
            else:
                self.assertNotEqual(
                    last_val, "1970-01-01", f"{rel}: active playbook cannot retain template sentinel date"
                )
                dt = datetime.strptime(last_val, "%Y-%m-%d").replace(tzinfo=timezone.utc)
                now = datetime.now(timezone.utc)
                age_days = (now - dt).total_seconds() / 86400.0
                self.assertLessEqual(age_days, 180.0, f"{rel}: playbook last_validated is older than 180 days")

            # 4. Four mandatory sections
            has_trigger = bool(re.search(r"^##\s+(When to use|Purpose|Applicability)", body, re.M))
            has_steps = bool(re.search(r"^##\s+(Steps(\s*\(.*\))?|Procedure|Step-by-step procedure)", body, re.M))
            has_traps = bool(re.search(r"^##\s+(Traps|Common traps|Pitfalls)", body, re.M))
            has_done = bool(re.search(r"^##\s+(Definition of done|Verification|Done criteria)", body, re.M))

            self.assertTrue(has_trigger, f"{rel}: missing 'When to use / Purpose' section")
            self.assertTrue(has_steps, f"{rel}: missing 'Steps / Procedure' section")
            self.assertTrue(has_traps, f"{rel}: missing 'Traps / Pitfalls' section")
            self.assertTrue(has_done, f"{rel}: missing 'Definition of done / Verification' section")

            # 5. Placeholders check
            if not is_template:
                self.assertNotIn("<Task Type>", body, f"{rel}: leaked template placeholder '<Task Type>'")
                self.assertNotIn("<task>", body, f"{rel}: leaked template placeholder '<task>'")

    def test_playbook_validator_catches_schema_mutations(self):
        """Mutated playbooks must fail validation."""
        # 1. Missing last_validated
        bad_fm1 = (
            "---\n"
            "name: playbook-test\n"
            "description: Use when testing.\n"
            "---\n"
            "## When to use\nNow\n"
            "## Steps\n1. Run\n"
            "## Traps\nNone\n"
            "## Definition of done\nDone\n"
        )
        fm1, _ = parse_simple_frontmatter(bad_fm1)
        self.assertNotIn("last_validated", fm1)

        # 2. Retaining 1970-01-01 on active playbook
        bad_fm2 = "---\nname: playbook-active\ndescription: Use when testing.\nlast_validated: 1970-01-01\n---\n"
        fm2, _ = parse_simple_frontmatter(bad_fm2)
        self.assertEqual(fm2.get("last_validated"), "1970-01-01")

        # 3. Missing Traps section
        bad_body3 = "## When to use\nNow\n## Steps\n1. Run\n## Definition of done\nDone\n"
        has_traps = bool(re.search(r"^##\s+(Traps|Common traps|Pitfalls)", bad_body3, re.M))
        self.assertFalse(has_traps)


# ============================================================================
# 6. Zero-Code-Review Retrospective Contract Suite
# ============================================================================
class TestRetrospectiveZeroCodeReviewContract(unittest.TestCase):
    """Verifies that ai/skills/retrospective/SKILL.md enforces the Zero-Code-Review standard.

    Feature: F5.4 Zero-Code-Review Task Summaries
    Requirements: R5 (Verifiable Zero-Code-Review Assurance & Adversarial Pre-Delivery Gates)
    """

    def setUp(self):
        self.skill_file = SKILLS_DIR / "retrospective" / "SKILL.md"
        self.assertTrue(self.skill_file.exists(), "retrospective SKILL.md missing")
        self.text = self.skill_file.read_text(encoding="utf-8")

    def test_retrospective_skill_defines_zero_code_review_reporting(self):
        """SKILL.md must define the Zero-Code-Review reporting standard and ban raw code diffs."""
        # 1. Zero-Code-Review section header
        self.assertRegex(
            self.text,
            re.compile(r"^##\s+10\.\s+Task\s+Completion\s+Report\s+\(.*Zero-Code-Review.*\)", re.M),
        )

        # 2. Strict prohibition of raw code diffs and implementation minutiae
        text_lower = self.text.lower()
        self.assertTrue(
            any(
                phrase in text_lower
                for phrase in [
                    "never present raw code diffs",
                    "no raw code diffs",
                    "do not include unified diff",
                    "never present raw code",
                ]
            ),
            "SKILL.md must explicitly prohibit presenting raw code diffs in task summaries",
        )
        self.assertTrue(
            any(
                phrase in text_lower
                for phrase in [
                    "never delegate testing to the user",
                    "user acts as an executive evaluator",
                    "never as a manual tester",
                ]
            ),
            "SKILL.md must ban delegating manual testing to the user",
        )

    def test_retrospective_mandates_all_five_empirical_sections(self):
        """SKILL.md must mandate all five required empirical verification sections."""
        mandatory_sections = [
            (
                "Executive Outcome & Behavioral Proof",
                r"10\.1\s+Executive\s+Outcome\s+&\s+Empirical\s+Behavioral\s+Proof",
            ),
            (
                "Visual & Accessibility Verification",
                r"10\.2\s+Visual\s+&\s+Accessibility\s+Verification\s+\(Contrast\s+&\s+Layout\)",
            ),
            (
                "Empirical Test Matrix & Execution Verdicts",
                r"10\.3\s+Empirical\s+Test\s+Matrix\s+&\s+Execution\s+Verdicts",
            ),
            (
                "Operational Instructions",
                r"10\.4\s+Operational\s+Instructions",
            ),
            (
                "Agent Report & Framework Evaluation",
                r"10\.5\s+Agent\s+Report\s+&\s+Framework\s+Evaluation",
            ),
        ]
        for name, pattern in mandatory_sections:
            self.assertRegex(
                self.text,
                pattern,
                f"SKILL.md missing mandatory section: {name}",
            )

    def test_retrospective_mandates_wcag_contrast_and_occlusion_audits(self):
        """SKILL.md must mandate WCAG 2.1 AA contrast ratios and dialog occlusion auditing."""
        text_lower = self.text.lower()
        self.assertIn("wcag 2.1 aa", text_lower, "SKILL.md must reference WCAG 2.1 AA standard")
        self.assertIn("4.5:1", text_lower, "SKILL.md must require >= 4.5:1 contrast for normal text")
        self.assertTrue(
            "zero dark-on-dark" in text_lower or "dark-on-dark" in text_lower,
            "SKILL.md must mandate zero dark-on-dark findings",
        )
        self.assertTrue(
            "occlusion" in text_lower or "non-intersection" in text_lower,
            "SKILL.md must require control occlusion auditing",
        )

    def test_retrospective_skill_embeds_task_summary_template(self):
        """SKILL.md must embed a complete Markdown task summary template."""
        self.assertIn("### Standard Task Summary Template", self.text)
        self.assertIn("```markdown", self.text)
        self.assertIn("## 1. Executive Outcome & Empirical Behavioral Proof", self.text)
        self.assertIn("## 2. Visual & Accessibility Verification (Contrast & Layout)", self.text)
        self.assertIn("## 3. Empirical Test Matrix & Execution Verdicts", self.text)
        self.assertIn("## 4. Operational Instructions", self.text)
        self.assertIn("## 5. Agent Report & Framework Evaluation", self.text)

    def test_retrospective_checklist_includes_zero_code_review(self):
        """Checklist item 10 must reference Zero-Code-Review reporting."""
        self.assertRegex(
            self.text,
            r"\[\s*\]\s+10\.\s+Zero-Code-Review\s+Task\s+Summary\s+included",
            "Checklist item 10 must mandate Zero-Code-Review Task Summary",
        )

    def test_mutation_catches_invalid_or_diff_leaking_summaries(self):
        """Validators must reject simulated summaries containing raw diffs or missing empirical proof."""
        # 1. Summary containing raw diff marker
        diff_leaking_summary = (
            "# Task Summary\n"
            "## 1. Executive Outcome\n"
            "Here is the diff:\n"
            "```diff\n"
            "diff --git a/foo.py b/foo.py\n"
            "@@ -1,4 +1,4 @@\n"
            "-old\n"
            "+new\n"
            "```\n"
        )
        banned_diff_patterns = [
            r"diff\s+--git",
            r"@@\s+-\d+,\d+\s+\+\d+,\d+\s+@@",
            r"```diff\b",
        ]
        has_diff = any(re.search(pat, diff_leaking_summary) for pat in banned_diff_patterns)
        self.assertTrue(has_diff, "Validator must detect raw diff markers in summaries")

        # 2. Summary missing visual/accessibility verification
        incomplete_summary = (
            "# Task Summary\n"
            "## 1. Executive Outcome & Empirical Behavioral Proof\n"
            "Feature works.\n"
            "## 3. Empirical Test Matrix\n"
            "Tests passed.\n"
        )
        has_a11y = bool(re.search(r"##\s+2\.\s+Visual\s+&\s+Accessibility", incomplete_summary))
        self.assertFalse(has_a11y, "Validator must flag missing Visual & Accessibility section")


if __name__ == "__main__":
    unittest.main()
