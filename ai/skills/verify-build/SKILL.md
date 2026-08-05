---
name: verify-build
description: "Run the local CI mirror (./scripts/run_tests.sh) and interpret its gate summary, including which ⊘ SKIPPED gates are accepted risk. Use before finishing any change, and whenever a .github/workflows/ gate is added, removed or altered."
allowed-tools: Bash, Read, Grep
---

# Verify against CI, not a subset

`scripts/run_tests.sh` is the **local mirror of every gate CI enforces**. It is the only
command that constitutes evidence a change will pass.

```bash
./scripts/run_tests.sh          # default
./scripts/run_tests.sh --all    # add when an emulator or device is attached
```

## Why a bare test command is not enough

| You ran | What it misses |
|---|---|
| `pytest` | flake8 (which gates the *entire* desktop pipeline), the coverage threshold, the `-m "not visual"` split |
| `gradlew testDebugUnitTest` | `assembleDebugAndroidTest` — the only task that compiles `tests/instrumented/` |

Running a subset and calling it verified is how a branch goes green locally and red in
Actions, which produces the chain of blind `fix(ci)` commits this project explicitly bans.

## Read the summary table

The script prints a per-gate summary. Three outcomes:

- **PASS** — done.
- **FAIL** — the task is not finished. Fix it here. Do **not** push a guess and let CI
  adjudicate it.
- **⊘ SKIPPED** — the gate could not run locally, with a reason. **A skipped gate is an
  accepted risk, not a pass.**

State explicitly in your task summary which gates were skipped and why. To generate that
sentence:

```bash
./scripts/run_tests.sh 2>&1 | python3 ai/skills/verify-build/scripts/summarize_gates.py
```

`docs/build/CI_PARITY.md` lists which gates are legitimately un-runnable locally — emulator
instrumented tests, visual regression off Linux, credentialed publish steps — and how to read
a CI failure for each.

## The parity obligation

If you add, remove or change a gate in `.github/workflows/`, then **in the same commit**:

1. add the equivalent to `scripts/run_tests.sh`;
2. update the gate matrix in `docs/build/CI_PARITY.md`.

A gate that exists only in CI cannot be satisfied before pushing. That asymmetry is the
root cause the parity rule removes.

## Framework validation

`run_tests.sh` also runs `validate_framework.py`, which fails on agent/skill definitions that
reference files that do not exist, name mismatches, broken symlinks and roster drift. If that
gate fails, fix it with the `sync-framework` skill — do not skip it.
