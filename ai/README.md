# AI Agent Framework

Everything that configures AI coding agents in this repository lives under `ai/` and
nowhere else. Product documentation lives in [`docs/`](../docs/README.md); source code
lives in `products/desktop/src/` and `products/android/`. Those three trees do not mix.

If you are a human: this directory does not affect the build. Skip it.
If you are an agent: read this file, then [`ROUTING.md`](ROUTING.md), then your own
`agents/<name>.md`.

## Layout

```
ai/
  README.md          This file — how the framework works
  ROUTING.md         Which agent owns which product and path; delegation rules
  agents/            CANONICAL agent definitions (14 files: name, description, tools, model)
  skills/            CANONICAL skills — the lifecycle, split by the moment it applies
  hooks/             CANONICAL hook scripts + the injected lifecycle payload
  commands/          CANONICAL slash commands / Antigravity workflows
  rules/             CANONICAL always-on rule (Antigravity's rules mechanism)
  memory/            Persistent lessons: bolt.md (perf), palette.md (UI/a11y),
                     sentinel.md (security), code_health.md (refactoring backlog)
```

Everything outside `ai/` is a pointer:

```
AGENTS.md              Entry point read by Jules, Antigravity, Codex and every
                       AGENTS.md-aware tool. Thin — it points here.
CLAUDE.md              Thin pointer + Claude-specific notes (auto-loaded by Claude Code/Cowork)
GEMINI.md              Thin pointer + Gemini/Antigravity notes (auto-loaded by Gemini CLI)
.claude/agents      -> ai/agents      (symlink; Claude Code subagent registration)
.claude/skills      -> ai/skills      (symlink; Claude Code skill registration)
.claude/commands    -> ai/commands    (symlink; Claude Code slash commands)
.claude/hooks       -> ai/hooks       (symlink; hook scripts)
.claude/settings.json  Hook registration for Claude Code
.agents/skills      -> ai/skills      (symlink; Antigravity skill registration)
.agents/workflows   -> ai/commands    (symlink; Antigravity workflows)
.agents/rules       -> ai/rules       (symlink; Antigravity workspace rules)
.agents/hooks.json     Hook registration for Antigravity
.gemini/agents/*.md -> ai/agents/*.md (per-file symlinks)
.gemini/skills/*    -> ai/skills/*    (per-item symlinks)
.gemini/settings.json  GENERATED from ai/agents/*.md — never hand-edited
```

**Edit rule:** always edit files under `ai/`. Never edit through `.claude/`, `.gemini/` or
`.agents/` — they are the same bytes, but treating the neutral directory as canonical keeps
intent unambiguous.

## Design goals

**One source of truth.** Every instruction exists in exactly one canonical file; tool-specific
locations are symlinks or thin pointers.

**Tool neutrality.** Any agent product that reads `AGENTS.md` (the cross-tool standard) gets
the full picture; Claude and Gemini additionally get native subagent and skill registration.

**Product separation.** The repository ships three independent products. Every agent belongs
to exactly one product, or is explicitly shared. An agent's name carries its product prefix so
that misrouting is visible at a glance rather than discovered in a diff.

**Always-on lifecycle.** Every task begins with mandated context reads and ends with a
retrospective, regardless of tool.

**Self-improvement.** Lessons, refactoring debt and framework drift are captured as part of
finishing a task, not as an afterthought.

## Tool compatibility matrix

| Capability | Claude Code / Cowork | Jules | Gemini CLI | Antigravity |
|---|---|---|---|---|
| Instructions auto-loaded | `CLAUDE.md` → `AGENTS.md` | `AGENTS.md` | `GEMINI.md` → `AGENTS.md` | `AGENTS.md` + `.agents/rules/` |
| Subagent registration | native, via `.claude/agents/` frontmatter | prose routing via the `ROUTING.md` roster | `.gemini/agents/` + `settings.json` | prose routing via the `ROUTING.md` roster |
| Skills | native, via `.claude/skills/` | read as markdown per `AGENTS.md` | `.gemini/skills/` | native, via `.agents/skills/` |
| Commands | `/name` via `.claude/commands/` | — | — | `/name` via `.agents/workflows/` |
| Hooks | `.claude/settings.json` | — | — | `.agents/hooks.json` |
| Lifecycle enforcement | **blocking hooks** + rules | rules in `AGENTS.md` | rules in `GEMINI.md`/`AGENTS.md` | **blocking hooks** + rules |

