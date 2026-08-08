---
name: sync-framework
description: "Add or change an agent, skill, playbook or routing rule and regenerate everything downstream: .gemini/settings.json, the symlink trees, and the ROUTING.md roster. Use whenever ai/agents/ or ai/skills/ is edited, and whenever an instruction is found stale mid-task."
allowed-tools: Read, Write, Edit, Glob, Bash
---

# Framework maintenance

The framework is treated as code: it has an owner (`@shared-code-health-agent`), a backlog,
and a definition of done. This skill is that definition of done.

## The edit rule

**Always edit the canonical file under `ai/`.** Never edit through `.claude/`, `.gemini/` or
`.agents/` — they are the same bytes via symlinks, but treating the neutral directory as
canonical keeps intent unambiguous. The `guard-paths` hook blocks writes through those
directories.

| Canonical | Mirrored into |
|---|---|
| `ai/agents/` | `.claude/agents` (symlink), `.gemini/agents/*.md` (per-file symlinks), `.gemini/settings.json` (generated) |
| `ai/skills/` | `.claude/skills`, `.agents/skills`, `.gemini/skills/*` (per-item symlinks) |
| `ai/commands/` | `.claude/commands`, `.agents/workflows` |
| `ai/hooks/` | `.claude/hooks`, referenced by `.claude/settings.json` and `.agents/hooks.json` |
| `ai/rules/` | `.agents/rules` |

## Add an agent

1. Create `ai/agents/<product>-<role>-agent.md`. The `name:` field **must equal the filename
   stem**; lowercase and hyphens only.
2. Write `description:` so a coordinator can tell *when* to delegate from that line alone.
   Include "Use proactively" if applicable, and name what the agent must **not** touch.
3. Set `tools:` to the minimum the role needs. An advisory agent that says it does not modify
   source must not be granted `Edit`/`Write` — the frontmatter is the enforcement, the prose
   is only the explanation.
4. Set `model:` (`inherit` unless the role justifies otherwise).
5. Product agents (and any agent that must not touch product source) register the
   product-separation hook in frontmatter — `validate_framework.py` enforces the slug:

   ```yaml
   hooks:
     PreToolUse:
       - matcher: "Write|Edit|MultiEdit|NotebookEdit"
         hooks:
           - type: command
             command: "python3 \"$CLAUDE_PROJECT_DIR/ai/hooks/guard_scope.py\" <slug>"
             timeout: 10
   ```

   Slugs: `desktop`, `android-desktop`, `phototok`, `android-build`, `no-products` — see
   `ai/hooks/guard_scope.py`. New slugs are added there and in the validator together.
6. Add a per-file symlink in `.gemini/agents/`.
7. Add roster and delegation rows to `ai/ROUTING.md`.
8. Regenerate and validate (below).

## Add a skill

1. Create `ai/skills/<name>/SKILL.md` with `name` (matching the directory, lowercase and
   hyphens only), `description`, and `allowed-tools`.
2. Write `description` for retrieval: it is the only thing a model sees when deciding whether
   to load the skill. State the trigger, not just the topic.
3. Add a per-item symlink `.gemini/skills/<name> -> ../../ai/skills/<name>`.
4. List it under Shared Skills in `ai/ROUTING.md`.
5. Playbooks follow the same steps, starting from `ai/skills/playbook-template/`.

Keep `SKILL.md` short. Deterministic steps belong in `scripts/` next to it, and long
reference material in `references/`, loaded only when needed.

## Change a scope

Edit the `ai/agents/*.md` file first, then sync the `ai/ROUTING.md` tables and regenerate
`.gemini/settings.json` in the same change.

## Regenerate and validate

```bash
python3 ai/skills/sync-framework/scripts/gen_gemini_settings.py   # rewrite .gemini/settings.json
python3 ai/skills/sync-framework/scripts/validate_framework.py    # must exit 0
```

`.gemini/settings.json` is a **generated artifact**. Hand-editing it is what let its roster
drift from the agent files; regenerate instead.

`validate_framework.py` also runs as a gate inside `./scripts/run_tests.sh`. It fails on:

- skill directory name ≠ `name:` field, or a name that is not `^[a-z0-9-]+$`
- agent filename stem ≠ `name:` field
- an agent missing `tools:` or `model:`
- a broken symlink in `.claude/`, `.gemini/` or `.agents/`
- an agent in `ROUTING.md` but not in `ai/agents/`, or the reverse
- `.gemini/settings.json` out of sync with agent frontmatter
- **a repository path referenced in an agent or skill body that does not exist** — the check
  that catches instructions rotting after a file move

## Fixing drift found mid-task

If an instruction was wrong or stale while you were working, fix it **now**, at the canonical
source, and run the two commands above. Then note it in your task summary. A stale
instruction you noticed and left will cost the next agent exactly what it cost you.
