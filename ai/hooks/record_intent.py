#!/usr/bin/env python3
"""Hook handler: automatically record user prompts and interaction context into intent_ledger.jsonl.

Captures user inputs along with their context (session ID, turn type, git branch, timestamp)
into a dormant, append-only memory ledger (ai/memory/intent_ledger.jsonl).
This ledger is not loaded into the agent's routine task context to preserve tokens,
but serves as an on-demand audit trail for tracing user intentions and debugging misinterpretations.

Fires on PreInvocation/UserPromptSubmit and Stop. Fails open and never blocks.
"""

from __future__ import annotations

import datetime
import json
import os
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
try:
    from hooklib import ANTIGRAVITY, CLAUDE, bypassed, git, read_payload, repo_root, session_key  # noqa: E402
except ImportError:
    # Standalone fallback if invoked outside the hook environment
    def bypassed() -> bool:
        return os.environ.get("PST_SKIP_HOOKS", "").strip() not in ("", "0", "false", "False")

    def repo_root() -> Path:
        return Path(__file__).resolve().parents[2]

    def session_key(payload) -> str:
        return str(payload.raw.get("conversationId") or payload.raw.get("session_id") or "nosession")

    def git(*args: str) -> str:
        return ""


USER_REQUEST_RE = re.compile(r"<USER_REQUEST>(.*?)</USER_REQUEST>", re.DOTALL)
REDIRECT_KEYWORDS = re.compile(r"\b(stop|revert|cancel|wrong|misunderstood|not what i (asked|meant)|redirect|undo)\b", re.I)


def clean_prompt_text(raw_text: str) -> str:
    """Extract clean user prompt from possible XML wrapping."""
    if not raw_text:
        return ""
    match = USER_REQUEST_RE.search(raw_text)
    if match:
        return match.group(1).strip()
    return raw_text.strip()


def classify_turn(step_idx: int, prompt_text: str, prev_had_question: bool) -> str:
    """Classify user turn type."""
    if step_idx == 0:
        return "NEW_SESSION"
    if prev_had_question:
        return "ANSWER_TO_QUESTION"
    if REDIRECT_KEYWORDS.search(prompt_text):
        return "INTERVENTION_REDIRECT"
    return "FOLLOW_UP"


def load_existing_fingerprints(ledger_file: Path) -> set[tuple[str, int]]:
    """Return set of (session_id, step_index) already recorded."""
    fingerprints = set()
    if not ledger_file.exists():
        return fingerprints
    try:
        with ledger_file.open("r", encoding="utf-8") as fh:
            for line in fh:
                line = line.strip()
                if not line:
                    continue
                try:
                    entry = json.loads(line)
                    sid = entry.get("session_id")
                    sidx = entry.get("step_index")
                    if sid is not None and sidx is not None:
                        fingerprints.add((str(sid), int(sidx)))
                except (json.JSONDecodeError, ValueError):
                    continue
    except OSError:
        pass
    return fingerprints


def find_antigravity_transcript(payload, session_id: str) -> Path | None:
    """Locate Antigravity transcript file."""
    tpath = payload.raw.get("transcriptPath")
    if tpath and Path(tpath).is_file():
        return Path(tpath)

    # Standard Antigravity local log location
    candidate = Path.home() / ".gemini" / "antigravity" / "brain" / session_id / ".system_generated" / "logs" / "transcript.jsonl"
    if candidate.is_file():
        return candidate
    return None


def extract_antigravity_turns(transcript_path: Path, session_id: str, branch: str) -> list[dict]:
    """Parse USER_INPUT turns from Antigravity transcript.jsonl."""
    turns: list[dict] = []
    if not transcript_path.is_file():
        return turns

    try:
        with transcript_path.open("r", encoding="utf-8") as fh:
            steps = [json.loads(line) for line in fh if line.strip()]
    except (OSError, json.JSONDecodeError):
        return turns

    for i, step in enumerate(steps):
        if step.get("type") == "USER_INPUT":
            step_idx = int(step.get("step_index", 0))
            raw_content = step.get("content", "")
            prompt_text = clean_prompt_text(raw_content)
            if not prompt_text:
                continue

            prev_had_question = False
            question_context = None
            if i > 0:
                prev_step = steps[i - 1]
                for tc in prev_step.get("tool_calls", []):
                    if tc.get("name") == "ask_question":
                        prev_had_question = True
                        question_context = tc.get("args", {}).get("questions")

            turn_type = classify_turn(step_idx, prompt_text, prev_had_question)
            record = {
                "timestamp": step.get("created_at") or datetime.datetime.now(datetime.timezone.utc).isoformat(),
                "session_id": session_id,
                "step_index": step_idx,
                "turn_type": turn_type,
                "prompt": prompt_text,
                "host": "antigravity",
                "git_branch": branch,
            }
            if question_context:
                record["trigger_context"] = {"question": question_context}
            turns.append(record)

    return turns


