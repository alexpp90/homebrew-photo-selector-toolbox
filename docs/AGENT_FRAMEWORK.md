# Agent Framework

How AI coding agents (Claude Code / Cowork, Jules, Gemini CLI, Antigravity) work in this repository: where their instructions live, how work is routed to specialists, and how the framework improves itself over time.

## Design Goals

The framework is built around four principles. First, **one source of truth**: every instruction exists in exactly one canonical file; tool-specific locations are symlinks or thin pointers. Second, **tool neutrality**: any agent product that reads `AGENTS.md` (the cross-tool standard) gets the full picture; Claude and Gemini additionally get native subagent and skill registration. Third, an **always-on lifecycle**: every task begins with mandated context reads and ends with a retrospective — this is how the framework "always starts" regardless of tool. Fourth, **self-improvement**: lessons, refactoring debt, and framework drift are captured as part of finishing a task, not as an afterthought.

## Layout

```
AGENTS.md              Entry point & routing rules (read by Jules, Antigravity, Codex, all AGENTS.md-aware tools)
CLAUDE.md              Thin pointer + Claude-specific notes (auto-loaded by Claude Code/Cowork)
GEMINI.md              Thin pointer + Gemini-specific notes (auto-loaded by Gemini CLI/Antigravity)
.agents/               CANONICAL per-agent definitions (11 agents, YAML frontmatter + instructions)
.skills/               CANONICAL skills (self_improvement, refactoring_guide, playbook_* learned procedures)
.claude/agents         -> symlink to .agents/        (Claude Code subagent registration)
.claude/skills         -> symlink to .skills/        (Claude Code skill registration)
.claude/settings.json  Hooks that inject the task lifecycle into every Claude session
.claude/hooks/         Hook payloads (task-lifecycle.md)
.agents/skills         -> symlink to ../.skills (Antigravity skill registration)
.gemini/agents/*.md    -> per-file symlinks to .agents/*.md
.gemini/skills         -> symlink to .skills/
.gemini/settings.json  Agent roster (name + description) for Gemini tooling
.Jules/                Persistent memory: bolt.md (perf), palette.md (UI/a11y),
                       sentinel.md (security), code_health.md (refactoring backlog)
docs/AGENT_FRAMEWORK.md  This document
```

**Edit rule:** always edit files in `.agents/` and `.skills/`. Never edit through `.claude/` or `.gemini/` or `.agents/skills` paths — they are the same files, but treating the neutral directory as canonical keeps intent clear. When an agent's scope changes, update its `.agents/*.md` file first, then the routing summary in `AGENTS.md` and the roster in `.gemini/settings.json` in the same change.

## Tool Compatibility Matrix

| Capability | Claude Code / Cowork | Jules | Gemini CLI | Antigravity |
|---|---|---|---|---|
| Instructions auto-loaded | `CLAUDE.md` → `AGENTS.md` | `AGENTS.md` | `GEMINI.md` → `AGENTS.md` | `AGENTS.md` |
| Subagent registration | native, via `.claude/agents/` frontmatter | prose routing via `AGENTS.md` roster | `.gemini/agents/` + `settings.json` | prose routing via `AGENTS.md` roster |
| Skills | native, via `.claude/skills/` | read as markdown per `AGENTS.md` | `.gemini/skills/` | native, via `.agents/skills/` |
| Lifecycle enforcement | hooks (`.claude/settings.json`) + rules | rules in `AGENTS.md` | rules in `GEMINI.md`/`AGENTS.md` | rules in `AGENTS.md` |

Tools without native subagents still follow the roster: `AGENTS.md` instructs them to read the matching `.agents/*.md` file and adopt that role before touching files in its scope.

## Agent Roster

Eleven agents. The coordinator (whatever default agent the tool runs) routes by file path and task type — the full delegation rules are in `AGENTS.md`.

Desktop (Python/Tkinter): `backend_agent` (core logic), `gui_agent` (Tkinter UI), `test_agent` (tests/benchmarks), `build_agent` (CI/packaging). Android (Kotlin/Compose): `android_ui_agent`, `android_core_agent`, `android_build_agent` — each spanning both Android Desktop (`android/app/`) and Android Phone (`android/phototok/`) with strict path scoping. Cross-cutting consultants: `photo_researcher_agent` (photographic science), `ux_agent` (design/ergonomics), `publish_agent` (Play Store & legal compliance), `code_health_agent` (continuous improvement — see below), and `mentor_agent` (reflection mentor and memory gatekeeper — see below).

Each `.agents/*.md` file carries YAML frontmatter (`name`, `description`) so Claude Code registers it as a delegatable subagent; the body is the role prompt any tool can adopt.

