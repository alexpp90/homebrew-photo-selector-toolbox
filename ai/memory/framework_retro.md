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

## 2026-10-13 - Defensive Watchdog Timer Protocol for Subagent Delegation
**Agents Involved:** `@DeepInvestigator`, `@DeepCoder`, `@shared-mentor-agent`
**Framework Fit & Tooling:**
- **Fit:** Adequate — While subagents performed domain analysis and code implementation effectively, relying purely on passive reactive wakeup caused a complete session stall when a nested worker process blocked.
- **Agents:** `@DeepInvestigator` formulated the color science, 3-blade aperture icon, and two-tier naming architecture; `@DeepCoder` coordinated implementation via a nested worker (`DeepCoderWorkerL0`); `@shared-mentor-agent` audited test parity.
- **Skills:** `task-lifecycle`, `retrospective`, `verify-build`.
- **Hooks:** Git status and pre-delivery gate ensured tests were executed before completion.
**Session Efficiency:** Low / Friction
- **Observations:** Execution stalled completely after `@DeepCoder` delegated to its nested worker (`DeepCoderWorkerL0`). The worker successfully implemented all Android assets, drawables, and themes, and verified `./scripts/run_tests.sh --android`. However, during a subsequent run of the full CI mirror, the macOS UI smoke test (`ui_smoke_test.sh`) was killed by the OS (exit code 137 / SIGKILL 9), and the worker's attempt to run it directly as background task `task-321` blocked without completion or notification. Because the worker never sent a message to the coordinator, and the coordinator never sent a message to the orchestrator, the orchestrator remained dormant waiting for a reactive notification that never fired.
**Implementation Quality:** High
- **Observations:** The actual Android implementation was robust on the first pass: all 102 Gradle tasks passed, adaptive vector drawables compiled cleanly, `Color.kt` and `Theme.kt` applied calibrated darkroom tokens, and all 734 Android unit and instrumented tests passed without regressions.
**Framework Root Cause:** The orchestrator instructions emphasize passive reactive wakeup ("Do NOT poll or check your inbox in a loop. The system will automatically notify you when the subagent sends a message."). This caused the coordinating agent to enter unconditional dormancy without setting a defensive timeout or liveness probe. When a failure occurred two levels down in the subagent hierarchy, the whole pipeline stalled silently.
**Actionable Framework Improvement:**
1. Mandate the **Defensive Watchdog Timer Protocol** whenever delegating to subagents: whenever `invoke_subagent` is called, simultaneously schedule a one-shot watchdog timer using `schedule(DurationSeconds=300..600, Prompt="Check subagent status and progress", TimerCondition="any")`. If subagents respond normally, the timer cancels early; if a subagent hangs or crashes silently, the timer wakes the agent to inspect `manage_subagents(Action='list')` and unblock progress.
2. In multi-product repositories, subagents assigned to one product (e.g. `:android-desktop`) must scope their local verification gates (`./scripts/run_tests.sh --android`) rather than running GUI smoke tests for other platforms (`ui_smoke_test.sh`) that require active windowing sessions.

## 2026-10-12 - Strict Zero-Human Policy for Sample Photos & Promotional Assets
**Agents Involved:** `@android-phototok-agent`, `@macos-desktop-agent`, `@shared-mentor-agent`
**Framework Fit & Tooling:**
- **Fit:** Strong — High-precision multimodal inspection (`view_file`) and prompt-based asset generation (`generate_image`) allowed immediate identification and replacement of non-compliant imagery.
- **Agents:** `@android-phototok-agent` owned `products/android/phototok/` sample assets and screenshot re-rendering; `@macos-desktop-agent` verified native test fixtures and macOS screenshot capture; `@shared-mentor-agent` enforced memory gating.
- **Skills:** `task-lifecycle`, `retrospective`, `verify-build`.
- **Hooks:** Git status and CI test runner gates verified complete system integrity across all products.
**Session Efficiency:** High
- **Observations:** Audited all image assets across the entire repository. Discovered that while `placeholder_landscape.jpg` and `placeholder_portrait.jpg` were human-free, `placeholder_architecture.jpg` contained a pedestrian walking along the colonnade in the background, which leaked into `phototok_01_landing.png` and `macos_desktop_screenshot.png` filmstrip thumbnails. Generated an uninhabited brutalist concrete architecture replacement using `generate_image`, validated it with `view_file`, updated test fixtures, and re-rendered the dependent assets. Concurrently resolved a main-thread deadlock in `UISelfTestRunner.swift` where `hitView.mouseDown:` blocked on AppKit's modal tracking loop.
**Implementation Quality:** High
- **Observations:** 100% human-free verification achieved across all product sample assets and documentation screenshots. Local CI mirror `./scripts/run_tests.sh` passed 15/16 gates with only the expected Linux-only Xvfb visual tests skipped.
**Framework Root Cause:** Previous asset creation did not conduct a full high-resolution background scan of architectural imagery, missing a distant pedestrian in the corridor.
**Actionable Framework Improvement:**
- Ensure all sample photo generators explicitly mandate uninhabited scenes (empty landscape, architecture without pedestrians, or equipment macros) and verify full frame with `view_file`.

