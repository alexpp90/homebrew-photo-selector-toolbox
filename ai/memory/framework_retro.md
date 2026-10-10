# AI Framework Retrospective & Session Evaluation

Persistent record of session efficiency, implementation quality, and AI framework fit.
Owned by `@shared-code-health-agent`; appended during the mandatory post-task retrospective (`ai/skills/retrospective/SKILL.md`).

## Purpose

While `bolt.md`, `palette.md`, `sentinel.md`, and `code_health.md` capture product-level code knowledge and technical debt, this file captures **meta-reflections on how well the AI framework worked**:
- **Framework Fit & Tooling**: Did the framework suit the task? How did the involved Agents, Skills, and Hooks perform?
- **Session Efficiency**: Search and navigation friction, context churn, time wasted locating code.
- **Implementation Quality**: Adherence to requirements, first-pass test success, avoidance of speculative guessing.
- **Root Cause & Framework Improvements**: Concrete changes to instructions, skills, playbooks, docs, or hooks to eliminate recurring friction.

## Entry Format

Newest entries at the top. Always use the real current date.

```markdown
## YYYY-MM-DD - <Short Task / Topic Title>
**Agents Involved:** `@<agent-name>`
**Framework Fit & Tooling:**
- **Fit:** [Strong | Adequate | Poor] — Why the framework matched or mismatched the task.
- **Agents:** Which agents were used/adopted; did scope boundaries hold or create friction?
- **Skills:** Which skills were invoked/read; were they helpful, complete, or missing crucial guidance?
- **Hooks:** Which hooks fired; did they protect or impede?
**Session Efficiency:** [High | Moderate | Low / Friction]
- **Observations:** What took longest and why (e.g. "Took a lot of time to find related code because of...").
**Implementation Quality:** [High | Rework Required | Defect Caught Late]
- **Observations:** First-pass test pass rate, requirement oversights, speculative edits (e.g. "Implementation quality slipped because requirements were not checked upfront...").
**Framework Root Cause:** What aspect of the instructions, skills, documentation, or code structure caused the friction.
**Actionable Framework Improvement:** Concrete proposal for skills, agents, playbooks, tooling, or docs (or `[OPEN]` backlog item for `@shared-code-health-agent`).
```

## 2026-10-10 - Multi-Product v0.5.0 Release Preparation, Legacy Desktop Archival, and CI Parity
**Agents Involved:** `@desktop-build-agent`, `@android-shared-build-agent`, `@macos-desktop-agent`, `@linux-desktop-agent`, `@shared-code-health-agent`, `@shared-mentor-agent`
**Framework Fit & Tooling:**
- **Fit:** Strong — The cross-product CI mirror script (`./scripts/run_tests.sh`) accurately reflected gates across all 5 products, and `guard_paths` successfully prevented committing unintended artifacts.
- **Agents:** Handled repository-wide release version synchronization and legacy desktop preservation.
- **Skills:** `task-lifecycle`, `retrospective`, `verify-build`, `sync-requirements`.
- **Hooks:** PreToolUse hook blocked `bug_report.md` edit due to strict regex match on `_report.md`, preventing unintended edits to issue templates.
**Session Efficiency:** High
- **Observations:** Git log inspection revealed existing local `archive/legacy-desktop` tag and branch, allowing instant preservation to remote `origin`. Full CI test suite ran synchronously in under 2 minutes with zero failures.
**Implementation Quality:** High
- **Observations:** All gates passed on the first run; version code offsets and version names cleanly bumped to 0.5.0 and code 6 across Android, Python Desktop, Formula, and Cask.
**Framework Root Cause:** The Linux Desktop product was lacking a mirrored `.github/workflows/linux.yml` file, which was immediately added to guarantee CI parity with the local test mirror.
**Actionable Framework Improvement:** Ensure newly added products always receive their corresponding `.github/workflows/<product>.yml` file in the same commit they are introduced.

