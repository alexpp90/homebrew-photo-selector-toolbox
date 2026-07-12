# Gemini Instructions

All agent instructions for this repository live in [`AGENTS.md`](AGENTS.md). Read it before making any changes. Framework documentation: [`docs/AGENT_FRAMEWORK.md`](docs/AGENT_FRAMEWORK.md).

Key rules (summary — `AGENTS.md` is authoritative):

- **Mandatory task lifecycle** (`.skills/self_improvement/SKILL.md`): before work, read the relevant `REQUIREMENTS.md` sections and `.Jules/` lessons; after work, run the retrospective (tests, requirements sync, lesson capture, refactoring backlog).
- Write and run tests for every feature, fix, or logic change.
- Never commit scratch files, report dumps, or PR description drafts.
- Three independent solutions: Desktop (`src/`, Python), Android Desktop (`android/app/`), Android Phone / PhotoTok (`android/phototok/`). Do not leak code between them.
- Per-agent configs: canonical in `.agents/*.md` (`.gemini/agents/` symlinks to them), roster in `.gemini/settings.json`. Use `code_health_agent` for refactoring and retro follow-ups.
- Learned lessons live in `.Jules/` (`bolt.md` performance, `palette.md` UI/a11y, `sentinel.md` security, `code_health.md` refactoring backlog). Read the relevant file before working in those areas; append new lessons.