## 2026-10-12 - Ecosystem README Parity, Canonical Ordering & Automated CI Enforcement
**Agents Involved:** `@shared-code-health-agent`, `@shared-mentor-agent`
**Framework Fit & Tooling:**
- **Fit:** Strong — Cross-product documentation, asset generation, and mechanical gate enforcement aligned repository reality with public presentation.
- **Agents:** `@shared-code-health-agent` orchestrated root README rewriting and CI gate integration; `@shared-mentor-agent` gated lessons and framework improvements.
- **Skills:** `task-lifecycle`, `sync-requirements`, `verify-build`, `retrospective`.
- **Hooks:** PreToolUse hook `guard_commit.py` protected against invalid mirror paths; `run_tests.sh` successfully reproduced all CI gates locally.
**Session Efficiency:** High
- **Observations:** Audited root `README.md` and resolved all outdated elements: added missing Linux Desktop product details and APT installation; restructured product presentation to canonical order (1. PhotoTok, 2. Android Desktop, 3. macOS Desktop, 4. Linux Desktop, 5. Legacy Desktop); updated Android Desktop to authentic 1-over-2 layout and pruned deprecated Google Drive references; produced and verified realistic, high-resolution screenshots for all active platforms.
**Implementation Quality:** High
- **Observations:** Root-caused why `README.md` drifted: requirements sync skills only audited `REQUIREMENTS.md` and no mechanical gate existed. Created `scripts/check_readme_parity.py` and registered `gate "readme parity"` in `./scripts/run_tests.sh` to permanently prevent future drift. Full CI mirror passed cleanly across all platforms.
**Framework Root Cause:** `sync-requirements` and `retrospective` skills previously treated `docs/products/*/REQUIREMENTS.md` as the sole documentation artifact, leaving `README.md` unmonitored during major feature additions (Linux Desktop, macOS Desktop, tablet 1-over-2 redesign).
**Actionable Framework Improvement:**
1. Updated `ai/skills/sync-requirements/SKILL.md` to mandate root `README.md` synchronization in the same commit whenever observable features, layouts, or product catalogs change.
2. Updated `ai/skills/retrospective/SKILL.md` checklist item 2 to verify `README.md` parity.
3. Added `scripts/check_readme_parity.py` to `./scripts/run_tests.sh` as an automated gate.

---

## 2026-10-12 - Android Desktop Asymmetric System Bar Policy (Preserve Status Bar, Hide Navigation Bar)
**Agents Involved:** `@android-desktop-ui-agent`, `@shared-mentor-agent`
**Framework Fit & Tooling:**
- **Fit:** Strong — The established product scoping and `FrameGeometry` solver isolated the layout arithmetic cleanly.
- **Agents:** `@android-desktop-ui-agent` owned the implementation in `products/android/android-desktop/`; `@shared-mentor-agent` gated memory.
- **Skills:** `task-lifecycle`, `retrospective`, `verify-build`, `sync-requirements`.
- **Hooks:** Scope guards and `./scripts/run_tests.sh` mirror verified changes without friction.
**Session Efficiency:** High
- **Observations:** Root-causing the bug was straightforward: `enableEdgeToEdge()` was paired with horizontal-only insets padding in `SelectorScreen.kt` and `AdaptiveNavigation.kt`, which intentionally drew behind system bars. Implementing `WindowInsetsControllerCompat.hide(navigationBars())` and padding for `safeDrawing.only(Horizontal + Top)` cleanly resolved the issue. Caught an unresolved `dp` reference in the test file before finalizing.
**Implementation Quality:** High
- **Observations:** Verified all 734 Android unit and instrumented tests, lint, traceability audit, and readme parity. Full CI mirror `./scripts/run_tests.sh --android` passed all gates.
**Framework Root Cause:** The previous requirement (REQ-AND-LAYOUT.26) intentionally drew through system bars under an assumption about display ceilings. Re-anchoring to user requirements resolved the user-facing occlusion.
**Actionable Framework Improvement:** When designing immersive or edge-to-edge layouts on tablets, distinguish between top status bars (which users often want visible without app collision) and bottom navigation bars (which can be hidden to recover scarce vertical space).

