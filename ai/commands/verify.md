---
description: Run the local CI mirror and report the gate summary
---

Run the `verify-build` skill (`ai/skills/verify-build/SKILL.md`).

```bash
./scripts/run_tests.sh $ARGUMENTS
```

Pass `--all` when an emulator or device is attached.

Then report, explicitly:

1. every gate that **failed**, and what you are doing about it;
2. every gate reported `⊘ SKIPPED`, with its reason — these are accepted risks, not passes,
   and they must appear in the task summary;
3. whether `.github/workflows/` changed in this task, and if so whether `scripts/run_tests.sh`
   and `docs/build/CI_PARITY.md` were updated in the same commit.

Do not describe the change as verified if any gate failed.
