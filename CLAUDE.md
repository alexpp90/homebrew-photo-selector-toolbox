# Claude Instructions

All agent instructions for this repository live in [`AGENTS.md`](AGENTS.md). Read it before
making any changes. Framework documentation: [`ai/README.md`](ai/README.md). Routing:
[`ai/ROUTING.md`](ai/ROUTING.md).

Key rules (summary — `AGENTS.md` is authoritative):

- **Identify the product first.** Three independent products: Desktop (`products/desktop/src/`, Python),
  Android Desktop (`products/android/android-desktop/`), PhotoTok (`products/android/phototok/`). `products/android/core/` is the
  only code the two Android products share. Do not leak code between products.
- **Mandatory task lifecycle.** Start with the `task-lifecycle` skill (pre-work reads), finish
  with the `retrospective` skill (tests, requirements sync, lesson capture, refactoring
  backlog). A `SessionStart` hook injects the summary; a `Stop` hook checks the retrospective
  actually ran.
- Write and run tests for every feature, fix, or logic change; verify with
  `./scripts/run_tests.sh`, not a subset — the `verify-build` skill explains how to read it.
- Never commit scratch files, report dumps, or PR description drafts. The `guard_paths.py` and
  `guard_commit.py` hooks block these at write and commit time.
- Subagents are registered in `.claude/agents/` (symlink to canonical `ai/agents/`) — delegate
  to the matching specialist per the roster in `ai/ROUTING.md`; use `shared-code-health-agent`
  for refactoring and retro follow-ups.
- Learned lessons live in `ai/memory/` (`bolt.md` performance, `palette.md` UI/a11y,
  `sentinel.md` security, `code_health.md` refactoring backlog). Read the relevant file before
  working in those areas; write new lessons via the `record-lesson` skill, which requires the
  `@shared-mentor-agent` gate.

## Skills

Invoked when their moment arrives, rather than read up front:

| Skill | Use when |
|---|---|
| `task-lifecycle` | starting any task |
| `retrospective` | before finalizing any task that changed files |
| `verify-build` | proving a change passes what CI enforces |
| `sync-requirements` | behaviour changed and the docs must follow |
| `record-lesson` | a mentor-approved lesson needs writing to `ai/memory/` |
| `create-playbook` | a task type will recur, or a playbook was followed |
| `sync-framework` | `ai/agents/` or `ai/skills/` was edited |
| `release-compliance` | permissions, endpoints, SDKs, data flow, or a release |
| `refactoring-guide` | any refactoring |
| `playbook-*` | a learned procedure matching the task type |

## Commands

`/route` — which product and agent owns this? · `/verify` — run the CI mirror ·
`/retro` — close out the task · `/sync-framework` — regenerate and validate after editing
agent or skill definitions.

## Hooks

Canonical in `ai/hooks/`, registered in `.claude/settings.json`. They block rather than warn:

- `guard_paths.py` (`PreToolUse` on writes) — scratch files, writes through the mirrored
  `.claude/` `.gemini/` `.agents/` trees, hand-edits of generated artifacts, code at the
  repository root.
- `guard_commit.py` (`PreToolUse` on `Bash`) — committing staged scratch artifacts,
  `--no-verify`, and `git push` on a chain of speculative `fix(ci)` commits.
- `check_retrospective.py` (`Stop`) — ends the turn back in the loop if product source
  changed but the retrospective left no trace. Fires at most once per session.
- `guard_scope.py` (per-agent frontmatter `hooks:`) — blocks a product agent writing into
  another product's source tree. Slugs are validated by `validate_framework.py`.
- `post_lint.py` (`PostToolUse` on writes, advisory) — feeds `flake8` findings on the
  just-edited Desktop Python file back to the model immediately.
- `session_report.py` (`Stop`, advisory, both hosts) — after major work (implementation or
  plan), reports which agents, skills and hooks were actually used this session.

Every denial explains what to do instead. `PST_SKIP_HOOKS=1` bypasses all of them for genuine
emergencies.

## No-hooks fallback (Cowork and other limited hosts)

Detection: if no task-lifecycle summary was injected at session start and the repo skills
(`task-lifecycle`, `retrospective`, …) are absent from your available skills, you are in a
host that loads only this file — Cowork mounts the repository as a data folder and ignores
`.claude/` entirely. The lifecycle then runs on discipline instead of hooks. You MUST:

1. Read `ai/hooks/task-lifecycle.md` before any work, then follow it: read the relevant
   `ai/skills/*/SKILL.md` files directly as markdown when their moment arrives.
2. Adopt the owning agent's role yourself: `ai/ROUTING.md` maps the path you are touching to
   an agent; read that `ai/agents/*.md` file and stay inside its scope. Never write into
   another product's source tree.
3. Self-enforce the guards: no scratch files or report dumps, edit canonical files under
   `ai/` (never through `.claude/`, `.gemini/`, `.agents/`), never hand-edit
   `.gemini/settings.json`, no `--no-verify`.
4. Before finishing any task that changed files, walk `ai/skills/retrospective/SKILL.md`
   explicitly — no Stop hook will catch you here.
