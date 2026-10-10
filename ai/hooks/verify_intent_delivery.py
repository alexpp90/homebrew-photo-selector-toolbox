#!/usr/bin/env python3
"""Pre-Delivery Intent Verification Gate (F2.3).

Executes automated checks at task completion (Stop hook):
1. Audits test execution: verifies that relevant test suites were actually executed
   during the session when product or framework code was modified.
2. Audits requirements sync: ensures changes to product source trees are paired with
   traceable requirements updates in docs/products/<product>/REQUIREMENTS.md.
3. Audits user prompt directives: extracts user intent from ai/memory/intent_ledger.jsonl
   and verifies that requested items (e.g., arrow keys, EXIF, contrast, loading,
   traceability) were addressed before the session ends.
4. Enforces read-only directives: blocks modification of product files when the user
   explicitly requested an investigation-only task.

Emergency escape hatch: set PST_SKIP_HOOKS=1.
"""

from __future__ import annotations

import json
import os
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from hooklib import (  # noqa: E402
    bypassed,
    git,
    ledger_path,
    read_payload,
    repo_root,
    session_key,
    stop_allow,
    stop_block,
)

ACK_PROMPTS = re.compile(
    r"^(yes|continue|ok|go\s+ahead|proceed|yep|sure|done|go\s+with\s+option\s+[a-z])\.?$",
    re.IGNORECASE,
)

READ_ONLY_DIRECTIVES = re.compile(
    r"\b(do\s+not\s+implement|investigation\s+only|no\s+changes|read-only|evaluate\s+only)\b",
    re.IGNORECASE,
)

PRODUCT_PATTERNS = {
    "desktop": ("products/desktop/", ("desktop", "python", "tkinter")),
    "macos-desktop": ("products/macos-desktop/", ("macos", "mac", "swift", "apple vision", "vision")),
    "android-desktop": ("products/android/android-desktop/", ("android desktop", "dex", "tablet")),
    "phototok": ("products/android/phototok/", ("phototok", "gesture", "saf")),
    "linux-desktop": ("products/linux-desktop/", ("linux", "gnome", "debian", "libadwaita")),
    "framework": ("ai/", ("framework", "agent", "skill", "hook", "toolchain")),
}

TEST_RUNNERS_BY_PRODUCT = {
    "products/desktop/": re.compile(r"\b(pytest|run_tests\.sh(?:\s+.*--(?:python|all))?)\b"),
    "products/macos-desktop/": re.compile(r"\b(swift\s+test|xcodebuild\s+test|run_tests\.sh(?:\s+.*--(?:macos|all))?)\b"),
    "products/android/": re.compile(r"\b(gradlew|run_tests\.sh(?:\s+.*--(?:android|all))?)\b"),
    "products/linux-desktop/": re.compile(r"\b(pytest|run_tests\.sh(?:\s+.*--(?:linux|all))?)\b"),
    "ai/": re.compile(r"\b(validate_framework\.py|verify_requirements_traceability\.py|run_tests\.sh)\b"),
    "scripts/": re.compile(r"\b(validate_framework\.py|verify_requirements_traceability\.py|run_tests\.sh)\b"),
}

FEATURE_KEYWORD_MAP = {
    "arrow_keys": re.compile(r"\b(arrow\s+keys?|navigation|keyboard|left/right)\b", re.I),
    "exif": re.compile(r"\b(exif|metadata|shutter|iso|aperture)\b", re.I),
    "contrast": re.compile(r"\b(contrast|barely\s+readable|black\s+on\s+dark|dark\s+mode)\b", re.I),
    "loading": re.compile(r"\b(loading|spinner|progress|indicator)\b", re.I),
    "duplicate": re.compile(r"\b(duplicate|duplicates|hash|dedup)\b", re.I),
    "settings": re.compile(r"\b(settings|preferences|config|⌘,)\b", re.I),
    "traceability": re.compile(r"\b(traceab\w*|traceability|traceable|requirement\s+id|req-)", re.I),
}


