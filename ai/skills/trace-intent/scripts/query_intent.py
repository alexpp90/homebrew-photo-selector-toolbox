#!/usr/bin/env python3
"""Query and format the user intent ledger (ai/memory/intent_ledger.jsonl).

Usage:
    python3 ai/skills/trace-intent/scripts/query_intent.py [--last N] [--session ID] [--query TERM] [--type TYPE] [--raw]
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[4]
LEDGER_FILE = REPO_ROOT / "ai" / "memory" / "intent_ledger.jsonl"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Query the User Intent Ledger")
    parser.add_argument("--last", "-n", type=int, default=15, help="Number of recent entries to show (default: 15)")
    parser.add_argument("--session", "-s", type=str, default="", help="Filter by session ID substring")
    parser.add_argument("--query", "-q", type=str, default="", help="Search query in user prompt")
    parser.add_argument("--type", "-t", type=str, default="", help="Filter by turn type (e.g. NEW_SESSION, INTERVENTION_REDIRECT)")
    parser.add_argument("--raw", action="store_true", help="Print raw JSON lines")
    return parser.parse_args()


def load_entries() -> list[dict]:
    if not LEDGER_FILE.exists():
        return []
    entries = []
    with LEDGER_FILE.open("r", encoding="utf-8") as fh:
        for line in fh:
            line = line.strip()
            if not line:
                continue
            try:
                entries.append(json.loads(line))
            except (json.JSONDecodeError, ValueError):
                continue
    return entries


def main() -> int:
    args = parse_args()
    entries = load_entries()

    if not entries:
        print("Intent ledger is empty (ai/memory/intent_ledger.jsonl).", file=sys.stderr)
        return 0

    filtered = entries
    if args.session:
        filtered = [e for e in filtered if args.session.lower() in str(e.get("session_id", "")).lower()]
    if args.query:
        filtered = [e for e in filtered if args.query.lower() in str(e.get("prompt", "")).lower()]
    if args.type:
        filtered = [e for e in filtered if args.type.lower() in str(e.get("turn_type", "")).lower()]

    if args.last > 0:
        filtered = filtered[-args.last:]

    if not filtered:
        print("No matching intent entries found.", file=sys.stderr)
        return 0

    if args.raw:
        for e in filtered:
            print(json.dumps(e, ensure_ascii=False))
        return 0

    print(f"=== User Intent Ledger ({len(filtered)} matching entries) ===\n")
    for e in filtered:
        ts = e.get("timestamp", "?")[:19].replace("T", " ")
        sid = str(e.get("session_id", "?"))[:8]
        sidx = e.get("step_index", "?")
        ttype = e.get("turn_type", "UNKNOWN")
        branch = e.get("git_branch", "main")
        prompt = e.get("prompt", "").strip()

        print(f"[{ts}] Session: {sid} (Step {sidx}) | Type: {ttype} | Branch: {branch}")
        if "trigger_context" in e and e["trigger_context"].get("question"):
            print(f"  Agent Question: {e['trigger_context']['question']}")
        indented = "\n".join("  > " + line for line in prompt.splitlines())
        print(indented)
        print("-" * 70)

    return 0


if __name__ == "__main__":
    sys.exit(main())