## 2026-10-12 - Android Desktop Learning Workstation Evolution (Star Ratings, Metadata Interop, Overlays, Photo Coach, Inspect Screen)
**Agents Involved:** `@android-desktop-agent`, `@shared-mentor-agent`
**Framework Fit & Tooling:**
- **Fit:** Strong — The teamwork preview multi-agent system successfully coordinated end-to-end execution across 6 milestones: core metadata, persistence migrations, computer vision, exposure heuristic engine, and Compose UI screens.
- **Agents:** `@android-desktop-agent` owned the implementation and verification of the Inspect & Learn workstation; `@shared-mentor-agent` gated memory and lifecycle closures.
- **Skills:** `task-lifecycle`, `retrospective`, `verify-build`, `sync-requirements`.
- **Hooks:** Fixed intent delivery hook to correctly recognize planning commands (`/teamwork-preview`, `/plan`) and ignore internal session ledgers (`intent_ledger.jsonl`) during read-only/planning turns.
**Session Efficiency:** High
- **Observations:** Orchestrator 9 drove 6 sequential milestone gates with full adversarial review and independent forensic auditing. Unit and instrumented test suites grew to 734 Android tests passing 100%.
**Implementation Quality:** High
- **Observations:** Clean separation of concerns: 3-up culling layout was preserved without arithmetic regressions (`FrameGeometry.threeUpLayout`), while dedicated Inspect & Learn screen (`Screen.Inspect`) added 1–5 star ratings, non-destructive metadata write-backs, OpenCV vector overlays, and the Photo Coach engine. All 14 active gates in `./scripts/run_tests.sh` passed. Requirements synchronized in `docs/products/android-desktop/REQUIREMENTS.md` Section 9.
**Framework Root Cause:** The intent verification hook previously did not distinguish between planning phases (`/teamwork-preview`) and implementation phases, causing premature blocking on user intent keywords before agents were dispatched.
**Actionable Framework Improvement:** Keep the planning prompt exemption in `verify_intent_delivery.py` so conversational elicitation and prompt drafting operate without spurious delivery gate blocks.

## 2026-10-11 - PhotoTok Screenshot Aesthetic Refinement, Human-Free Imagery & Swift Testing Plugin Fix
**Agents Involved:** `@android-phototok-agent`, `@macos-desktop-agent`, `@shared-mentor-agent`
**Framework Fit & Tooling:**
- **Fit:** Strong — Image inspection via `view_file` immediately revealed visual asset defects before delivery, allowing fast, deterministic resolution.
- **Agents:** `@android-phototok-agent` validated PhotoTok UI layout and launcher asset paths; `@macos-desktop-agent` verified Swift Testing macro resolution.
- **Skills:** `task-lifecycle`, `retrospective`, `verify-build`.
- **Hooks:** Git status and path guards prevented scratch files from leaking into the repository.
**Session Efficiency:** High
- **Observations:** User feedback was precise: the top-left app bar logo erroneously displayed the Desktop suite's equalizer icon, and the photo depicted an AI human portrait. Reworked the standalone screenshot generator to pull the genuine camera aperture icon (`ic_launcher_foreground.png`), display 0 persons by using `placeholder_landscape.jpg` (alpine lake reflection), and removed erroneous app-bar text labels to mirror `PhoneModeScreen.kt`. Furthermore, diagnosed a Swift Testing failure on systems using Apple CommandLineTools where `libTestingMacros.dylib` resides in `plugins/testing/`, adding the `-load-plugin-library` flag to `scripts/run_tests.sh`.
**Implementation Quality:** High
- **Observations:** Programmatic visual inspection (`view_file`) verified all 4 showcase screenshots and `phototok_screenshot.png` have 0 persons and authentic aperture branding. Full CI mirror `./scripts/run_tests.sh` passed 14/15 gates (1 accepted visual regression skip).
**Framework Root Cause:** The previous asset generation step used generic branding (`assets/logo.png`) and AI-generated portraits without cross-checking the exact Compose code (`PhoneModeLanding.kt` and `PhoneModeScreen.kt`) and without considering sensitivity around depicting humans in sample culling imagery.
**Actionable Framework Improvement:** Always cross-reference the actual Compose/SwiftUI UI code when generating promotional mockups to verify icon draws and layout match production code, and default sample photo assets to landscape/nature or equipment photos (`placeholder_landscape.jpg`, `placeholder_portrait.jpg`).