def changed_files() -> list[str]:
    """Return all modified or untracked files relative to repository root."""
    out = git("status", "--porcelain", "-uall")
    files: list[str] = []
    for line in out.splitlines():
        if len(line) > 3:
            path = line[3:].strip()
            if " -> " in path:
                path = path.split(" -> ", 1)[1]
            files.append(path.strip('"'))
    return files


def load_session_prompts(root: Path, session_id: str) -> list[dict]:
    """Load user prompt history for this session from ai/memory/intent_ledger.jsonl."""
    ledger_file = root / "ai" / "memory" / "intent_ledger.jsonl"
    if not ledger_file.is_file():
        return []

    turns: list[dict] = []
    try:
        with ledger_file.open("r", encoding="utf-8") as fh:
            for line in fh:
                line = line.strip()
                if not line:
                    continue
                try:
                    entry = json.loads(line)
                    if entry.get("session_id") == session_id:
                        turns.append(entry)
                except json.JSONDecodeError:
                    continue
    except OSError:
        return []

    turns.sort(key=lambda t: int(t.get("step_index", 0)))
    return turns


def get_latest_actionable_prompt(turns: list[dict]) -> dict | None:
    """Find the most recent actionable (non-ack) user prompt in this session."""
    for turn in reversed(turns):
        text = str(turn.get("prompt", "")).strip()
        if text and not ACK_PROMPTS.match(text):
            return turn
    return turns[-1] if turns else None


def load_executed_commands(payload) -> list[str]:
    """Extract all commands executed during the session from ledger & transcripts."""
    commands: list[str] = []

    # 1. From session hook ledger
    lpath = ledger_path(payload)
    if lpath.is_file():
        try:
            with lpath.open("r", encoding="utf-8") as fh:
                for line in fh:
                    try:
                        record = json.loads(line.strip())
                        cmd = record.get("command")
                        if cmd:
                            commands.append(cmd)
                    except json.JSONDecodeError:
                        continue
        except OSError:
            pass

    # 2. From Claude Code transcript if available
    tpath_claude = payload.raw.get("transcript_path")
    if tpath_claude and Path(tpath_claude).is_file():
        try:
            with open(tpath_claude, "r", encoding="utf-8") as fh:
                for line in fh:
                    try:
                        node = json.loads(line)
                        if node.get("type") == "tool_use" and node.get("name") in ("Bash", "bash"):
                            cmd = node.get("input", {}).get("command")
                            if cmd:
                                commands.append(cmd)
                    except json.JSONDecodeError:
                        continue
        except OSError:
            pass

    # 3. From Antigravity transcript if available
    sid = session_key(payload)
    candidate_anti = (
        Path.home() / ".gemini" / "antigravity" / "brain" / sid
        / ".system_generated" / "logs" / "transcript.jsonl"
    )
    if candidate_anti.is_file():
        try:
            with candidate_anti.open("r", encoding="utf-8") as fh:
                for line in fh:
                    try:
                        node = json.loads(line)
                        for tc in node.get("tool_calls", []):
                            if tc.get("name") in ("run_command", "bash", "execute_command"):
                                args = tc.get("args", {})
                                cmd = args.get("CommandLine") or args.get("command")
                                if cmd:
                                    commands.append(cmd)
                    except json.JSONDecodeError:
                        continue
        except OSError:
            pass

    return commands


