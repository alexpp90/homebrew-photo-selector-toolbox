"""test_validate_framework.py — Unit & Regression Tests for Framework Validator and Sync.

Verifies:
1. Toolchain parity verification across Cursor, Windsurf, Copilot, Claude, Antigravity, and Gemini.
2. Unowned product detection when unmapped product directories with code are introduced.
3. Static boundary leak detection for forbidden cross-product imports and path escapes.
4. Copy-mode integrity checking, including detection of modified copies, missing files, and orphans.
5. Clean execution of validate_framework.py and sync_framework.py on repository HEAD.
"""

from __future__ import annotations

import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

REPO_ROOT = Path(__file__).resolve().parents[4]
VALIDATE_SCRIPT = REPO_ROOT / "ai" / "skills" / "sync-framework" / "scripts" / "validate_framework.py"
SYNC_SCRIPT = REPO_ROOT / "ai" / "skills" / "sync-framework" / "scripts" / "sync_framework.py"


class TestValidateFramework(unittest.TestCase):
    """Test suite for validate_framework.py and sync_framework.py."""

    def test_run_on_head(self):
        """validate_framework.py must execute with exit code 0 on unmodified HEAD."""
        proc = subprocess.run(
            [sys.executable, str(VALIDATE_SCRIPT)],
            cwd=str(REPO_ROOT),
            capture_output=True,
            text=True,
        )
        self.assertEqual(
            proc.returncode,
            0,
            f"validate_framework.py failed on HEAD:\nSTDOUT:\n{proc.stdout}\nSTDERR:\n{proc.stderr}",
        )
        self.assertIn("framework validation: OK", proc.stdout)

    def test_sync_framework_check_on_head(self):
        """sync_framework.py --check must return 0 on unmodified HEAD."""
        proc = subprocess.run(
            [sys.executable, str(SYNC_SCRIPT), "--check"],
            cwd=str(REPO_ROOT),
            capture_output=True,
            text=True,
        )
        self.assertEqual(
            proc.returncode,
            0,
            f"sync_framework.py --check failed on HEAD:\nSTDOUT:\n{proc.stdout}\nSTDERR:\n{proc.stderr}",
        )
        self.assertIn("100% in sync", proc.stdout)

    def test_detects_unowned_product(self):
        """Validator must fail when an untracked product directory with source is added."""
        with tempfile.TemporaryDirectory(dir=str(REPO_ROOT / "products")) as tmp_dir:
            tmp_path = Path(tmp_dir)
            (tmp_path / "src").mkdir(parents=True, exist_ok=True)
            (tmp_path / "src" / "main.py").write_text("print('rogue product code')\n", encoding="utf-8")

            proc = subprocess.run(
                [sys.executable, str(VALIDATE_SCRIPT)],
                cwd=str(REPO_ROOT),
                capture_output=True,
                text=True,
            )
            self.assertEqual(proc.returncode, 1)
            self.assertIn("unowned product directory discovered", proc.stderr)

    def test_detects_boundary_leak_import(self):
        """Validator must fail if a cross-product import is introduced into product source."""
        test_file = REPO_ROOT / "products" / "desktop" / "src" / "photo_selector_toolbox" / "leak_test_temp.py"
        try:
            test_file.write_text("import photo_selector_linux\n", encoding="utf-8")
            proc = subprocess.run(
                [sys.executable, str(VALIDATE_SCRIPT)],
                cwd=str(REPO_ROOT),
                capture_output=True,
                text=True,
            )
            self.assertEqual(proc.returncode, 1)
            self.assertIn("product boundary leak — imports Linux Desktop code", proc.stderr)
        finally:
            if test_file.exists():
                test_file.unlink()

    def test_detects_copy_mode_drift(self):
        """In copy mode, validator must fail if a mirrored file diverges from canonical source."""
        with tempfile.TemporaryDirectory() as tmp_dir:
            tmp_repo = Path(tmp_dir)
            ai_dir = tmp_repo / "ai" / "agents"
            ai_dir.mkdir(parents=True)
            canonical = ai_dir / "test-agent.md"
            agent_content = (
                "---\n"
                "name: test-agent\n"
                "description: Test\n"
                "tools: Glob\n"
                "model: inherit\n"
                "---\n"
                "Body\n"
            )
            canonical.write_text(agent_content, encoding="utf-8")

            claude_dir = tmp_repo / ".claude" / "agents"
            claude_dir.mkdir(parents=True)
            copy_file = claude_dir / "test-agent.md"
            copy_file.write_text("DIVERGENT CONTENT\n", encoding="utf-8")

            sys.path.insert(0, str(VALIDATE_SCRIPT.parent))
            import validate_framework  # type: ignore
            old_repo = validate_framework.REPO
            try:
                validate_framework.REPO = tmp_repo
                validate_framework.errors.clear()
                validate_framework._compare_trees(claude_dir, ai_dir, ".claude/agents")
                self.assertTrue(any("copy-mode drift detected" in e for e in validate_framework.errors))
            finally:
                validate_framework.REPO = old_repo
                validate_framework.errors.clear()

    def test_detects_missing_file_in_copy_mode(self):
        """In copy mode, validator must fail if a canonical file is missing in mirror."""
        with tempfile.TemporaryDirectory() as tmp_dir:
            tmp_repo = Path(tmp_dir)
            ai_dir = tmp_repo / "ai" / "agents"
            ai_dir.mkdir(parents=True)
            canonical = ai_dir / "missing-agent.md"
            canonical.write_text("Canonical content\n", encoding="utf-8")

            claude_dir = tmp_repo / ".claude" / "agents"
            claude_dir.mkdir(parents=True)

            sys.path.insert(0, str(VALIDATE_SCRIPT.parent))
            import validate_framework  # type: ignore
            old_repo = validate_framework.REPO
            try:
                validate_framework.REPO = tmp_repo
                validate_framework.errors.clear()
                validate_framework._compare_trees(claude_dir, ai_dir, ".claude/agents")
                self.assertTrue(any("missing in copy mode" in e for e in validate_framework.errors))
            finally:
                validate_framework.REPO = old_repo
                validate_framework.errors.clear()

    def test_detects_orphaned_file_in_copy_mode(self):
        """In copy mode, validator must fail if an orphaned file exists in mirror."""
        with tempfile.TemporaryDirectory() as tmp_dir:
            tmp_repo = Path(tmp_dir)
            ai_dir = tmp_repo / "ai" / "agents"
            ai_dir.mkdir(parents=True)

            claude_dir = tmp_repo / ".claude" / "agents"
            claude_dir.mkdir(parents=True)
            orphan = claude_dir / "orphan-agent.md"
            orphan.write_text("Orphan content\n", encoding="utf-8")

            sys.path.insert(0, str(VALIDATE_SCRIPT.parent))
            import validate_framework  # type: ignore
            old_repo = validate_framework.REPO
            try:
                validate_framework.REPO = tmp_repo
                validate_framework.errors.clear()
                validate_framework._compare_trees(claude_dir, ai_dir, ".claude/agents")
                self.assertTrue(any("orphaned file in copy tree" in e for e in validate_framework.errors))
            finally:
                validate_framework.REPO = old_repo
                validate_framework.errors.clear()

    def test_copy_mode_idempotent_consecutive_runs(self):
        """sync_framework.py copy mode must succeed across consecutive runs without PermissionError."""
        with tempfile.TemporaryDirectory() as tmp_dir:
            tmp_repo = Path(tmp_dir)
            # Setup minimal canonical source tree
            (tmp_repo / "ai" / "agents").mkdir(parents=True)
            (tmp_repo / "ai" / "skills").mkdir(parents=True)
            (tmp_repo / "ai" / "rules").mkdir(parents=True)
            (tmp_repo / "ai" / "commands").mkdir(parents=True)
            (tmp_repo / "ai" / "ROUTING.md").write_text("# Routing\n", encoding="utf-8")
            (tmp_repo / ".claude").mkdir(parents=True)
            (tmp_repo / ".agents").mkdir(parents=True)
            (tmp_repo / ".gemini").mkdir(parents=True)

            agent_path = tmp_repo / "ai" / "agents" / "demo-agent.md"
            agent_path.write_text(
                "---\n"
                "name: demo-agent\n"
                "description: Demo Agent\n"
                "---\n"
                "Demo instructions.\n",
                encoding="utf-8",
            )

            sys.path.insert(0, str(SYNC_SCRIPT.parent))
            import sync_framework  # type: ignore

            old_repo = sync_framework.REPO
            old_ai = sync_framework.AI
            old_agents = sync_framework.AGENTS
            old_skills = sync_framework.SKILLS
            old_rules = sync_framework.RULES
            old_commands = sync_framework.COMMANDS
            old_routing = sync_framework.ROUTING
            old_sync_mode = sync_framework.SYNC_MODE_FILE

            try:
                sync_framework.REPO = tmp_repo
                sync_framework.AI = tmp_repo / "ai"
                sync_framework.AGENTS = tmp_repo / "ai" / "agents"
                sync_framework.SKILLS = tmp_repo / "ai" / "skills"
                sync_framework.RULES = tmp_repo / "ai" / "rules"
                sync_framework.COMMANDS = tmp_repo / "ai" / "commands"
                sync_framework.ROUTING = tmp_repo / "ai" / "ROUTING.md"
                sync_framework.SYNC_MODE_FILE = tmp_repo / ".sync_mode"

                # 1. Initial run: establish copy mode
                ret1 = sync_framework.run_sync(mode="copy")
                self.assertEqual(ret1, 0, "Initial run_sync(mode='copy') failed")

                # Verify copied files and banner exist and are read-only
                copied_agent = tmp_repo / ".claude" / "agents" / "demo-agent.md"
                banner_file = tmp_repo / ".claude" / "DO_NOT_EDIT.md"
                self.assertTrue(copied_agent.exists(), "Copied agent file was not created")
                self.assertTrue(banner_file.exists(), "Banner DO_NOT_EDIT.md was not created")
                self.assertFalse(os.access(copied_agent, os.W_OK), "Copied agent should be read-only")
                self.assertFalse(os.access(banner_file, os.W_OK), "Banner file should be read-only")

                # 2. Second run: MUST be idempotent on existing read-only files without PermissionError
                try:
                    ret2 = sync_framework.run_sync(mode="copy")
                except PermissionError as exc:
                    self.fail(f"Consecutive run_sync(mode='copy') raised PermissionError: {exc}")
                self.assertEqual(ret2, 0, "Consecutive run_sync(mode='copy') failed")

                # 3. Third run: modify source file and re-sync over existing read-only destination
                agent_path.write_text(
                    "---\n"
                    "name: demo-agent\n"
                    "description: Demo Agent Updated\n"
                    "---\n"
                    "Updated instructions.\n",
                    encoding="utf-8",
                )
                try:
                    ret3 = sync_framework.run_sync(mode="copy")
                except PermissionError as exc:
                    self.fail(f"Re-syncing modified source raised PermissionError: {exc}")
                self.assertEqual(ret3, 0, "Re-syncing modified source in copy mode failed")
                self.assertIn("Updated instructions.", copied_agent.read_text(encoding="utf-8"))
                self.assertFalse(os.access(copied_agent, os.W_OK), "Updated copy must remain read-only")
            finally:
                sync_framework.REPO = old_repo
                sync_framework.AI = old_ai
                sync_framework.AGENTS = old_agents
                sync_framework.SKILLS = old_skills
                sync_framework.RULES = old_rules
                sync_framework.COMMANDS = old_commands
                sync_framework.ROUTING = old_routing
                sync_framework.SYNC_MODE_FILE = old_sync_mode

    def test_copy_mode_subprocess_consecutive_runs(self):
        """sync_framework.py --copy CLI must be idempotent across consecutive runs."""
        sys.path.insert(0, str(SYNC_SCRIPT.parent))
        import sync_framework  # type: ignore
        initial_mode = sync_framework.get_current_sync_mode()

        try:
            # 1. First invocation: enter copy mode
            proc1 = subprocess.run(
                [sys.executable, str(SYNC_SCRIPT), "--copy"],
                cwd=str(REPO_ROOT),
                capture_output=True,
                text=True,
            )
            self.assertEqual(
                proc1.returncode,
                0,
                f"Initial sync_framework.py --copy failed:\nSTDOUT:\n{proc1.stdout}\nSTDERR:\n{proc1.stderr}",
            )

            # 2. Second consecutive invocation: must not crash with PermissionError
            proc2 = subprocess.run(
                [sys.executable, str(SYNC_SCRIPT), "--copy"],
                cwd=str(REPO_ROOT),
                capture_output=True,
                text=True,
            )
            self.assertEqual(
                proc2.returncode,
                0,
                f"Consecutive sync_framework.py --copy failed:\nSTDOUT:\n{proc2.stdout}\nSTDERR:\n{proc2.stderr}",
            )
            self.assertNotIn("PermissionError", proc2.stderr)

            # 3. Verify sync_framework.py --check passes in copy mode
            proc_check = subprocess.run(
                [sys.executable, str(SYNC_SCRIPT), "--check"],
                cwd=str(REPO_ROOT),
                capture_output=True,
                text=True,
            )
            self.assertEqual(
                proc_check.returncode,
                0,
                f"sync_framework.py --check failed in copy mode:\n{proc_check.stderr}",
            )
        finally:
            # Always restore repository to its initial sync mode
            restore_proc = subprocess.run(
                [sys.executable, str(SYNC_SCRIPT), f"--mode={initial_mode}"],
                cwd=str(REPO_ROOT),
                capture_output=True,
                text=True,
            )
            self.assertEqual(
                restore_proc.returncode,
                0,
                f"Failed to restore sync mode to {initial_mode}:\n{restore_proc.stderr}",
            )

    def test_playbooks_pass_on_head(self):
        """All existing playbooks in ai/skills/ must pass check_playbook_schema cleanly."""
        sys.path.insert(0, str(VALIDATE_SCRIPT.parent))
        import validate_framework  # type: ignore

        validate_framework.errors.clear()
        names = validate_framework.check_playbook_schema()
        self.assertEqual(
            validate_framework.errors, [], f"Playbook schema validation failed on HEAD: {validate_framework.errors}"
        )
        self.assertIn("playbook-template", names)
        self.assertIn("playbook-port-pattern-across-products", names)
        self.assertIn("playbook-verify-android-desktop-on-reference-device", names)

    def test_playbook_detects_missing_mandatory_section(self):
        """Validator must fail when a playbook omits a mandatory section like 'Traps'."""
        with tempfile.TemporaryDirectory() as tmp_dir:
            tmp_skills = Path(tmp_dir)
            pb_dir = tmp_skills / "playbook-sample"
            pb_dir.mkdir(parents=True)
            skill_content = (
                "---\n"
                "name: playbook-sample\n"
                "description: Use when running sample verification.\n"
                "last_validated: 2026-09-01\n"
                "---\n"
                "# Playbook: Sample\n"
                "## When to use\nValid trigger context.\n"
                "## Steps\n1. Run sample step.\n"
                "# Note: missing ## Traps section\n"
                "## Definition of done\nVerification complete.\n"
            )
            (pb_dir / "SKILL.md").write_text(skill_content, encoding="utf-8")

            sys.path.insert(0, str(VALIDATE_SCRIPT.parent))
            import validate_framework  # type: ignore

            validate_framework.errors.clear()
            validate_framework.check_playbook_schema(skills_dir=tmp_skills)
            self.assertTrue(
                any("missing mandatory section 'Traps / Pitfalls'" in e for e in validate_framework.errors),
                f"Expected missing traps error, got {validate_framework.errors}",
            )

    def test_playbook_detects_empty_mandatory_section(self):
        """Validator must fail when a mandatory section heading exists but has empty body."""
        with tempfile.TemporaryDirectory() as tmp_dir:
            tmp_skills = Path(tmp_dir)
            pb_dir = tmp_skills / "playbook-empty-sec"
            pb_dir.mkdir(parents=True)
            skill_content = (
                "---\n"
                "name: playbook-empty-sec\n"
                "description: Use when running sample verification.\n"
                "last_validated: 2026-09-01\n"
                "---\n"
                "# Playbook: Empty Section\n"
                "## When to use\nValid trigger context.\n"
                "## Steps\n1. Run sample step.\n"
                "## Traps\n\n"  # Empty body
                "## Definition of done\nVerification complete.\n"
            )
            (pb_dir / "SKILL.md").write_text(skill_content, encoding="utf-8")

            sys.path.insert(0, str(VALIDATE_SCRIPT.parent))
            import validate_framework  # type: ignore

            validate_framework.errors.clear()
            validate_framework.check_playbook_schema(skills_dir=tmp_skills)
            self.assertTrue(
                any("section '## Traps' is empty" in e for e in validate_framework.errors),
                f"Expected empty section error, got {validate_framework.errors}",
            )

    def test_playbook_detects_missing_last_validated(self):
        """Validator must fail when last_validated date is absent."""
        with tempfile.TemporaryDirectory() as tmp_dir:
            tmp_skills = Path(tmp_dir)
            pb_dir = tmp_skills / "playbook-nodate"
            pb_dir.mkdir(parents=True)
            skill_content = (
                "---\n"
                "name: playbook-nodate\n"
                "description: Use when running sample verification.\n"
                "---\n"
                "# Playbook: No Date\n"
                "## When to use\nValid trigger.\n"
                "## Steps\n1. Steps here.\n"
                "## Traps\nTraps here.\n"
                "## Definition of done\nDone.\n"
            )
            (pb_dir / "SKILL.md").write_text(skill_content, encoding="utf-8")

            sys.path.insert(0, str(VALIDATE_SCRIPT.parent))
            import validate_framework  # type: ignore

            validate_framework.errors.clear()
            validate_framework.check_playbook_schema(skills_dir=tmp_skills)
            self.assertTrue(
                any("missing 'last_validated: YYYY-MM-DD'" in e for e in validate_framework.errors),
                f"Expected missing last_validated error, got {validate_framework.errors}",
            )

    def test_playbook_detects_malformed_last_validated(self):
        """Validator must fail when last_validated has invalid date format or impossible date."""
        with tempfile.TemporaryDirectory() as tmp_dir:
            tmp_skills = Path(tmp_dir)
            pb_dir = tmp_skills / "playbook-baddate"
            pb_dir.mkdir(parents=True)
            skill_content = (
                "---\n"
                "name: playbook-baddate\n"
                "description: Use when running sample verification.\n"
                "last_validated: 2026/09/01\n"
                "---\n"
                "# Playbook: Bad Date\n"
                "## When to use\nValid trigger.\n"
                "## Steps\n1. Steps here.\n"
                "## Traps\nTraps here.\n"
                "## Definition of done\nDone.\n"
            )
            (pb_dir / "SKILL.md").write_text(skill_content, encoding="utf-8")

            sys.path.insert(0, str(VALIDATE_SCRIPT.parent))
            import validate_framework  # type: ignore

            validate_framework.errors.clear()
            validate_framework.check_playbook_schema(skills_dir=tmp_skills)
            self.assertTrue(
                any("invalid last_validated date format" in e for e in validate_framework.errors),
                f"Expected invalid date format error, got {validate_framework.errors}",
            )

    def test_playbook_detects_template_sentinel_1970_on_active_playbook(self):
        """Validator must fail when an active playbook retains the 1970-01-01 template sentinel date."""
        with tempfile.TemporaryDirectory() as tmp_dir:
            tmp_skills = Path(tmp_dir)
            pb_dir = tmp_skills / "playbook-unbumped"
            pb_dir.mkdir(parents=True)
            skill_content = (
                "---\n"
                "name: playbook-unbumped\n"
                "description: Use when running sample verification.\n"
                "last_validated: 1970-01-01\n"
                "---\n"
                "# Playbook: Unbumped\n"
                "## When to use\nValid trigger.\n"
                "## Steps\n1. Steps here.\n"
                "## Traps\nTraps here.\n"
                "## Definition of done\nDone.\n"
            )
            (pb_dir / "SKILL.md").write_text(skill_content, encoding="utf-8")

            sys.path.insert(0, str(VALIDATE_SCRIPT.parent))
            import validate_framework  # type: ignore

            validate_framework.errors.clear()
            validate_framework.check_playbook_schema(skills_dir=tmp_skills)
            self.assertTrue(
                any("retains template default '1970-01-01'" in e for e in validate_framework.errors),
                f"Expected 1970 default error, got {validate_framework.errors}",
            )

    def test_playbook_detects_future_last_validated(self):
        """Validator must fail when last_validated date is in the future."""
        with tempfile.TemporaryDirectory() as tmp_dir:
            tmp_skills = Path(tmp_dir)
            pb_dir = tmp_skills / "playbook-future"
            pb_dir.mkdir(parents=True)
            skill_content = (
                "---\n"
                "name: playbook-future\n"
                "description: Use when running sample verification.\n"
                "last_validated: 2099-01-01\n"
                "---\n"
                "# Playbook: Future\n"
                "## When to use\nValid trigger.\n"
                "## Steps\n1. Steps here.\n"
                "## Traps\nTraps here.\n"
                "## Definition of done\nDone.\n"
            )
            (pb_dir / "SKILL.md").write_text(skill_content, encoding="utf-8")

            sys.path.insert(0, str(VALIDATE_SCRIPT.parent))
            import validate_framework  # type: ignore

            validate_framework.errors.clear()
            validate_framework.check_playbook_schema(skills_dir=tmp_skills)
            self.assertTrue(
                any("in the future" in e for e in validate_framework.errors),
                f"Expected future date error, got {validate_framework.errors}",
            )

    def test_playbook_detects_stale_last_validated(self):
        """Validator must fail when last_validated date is older than 180 days."""
        with tempfile.TemporaryDirectory() as tmp_dir:
            tmp_skills = Path(tmp_dir)
            pb_dir = tmp_skills / "playbook-stale"
            pb_dir.mkdir(parents=True)
            skill_content = (
                "---\n"
                "name: playbook-stale\n"
                "description: Use when running sample verification.\n"
                "last_validated: 2024-01-01\n"
                "---\n"
                "# Playbook: Stale\n"
                "## When to use\nValid trigger.\n"
                "## Steps\n1. Steps here.\n"
                "## Traps\nTraps here.\n"
                "## Definition of done\nDone.\n"
            )
            (pb_dir / "SKILL.md").write_text(skill_content, encoding="utf-8")

            sys.path.insert(0, str(VALIDATE_SCRIPT.parent))
            import validate_framework  # type: ignore

            validate_framework.errors.clear()
            validate_framework.check_playbook_schema(skills_dir=tmp_skills)
            self.assertTrue(
                any("playbook is stale" in e for e in validate_framework.errors),
                f"Expected stale playbook error, got {validate_framework.errors}",
            )

    def test_playbook_detects_unreplaced_template_placeholders(self):
        """Validator must fail when active playbook contains unfilled template placeholders."""
        with tempfile.TemporaryDirectory() as tmp_dir:
            tmp_skills = Path(tmp_dir)
            pb_dir = tmp_skills / "playbook-placeholder"
            pb_dir.mkdir(parents=True)
            skill_content = (
                "---\n"
                "name: playbook-placeholder\n"
                "description: Use when running sample verification.\n"
                "last_validated: 2026-09-01\n"
                "---\n"
                "# Playbook: <Task Type>\n"
                "## When to use\nValid trigger.\n"
                "## Steps\n1. Steps here.\n"
                "## Traps\nTraps here.\n"
                "## Definition of done\nDone.\n"
            )
            (pb_dir / "SKILL.md").write_text(skill_content, encoding="utf-8")

            sys.path.insert(0, str(VALIDATE_SCRIPT.parent))
            import validate_framework  # type: ignore

            validate_framework.errors.clear()
            validate_framework.check_playbook_schema(skills_dir=tmp_skills)
            self.assertTrue(
                any("unreplaced template placeholder '<Task Type>'" in e for e in validate_framework.errors),
                f"Expected placeholder error, got {validate_framework.errors}",
            )

    def test_playbook_detects_missing_operational_trigger_phrase(self):
        """Validator must fail when active playbook description lacks operational trigger phrase."""
        with tempfile.TemporaryDirectory() as tmp_dir:
            tmp_skills = Path(tmp_dir)
            pb_dir = tmp_skills / "playbook-notrigger"
            pb_dir.mkdir(parents=True)
            skill_content = (
                "---\n"
                "name: playbook-notrigger\n"
                "description: Static reference material for photos.\n"
                "last_validated: 2026-09-01\n"
                "---\n"
                "# Playbook: No Trigger\n"
                "## When to use\nValid trigger.\n"
                "## Steps\n1. Steps here.\n"
                "## Traps\nTraps here.\n"
                "## Definition of done\nDone.\n"
            )
            (pb_dir / "SKILL.md").write_text(skill_content, encoding="utf-8")

            sys.path.insert(0, str(VALIDATE_SCRIPT.parent))
            import validate_framework  # type: ignore

            validate_framework.errors.clear()
            validate_framework.check_playbook_schema(skills_dir=tmp_skills)
            self.assertTrue(
                any("description missing operational trigger phrase" in e for e in validate_framework.errors),
                f"Expected missing trigger phrase error, got {validate_framework.errors}",
            )

    def test_playbook_template_validates_cleanly(self):
        """playbook-template must pass validation even with 1970-01-01 sentinel date."""
        with tempfile.TemporaryDirectory() as tmp_dir:
            tmp_skills = Path(tmp_dir)
            pb_dir = tmp_skills / "playbook-template"
            pb_dir.mkdir(parents=True)
            skill_content = (
                "---\n"
                "name: playbook-template\n"
                "description: \"TEMPLATE — do not execute. Copy this directory.\"\n"
                "last_validated: 1970-01-01\n"
                "---\n"
                "# Playbook: <Task Type>\n"
                "## When to use\nTrigger phrases / task shapes.\n"
                "## Steps (the efficient path)\n1. Steps here.\n"
                "## Traps\nTraps here.\n"
                "## Definition of done\nChecks here.\n"
            )
            (pb_dir / "SKILL.md").write_text(skill_content, encoding="utf-8")

            sys.path.insert(0, str(VALIDATE_SCRIPT.parent))
            import validate_framework  # type: ignore

            validate_framework.errors.clear()
            names = validate_framework.check_playbook_schema(skills_dir=tmp_skills)
            self.assertEqual(validate_framework.errors, [], f"Template validation failed: {validate_framework.errors}")
            self.assertIn("playbook-template", names)


if __name__ == "__main__":
    unittest.main()
