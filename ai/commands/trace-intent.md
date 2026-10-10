# /trace-intent

Trace user intent, prompts, and decision history from `ai/memory/intent_ledger.jsonl`.

## Usage

Run the intent query helper to inspect user inputs and context:

```bash
python3 ai/skills/trace-intent/scripts/query_intent.py "$@"
```

Options:
- `--last N`: number of recent turns to display (default: 15)
- `--query <term>`: search keywords in user prompts
- `--session <id>`: filter by session / conversation ID
- `--type <type>`: filter by turn type (`NEW_SESSION`, `FOLLOW_UP`, `ANSWER_TO_QUESTION`, `INTERVENTION_REDIRECT`)