## 2026-10-11 - PhotoTok Launch Readiness, Showcase Overhaul & German Legal Compliance Package
**Agents Involved:** `@android-phototok-agent`, `@shared-publish-agent`, `@shared-mentor-agent`
**Framework Fit & Tooling:**
- **Fit:** Strong — The teamwork preview multi-agent system successfully coordinated parallel investigation across build configurations, showcase assets, regulatory compliance, and open-source hygiene.
- **Agents:** `@shared-publish-agent` owned release compliance and store assets; `@android-phototok-agent` validated behavioral requirements and build scripts.
- **Skills:** `task-lifecycle`, `release-compliance`, `retrospective`, `verify-build`, `sync-requirements`.
- **Hooks:** Pre-delivery intent verification hook properly required CI test execution (`run_tests.sh`) before allowing task completion.
**Session Efficiency:** High
- **Observations:** Release APK and AAB compilation ran synchronously with clean R8 minification in under 60 seconds. Parallel subagents produced comprehensive compliance documentation (`LEGAL_COMPLIANCE.md`, `SECURITY_AUDIT.md`, `RELEASE_CHECKLIST.md`) and four real high-res phone screenshots (`phototok_01_landing.png` through `phototok_04_settings.png`) without blocking the main workflow.
**Implementation Quality:** High
- **Observations:** Verified 100% test pass rate across 203 JVM unit tests and zero-permission Android architecture. Full CI mirror (`./scripts/run_tests.sh`) passed cleanly across all 5 products. Requirements synced with `REQ-TOK-FEATURE.12`.
**Framework Root Cause:** The repository previously lacked an authoritative `RELEASE_CHECKLIST.md` for PhotoTok (flagged in docs), and the root `README.md` contained outdated technical descriptions and inaccurate matrix checkmarks for PhotoTok.
**Actionable Framework Improvement:** Ensure every Android product maintains an active `RELEASE_CHECKLIST.md` and keep feature comparison matrices synchronized during architectural changes.

## 2026-10-10 - Multi-Product v0.5.0 Release Preparation, Legacy Desktop Archival, and CI Parity
**Agents Involved:** `@desktop-build-agent`, `@android-shared-build-agent`, `@macos-desktop-agent`, `@linux-desktop-agent`, `@shared-code-health-agent`, `@shared-mentor-agent`
**Framework Fit & Tooling:**
- **Fit:** Strong — The cross-product CI mirror script (`./scripts/run_tests.sh`) accurately reflected gates across all 5 products, and `guard_paths` successfully prevented committing unintended artifacts.
- **Agents:** Handled repository-wide release version synchronization and legacy desktop preservation.
- **Skills:** `task-lifecycle`, `retrospective`, `verify-build`, `sync-requirements`.
- **Hooks:** PreToolUse hook blocked `bug_report.md` edit due to strict regex match on `_report.md`, preventing unintended edits to issue templates.
**Session Efficiency:** High
- **Observations:** Git log inspection revealed existing local `archive/legacy-desktop` tag and branch, allowing instant preservation to remote `origin`. Full local CI test suite ran synchronously in under 2 minutes with zero failures. When GitHub Actions macOS runner hung on concurrent Swift Testing execution, log analysis identified cooperative thread pool starvation across 28 suites, solved by enforcing `--no-parallel` serialized test execution in both `.github/workflows/macos.yml` and `scripts/run_tests.sh`.
**Implementation Quality:** High
- **Observations:** All gates passed; version code offsets and version names cleanly bumped to 0.5.0 and code 6 across Android, Python Desktop, Formula, and Cask. All 4 GitHub Actions pipelines (Android CI with API 30 emulator, Desktop CI on Ubuntu/macOS/Windows, Linux Desktop CI, and macOS Desktop CI) achieved 100% green status.
**Framework Root Cause:** The Linux Desktop product was lacking a mirrored `.github/workflows/linux.yml` file, and `swift test -c release` defaulted to unbounded parallel testing under Swift 6 on virtualized macOS CI runners.
**Actionable Framework Improvement:** Ensure newly added products always receive their corresponding `.github/workflows/<product>.yml` file in the same commit they are introduced, and configure `--no-parallel` for Swift Testing on resource-constrained virtualized CI runners.

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

---