def extract_claude_turns(payload, session_id: str, branch: str) -> list[dict]:
    """Parse user turns from Claude Code hook payload or transcript."""
    turns: list[dict] = []
    # If direct prompt is provided in payload (e.g., UserPromptSubmit)
    direct_prompt = (
        payload.raw.get("prompt")
        or payload.raw.get("user_prompt")
        or payload.args.get("prompt")
        or payload.args.get("user_prompt")
    )
    if direct_prompt and isinstance(direct_prompt, str):
        cleaned = clean_prompt_text(direct_prompt)
        if cleaned:
            turns.append({
                "timestamp": datetime.datetime.now(datetime.timezone.utc).isoformat(),
                "session_id": session_id,
                "step_index": int(payload.raw.get("step_index", 0)),
                "turn_type": "FOLLOW_UP" if payload.raw.get("step_index", 0) > 0 else "NEW_SESSION",
                "prompt": cleaned,
                "host": "claude",
                "git_branch": branch,
            })
            return turns

    # Try reading transcript_path if provided
    tpath_str = payload.raw.get("transcript_path")
    if tpath_str and Path(tpath_str).is_file():
        try:
            with open(tpath_str, "r", encoding="utf-8") as fh:
                idx = 0
                for line in fh:
                    if not line.strip():
                        continue
                    try:
                        node = json.loads(line)
                        if node.get("type") == "user" or node.get("sender") == "user":
                            msg = node.get("message", {})
                            content = msg.get("content") or node.get("text") or node.get("content") or ""
                            if isinstance(content, list):
                                text_parts = [c.get("text", "") for c in content if isinstance(c, dict) and c.get("text")]
                                content = "\n".join(text_parts)
                            cleaned = clean_prompt_text(str(content))
                            if cleaned:
                                turns.append({
                                    "timestamp": node.get("timestamp") or datetime.datetime.now(datetime.timezone.utc).isoformat(),
                                    "session_id": session_id,
                                    "step_index": idx,
                                    "turn_type": "NEW_SESSION" if idx == 0 else "FOLLOW_UP",
                                    "prompt": cleaned,
                                    "host": "claude",
                                    "git_branch": branch,
                                })
                                idx += 1
                    except (json.JSONDecodeError, ValueError):
                        continue
        except OSError:
            pass

    return turns


def main() -> int:
    try:
        payload = read_payload()
        if bypassed():
            sys.stdout.write("{}\n")
            return 0

        root = repo_root()
        ledger_path = root / "ai" / "memory" / "intent_ledger.jsonl"
        ledger_path.parent.mkdir(parents=True, exist_ok=True)

        sid = session_key(payload)
        branch = git("rev-parse", "--abbrev-ref", "HEAD").strip() or "unknown"

        fingerprints = load_existing_fingerprints(ledger_path)

        new_turns: list[dict] = []
        if payload.dialect == ANTIGRAVITY:
            tpath = find_antigravity_transcript(payload, sid)
            if tpath:
                turns = extract_antigravity_turns(tpath, sid, branch)
                for t in turns:
                    key = (t["session_id"], t["step_index"])
                    if key not in fingerprints:
                        new_turns.append(t)
                        fingerprints.add(key)
        else:
            turns = extract_claude_turns(payload, sid, branch)
            for t in turns:
                key = (t["session_id"], t["step_index"])
                if key not in fingerprints:
                    new_turns.append(t)
                    fingerprints.add(key)

        if new_turns:
            with ledger_path.open("a", encoding="utf-8") as fh:
                for t in new_turns:
                    fh.write(json.dumps(t, ensure_ascii=False) + "\n")
                fh.flush()

    except Exception:
        # Fails open so it never breaks agent execution
        pass

    sys.stdout.write("{}\n")
    sys.stdout.flush()
    return 0


if __name__ == "__main__":
    sys.exit(main())
