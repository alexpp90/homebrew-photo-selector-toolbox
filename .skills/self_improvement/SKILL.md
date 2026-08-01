---
name: self-improvement
description: "Mandatory task lifecycle and post-task retrospective for the Photo Selector Toolbox project. Every agent runs this at the start and end of every task: pre-work reads, then a retrospective that records lessons, updates requirements, and files refactoring candidates."
---

# Self-Improvement Loop

This skill defines the **mandatory task lifecycle**. It applies to every task, in every tool (Claude, Jules, Gemini/Antigravity), no exceptions. It is what makes the agent framework continuously improve itself.

## Phase 1 — Before work (always)

1. Read the sections of `REQUIREMENTS.md` relevant to the task.
2. Read the relevant `.Jules/` lesson file(s):
   - `bolt.md` before performance-sensitive work
   - `palette.md` before UI/accessibility work
   - `sentinel.md` before anything touching security, archives, URLs, or user input
   - `code_health.md` before refactoring work
3. Read your agent config in `.agents/<agent>.md` and, for refactorings, `.skills/refactoring_guide/SKILL.md`.
4. Identify the target solution(s) — Desktop, Android Desktop, Android Phone — and do not leak code between them.

## Phase 2 — After work (always, before finalizing)

Run this retrospective checklist:

1. **Tests — run the CI mirror, not a subset.** Were tests written/updated, and did the **full local gate** pass?

   ```bash
   ./scripts/run_tests.sh          # add --all if an emulator/device is attached
   ```

   This is mandatory and it is not the same as `pytest` or `gradlew testDebugUnitTest`.
   Those run a strict subset of what CI enforces, which is how a branch ends up
   green locally and red in Actions. The script mirrors every CI gate — flake8
   (which gates the *entire* desktop pipeline), the coverage threshold, the
   `-m "not visual"` split, and `assembleDebugAndroidTest` (the only thing that
   compiles `src/androidTest`).

   **Read the summary table it prints.** Gates it could not run are reported as
   `⊘ SKIPPED` with a reason. A skipped gate is an accepted risk, not a pass:
   state explicitly in your task summary which gates were skipped and why.
   `docs/CI_PARITY.md` explains which gates are legitimately un-runnable locally
   (emulator instrumented tests, visual regression off Linux, credentialed
   publish steps) and how to read a CI failure for each.

   If the task is not done, the task is not done — do not push a guess and let
   CI adjudicate it. A chain of `fix(ci)` / `fix(test)` commits on a branch is
   the anti-pattern this step exists to prevent.

   **Workflow changes carry a parity obligation:** if you added or changed a
   gate in `.github/workflows/`, add the equivalent to `scripts/run_tests.sh`
   and update the matrix in `docs/CI_PARITY.md` *in the same commit*.
2. **Requirements**: Did observable behavior change? → update `REQUIREMENTS.md` in the same change.
3. **Reflection & mentor consult**: Reflect on the task — what failed on the first attempt, what took longest, what surprised you. Draft candidate memories, then **consult `@mentor_agent`** (as a subagent where supported; otherwise adopt `.agents/mentor_agent.md` as a role in a separate reflection pass) with the task summary, diff, and candidates. The mentor decides what gets memorized, where, and in what wording — do not write directly to `.Jules/` or playbooks without this gate. If nothing at all was learned, you may skip the consult, but say so explicitly in your summary.
4. **Lesson format** (what the mentor commits): append an entry to the matching `.Jules/` file using the format:

   ```markdown
   ## YYYY-MM-DD - Short title
   **Learning:** What was discovered and why it matters.
   **Action:** The concrete rule future agents should follow.
   ```

5. **Refactoring candidates**: If you saw debt you could not fix in-scope (duplication, dead code, pattern violations, oversized functions), add a backlog entry to `.Jules/code_health.md` with file paths and rationale. Do not silently drop it.
6. **Framework drift**: If any agent instruction, scope, or skill was wrong or stale during this task, fix the canonical file in `.agents/` or `.skills/` now, and sync the summaries in `AGENTS.md` and `.gemini/settings.json`.
7. **Playbook (efficiency memory)**: If this task type is likely to recur (e.g., "add a new EXIF reader", "add a Compose screen to PhotoTok", "cut a release"), propose the efficient path as a playbook skill in `.skills/playbook_<task>/SKILL.md` (via the mentor consult in step 3): the file touchpoints, the order of steps that worked, commands run, and traps hit. If a playbook already existed for this task, **update it** with anything that would have made this run faster — playbooks must get better every time they are used.
8. **Hygiene**: No scratch files, report dumps, or PR-description drafts staged for commit.

## Using playbooks (Phase 1 addendum)

Before starting, check `.skills/` for a `playbook_*` skill matching the task type and follow it — then improve it afterwards (step 6). A playbook that was followed without edits should at least get its `last_validated` date bumped.

## Rules for `.Jules/` entries

- One entry per lesson; newest entries at the top; always use the real current date.
- Lessons must be generalizable rules, not task diaries.
- Before appending, skim the file to avoid duplicating an existing lesson — extend it instead.

## Escalation

Large refactorings (multi-file, cross-module) are not done as retro side effects. File them in `.Jules/code_health.md` and let `@code_health_agent` schedule them as dedicated tasks.