## 2026-10-12 - Android Desktop Multi-Mode Ergonomics (Galaxy Tab S11 Ultra, Chromebooks, Touch Gestures)
**Agents Involved:** `@android-desktop-agent`, `@shared-mentor-agent`
**Framework Fit & Tooling:**
- **Fit:** Strong — Headless domain separation in `products/android/android-desktop/` and Compose view architecture made targeting the Galaxy Tab S11 Ultra landscape baseline alongside Chromebooks, DeX, and touch-first tablet modes straightforward and test-driven.
- **Agents:** `@android-desktop-agent` owned layout, manifest, gestures, and test suites.
- **Skills:** `task-lifecycle`, `retrospective`, `verify-build`, `sync-requirements` guided the end-to-end implementation and verification.
- **Hooks:** Git status, scope guards, and CI mirror (`./scripts/run_tests.sh --quick`) operated cleanly.
**Session Efficiency:** High
- **Observations:** Auditing `FrameGeometry` early confirmed that the primary Galaxy Tab S11 Ultra landscape baseline (1480 × 924 dp logical resolution, 675 × 450 dp 3:2 frames) was already optimal and height-bound. The actual constraints preventing flexible tablet and Chromebook usage were manifest layout bounds (800x600 dp preventing 50/50 split snapping at 740 dp), camera feature declarations filtering clamshell Chromebooks on Google Play, missing horizontal touch swipe detection on the comparison workspace, and fixed 48 dp toggle row width overflowing in narrow split-screen flanks.
**Implementation Quality:** High
- **Observations:** Clean implementation across `AndroidManifest.xml`, `ThreeUpSelectorLayout.kt`, and `SelectorControlBlock.kt`. Unit tests and instrumented tests passed without failure on the first full test execution.
**Framework Root Cause:** The layout bounds in `AndroidManifest.xml` had been configured for large standalone desktop windows without considering 50/50 split-screen snapping on tablets.
**Actionable Framework Improvement:** When defining desktop window limits in manifests (`<layout>`), always test against 50/50 split-screen dimensions for target reference hardware, not only full-screen maximized windows.

---

## 2026-10-14 - Comprehensive Ecosystem Audit Resolution (AI Framework, Traceability, & Multi-Product Testing)
**Agents Involved:** `@shared-code-health-agent`, `@shared-mentor-agent`, `@desktop-backend-agent`, `@desktop-gui-agent`, `@desktop-test-agent`, `@macos-desktop-agent`, `@linux-desktop-agent`, `@android-desktop-core-agent`, `@phototok-core-agent`, `@android-shared-build-agent`
**Framework Fit & Tooling:**
- **Fit:** Strong — All findings from the comprehensive 3-pillar audit (AI Framework, Requirements & Traceability, Applications & CI/CD Testing) were resolved and verified against the CI mirror.
- **Agents:** Specialized product and framework agents handled disjoint scopes cleanly.
- **Skills:** `task-lifecycle`, `sync-framework`, `sync-requirements`, `verify-build`, and `retrospective` guided the end-to-end remediation.
- **Hooks:** Hardened `check_retrospective.py` (excluding `intent_ledger.jsonl` from `wrote_memory`), `verify_intent_delivery.py` (subagent/read-only exemption and regex feature map), `record_intent.py` (`<USER_REQUEST>` filtering), `session_report.py` (Antigravity transcript parsing), and `synthesize_retro.py` (word-boundary friction clustering).
**Session Efficiency:** High
- **Observations:** Parallel subagent execution resolved findings across all 5 products, `ai/`, `docs/`, and `.github/workflows/`, with CI parity and framework governance checks passing.
**Implementation Quality:** High
- **Observations:** Implemented `ThumbnailCache` & `PrefetchManager` in Linux Desktop, closed SQLite connection leaks and background Tkinter thread exceptions in Desktop, added unit tests for macOS `QualityScoringService` and Android `:core` readers (`MediaStoreReader`, `ExternalStorageDetector`, `AndroidExifReader` CR3), and wired `framework-governance` into `.github/workflows/desktop.yml`.
**Framework Root Cause:** Passive hooks (`record_intent.py`) previously dirtied `ai/memory/intent_ledger.jsonl` on every prompt, which satisfied `f.startswith("ai/memory/")` in `check_retrospective.py`, and `validate_framework.py` skipped paths under deleted top-level roots (`android/`).
**Actionable Framework Improvement:** `check_retrospective.py` now explicitly excludes `ai/memory/intent_ledger.jsonl`, `validate_framework.py` enforces `DEPRECATED_ROOTS` and stale `(planned)` detection, and `desktop.yml` runs the `framework-governance` job in CI.
