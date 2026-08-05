# Gemini / Antigravity Instructions

All agent instructions for this repository live in [`AGENTS.md`](AGENTS.md). Read it before
making any changes. Framework documentation: [`ai/README.md`](ai/README.md). Routing:
[`ai/ROUTING.md`](ai/ROUTING.md).

Key rules (summary — `AGENTS.md` is authoritative):

- **Identify the product first.** Three independent products: Desktop (`products/desktop/src/`, Python),
  Android Desktop (`products/android/android-desktop/`), PhotoTok (`products/android/phototok/`). `products/android/core/` is the
  only code the two Android products share. Do not leak code between products.
- **Mandatory task lifecycle.** Start with the `task-lifecycle` skill (pre-work reads), finish
  with the `retrospective` skill (tests, requirements sync, lesson capture, refactoring
  backlog). The `Stop` hook checks the retrospective actually ran.
- Write and run tests for every feature, fix, or logic change; verify with
  `./scripts/run_tests.sh`, not a subset — the `verify-build` skill explains how to read it.
- Never commit scratch files, report dumps, or PR description drafts. The `guard_paths.py` and
  `guard_commit.py` hooks block these at write and commit time.
- Per-agent configs: canonical in `ai/agents/*.md` (`.gemini/agents/` symlinks to them), roster
  in `.gemini/settings.json`. Use `shared-code-health-agent` for refactoring and retro
  follow-ups.
- Learned lessons live in `ai/memory/` (`bolt.md` performance, `palette.md` UI/a11y,
  `sentinel.md` security, `code_health.md` refactoring backlog). Read the relevant file before
  working in those areas; write new lessons via the `record-lesson` skill, which requires the
  `@shared-mentor-agent` gate.

## Where Antigravity finds things

| Antigravity concept | Path | Canonical source |
|---|---|---|
| Workspace rules | `.agents/rules/` | `ai/rules/` |
| Skills | `.agents/skills/` | `ai/skills/` |
| Workflows (`/command`) | `.agents/workflows/` | `ai/commands/` |
| Hooks | `.agents/hooks.json` | scripts in `ai/hooks/` |

Set `.agents/rules/task-lifecycle.md` to **Always On** in the Customizations panel
(Rules → Workspace). Antigravity does not infer the activation mode from the file.

Workflows available as slash commands: `/route`, `/verify`, `/retro`, `/sync-framework`.

## Gemini CLI

`.gemini/settings.json` is **generated** from the frontmatter of `ai/agents/*.md`:

```bash
python3 ai/skills/sync-framework/scripts/gen_gemini_settings.py
```

Never hand-edit it — `./scripts/run_tests.sh` fails when it drifts from `ai/agents/`.

## Hooks

The guards in `ai/hooks/` run under both Antigravity and Claude Code: they detect which host
invoked them from the payload shape and reply in that host's dialect (`decision: deny` for
Antigravity, `permissionDecision: deny` for Claude Code). `.agents/hooks.json` registers them
against `write_to_file`, `replace_file_content`, `multi_replace_file_content` and
`run_command`, plus a `Stop` handler.

`PST_SKIP_HOOKS=1` bypasses all three for genuine emergencies.