## 2026-10-10 - macOS Desktop Keyboard Delivery, One-Over-Two 3-Up, Zoom Hit-Testing & Folder Load
**Agents Involved:** `@macos-desktop-agent`, `@shared-mentor-agent`
**Framework Fit & Tooling:**
- **Fit:** Adequate. Product scoping and the Kit/App split worked, but the framework had no gate that ran the real app, so four user-visible defects survived several "verified" sessions.
- **Agents:** `@macos-desktop-agent` owned the change; scope held inside `products/macos-desktop/`.
- **Skills:** `task-lifecycle`, `verify-build`, `sync-requirements`, `retrospective`. sync-requirements caught the decisive fact (REQ-MAC-LAYOUT.02 itself specified three columns).
- **Hooks:** scope/path guards were quiet. The new `osascript` window-server probe hung on error -1712 until bounded with `with timeout of 5 seconds`.
**Session Efficiency:** Moderate. Most time went to undoing confident prior "fixes" before measuring.
- **Observations:** The decisive step was one measurement (`NSApp.activationPolicy`/`isActive` at runtime). Earlier sessions edited the router without it.
**Implementation Quality:** Defect Caught Late (by the user, repeatedly)
- **Observations:** The 2026-10-07 and 2026-10-09 entries rated "High" fixes that never reached the user. Unit tests passed because they injected events and asserted geometry below the failing layer. Memory and FEATURE_PARITY then repeated the false claims (focus-routing "guarantee"; three-column triplet "ported" to Linux).
**Framework Root Cause:** "Verified" meant "unit tests green". No gate drove the running app, and retro entries recorded a fix as working without saying how it was observed.
**Actionable Framework Improvement:** (1) The `ui_smoke_test.sh` gate (local-only, `run_tests.sh --macos`, documented in CI_PARITY) is mandatory for any keyboard, layout, zoom or latency claim in macOS Desktop. (2) Any retro entry rating Implementation Quality "High" for a user-reported defect must name the user-path evidence (running-app test, device run). Unit tests alone earn "Unverified on user path". (3) Every probe that can block on a system service (osascript, Accessibility, device) is time-bounded and reports SKIPPED, never hangs.

## 2026-10-10 - Comprehensive AI Framework Audit, Remediation & Standardization
**Agents Involved:** `teamwork_preview` (Sentinel, Orchestrator, Explorers, Workers, Reviewers, Challengers, Forensic Auditor, Victory Auditor), `@shared-code-health-agent`, `@shared-mentor-agent`
**Framework Fit & Tooling:**
- **Fit:** Strong — Multi-agent teamwork system with adversarial challengers and forensic auditing delivered complete cross-product parity, intent traceability, and zero-code-review quality gates.
- **Agents:** Orchestrators (`orchestrator_3`, `orchestrator_3_gen1`) coordinated 5 milestones across 16 canonical agents (including new `@macos-desktop-agent` and `@linux-desktop-agent`).
- **Skills:** `task-lifecycle`, `retrospective`, `sync-framework`, `verify-build`, `sync-requirements`.
- **Hooks:** Scope guards, path guards, commit guards, and new pre-delivery intent verification hook (`ai/hooks/verify_intent_delivery.py`).
**Session Efficiency:** High
- **Observations:** Multi-agent self-healing recovered seamlessly from server restart and individual API quota rate-limit pauses via Sentinel persistent state. Idempotence bug with read-only file removal on repeated sync passes and asynchronous disk test sleep jitter were successfully caught and remediated via adversarial review.
**Implementation Quality:** High (Adversarially Gated & Victory Confirmed)
- **Observations:** All 5 milestones cleared independent gates: 276 machine-readable requirement IDs mapped across Python, Android, and Swift; 12 toolchain files generated across Cursor, Windsurf, Copilot, Gemini; automated retro synthesis engine; 32 WCAG 2.1 contrast tests; 17 geometric occlusion tests; and full CI mirror passing across all products.
**Framework Root Cause:** Previous lack of machine-readable requirement identifiers and lack of automated toolchain generators caused documentation and configuration drift when new native apps were created.
**Actionable Framework Improvement:** Maintain canonical definitions in `ai/` and enforce automated multi-toolchain compilation (`sync_framework.py`), requirement traceability scanning (`verify_requirements_traceability.py`), and empirical UI contrast/occlusion testing on every milestone.

---

