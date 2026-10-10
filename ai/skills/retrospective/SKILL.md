---
name: retrospective
description: "Post-work close-out for any change in this repository: verify the full CI mirror passed, sync REQUIREMENTS.md, consult the mentor before writing memory, file refactoring debt, fix framework drift, and check hygiene. Use BEFORE finalizing any task that changed files."
allowed-tools: Read, Grep, Glob, Edit, Write, Bash
---

# Retrospective — after work, before finalizing

Phase 2 of the mandatory lifecycle. Run every step. This is what makes the framework improve
itself instead of repeating its mistakes.

## 1. Tests — run the CI mirror, not a subset

Use the `verify-build` skill. In short: `./scripts/run_tests.sh` (add `--all` when an
emulator or device is attached), read the printed gate summary, and name every `⊘ SKIPPED`
gate in your task summary as an accepted risk.

Bare `pytest` or `gradlew testDebugUnitTest` is a strict subset of what CI enforces and is
**not** sufficient evidence.

**If the task is not done, the task is not done.** Never push a speculative fix and let CI
adjudicate it — a chain of `fix(ci)` / `fix(test)` commits on a branch is the anti-pattern
this step exists to prevent.

## 2. Requirements

Did observable behaviour change? Then the owning product's `REQUIREMENTS.md` changes in the
same commit. Use the `sync-requirements` skill — it covers what counts as observable, which
file owns which rule, and the workflow-parity obligation.

## 3. Reflect, then consult the mentor

Reflect on the task honestly:

- What failed on the first attempt?
- What took longest, and why?
- What surprised you — an assumption the codebase did not honour?

Draft candidate memories from that, then **consult `@shared-mentor-agent`** with the task
summary, the diff, and your candidates. The mentor is a fresh-context reviewer: it dedupes
against existing memory, rejects task-diary noise, routes each candidate to the right home,
and writes the final wording.

**Do not write to `ai/memory/` or to a playbook without this gate.** Working agents are
biased toward memorizing their own struggle; the two-phase commit is what keeps memory sharp.

In tools without subagents, adopt `ai/agents/shared-mentor-agent.md` as a role in a separate
reflection pass, or file candidates tagged `[PROPOSED]`.

If nothing at all was learned you may skip the consult — but say so explicitly in your summary.

## 4. Lessons

What the mentor approves gets written via the `record-lesson` skill, which owns the file
routing, the dated `Learning`/`Action` format, and the dedupe rules.

## 5. Cross-product propagation

Four independent products ship from this repository, and a lesson or fix recorded for one
does not reach the others by itself — agents read `ai/memory/` for the product they are
already working in.

For every behaviour change and every approved lesson, ask: **does this apply to the other
products?** Interaction patterns and performance fixes count, not only features. Record the
answer in `docs/shared/FEATURE_PARITY.md` — *including* "not applicable, because…" — and file
a task for anything that should be ported. Never port by copying code: each product gets its
own implementation in its own stack (`ai/ROUTING.md`, the separation rule).

An unrecorded "I checked and it does not apply" is indistinguishable from never having looked.

## 6. Refactoring candidates

Saw debt you could not fix in scope — duplication, dead code, pattern violations, oversized
functions? Add a backlog entry to `ai/memory/code_health.md` with file paths and rationale.
**Do not silently drop it.**

Large refactorings (multi-file, cross-module) are not retro side effects. File them and let
`@shared-code-health-agent` schedule them as dedicated tasks.

## 7. Framework retrospective, evaluation & drift

Evaluate how the AI framework was used during this session and record reflections into `ai/memory/framework_retro.md`. This captures meta-process lessons to continuously improve the framework:

1. **Framework fit & tool performance**:
   - Was the framework a good fit for this task?
   - **Agents**: Which agents were used or adopted? Did their scopes fit the task, or was there boundary friction / unnecessary delegation?
   - **Skills**: Which skills were invoked or followed? Were their instructions clear and actionable, or did they miss crucial steps or traps?
   - **Hooks**: Which hooks fired? Did guards (`guard_paths`, `guard_commit`, `guard_scope`) protect against errors, or did they create friction?
2. **Session efficiency**:
   - What took longest, and why?
   - Was time wasted locating files or understanding dependencies (e.g., "Took a lot of time to find related code because of poorly indexed modules")?
   - Was there context churn or exploratory roundtrips?
3. **Implementation quality**:
   - Was implementation quality high from the start, or was rework required?
   - Did the agent rush into code without checking `REQUIREMENTS.md` upfront?
   - Did tests fail because assumptions or edge cases were not verified before writing code?
4. **Actionable framework improvement**:
   - What concrete change to agent definitions, skills, playbooks, docs, or hooks would eliminate this friction for future sessions?
   - Record an entry in `ai/memory/framework_retro.md`.

