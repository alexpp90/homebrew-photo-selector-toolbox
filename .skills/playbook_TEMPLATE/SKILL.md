---
name: playbook-template
description: "TEMPLATE — do not execute. Copy this directory to .skills/playbook_<task>/ when the retrospective (self_improvement step 6) identifies a recurring task type. Playbooks are learned procedures that make repeat tasks faster."
last_validated: 1970-01-01
---

# Playbook: <Task Type>

<One sentence: what task this playbook makes faster, and for which solution(s).>

## When to use

Trigger phrases / task shapes that mean this playbook applies.

## Steps (the efficient path)

1. Files to touch, in order, with why.
2. Exact commands that worked (test invocations, build commands, `xvfb-run` wrappers…).
3. Where the tests for this task type live and which fixtures to reuse.

## Traps

- Things that cost time on previous runs and how to avoid them (link `.Jules/` lessons instead of duplicating them).

## Definition of done

Task-type-specific checks beyond the standard retrospective.

---
Maintenance: every use must either improve this file or bump `last_validated`. `@code_health_agent` prunes playbooks that go stale or reference deleted files.
