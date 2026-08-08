#!/usr/bin/env python3
"""Shared plumbing for hooks that must run under both Claude Code and Antigravity.

Both hosts invoke hooks the same way — JSON on stdin, JSON on stdout — but they disagree on
tool names, argument keys and the vocabulary of a decision. Rather than maintain two copies
of every guard, each guard imports this module, asks for a normalised view of the payload,
and replies through `allow()` / `deny()`, which emit the dialect the caller understands.

Dialect detection is structural, not configured: Antigravity nests the call under
`toolCall`, Claude Code sends `tool_name` / `tool_input` at the top level.

    Claude Code                          Antigravity
    -----------                          -----------
    Write, Edit, MultiEdit               write_to_file, replace_file_content,
                                         multi_replace_file_content
    Bash                                 run_command
    tool_input.file_path                 toolCall.args.TargetFile
    tool_input.command                   toolCall.args.CommandLine
    permissionDecision: deny             decision: deny
    Stop -> decision: block              Stop -> decision: continue

Escape hatch: set PST_SKIP_HOOKS=1 to make every guard allow unconditionally.
"""

from __future__ import annotations

import json
import os
import subprocess
import sys
import tempfile
from pathlib import Path

CLAUDE = "claude"
ANTIGRAVITY = "antigravity"

WRITE_TOOLS = {
    "Write", "Edit", "MultiEdit", "NotebookEdit",
    "write_to_file", "replace_file_content", "multi_replace_file_content",
}
SHELL_TOOLS = {"Bash", "run_command"}

_PATH_KEYS = ("file_path", "filePath", "TargetFile", "target_file", "path", "notebook_path")
_COMMAND_KEYS = ("command", "CommandLine", "commandLine")


class Payload:
    """A host-agnostic view of one hook invocation."""

    # Structural fingerprints. Tool events are told apart by `toolCall`, but Stop events
    # carry no tool call at all, so fall back to the hosts' common metadata fields —
    # Antigravity is camelCase, Claude Code is snake_case.
    _ANTIGRAVITY_KEYS = frozenset(
        {"toolCall", "conversationId", "workspacePaths", "artifactDirectoryPath",
         "modelName", "stepIdx", "invocationNum", "terminationReason", "fullyIdle"}
    )
    _CLAUDE_KEYS = frozenset(
        {"tool_name", "tool_input", "session_id", "transcript_path", "hook_event_name",
         "stop_hook_active", "cwd"}
    )

    def __init__(self, raw: dict):
        self.raw = raw
        keys = set(raw)
        if "toolCall" in raw or (keys & self._ANTIGRAVITY_KEYS and not keys & self._CLAUDE_KEYS):
            self.dialect = ANTIGRAVITY
        else:
            self.dialect = CLAUDE

        if self.dialect == ANTIGRAVITY:
            call = raw.get("toolCall") or {}
            self.tool_name = call.get("name", "")
            self.args = call.get("args") or {}
        else:
            self.tool_name = raw.get("tool_name", "")
            self.args = raw.get("tool_input") or {}

    @property
    def file_path(self) -> str:
        for key in _PATH_KEYS:
            value = self.args.get(key)
            if isinstance(value, str) and value:
                return value
        return ""

    @property
    def command(self) -> str:
        for key in _COMMAND_KEYS:
            value = self.args.get(key)
            if isinstance(value, str) and value:
                return value
        return ""

    @property
    def is_write(self) -> bool:
        return self.tool_name in WRITE_TOOLS

    @property
    def is_shell(self) -> bool:
        return self.tool_name in SHELL_TOOLS

    def repo_relative(self) -> str:
        """The touched path relative to the repository root, with symlinks resolved.

        Returns the raw value when it lies outside the repository — a guard that cares
        about in-repo paths should check for a leading '..' or absolute path.
        """
        raw = self.file_path
        if not raw:
            return ""
        try:
            resolved = Path(raw).expanduser().resolve()
            return resolved.relative_to(repo_root()).as_posix()
        except (ValueError, OSError):
            return self.literal_relative()

    def literal_relative(self) -> str:
        """The touched path as written, relative to the repository, WITHOUT resolving links.

        Needed because `.claude/`, `.gemini/` and `.agents/` are symlinks into `ai/`:
        `Path.resolve()` rewrites `.claude/agents/x.md` to `ai/agents/x.md`, which would
        hide exactly the violation the canonical-source rule exists to catch.
        """
        raw = self.file_path
        if not raw:
            return ""
        norm = raw.replace("\\", "/")
        root = repo_root().as_posix().rstrip("/")
        if norm.startswith(root + "/"):
            norm = norm[len(root) + 1:]
        elif Path(norm).is_absolute():
            return norm
        while norm.startswith("./"):
            norm = norm[2:]
        return norm


