---
description: Run the mandatory post-task retrospective on the current change
---

Run the `retrospective` skill (`ai/skills/retrospective/SKILL.md`) against the work in this
session.

Start from the actual diff, not from memory of what you intended:

```bash
git status --short
git diff HEAD
```

Then work the eight steps in order. For each, report one of: done (with what changed),
not applicable (with why), or blocked (with what is needed).

$ARGUMENTS
