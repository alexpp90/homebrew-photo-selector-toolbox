---
name: create-playbook
description: "Create or improve an ai/skills/playbook-<task>/ skill recording the efficient path for a recurring task type: file touchpoints, step order, working commands, and traps. Use when a task type is likely to recur, or after following an existing playbook."
allowed-tools: Read, Write, Edit, Glob
---

# Playbooks — efficiency memory

A lesson in `ai/memory/` records *what is true*. A playbook records *what to do, in what
order*. When a task type recurs — "add a new EXIF reader", "add a Compose screen to
PhotoTok", "cut a release" — the second run should be much faster than the first, and a
playbook is how that happens.

## When to create one

During the `retrospective` (step 7), if the task type is likely to recur. Propose it through
the `@shared-mentor-agent` consult like any other memory candidate — the mentor decides
whether it earns a playbook or a plain lesson.

Signals it should be a playbook rather than a lesson:

- the knowledge is a *sequence*, not a fact
- it touches several files in a specific order
- you had to discover which command actually works
- you hit a trap that cost time and will be hit again

## When to improve one

**Every time you follow a playbook.** If it was missing a step, listed a stale path, or you
found a faster route, update it. A playbook you followed without any edit still gets its
`last_validated` date bumped, so a reader can tell "still correct" from "not looked at since
1970".

Playbooks that are never improved rot into a second source of stale instructions — which is
worse than not having them.

## How

1. Copy `ai/skills/playbook-template/` to `ai/skills/playbook-<task>/`.
2. Name it for the task, not the area: `playbook-add-exif-reader`, not `playbook-exif`.
   Lowercase, hyphens only — the directory name and the `name:` field must match.
3. Fill in the template. Write the `description` so another agent can tell from that line
   alone whether this playbook covers the task in front of it.
4. Set `last_validated` to today.
5. Register it: add a per-item symlink in `.gemini/skills/`, and list it under Shared Skills
   in `ai/ROUTING.md`. The `sync-framework` skill does this and validates the result.

## What a good playbook contains

- **Touchpoints** — the files that always change, in the order they should change
- **Commands that actually worked**, verbatim, including flags
- **Traps** — what looked right and was not, with the symptom you would see
- **Verification** — how you knew it was done, beyond "tests passed"

Keep it to the path that worked. A playbook is not documentation of the subsystem; it is the
shortest route through it.