## Mandatory Task Lifecycle

Defined in `.skills/self_improvement/SKILL.md`; summarized here. It runs on **every task in every tool** — this is the "always started" guarantee.

**Before work:** read the relevant `REQUIREMENTS.md` sections; read the matching `.Jules/` lesson file(s); read your agent config (and `refactoring_guide` for refactorings); identify the target solution(s) and never leak code between them.

**After work (before finalizing):** tests written and passing; `REQUIREMENTS.md` updated if behavior changed; non-obvious lessons appended to the matching `.Jules/` file (dated Learning/Action format); out-of-scope debt filed in `.Jules/code_health.md`; stale agent/skill instructions fixed at their canonical source; no scratch files committed.

In Claude Code the lifecycle is additionally injected mechanically: a `SessionStart` hook prints `.claude/hooks/task-lifecycle.md` into context, and a `UserPromptSubmit` hook adds a one-line reminder per prompt. Both hooks are read-only (no side effects). Other tools rely on the rules being front and center in `AGENTS.md`/`GEMINI.md`, which they always load.

## Self-Improvement Loop

Continuous improvement is closed-loop, with `code_health_agent` as the owner:

1. **Capture, gated by a mentor** — during the mandatory retrospective, the working agent reflects (what failed first, what took longest, what surprised it), drafts candidate memories, and consults `@mentor_agent` — a fresh-context reviewer that dedupes against existing memory, rejects task-diary noise, routes each candidate to the right place (lesson vs. playbook vs. instruction fix), and writes the final wording. Working agents do not write to `.Jules/` or playbooks directly; this two-phase commit (propose → mentor approves) is what keeps memory sharp. Refactoring candidates go to `.Jules/code_health.md`. In tools without subagents, the agent adopts `mentor_agent.md` as a role in a separate reflection pass or files candidates as `[PROPOSED]`.
2. **Schedule** — `code_health_agent` periodically works the backlog: behavior-preserving refactorings, test-covered before and after, following `.skills/refactoring_guide/SKILL.md`, never mixed with behavior changes.
3. **Framework maintenance** — when instructions themselves are found stale or wrong mid-task, they are fixed immediately at the canonical source (`.agents/`, `.skills/`) with the summaries synced. The framework is treated as code: it has an owner, a backlog, and a definition of done.
4. **Procedural learning (playbooks)** — recurring task types get a `playbook_<task>` skill (template: `.skills/playbook_TEMPLATE/`) recording the efficient path: file touchpoints, step order, working commands, traps. Agents check for a matching playbook before starting (Phase 1) and must improve it — or at least bump its `last_validated` date — after using it (Phase 2, step 6). This is how the system gets *faster* at repeated work, not just safer.
5. **Consolidation** — `code_health_agent` periodically merges duplicate lessons, deletes invalidated ones, promotes procedure-shaped lessons into playbooks, and prunes stale playbooks, so memory stays small and trustworthy instead of accreting noise.

## Persistent Memory (`.Jules/`)

Append-only lesson files, newest first, format `## YYYY-MM-DD - Title` / `**Learning:**` / `**Action:**`. Lessons must be generalizable rules, not task diaries; skim before appending to extend rather than duplicate. `code_health.md` additionally holds backlog items tagged `[OPEN]`/`[DONE]`.

## How To

**Add an agent:** create `.agents/<name>.md` with frontmatter (`name`, `description` — write the description so a coordinator knows *when* to delegate, including "use proactively" if applicable); add a per-file symlink in `.gemini/agents/`; add the roster entry to `.gemini/settings.json`; add roster + delegation rows to `AGENTS.md`.

**Add a skill:** create `.skills/<name>/SKILL.md` with frontmatter (`name`, `description`); Claude sees it via the `.claude/skills` directory symlink; add a per-item symlink `.gemini/skills/<name> -> ../../.skills/<name>`; list it under Shared Skills in `AGENTS.md`. Playbooks (`playbook_<task>`) follow the same steps, starting from `.skills/playbook_TEMPLATE/`.

**Change a scope:** edit the `.agents/*.md` file, then sync `AGENTS.md` tables and `.gemini/settings.json` in the same change (rule 7 in `AGENTS.md`).

**Record a lesson:** append to the matching `.Jules/` file during the retrospective — never in a separate scratch file.

## Known Gaps

- `docs/phototok/` (release checklist, privacy policy, Impressum) is referenced by `publish_agent` but does not exist yet — it must be created when release work resumes.
- Symlinks require `core.symlinks=true` (default on macOS/Linux). Windows checkouts need Developer Mode or `git config core.symlinks true`.