**Framework drift**:
If any agent instruction, scope, skill or routing rule was wrong or stale during this task,
fix it now at the canonical source under `ai/` and regenerate what depends on it. Use the
`sync-framework` skill — it owns the regeneration and validation steps.

A stale instruction you noticed and did not fix will cost the next agent the same time it
cost you.

### 7.1 Retrospective Synthesis Engine

After appending reflections to `ai/memory/framework_retro.md`, synthesize friction patterns to drive continuous framework improvement:

```bash
# Inspect detected friction clusters and actionable proposals without modifying files
python3 ai/skills/retrospective/scripts/synthesize_retro.py --dry-run

# Emit machine-readable JSON summary for automated reporting/auditing
python3 ai/skills/retrospective/scripts/synthesize_retro.py --json

# Verify whether all recurring friction patterns (>= 2 occurrences) are addressed
python3 ai/skills/retrospective/scripts/synthesize_retro.py --check

# Append unaddressed recurring patterns as [OPEN] items in ai/memory/code_health.md
python3 ai/skills/retrospective/scripts/synthesize_retro.py --apply

# Optionally scaffold proposed new playbook SKILL.md templates alongside --apply
python3 ai/skills/retrospective/scripts/synthesize_retro.py --apply --scaffold-playbooks

# Output full markdown synthesis report to a designated file
python3 ai/skills/retrospective/scripts/synthesize_retro.py --output /path/to/report.md
```

CLI Modes:
- `--dry-run`: Parse entries, cluster friction patterns across domains (desktop, macos-desktop, phototok, android-desktop, linux-desktop, ci, framework), and print synthesis report to stdout.
- `--check`: Validate whether all recurring patterns (>= threshold occurrences) have been addressed by existing playbooks or `code_health.md` backlog items (exits with code 1 if unaddressed).
- `--apply`: Automatically append formatted `[OPEN]` items for unaddressed recurring patterns to `ai/memory/code_health.md` (idempotent, deduplicated).
- `--scaffold-playbooks`: Used in combination with `--apply` to scaffold new playbook `SKILL.md` files conforming to `playbook-template`.
- `--output FILE`: Save markdown synthesis report to `FILE`.
- `--json`: Emit JSON payload with cluster metrics, occurrences, domains, and unaddressed counts.
- `--threshold N`: Occurrence count threshold for classifying a pattern as recurring (default: 2).
- `--product DOMAIN`: Filter synthesis to a specific domain (e.g. `macos-desktop`, `phototok`).

## 8. Playbook

Is this task type likely to recur? Use the `create-playbook` skill to record the efficient
path, or to improve the playbook you followed. Playbooks must get better every time they are
used.

## 9. Hygiene

No scratch files, report dumps, or PR-description drafts staged for commit. Benchmarks belong
in `products/desktop/benchmarks/`, never in the repository root. The `guard-paths` hook blocks
most of these at write time, but check `git status` before you finish.

## 10. Task Completion Report (Zero-Code-Review Task Summary & Verification)

### Zero-Code-Review Core Standard

The user acts as an executive evaluator and stakeholder, **never** as a manual tester or code reviewer.
Therefore, completed task summaries must strictly adhere to the following non-negotiable reporting rules:

1. **NEVER present raw code diffs or implementation minutiae**:
   - Do NOT include unified diff blocks (`diff --git`, `@@ -1,5 +1,5 @@`, `+class Foo:`), raw source code listings, or plumbing adjustments in the final summary.
   - Do NOT dump internal variable names, import modifications, AST structures, or private helper details.
2. **NEVER delegate testing to the user**:
   - Do NOT instruct the user to "test this manually", "click around to see if it works", or "verify if it crashes".
   - The agent must have already empirically proven correctness, stability, and visual fidelity before delivering.
3. **Mandate Empirical Behavioral Proof**:
   - Present verifiable, step-by-step evidence of user-facing runtime functionality, accessibility compliance, and performance metrics.

### Mandatory Summary Structure

Every completed task summary MUST include the following 5 structured sections:

#### 10.1 Executive Outcome & Empirical Behavioral Proof
- Concise synopsis of observable features delivered and UX improvements achieved.
- Concrete, step-by-step proof of end-to-end user journeys (e.g., keyboard shortcut routing, folder ingestion latency, culling state transitions, undo stack reversibility, non-blocking background analysis).

#### 10.2 Visual & Accessibility Verification (Contrast & Layout)
- **Contrast Auditing**: Programmatic contrast ratio verification conforming to WCAG 2.1 AA (>= 4.5:1 for normal text, >= 3.0:1 for graphical controls and large text). Explicit confirmation of **zero dark-on-dark or low-contrast text findings**.
- **Control & Dialog Occlusion Auditing**: Bounding box non-intersection verification proving **zero occluded, overlapped, or clipped interactive controls, buttons, or dialogs** across window dimensions.
- **Visual & Theme Adherence**: Verification of dark theme aesthetics, focus indicator visibility, and touch/click target geometry.

