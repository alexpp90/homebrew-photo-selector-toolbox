# CI Parity — what runs where, and why

**Read this before pushing a branch.** It exists because of a recurring failure
mode: a change is green locally, red in GitHub Actions, and the branch then
accumulates a chain of blind `fix(ci)` / `fix(test)` commits, each one a guess
validated only by a 15-minute CI round trip.

The rule that prevents it:

> **Every gate CI enforces must be reproducible locally, or explicitly
> documented as un-reproducible with the reason why.**
> `scripts/run_tests.sh` is the mirror. It reports each gate as
> PASS / FAIL / **SKIPPED**, and a SKIPPED gate is a known risk you are
> accepting, not an absence of risk.

```bash
./scripts/run_tests.sh            # everything runnable on this machine
./scripts/run_tests.sh --strict   # non-zero exit if any gate had to be skipped
```

---

## Gate matrix

| Gate | CI job | Local | Notes |
|---|---|---|---|
| flake8 `src/ tests/` | `desktop.yml:lint` | ✅ everywhere | **Gates the entire desktop pipeline.** A lint error fails the run before one test executes. |
| pytest `-m "not visual"` | `desktop.yml:test` | ✅ everywhere | Same marker filter locally and in CI. |
| Coverage `--cov-fail-under=60` | `desktop.yml:test` (**Linux only**) | ✅ advisory everywhere | See [Coverage is Linux-authoritative](#coverage-is-linux-authoritative). |
| Visual regression | `desktop.yml:visual` | ⚠️ Linux + `DISPLAY` only | See [Visual regression](#visual-regression-linux-only). |
| `testDebugUnitTest` | `android.yml:unit-tests` | ✅ needs JDK 17 + Android SDK | |
| `assembleDebugAndroidTest` | `android.yml:unit-tests` | ✅ needs JDK 17 + Android SDK | Compiles `tests/instrumented/`. Nothing else does. |
| `lintDebug` | `android.yml:lint` | ✅ advisory | Non-blocking in both places, on purpose. |
| `connectedDebugAndroidTest` | `android.yml:instrumented-tests` | ❌ **needs emulator/device** | See [Instrumented tests](#instrumented-tests-genuinely-need-a-device). |
| PyInstaller build, release, Play/Firebase publish | `desktop.yml:build`+, `android.yml:build`+ | ❌ credentials-bound | Only runs on pushes to `main` / `v*` tags. Not reproducible locally by design. |
| `validate_framework.py` | — (local only) | ✅ everywhere | Agent-framework integrity: naming, registration, roster, and paths referenced by instructions. No CI job yet — the inverse of the usual asymmetry, and safe because it needs only `python3`. |
| `gen_gemini_settings.py --check` | — (local only) | ✅ everywhere | Fails when `.gemini/settings.json` has drifted from `ai/agents/*.md`. Fix by regenerating, never by hand-editing. |

---

## Why each un-reproducible gate stays un-reproducible

### Coverage is Linux-authoritative

`products/desktop/tests/conftest.py` skips tests by platform (`linux_only`, `mac_only`,
`windows_only`) and skips `gui_required` tests when no display is available.
Each OS therefore executes a **different subset** and reaches a **different**
coverage number.

Enforcing one threshold on all three runners made coverage a per-OS lottery: a
change could pass on a contributor's Mac and fail `--cov-fail-under` only on
the Windows runner — with no local reproduction available. The threshold is now
enforced on Linux only (largest subset = authoritative). macOS and Windows still
run the full suite and still fail on any real test failure.

`run_tests.sh` runs the coverage gate on every platform anyway, because a
genuine coverage regression is worth catching early. If it fails locally on
macOS but the Linux CI number is fine, trust Linux.

### Visual regression (Linux only)

Baselines in `products/desktop/tests/visual/baselines/` are rendered under Xvfb. macOS and
Windows font rasterisation differs enough to produce false diffs, so the tests
are marked `linux_only` and the local runner reports them SKIPPED off Linux.

To run them on Linux:

```bash
Xvfb :99 -screen 0 1280x1024x24 & export DISPLAY=:99
./scripts/run_tests.sh --python
```

### Instrumented tests genuinely need a device

`products/android/android-desktop/tests/instrumented/` and `products/android/phototok/tests/instrumented/` assert
against the **live Compose semantics tree** and use Hilt test injection, a real
`Activity` lifecycle, and Room on a real SQLite instance. These are the right
tests for what they cover — they are not portable to the JVM without losing the
behaviour they exist to verify, so **they stay where they are**.

What changed is that their *failure modes are now split*:

1. **Compilation errors** — unresolved references, wrong matcher names, wrong
   data-class fields. These no longer require a device. `run_tests.sh` runs
   `assembleDebugAndroidTest`, which compiles the exact same Kotlin + KSP/Hilt
   sources in ~2 minutes. **This catches them before you push.**
2. **Semantics-tree assertion errors** — merged vs. unmerged tree, a matcher
   hitting multiple nodes, ancestor scoping. These still require a real
   runtime. Mitigate them by following the conventions below rather than by
   guessing through CI.

To run them locally:

```bash
emulator -avd <avd_name> -no-audio -no-boot-anim &
adb wait-for-device
./scripts/run_tests.sh --android-device
```

If you have no emulator, that is a legitimate SKIP — but then treat the first
CI run as a real test run, read the uploaded
`instrumented-test-report-api30` artifact, and fix from the report rather than
pushing another guess.

---

## Compose instrumented-test conventions

Of the nine consecutive `fix(test)` / `fix(android)` commits on
`feat/score-chips-and-first-run-hints`, **one** was a compile error and
**eight** were the same small family of semantics-tree mistakes. Following
these four rules removes almost all of that class:

1. **Use `onAllNodes(...).onFirst()`, not `onNode(...)`, for anything that can
   legitimately match more than once.** `onNode` throws when a matcher hits
   several nodes, and Compose duplicates nodes far more often than it looks —
   a text label appears in both the merged parent and the unmerged child.
2. **Pass `useUnmergedTree = true` when asserting on content inside a
   container that merges semantics** — `ModalBottomSheet`, list rows, buttons
   with an icon plus label. Without it the child node you are matching does not
   exist in the tree you are querying.
3. **Scope assertions to their container** with
   `hasAnyAncestor(hasTestTag("..."))` rather than matching bare text globally.
   A bare `hasText("Sharpness")` will find the chip *and* the legend row.
4. **Import Compose test matchers explicitly, never
   `import androidx.compose.ui.test.*`.** The wildcard is what let `hasTag`
   (which does not exist; the real matcher is `hasTestTag`) survive review and
   reach CI.

Rules 1–3 are also recorded in `.Jules/palette.md`; rule 4 in
`.Jules/code_health.md`.

---

## Reading a failed Actions run

- **Emulator job fails in under ~5 minutes** → it is a *build* failure, not a
  test failure. The emulator cannot even boot that fast. The annotation reads
  `The process '/usr/bin/sh' failed with exit code 1` and hides the real Kotlin
  error. Run `./scripts/run_tests.sh --android` locally to see it in plain text.
- **Desktop pipeline fails with no test output** → the `lint` job failed;
  everything else was skipped by `needs:`. Run flake8.
- **`publish-play` fails on a missing/empty artifact** → the upstream build
  step did not produce it. All release uploads now use
  `if-no-files-found: error`, so this should fail at the producing step first.
- **An "Unexpected input(s) …" annotation** is a *warning*, not an error: the
  input was silently ignored and whatever it was supposed to configure never
  took effect. Treat it as a broken fix, not cosmetic noise.

---

## Keeping this file honest

When you add or change a gate in `.github/workflows/`, in the **same commit**:

1. Add the equivalent to `scripts/run_tests.sh` (or add an explicit `skip`
   with the reason it cannot run locally).
2. Update the gate matrix above.

A gate that exists only in CI is a gate contributors and agents cannot satisfy
before pushing — which is exactly how the push-and-pray loop starts.