## 2026-10-09 - PhotoTok FTUE, Usability & File Safety Improvements
**Agents Involved:** `@phototok-ui-agent`, `@phototok-core-agent`, `@shared-mentor-agent`
**Framework Fit & Tooling:**
- **Fit:** Strong — The domain separation between pure domain rules (`RelatedFiles`, `PhoneSettings`, `PhoneFeedOrdering`) and UI components (`PhoneModeLanding`, `ViewerBottomBar`, `SettingsScreen`) made FTUE improvements straightforward to verify via Robolectric unit tests.
- **Agents:** `@phototok-ui-agent` implemented the UI/landing/settings changes; `@phototok-core-agent` hardened sibling discovery safety; `@shared-mentor-agent` adjudicated memory lessons.
- **Skills:** `task-lifecycle`, `sync-requirements`, `record-lesson`, and `retrospective` ensured requirements, tests, and cross-product parity sync stayed aligned.
- **Hooks:** Scope guards and `./scripts/run_tests.sh` CI mirror caught build state and framework integrity checks.
**Session Efficiency:** High
- **Observations:** Fixed the FTUE landing page by providing direct dual sources ("Phone Camera" and "Custom Folder"), eliminating the redundant "Start Browsing" gate, adding an explicit warning card for empty folders, decaying coach mark tutorials only after ≥7 days of app inactivity, decoupling deletion undo from vertical paging, and clarifying the Selection folder affordance.
**Implementation Quality:** High
- **Observations:** 100% of PhotoTok unit tests pass (including `PhoneModeViewModelTest`, `SettingsRepositoryTest`, `RelatedFilesTest`, `SelectionViewerViewModelTest`). Discovered that `UnconfinedTestDispatcher` executes coroutine discovery synchronously to completion, requiring proper mocking of subsequent folder scans when testing state reset.
**Framework Root Cause:** Initial tests assumed folder switching would lazily evaluate discovery; understanding the unconfined dispatcher's execution model ensured robust assertion ordering.
**Actionable Framework Improvement:** When testing ViewModel state transitions that trigger new coroutine launches on an `UnconfinedTestDispatcher`, configure mocks for subsequent invocations explicitly to avoid testing stale mock fallbacks.

---

## 2026-10-09 - macOS Desktop Contrast, Folder Ingestion HUD & Sliding Triplet Focus Alignment
**Agents Involved:** `@macos-desktop-agent`, `@shared-mentor-agent`
**Framework Fit & Tooling:**
- **Fit:** Strong — Headless core architecture (`PhotoSelectorKit`) and SwiftUI views (`PhotoSelectorApp`) allowed modular, high-confidence styling and navigation updates while maintaining 100% test coverage.
- **Agents:** `@macos-desktop-agent` implemented the macOS desktop improvements; `@shared-mentor-agent` verified memory criteria.
- **Skills:** `task-lifecycle`, `sync-requirements`, `verify-build`, and `retrospective` structured the workflow, documentation sync, and gate verification.
- **Hooks:** Scope guards and `./scripts/run_tests.sh` CI mirror validated build and test integrity across all repository products.
**Session Efficiency:** High
- **Observations:** Enforced global studio dark mode with WCAG AAA high-contrast white text (`Color.white` / `#FFFFFF`) across all views, eliminating dark-on-dark illegibility. Implemented instant folder scanning HUD feedback (`FolderLoadingView`) with animated progress. Aligned 3-Up mode to a genuine Sliding Triplet layout (previous on left, current in middle, next on right) where navigation and auto-advancing culling slide the entire triplet together across the album.
**Implementation Quality:** High
- **Observations:** All 204 native Swift unit tests pass in 1.1s. Fixed timing vulnerabilities in async stress tests (replacing fixed sleep with polling loops and using debug-aware SLA thresholds). Full CI mirror (`./scripts/run_tests.sh`) passed all gates.
**Framework Root Cause:** The original 3-Up implementation treated 3-Up as a slot-replacement tournament rather than photographic context. Re-anchoring to the photographer workflow specification (`docs/products/desktop/REQUIREMENTS.md` §3.2) resolved the architectural mismatch.
**Corrected 2026-10-10:** the three-column triplet was wrong. Desktop REQ-DESK-VIEW.02 is a top/bottom grid, and the user requires current on top, previous bottom-left, next bottom-right. The macOS spec itself encoded the error; fixed in REQ-MAC-LAYOUT.02.
**Actionable Framework Improvement:** In asynchronous concurrency tests, always prefer polling with timeout budgets over hardcoded sleeps to avoid false test failures under heavy CPU/CI load.

---