#### 10.3 Empirical Test Matrix & Execution Verdicts
- Comprehensive execution matrix detailing test suites run:
  - Exact command executed (e.g. `swift test -c release`, `poetry run pytest`).
  - Target module/package scope.
  - Test suites and test counts passed (e.g. `204 tests in 24 suites passed (0 failures)`).
  - Execution duration and performance SLAs verified (e.g. `FocusMetricService latency SLA < 2.0ms core compute`).
- **CI Mirror Parity Status**: Exact verdict of `./scripts/run_tests.sh`, explicitly enumerating any `⊘ SKIPPED` gates and their accepted risk rationales.

#### 10.4 Operational Instructions
- Single-line, unambiguous, copy-paste commands to run the application, launch the test suite, or inspect artifacts.
- Exact expected visual and runtime behavior upon execution.

#### 10.5 Agent Report & Framework Evaluation
- **Agent Report Table**: Complete accounting of all agents involved in the task:

| Agent | Scope / Role | Duties Performed |
|---|---|---|
| `@<agent-name>` | `<file path or domain scope>` | Description of changes made or checks executed |

- **Framework & Session Evaluation**:
  - **Framework Fit & Tool Performance**: How well the framework matched the task, and performance of the Agents, Skills, and Hooks used.
  - **Session Efficiency & Quality**: Key observations on time spent (e.g., locating files) and implementation quality (e.g., requirement adherence, first-pass test pass rate).
  - **Framework Retro Log**: Confirmation of the entry appended to `ai/memory/framework_retro.md` (or note if execution was frictionless).

### Standard Task Summary Template

Agents must format their final summary using this template:

```markdown
# Task Summary: <Feature Title>

## 1. Executive Outcome & Empirical Behavioral Proof
- **Delivered Capabilities**: <concise synopsis of what user-facing capabilities were delivered>
- **Behavioral Proof**: <step-by-step walkthrough of verified runtime flows, state transitions, and user interactions>

## 2. Visual & Accessibility Verification (Contrast & Layout)
- **WCAG 2.1 AA Contrast Audit**: Verified all text meets >= 4.5:1 (large text/icons >= 3.0:1). Zero dark-on-dark findings.
- **Control Occlusion Audit**: Verified non-intersection across all dialog buttons, inputs, and comparison viewports. Zero clipped or occluded controls.
- **Theme & HIG Compliance**: Studio dark styling and focus indicators verified across window geometries.

## 3. Empirical Test Matrix & Execution Verdicts
| Target / Scope | Test Command | Suites | Tests Passed | Duration / SLA | Verdict |
|---|---|---|---|---|---|
| `<product/module>` | `<exact command>` | `<N>` | `<Total>` | `<time / SLA metrics>` | PASS |

- **CI Mirror Parity (`./scripts/run_tests.sh`)**: All active gates passed.
- **Accepted Skips (`⊘`)**: <list any skipped gates with explicit rationale, or "None">.

## 4. Operational Instructions
```bash
<exact command to launch or verify>
```
*Expected behavior*: <description of what the user will observe>.

## 5. Agent Report & Framework Evaluation

| Agent | Scope / Role | Duties Performed |
|---|---|---|
| `@<agent-name>` | `<scope>` | `<duties performed>` |

### Framework & Session Evaluation
- **Framework Fit & Tool Performance**: <fit assessment across agents, skills, and hooks>
- **Session Efficiency & Quality**: <observations on efficiency, context churn, and first-pass pass rate>
- **Framework Retro Log**: Appended entry to `ai/memory/framework_retro.md`.
```

## Checklist

```
[ ] 1. ./scripts/run_tests.sh passed; skipped gates named in the summary
[ ] 2. REQUIREMENTS.md synced (or: behaviour did not change)
[ ] 3. Reflected; mentor consulted (or: nothing learned, stated explicitly)
[ ] 4. Approved lessons written via record-lesson
[ ] 5. Cross-product propagation evaluated; decision recorded in FEATURE_PARITY.md
[ ] 6. Out-of-scope debt filed in ai/memory/code_health.md
[ ] 7. Framework evaluated (fit, agents/skills/hooks, efficiency, quality), logged in ai/memory/framework_retro.md, and drift fixed
[ ] 8. Playbook created or improved (or: task type will not recur)
[ ] 9. git status clean of scratch artifacts
[ ] 10. Zero-Code-Review Task Summary included (Behavioral Proof, Visual/A11y Audit, Test Matrix, Operational Instructions, Agent Report)
```
