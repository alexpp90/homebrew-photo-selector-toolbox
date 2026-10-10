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
./scripts/run_tests.sh            # auto-detects changed products (mirrors CI paths: filters)
./scripts/run_tests.sh --all      # run everything unconditionally
./scripts/run_tests.sh --python   # Python gates only
./scripts/run_tests.sh --android  # Android gates only
./scripts/run_tests.sh --macos    # macOS Desktop native Swift gates
./scripts/run_tests.sh --linux    # Linux Desktop native gates
./scripts/run_tests.sh --strict   # non-zero exit if any unrunnable gate had to be skipped
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
| `swift test (macos-desktop)` | `macos.yml:swift-test` | ⚠️ Darwin only | Native Swift 6 suite covering PhotoSelectorKit & App (release SLA benchmark included). Runs on `macos-latest` in CI. |
| macOS UI smoke test (running app) | — (local-only) | ⚠️ Darwin + logged-in GUI session | `products/macos-desktop/scripts/ui_smoke_test.sh`: drives the running app (raw binary **and** `.app` bundle) — activation policy, real key events, rendered Focus 3-Up geometry, clicks while zoomed, time-to-first-photo. Window-server key probe needs Accessibility permission, else `⊘ SKIPPED`. See [macOS UI smoke test](#macos-ui-smoke-test-local-only). |
| `linux-desktop lint` | `linux.yml:lint` | ✅ everywhere | Flake8 static analysis on `products/linux-desktop/` |
| `linux-desktop test` | `linux.yml:test` | ✅ everywhere | Headless unit tests covering metadata, scoring, and file transactions |
| `debian packaging & apt repo` | `linux.yml:package` | ⚠️ Linux / `dpkg-deb` only | Verifies `.deb` package creation and APT metadata generation |
| PyInstaller build, release, Play/Firebase publish | `desktop.yml:build`+, `android.yml:build`+, `macos.yml:build` | ❌ credentials-bound | Only runs on pushes to `main` / `v*` tags. Not reproducible locally by design. |
| `sync_framework.py --check` | — (local CI mirror) | ✅ everywhere | Toolchain synchronization: verifies Cursor, Windsurf, Copilot, Gemini settings, and mirrors match canonical definitions in `ai/` with zero drift. |
| `validate_framework.py` | — (local CI mirror) | ✅ everywhere | Agent-framework integrity: naming, registration, roster, toolchain parity, unowned product detection, product boundary leaks, and paths referenced by instructions. |
| `gen_gemini_settings.py --check` | — (local CI mirror) | ✅ everywhere | Fails when `.gemini/settings.json` has drifted from `ai/agents/*.md`. Fix by regenerating, never by hand-editing. |
| `verify_requirements_traceability.py --check --strict` | — (local CI mirror) | ✅ everywhere | Validates bidirectional requirement-to-test traceability: ensures requirement IDs exist in specs, rejects orphan test references, and strictly isolates multi-product test suite mappings. |

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

### macOS native Swift testing and Darwin runner isolation

The macOS Desktop application (`products/macos-desktop/`) is built on Swift 6, SwiftUI,
Apple Vision (`VNCalculateImageAestheticsScoresRequest`), Accelerate vImage, and AppKit.
These proprietary frameworks require Apple Darwin operating system kernels:

1. **Darwin Host Dependency**: Running `swift test` against `products/macos-desktop/`
   requires a macOS host environment. In GitHub Actions, `.github/workflows/macos.yml`
   runs on `macos-latest` runners (Apple Silicon arm64). On Linux or Windows developer
   machines, `scripts/run_tests.sh` reports `swift test (macos-desktop)` as `⊘ SKIPPED`.
2. **Xcode 16 / Swift 6 Parity**: The CI runner selects the active Xcode 16 toolchain,
   matching local developer environments.
3. **Performance SLA Verification**: The test suite includes latency SLA gates
   (e.g., `FocusMetricService` 1080p frame latency < 2.0ms core compute in release mode),
   which run under `swift test -c release`.

### macOS UI smoke test (local-only)

`swift test` cannot see failures that live outside `PhotoSelectorKit`. The keyboard router's
unit tests passed for months while arrow keys did nothing in the real app: an unbundled
SwiftPM binary starts with activation policy `.prohibited`, so the window server never makes
it the active app and delivers no key events to it. `run_tests.sh --macos` therefore also
runs `products/macos-desktop/scripts/ui_smoke_test.sh`, which builds the app, generates 120
camera-sized landscape JPEGs (plus decoys in an excluded `Selection/` folder) and launches
the app with `--ui-self-test`. The in-app runner (`UISelfTestRunner`) asserts:

- activation policy `.regular`, app active, workspace window key, key monitor attached;
- the first photo is drawn in under 2 s, before the scan completes, and every photo is listed;
- posted `NSEvent` key-downs (→ ← ↓ 3 Space Esc) change the workspace state;
- the rendered Focus 3-Up frames: CURRENT above PREVIOUS and NEXT, PREVIOUS left of NEXT;
- posted mouse clicks on the toolbar and on the Fit pill work while zoomed.

It runs against the raw binary (the `swift run` path) and the assembled `PhotoSelector.app`.
On the raw binary it also sends a real arrow key through the window server with
`osascript … System Events … key code 124`. That needs Accessibility permission for the
calling terminal; without it the probe prints `⊘ SKIPPED` and the gate still passes on the
in-app checks. Without a logged-in GUI session (SSH, headless CI) the gate is `⊘ SKIPPED`.
It is local-only: GitHub's macOS runners have no reliable foreground session. `--quick`
skips it.

### Automatic path filtering (selective product gates)

GitHub Actions workflows (`desktop.yml`, `android.yml`, `macos.yml`) configure `paths:`
filters so that pull requests touching only Android do not execute Desktop or macOS CI,
and vice versa. `run_tests.sh` mirrors this locally: unless `--all` or an explicit product
flag (`--python`, `--android`, `--macos`, `--linux`) is given, it inspects git status and
branch diffs against `main`. If only one product has modifications, gates for untouched
products are reported as skipped due to path filtering rather than executing redundant suites.

### Headless & background GUI execution on macOS

On macOS, running Tkinter GUI tests natively causes Aqua Tk to invoke `[NSApp activateIgnoringOtherApps:YES]`
and `[NSWindow makeKeyAndOrderFront:]`, which steals window focus from the user and flashes test windows
on screen. In `products/desktop/tests/conftest.py`, AppKit activation and window order methods are
automatically swizzled via ctypes during pytest runs on Darwin. This keeps GUI testing completely
backgrounded without stealing focus or displaying windows, preserving full test fidelity in memory.
To disable this and view GUI windows interactively, run with `PST_SHOW_GUI=1`.

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

## Adversarial Pre-Delivery Verification Gates

To ensure hands-off autonomous execution with zero required code review, changes must
satisfy five adversarial verification gates prior to delivery:

1. **Focus Theft & Window Isolation**: GUI test suites must execute completely headless
   or backgrounded without stealing user window focus, flashing test windows, or altering
   system input focus. Enforced via ctypes method swizzling on macOS for Tkinter and
   `MainActor`-isolated async testing in Swift.
2. **Automated Visual Contrast & Layout Non-Occlusion (WCAG 2.1 AA)**:
   All visual UI elements must satisfy minimum contrast ratios (>= 4.5:1 for normal text,
   >= 3.0:1 for graphical controls and large text). Dark-on-dark styling is strictly banned.
   All dialogs, buttons, and input controls must undergo bounding box intersection audits
   to verify zero occluded or clipped controls across all supported screen dimensions.
3. **Concurrency & Race Condition Hardening**:
   Asynchronous workflows (e.g. score calculation, thumbnail generation, duplicate scanning)
   must be validated against adversarial burst inputs, out-of-order undo actions, and
   cooperative task cancellation without memory leaks or state corruption.
4. **Bidirectional Requirements Traceability**:
   `scripts/verify_requirements_traceability.py --check --strict` validates that every
   observable requirement identifier exists in product specifications, is mapped to automated
   tests, and contains zero orphan test references.
5. **Pre-Delivery Intent Gate**:
   Lifecycle Stop hooks (`ai/hooks/check_retrospective.py` and `ai/hooks/verify_intent_delivery.py`)
   verify that tests were executed during the session and that all prompt directives from
   `ai/memory/intent_ledger.jsonl` were fulfilled before work can be marked complete.

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
- **macOS pipeline fails** → check `swift-test` step logs; compilation errors,
  missing Xcode toolchain, or test assertions will be reported with exact file
  and line references.
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