## 2026-10-07 - macOS Desktop Arrow Navigation, EXIF Extraction & Focus Mode Polish
**Agents Involved:** `@macos-desktop-agent`, `@shared-mentor-agent`
**Framework Fit & Tooling:**
- **Fit:** Strong — Headless architecture (`PhotoSelectorKit`) decoupled from UI (`PhotoSelectorApp`) allowed isolated unit testing of key interceptors, ImageIO metadata parsing, and state navigation without UI harness overhead.
- **Agents:** `@macos-desktop-agent` owned `products/macos-desktop/` implementation and tests; `@shared-mentor-agent` verified memory criteria.
- **Skills:** `task-lifecycle`, `retrospective`, `verify-build`, `sync-requirements` guided the end-to-end resolution.
- **Hooks:** Git status and CI test mirror (`./scripts/run_tests.sh`) operated smoothly.
**Session Efficiency:** High
- **Observations:** Root-causing arrow key hijacking was immediate: SwiftUI default keyboard focus traverses focusable buttons using arrow keys unless `.focusable(false)` is specified and `NSEvent.addLocalMonitorForEvents` intercepts `.keyDown`. Root-causing EXIF omission surfaced container-level property segregation in camera RAW files.
**Implementation Quality:** High
- **Observations:** Added 12 new unit tests covering `KeyboardShortcutRouter` (arrow keys, Vim j/k, delete, space, 1/2/3 modes, ⌘Z undo, text field isolation), APEX shutter/aperture conversion, camera model formatting, and formatted optical summary. All 204 unit tests across 24 suites pass in 0.70s.
**Framework Root Cause:** The initial UI layout lacked `.focusable(false)` on clickable controls, allowing AppKit to steal arrow keys from the preview container. Concurrently, `DirectoryScanner` initially omitted EXIF scanning during progressive batch generation.
**Actionable Framework Improvement:** In SwiftUI macOS desktop apps, always pair top-level key routing with `.focusable(false)` on toolbar buttons and use `NSEvent.addLocalMonitorForEvents` to guarantee keyboard shortcut supremacy over UI focus loops.
**Corrected 2026-10-10:** this did not fix arrow keys. The unbundled app had activation policy `.prohibited` and received no key events; router tests inject events and could not see it. See palette.md 2026-07-24 (extended).

---

## 2026-10-07 - Native macOS Desktop Swift/SwiftUI Application & Culling Overhaul
**Agents Involved:** `teamwork_preview` (Sentinel, Orchestrator, Explorers, Workers, Reviewers, Challengers, Forensic Auditor, Victory Auditor), `@shared-mentor-agent`
**Framework Fit & Tooling:**
- **Fit:** Strong — The teamwork preview multi-agent system combined with strict milestone gates, adversarial challengers, and independent forensic auditors ensured a production-grade, zero-mock native Swift 6/SwiftUI desktop application built from scratch while fully preserving the legacy Python app.
- **Agents:** Orchestrator structured 5 clean milestones; specialized implementers delivered headless core (`PhotoSelectorKit`) and UI (`PhotoSelectorApp`); adversarial challengers caught subtle edge cases (data loss on copy undo, comparison slot resurrection, Accelerate vImage stride padding). Scope boundaries held cleanly under `products/macos-desktop/`.
- **Skills:** `task-lifecycle`, `retrospective`, `verify-build`, `sync-requirements`, and `record-lesson` guided end-to-end verification and documentation parity.
- **Hooks:** Git status, scope guards, and test mirrors operated transparently.
**Session Efficiency:** High
- **Observations:** Multi-agent self-healing protocols successfully recovered from subagent stalls and API rate-limit pauses without losing state. 192 unit tests across 22 suites execute in 0.55s.
**Implementation Quality:** High (Remediated via Adversarial Audit)
- **Observations:** Challenger agents and forensic auditors prevented subtle race conditions and data loss defects prior to milestone sign-off. Final victory audit confirmed zero stubs/facades and 100% genuine Apple framework integration.
**Framework Root Cause:** The requirement for adversarial review and independent forensic auditing was essential in surfacing multi-threaded filesystem race conditions that unit tests alone would have missed.
**Actionable Framework Improvement:** Ensure external media (SD card) I/O models always mandate transactional barriers in multi-threaded file operation architectures.

