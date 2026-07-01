# Claude Instructions

All agent instructions for this repository live in [`AGENTS.md`](AGENTS.md). Read it before making any changes.

Key rules (summary — `AGENTS.md` is authoritative):

- Read `REQUIREMENTS.md` before starting work; update it when behavior changes.
- Write and run tests for every feature, fix, or logic change.
- Never commit scratch files, report dumps, or PR description drafts.
- Three independent solutions: Desktop (`src/`, Python), Android Desktop (`android/app/`), Android Phone / PhotoTok (`android/phototok/`). Do not leak code between them.