def verify_session(payload) -> tuple[bool, str]:
    """Core verification function. Returns (passed, failure_reason)."""
    if bypassed():
        return True, ""

    if payload.raw.get("fullyIdle") is False:
        return True, ""
    if payload.raw.get("stop_hook_active"):
        return True, ""

    files = changed_files()
    # If no files changed, or only teamwork metadata changed, allow stop (e.g. read-only explorer)
    productive_files = [f for f in files if not f.startswith(".agents/teamwork/")]
    if not productive_files:
        return True, ""

    root = repo_root()
    sid = session_key(payload)
    turns = load_session_prompts(root, sid)
    latest_turn = get_latest_actionable_prompt(turns)
    prompt_text = str(latest_turn.get("prompt", "")) if latest_turn else ""

    # Check read-only constraint
    if prompt_text and READ_ONLY_DIRECTIVES.search(prompt_text):
        touched_prod = [f for f in productive_files if f.startswith("products/")]
        if touched_prod:
            sample = "\n".join(f"  - {f}" for f in touched_prod[:5])
            return False, (
                f"User directive specified read-only investigation, but product files were modified:\n"
                f"{sample}\nRevert product modifications before concluding."
            )

    # 1. Audit Test Execution
    executed_commands = load_executed_commands(payload)
    missing_test_targets: list[str] = []

    for prefix, pattern in TEST_RUNNERS_BY_PRODUCT.items():
        touched = any(f.startswith(prefix) for f in productive_files)
        if touched:
            ran_test = any(pattern.search(cmd) for cmd in executed_commands)
            if not ran_test:
                missing_test_targets.append(prefix)

    # 2. Audit Requirements Sync
    unmatched_requirements: list[str] = []
    for prod_name, (prefix, _) in PRODUCT_PATTERNS.items():
        if prod_name == "framework":
            continue
        touched_src = any(f.startswith(f"{prefix}src/") for f in productive_files)
        if touched_src:
            req_file = f"docs/products/{prod_name}/REQUIREMENTS.md"
            if not any(req_file in f for f in productive_files):
                unmatched_requirements.append(req_file)

    # 3. Audit Specific Prompt Directives
    missing_directives: list[str] = []
    if prompt_text:
        for feat_name, pattern in FEATURE_KEYWORD_MAP.items():
            if pattern.search(prompt_text):
                feat_tokens = feat_name.split("_")
                covered = any(
                    any(t in f.lower() for t in feat_tokens)
                    for f in productive_files
                ) or any(
                    any(t in cmd.lower() for t in feat_tokens)
                    for cmd in executed_commands
                )
                if not covered:
                    missing_directives.append(feat_name.replace("_", " "))

    # Generate actionable denial if any verification check failed
    reasons: list[str] = []
    if missing_test_targets:
        targets_str = ", ".join(missing_test_targets)
        reasons.append(
            f"• [TEST EXECUTION REQUIRED]: Code was modified under {targets_str}, but no corresponding "
            f"test runner was executed during this session.\n"
            f"  Action: Run `./scripts/run_tests.sh` (or specific runner: pytest / swift test / gradlew) and verify passes."
        )

    if unmatched_requirements:
        req_str = ", ".join(unmatched_requirements)
        reasons.append(
            f"• [REQUIREMENTS SYNC REQUIRED]: Product source was modified without updating requirements in {req_str}.\n"
            f"  Action: Update REQUIREMENTS.md with traceable REQ-<PRODUCT>-<SECTION>.<INDEX> IDs."
        )

    if missing_directives:
        dir_str = ", ".join(missing_directives)
        reasons.append(
            f"• [UNFULFILLED PROMPT DIRECTIVES]: User prompt requested work on ({dir_str}), but no relevant "
            f"files or tests were touched or executed.\n"
            f"  Action: Verify all items explicitly requested in the user prompt before finishing."
        )

    if reasons:
        explanation = "\n\n".join(reasons)
        message = (
            f"Pre-Delivery Intent Gate: Task completion BLOCKED.\n\n"
            f"The following required steps must be completed before Stop is permitted:\n\n"
            f"{explanation}\n\n"
            f"Note: Arbitrary retries will not bypass this check. Address the actions above and rerun.\n"
            f"Emergency Manual Escape: Set PST_SKIP_HOOKS=1 if overriding manually."
        )
        return False, message

    return True, ""


def main() -> int:
    payload = read_payload()
    passed, reason = verify_session(payload)
    if not passed:
        stop_block(payload, reason)
    stop_allow(payload)
    return 0


if __name__ == "__main__":
    sys.exit(main())