Claude Code and Antigravity both run hooks with a JSON-on-stdin / JSON-on-stdout contract, but
they differ on tool names, argument keys and decision vocabulary. The scripts in `ai/hooks/`
detect the calling host from the payload shape and answer in its dialect, so one
implementation serves both. Tools without hooks fall back to the rules, which stay
authoritative in `AGENTS.md`.

Tools without native subagents still follow the roster: `AGENTS.md` instructs them to read the
matching `ai/agents/*.md` file and adopt that role before touching files in its scope.

## Agent roster

Fourteen agents, grouped by the product they serve. Full scopes and delegation rules are in
[`ROUTING.md`](ROUTING.md).

| Product | Agents |
|---|---|
| Desktop | `desktop-backend-agent`, `desktop-gui-agent`, `desktop-test-agent`, `desktop-build-agent` |
| Android Desktop | `android-desktop-ui-agent`, `android-desktop-core-agent` |
| PhotoTok | `phototok-ui-agent`, `phototok-core-agent` |
| Both Android products | `android-shared-build-agent` |
| Cross-product consultants | `shared-photo-researcher-agent`, `shared-ux-agent`, `shared-publish-agent`, `shared-code-health-agent`, `shared-mentor-agent` |

Each `ai/agents/*.md` file carries YAML frontmatter (`name`, `description`) so Claude Code
registers it as a delegatable subagent; the body is the role prompt any tool can adopt. The
`name` must equal the filename stem.

## Mandatory task lifecycle

Two skills, not one, because the two halves apply at different moments and a skill that loads
only when needed costs nothing in between:

- [`skills/task-lifecycle/`](skills/task-lifecycle/SKILL.md) — **before work.** Identify the
  target product and never leak code between products; read that product's
  `docs/products/<product>/REQUIREMENTS.md`; read the matching `ai/memory/` lesson file(s);
  read your agent config; follow any matching `playbook-*`.
- [`skills/retrospective/`](skills/retrospective/SKILL.md) — **before finalising.** Tests via
  the full CI mirror; `REQUIREMENTS.md` synced if behaviour changed; reflection and the mentor
  consult; approved lessons written; out-of-scope debt filed; framework drift fixed; playbook
  created or improved; no scratch files.

It runs on **every task in every tool** — this is the "always started" guarantee.

Enforcement, rather than reminder, is what changed: a `SessionStart` hook prints
`ai/hooks/task-lifecycle.md` into context, and a `Stop` hook (`check_retrospective.py`) refuses
to end a session that changed product source but left no trace of a retrospective — no
`REQUIREMENTS.md` update, no `ai/memory/` entry, no framework fix. It fires at most once per
session, so it cannot trap an agent. Tools without hooks rely on the rules being front and
centre in `AGENTS.md`/`GEMINI.md`, which they always load.

## Self-improvement loop

Continuous improvement is closed-loop, with `shared-code-health-agent` as the owner:

1. **Capture, gated by a mentor** — during the mandatory retrospective the working agent
   reflects (what failed first, what took longest, what surprised it), drafts candidate
   memories, and consults `@shared-mentor-agent` — a fresh-context reviewer that dedupes
   against existing memory, rejects task-diary noise, routes each candidate to the right place
   (lesson vs. playbook vs. instruction fix), and writes the final wording. Working agents do
   not write to `ai/memory/` or playbooks directly; this two-phase commit is what keeps memory
   sharp. Refactoring candidates go to `ai/memory/code_health.md`. In tools without subagents,
   the agent adopts `shared-mentor-agent.md` as a role in a separate reflection pass, or files
   candidates as `[PROPOSED]`.
2. **Schedule** — `shared-code-health-agent` periodically works the backlog: behaviour-preserving
   refactorings, test-covered before and after, following
   [`skills/refactoring-guide/SKILL.md`](skills/refactoring-guide/SKILL.md), never mixed with
   behaviour changes.
3. **Framework maintenance** — when instructions themselves are found stale or wrong mid-task,
   they are fixed immediately at the canonical source (`ai/agents/`, `ai/skills/`) with the
   summaries synced. The framework is treated as code: it has an owner, a backlog, and a
   definition of done.
4. **Procedural learning (playbooks)** — recurring task types get a `playbook-<task>` skill
   (template: `skills/playbook-template/`) recording the efficient path: file touchpoints, step
   order, working commands, traps. Agents check for a matching playbook before starting and must
   improve it — or at least bump its `last_validated` date — after using it.
