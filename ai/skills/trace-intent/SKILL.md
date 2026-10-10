---
name: trace-intent
description: "Trace user intent and prompt history across sessions from ai/memory/intent_ledger.jsonl. Use when reconstructing original user goals, resolving misunderstandings, or checking whether an implementation diverged from user instructions."
allowed-tools: Read, Grep, Glob, Bash
---

# Trace User Intent

Use this skill when you need to reconstruct the user's authentic goals and instructions,
especially when:
- An implementation drifted or over-interpreted the user's initial guidance.
- The user points out a misunderstanding ("that's not what I asked", "revert to what I meant").
- Reflecting during the retrospective with `@shared-mentor-agent` on whether the outcome matches user intent.

## The Intent Ledger

User prompts are passively recorded by `ai/hooks/record_intent.py` into:
`ai/memory/intent_ledger.jsonl`

> [!NOTE]
> This ledger is **dormant memory**. It is intentionally excluded from the pre-work
> `task-lifecycle` reads to prevent context pollution and token waste during routine work.
> Only query it when specifically tracing user intent.

## How to Query

Run the helper script from the repository root:

```bash
# View the last 15 user turns chronologically
python3 ai/skills/trace-intent/scripts/query_intent.py

# Search for a specific topic across all user inputs
python3 ai/skills/trace-intent/scripts/query_intent.py --query "cache size"

# Filter by a specific session ID
python3 ai/skills/trace-intent/scripts/query_intent.py --session "b54c4b60"

# Filter by turn type (NEW_SESSION, FOLLOW_UP, ANSWER_TO_QUESTION, INTERVENTION_REDIRECT)
python3 ai/skills/trace-intent/scripts/query_intent.py --type "INTERVENTION_REDIRECT"
```

## How to Use Findings

1. **Identify the pivot:** Find the exact prompt where the user stated their goal or gave feedback.
2. **Distinguish Intent from Interpretation:** Compare the user's literal words with what the agent planned. Did the agent add unwanted features or solve an unasked problem?
3. **Course Correct:** Align the current plan or diff directly with the user's recorded prompt.
