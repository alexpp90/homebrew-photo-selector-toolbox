---
name: task-lifecycle
description: "Pre-work reads for any task in this repository: identify the target product (Desktop, Android Desktop or PhotoTok), read that product's REQUIREMENTS.md, read the matching ai/memory/ lesson file, read the owning agent config, and follow any matching playbook. Use at the START of every coding task, before opening a source file."
allowed-tools: Read, Grep, Glob
---

# Task Lifecycle — before work

Phase 1 of the mandatory lifecycle. Phase 2 is the `retrospective` skill; run it before
finalizing.

## 1. Identify the product — before any file is opened

This repository ships **three independent products**. Getting this wrong is the most
expensive mistake available, because it produces code in the wrong place that looks correct.

| Product | Code | Requirements |
|---|---|---|
| Desktop | `products/desktop/src/photo_selector_toolbox/` | `docs/products/desktop/REQUIREMENTS.md` |
| Android Desktop | `products/android/android-desktop/` (`:android-desktop`) | `docs/products/android-desktop/REQUIREMENTS.md` |
| PhotoTok | `products/android/phototok/` (`:phototok`) | `docs/products/phototok/REQUIREMENTS.md` |

`products/android/core/` (`:core`) is the only code the two Android products share, and a
change there affects both by definition.

The three products share photographic *concepts* — the EXIF contract, score semantics, what
"Selection" means — and nothing else. **Copying a file from one product into another is a
defect, not reuse.** A feature wanted in more than one product splits into one independent
subtask per product, each tailored to its stack.

If the task spans products, stop and split it now.

## 2. Read the requirements

Read the sections of the owning product's `docs/products/<product>/REQUIREMENTS.md` that
cover what you are about to change. Cross-product policy lives in `docs/shared/`.

Your agent config names the sections that bind your scope — read those at minimum.

## 3. Read the matching lessons

`ai/memory/` holds what previous tasks learned. Read the file that matches the work:

| File | Read it before |
|---|---|
| `ai/memory/bolt.md` | performance-sensitive work |
| `ai/memory/palette.md` | UI or accessibility work |
| `ai/memory/sentinel.md` | anything touching security, archives, URLs, or user input |
| `ai/memory/code_health.md` | refactoring work, and to check for existing backlog items in your area |

## 4. Read your agent config

Read `ai/agents/<agent>.md` for the agent that owns the files you are about to touch — see
`ai/ROUTING.md` for the mapping. For refactoring work also read
`ai/skills/refactoring-guide/SKILL.md`.

Delegate to the owning agent rather than working outside your scope.

## 5. Check for a playbook

Look in `ai/skills/` for a `playbook-*` skill matching this task type ("add a new EXIF
reader", "add a Compose screen to PhotoTok", "cut a release"). If one exists, follow it —
it records the path that already worked, including the traps.

You will be expected to improve it afterwards (see the `create-playbook` skill). A playbook
followed without edits should at least get its `last_validated` date bumped.

## Related skills

- `retrospective` — Phase 2, mandatory before finalizing
- `verify-build` — how to prove the change passes what CI enforces
- `sync-requirements` — when and how `REQUIREMENTS.md` must change
- `refactoring-guide` — the project's established patterns