5. **Consolidation** — `shared-code-health-agent` periodically merges duplicate lessons, deletes
   invalidated ones, promotes procedure-shaped lessons into playbooks, and prunes stale playbooks.

## Persistent memory (`ai/memory/`)

Append-only lesson files, newest first, format `## YYYY-MM-DD - Title` / `**Learning:**` /
`**Action:**`. Lessons must be generalisable rules, not task diaries; skim before appending so
you extend rather than duplicate. `code_health.md` additionally holds backlog items tagged
`[OPEN]`/`[DONE]`.

## How to

All of these are owned by the [`sync-framework`](skills/sync-framework/SKILL.md) skill, which
carries the full procedure and the validation step. In short:

**Add an agent:** create `ai/agents/<product>-<role>-agent.md` with frontmatter — `name` equal
to the filename stem (lowercase, hyphens only), `description` written so a coordinator knows
*when* to delegate, `tools` set to the minimum the role needs, and `model`. Add a per-file
symlink in `.gemini/agents/`, add roster and delegation rows to [`ROUTING.md`](ROUTING.md),
then regenerate and validate.

**Add a skill:** create `ai/skills/<name>/SKILL.md` with `name` (matching the directory),
`description` written for retrieval, and `allowed-tools`. Add a per-item symlink
`.gemini/skills/<name> -> ../../ai/skills/<name>` and list it in `ROUTING.md`. Deterministic
steps belong in `scripts/` beside the skill, not in its prose. Playbooks (`playbook-<task>`)
follow the same steps from `skills/playbook-template/`.

**Add a command:** create `ai/commands/<name>.md`. It becomes `/name` in Claude Code (via
`.claude/commands`) and an Antigravity workflow (via `.agents/workflows`) with no further work.

**Change a scope:** edit the `ai/agents/*.md` file, then sync `ROUTING.md` and regenerate
`.gemini/settings.json` in the same change.

**Record a lesson:** use the [`record-lesson`](skills/record-lesson/SKILL.md) skill during the
retrospective, after the mentor consult — never in a separate scratch file.

**Regenerate and validate** — the definition of done for any of the above:

```bash
python3 ai/skills/sync-framework/scripts/gen_gemini_settings.py
python3 ai/skills/sync-framework/scripts/validate_framework.py
```

Both also run as gates inside `./scripts/run_tests.sh`, so framework drift fails the build the
way a broken test does.

## Enforcement

What the framework can check mechanically, it does. The scripts in `ai/hooks/` are registered
with both Claude Code (`.claude/settings.json`) and Antigravity (`.agents/hooks.json`), and
they **block** rather than warn:

| Hook | Event | Blocks |
|---|---|---|
| `guard_paths.py` | `PreToolUse` on writes | scratch files and report dumps; writes through `.claude/` `.gemini/` `.agents/`; hand-edits of generated artifacts; code at the repository root |
| `guard_commit.py` | `PreToolUse` on shell | committing staged scratch artifacts; `--no-verify`; `git push` on a chain of speculative `fix(ci)` commits |
| `check_retrospective.py` | `Stop` | ending a session that changed product source with no sign the retrospective ran |

Every denial states what to do instead, so the model can correct itself rather than merely
being refused. `PST_SKIP_HOOKS=1` bypasses all three for genuine emergencies.

`validate_framework.py` covers what a hook cannot: name/directory mismatches, missing `tools:`,
broken symlinks, roster drift, an advisory agent holding write tools, and **paths referenced in
an instruction that no longer exist** — the check that catches instructions rotting silently
after a file move.

## Known gaps

- `docs/products/phototok/RELEASE_CHECKLIST.md` is referenced by `shared-publish-agent` but has
  not been written yet — it must be created when release work resumes, together with the
  privacy policy and Impressum sources.
- Symlinks require `core.symlinks=true` (default on macOS/Linux). Windows checkouts need
  Developer Mode or `git config core.symlinks true`. This now covers `.claude/commands`,
  `.claude/hooks`, `.agents/workflows` and `.agents/rules` as well as the agent and skill trees.
- `guard_paths.py` cannot yet enforce product separation per agent (blocking a PhotoTok agent
  from writing Desktop files) because the invoking subagent's identity is not reliably present
  in the hook payload. Product separation stays a rule, checked in review.
- Antigravity does not infer a rule's activation mode from the file: set
  `.agents/rules/task-lifecycle.md` to **Always On** in the Customizations panel once per
  workspace.
