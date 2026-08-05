---
description: Regenerate and validate the agent framework after editing ai/agents or ai/skills
---

Run the `sync-framework` skill (`ai/skills/sync-framework/SKILL.md`).

```bash
python3 ai/skills/sync-framework/scripts/gen_gemini_settings.py
python3 ai/skills/sync-framework/scripts/validate_framework.py
```

If validation fails, fix the **canonical** file under `ai/` — never the mirrored copy under
`.claude/`, `.gemini/` or `.agents/` — and run both commands again.

Report what changed in `.gemini/settings.json`, and whether `ai/ROUTING.md` still matches the
agent roster.

$ARGUMENTS