## 2026-10-06 - PhotoTok Folder Reload Unscanned Photos & Jump Prompt
**Agents Involved:** `@phototok-core-agent`, `@phototok-ui-agent`, `@shared-mentor-agent`
**Framework Fit & Tooling:**
- **Fit:** Strong — The established patterns (pure domain logic in `com.phototok.domain`, URI-keyed DataStore preferences with LRU cleanup in `SettingsRepository`, and modal `AlertDialog` for multi-action prompts) allowed direct, high-confidence implementation without architectural guesswork.
- **Agents:** `@phototok-core-agent` (domain logic in `FolderScanLogic.kt` and `SettingsRepository.kt`), `@phototok-ui-agent` (ViewModel orchestration, `PhoneModeScreen.kt` dialog, unit and compose tests), and `@shared-mentor-agent` (reflection and candidate memory review). Scope boundaries held cleanly.
- **Skills:** `task-lifecycle`, `sync-requirements`, `verify-build`, and `retrospective` structured the workflow, requirements documentation, and local verification.
- **Hooks:** Scope guards and commit guards operated transparently with zero false positives.
**Session Efficiency:** High
- **Observations:** Fast localization because domain models, viewmodel patterns, and UI guidance were clearly structured. Following previously established lessons (2026-10-06 modal dialogs and 2026-07-24 identity pinning) prevented UI occlusion regressions and position displacement defects before they could occur.
**Implementation Quality:** High
- **Observations:** Clean first-pass test implementation: domain unit tests (`FolderScanLogicTest`), repository tests (`SettingsRepositoryTest`), ViewModel tests (`PhoneModeViewModelTest`), and Compose tests (`PhoneModeScreenDialogTest`) all passed on the first run. Local CI mirror passed completely.
**Framework Root Cause:** No framework friction encountered. The existence of clear domain boundaries and recent UI lessons in `palette.md` gave immediate patterns to follow.
**Actionable Framework Improvement:** Reject candidate memories that merely re-state existing memory or task-specific requirements to maintain high signal-to-noise ratio in `ai/memory/`.

## 2026-10-06 - PhotoTok Filter Mismatch Guidance Hint & One-Tap Remediation
**Agents Involved:** `@phototok-core-agent`, `@phototok-ui-agent`, `@shared-mentor-agent`
**Framework Fit & Tooling:**
- **Fit:** Strong — Pure domain separation in `com.phototok.domain` allowed fast, comprehensive unit testing of the stem-grouping filter mismatch heuristic without Android runtime or Compose dependencies.
- **Agents:** `@phototok-core-agent` (domain logic in `RelatedFiles.kt` and `FirstRunHints.kt`), `@phototok-ui-agent` (ViewModel orchestration, `FirstRunHintCard` action button, and Compose tests), and `@shared-mentor-agent` (adjudicating the stem-grouping lesson).
- **Skills:** `task-lifecycle`, `sync-requirements`, `verify-build`, and `retrospective` structured the implementation, documentation sync, and verification.
- **Hooks:** Git status and scope guards operated cleanly with zero friction.
**Session Efficiency:** High
- **Observations:** Clean separation of concerns made adding the hint heuristic, ViewModel trigger, and UI action straightforward. Existing test conventions in `RelatedFilesTest` and `PhoneModeViewModelTest` provided direct patterns to follow.
**Implementation Quality:** High
- **Observations:** 100% test pass on first run for new domain tests, ViewModel tests, and Compose dialog test. The CI mirror passed with all gates green (visual regression test skipped as expected on macOS).
**Framework Root Cause:** No framework friction encountered.
**Actionable Framework Improvement:** Continue leveraging pure domain models in `com.phototok.domain` for heuristic calculations before wiring into ViewModels.