def repo_root() -> Path:
    """Repository root: $CLAUDE_PROJECT_DIR if set, else the git root, else this file's."""
    env = os.environ.get("CLAUDE_PROJECT_DIR") or os.environ.get("ANTIGRAVITY_WORKSPACE")
    if env and Path(env).is_dir():
        return Path(env).resolve()
    here = Path(__file__).resolve()
    try:
        out = subprocess.run(
            ["git", "-C", str(here.parent), "rev-parse", "--show-toplevel"],
            capture_output=True, text=True, timeout=5, check=False,
        )
        if out.returncode == 0 and out.stdout.strip():
            return Path(out.stdout.strip()).resolve()
    except (OSError, subprocess.SubprocessError):
        pass
    return here.parents[2]


def read_payload() -> Payload:
    try:
        raw = json.load(sys.stdin)
        if not isinstance(raw, dict):
            raw = {}
    except (json.JSONDecodeError, ValueError):
        raw = {}
    return Payload(raw)


def bypassed() -> bool:
    return os.environ.get("PST_SKIP_HOOKS", "").strip() not in ("", "0", "false", "False")


# --------------------------------------------------------------------------- usage ledger
def session_key(payload: Payload) -> str:
    """Stable per-session key: Antigravity conversationId, Claude Code session_id."""
    cid = (payload.raw.get("conversationId") or payload.raw.get("session_id")
           or os.environ.get("CLAUDE_SESSION_ID", ""))
    return str(cid) or "nosession"


def ledger_path(payload: Payload) -> Path:
    return Path(tempfile.gettempdir()) / f"pst-hook-ledger-{session_key(payload)}.jsonl"


def _hook_stem() -> str:
    try:
        return Path(sys.argv[0]).stem
    except Exception:
        return "unknown"


def record_use(payload: Payload, decision: str, hook: str | None = None) -> None:
    """Append one line to the session's hook-usage ledger (best effort, never fails).

    Read by session_report.py at Stop time so the end-of-session report can say which
    guards were active and what they decided. session_report excludes itself.
    """
    name = hook or _hook_stem()
    if name == "session_report":
        return
    try:
        with ledger_path(payload).open("a", encoding="utf-8") as fh:
            json.dump({"hook": name, "decision": decision, "tool": payload.tool_name}, fh)
            fh.write("\n")
    except OSError:
        pass


# --------------------------------------------------------------------------- responses
def allow(payload: Payload) -> None:
    """Permit the call, deferring to the host's normal permission flow."""
    record_use(payload, "allow")
    if payload.dialect == ANTIGRAVITY:
        _emit({"decision": "allow"})
    else:
        _emit({})
    sys.exit(0)


def deny(payload: Payload, reason: str) -> None:
    """Block the call and hand the model a reason it can act on."""
    record_use(payload, "deny")
    if payload.dialect == ANTIGRAVITY:
        _emit({"decision": "deny", "reason": reason})
    else:
        _emit({
            "hookSpecificOutput": {
                "hookEventName": "PreToolUse",
                "permissionDecision": "deny",
                "permissionDecisionReason": reason,
            }
        })
    sys.exit(0)


def stop_allow(payload: Payload) -> None:
    """Let the agent finish."""
    record_use(payload, "allow")
    _emit({} if payload.dialect == CLAUDE else {"decision": "allow"})
    sys.exit(0)


def stop_block(payload: Payload, reason: str) -> None:
    """Send the agent back into the loop with an instruction."""
    record_use(payload, "block")
    if payload.dialect == ANTIGRAVITY:
        _emit({"decision": "continue", "reason": reason})
    else:
        _emit({"decision": "block", "reason": reason})
    sys.exit(0)


def _emit(obj: dict) -> None:
    json.dump(obj, sys.stdout)
    sys.stdout.write("\n")
    sys.stdout.flush()


def git(*args: str) -> str:
    """Run a git command at the repository root; return stdout, or '' on failure."""
    try:
        out = subprocess.run(
            ["git", "-C", str(repo_root()), *args],
            capture_output=True, text=True, timeout=10, check=False,
        )
        return out.stdout if out.returncode == 0 else ""
    except (OSError, subprocess.SubprocessError):
        return ""