## 2026-10-06 - PhotoTok RAW+JPEG Prompt Occlusion Fix and Choice Clarification
**Agents Involved:** `@phototok-ui-agent`, `@shared-mentor-agent`
**Framework Fit & Tooling:**
- **Fit:** Strong — Identifying the owning product first (`PhotoTok`), reading `REQUIREMENTS.md` and `palette.md` upfront, and checking the root cause of the bottom bar collision led to an immediate, clean fix without guessing.
- **Agents:** `@phototok-ui-agent` owned `ui/phonemode/` composables and viewmodel interactions. `@shared-mentor-agent` gate approved the lesson on modal dialogs for multi-action prompts.
- **Skills:** `task-lifecycle`, `retrospective`, and `verify-build` guided the pre-work reads, verification through the CI mirror, and requirements syncing.
- **Hooks:** Scope guards and commit guards remained quiet and verified scope adherence.
**Session Efficiency:** High
- **Observations:** Locating the relevant components took a single targeted search. Running the CI mirror caught a missing test import (`onFirst`) locally before pushing, proving the value of running `run_tests.sh` locally.
**Implementation Quality:** High
- **Observations:** Replaced the fragile in-layout bottom card with a centered, scrim-dimmed `Dialog` (`RawJpegSuggestionDialog`), added clear descriptive copy for all options, added the requested explicit "Don't change settings" option, and added unit and instrumented tests covering portrait, landscape, and co-existence with bottom navigation bars.
**Framework Root Cause:** The original code had copied a floating card pattern from non-interactive toasts (`FirstRunHintCard`) instead of using modal dialogs for complex decision prompts.
**Actionable Framework Improvement:** Recorded lesson in `ai/memory/palette.md` advising agents to use `Dialog`/`AlertDialog` for multi-choice workflow prompts, always provide an explicit neutral action ("Don't change settings"), and assert non-occlusion against persistent navigation bars in tests.

## 2026-10-06 - Test Runner Optimization and Headless macOS GUI Test Backgrounding
**Agents Involved:** `@desktop-build-agent`, `@desktop-test-agent`, `@shared-mentor-agent`
**Framework Fit & Tooling:**
- **Fit:** Strong — The CI-parity philosophy in `docs/build/CI_PARITY.md` and `scripts/run_tests.sh` provided a clear contract. By aligning local test runner change detection with CI's path filters, we preserved parity while eliminating unnecessary test execution.
- **Agents:** `@desktop-build-agent` (CI/tooling ownership) and `@desktop-test-agent` (pytest suite and conftest ownership) neatly divided the responsibilities.
- **Skills:** `verify-build` and `retrospective` guided test verification and documentation sync.
- **Hooks:** PreToolUse and Stop hooks ran smoothly without false positives.
**Session Efficiency:** High
- **Observations:** Root-causing Tk focus stealing on macOS was rapid via ctypes probing; verifying that AppKit swizzling kept all 483 tests green without opening a single window was confirmed immediately.
**Implementation Quality:** High
- **Observations:** No speculative commits; all 483 desktop unit tests, Android JVM unit tests, and framework validation gates passed on the first run after fixing one flake8 unused variable.
**Framework Root Cause:** The original test instructions unconditionally mandated `./scripts/run_tests.sh` without path filtering, which ran all products even when working on unrelated subsystems. Concurrently, macOS Aqua Tkinter lacked background isolation.
**Actionable Framework Improvement:** Path-based change detection is now active in `scripts/run_tests.sh`, and macOS GUI focus suppression is established in `products/desktop/tests/conftest.py`.

## 2026-10-06 - Initial Framework Retrospective Integration
**Agents Involved:** `@shared-code-health-agent`, `@shared-mentor-agent`
**Framework Fit & Tooling:**
- **Fit:** Strong — The agent framework's centralized structure (`ai/`) and hook validation made extending the retrospective straightforward and mechanically testable.
- **Agents:** `@shared-code-health-agent` (framework maintenance and retrospective quality) and `@shared-mentor-agent` (memory gatekeeping) are the exact owners for this meta-evaluation.
- **Skills:** `retrospective`, `task-lifecycle`, `record-lesson`, and `sync-framework` were referenced and updated; `validate_framework.py` immediately verified path and roster integrity.
- **Hooks:** `guard_paths.py` and `check_retrospective.py` properly allow `ai/memory/framework_retro.md` as valid memory without false-positive scratch blocks.
**Session Efficiency:** High
- **Observations:** Fast localization because all framework rules, skills, agents, and hooks reside in `ai/` and are indexed in `ai/README.md`.
**Implementation Quality:** High
- **Observations:** Evaluated existing gap thoroughly (why process reflections were historically discarded as "task diaries" under `record-lesson`) before drafting the integration schema. Verified against the CI mirror (`scripts/run_tests.sh`).
**Framework Root Cause:** Previously, all memory files were strictly product/domain-specific (`bolt`, `palette`, `sentinel`, `code_health`), creating an impedance mismatch where agents had no destination to log framework friction or session quality issues.
**Actionable Framework Improvement:** Establish `ai/memory/framework_retro.md` as the permanent home for AI session efficiency and framework fit evaluations, audited during each task retrospective.
