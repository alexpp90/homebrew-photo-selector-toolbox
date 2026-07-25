# Code Health Backlog & Lessons

Refactoring candidates and structural lessons. Owned by `@code_health_agent`; any agent may append. Format for backlog items:

```markdown
## [OPEN|DONE] YYYY-MM-DD - Short title
**Where:** file paths / modules
**Debt:** what is wrong and why it matters
**Proposal:** the safe refactoring, referencing `.skills/refactoring_guide/SKILL.md` patterns
```

Lessons use the standard `.Jules/` format (Learning/Action).

---

## 2026-07-12 - GitHub Actions Workflow Rename Run Number Reset
**Learning:** Renaming or changing a GitHub Actions workflow filename resets the `github.run_number` count back to 1 for that new file. If `github.run_number` is used in version code calculations, the reset causes version code regressions. This will result in Google Play Store rollout errors (such as "does not allow any existing users to upgrade").
**Action:** When introducing or renaming a CI workflow that determines version codes, always add a baseline offset to ensure version codes remain strictly higher than any previously published builds.

## 2026-07-11 - Gradle Kotlin DSL 'java' Collision & GitHub Workflow Resilience
**Learning:** 
1. In Gradle Kotlin DSL, `java` is a reserved keyword referring to the Gradle Java plugin/extension. Referencing fully qualified names starting with `java.` (e.g. `java.util.Properties`) inside build files yields compiler errors like `Unresolved reference 'util'`.
2. When referencing repository configuration fields in GitHub Actions workflows, developers might store configuration values under either Secrets or Variables. Referencing them strictly via one scope can result in blank values if stored in the other.
**Action:** 
1. Place standard Java package imports at the very top of `.gradle.kts` files (e.g., `import java.util.Properties`) and instantiate them using their simple name (`Properties()`).
2. Implement workflow configuration fallbacks using the expression `${{ secrets.KEY || vars.KEY }}` to ensure the build remains resilient regardless of where the keys are stored.

## 2026-07-11 - Antigravity Workspace Customizations Root
**Learning:** Antigravity's customizations model discovers workspace rules and skills from the `.agents/` directory (when configured as the Workspace Customizations Root). It expects skills to be placed in `skills/<skill_name>/` relative to that root. Without a `.agents/skills` directory or symlink, workspace skills are ignored by Antigravity.
**Action:** Create and maintain a symlink `.agents/skills -> ../.skills` to ensure workspace skills are automatically discovered and loaded by the Antigravity IDE.

## 2026-07-23 - Group-Limited Navigation via Contiguous Candidate Blocks
**Learning:** In the desktop Sharpness tool, an *expanded* similarity group always occupies a contiguous block in `self.candidates` starting at its representative (see `apply_grouping_and_refresh`). This means "confine prev/next to the current group" needs no parallel data structure — it is just clamping the candidate index to `[start, start+len(files)-1]`. Deriving bounds from the existing candidate order (rather than iterating `group.files` in a possibly different sort order) keeps navigation consistent with the visible listbox and avoids an index-mapping bug where the representative is not first in `group.files`.
**Action:** When adding constrained navigation over an already-materialised ordered list, prefer clamping indices within a computed contiguous sub-range over introducing a second ordering. `_current_group_bounds()` centralises this so button-state, triplet loading, and fullscreen all share one definition.

## 2026-07-23 - Multi-Engine Aesthetic Scoring via a Dispatcher Tool
**Learning:** Replacing the single Ollama-backed `aesthetic` tool with several engines (Apple Vision, NIMA ONNX, Ollama) is cleanest as ONE registered dispatcher tool (`AestheticTool`) that reads an `aesthetic_engine` config key and delegates, rather than multiple classes all claiming the `aesthetic` registry key (last-registration-wins is fragile and import-order dependent). Engine availability probes and score-mapping are pure functions with injectable flags so they unit-test without native deps (PyObjC/onnxruntime). `auto` mode degrades to Ollama so existing installs keep working until a lighter engine is provisioned. On Android the mirror pattern is graceful degradation: `AestheticAnalyzer` returns null when no `.tflite` asset is bundled, so the feature is inert (no chip shown) until a model ships.
**Action:** For pluggable backends behind one logical capability, register a single dispatcher keyed by config and keep the decision logic pure/injectable. Guard native/model paths behind availability checks and prefer null/inert fallbacks over hard failures so the app builds and runs without the optional heavy assets.

## 2026-07-23 - Multi-Module Selective CI Deployment via Path Filtering
**Learning:** In a repository containing multiple independent deployable applications sharing a CI pipeline (such as `.github/workflows/android.yml` for `:app` and `:phototok`), triggering release builds and Play Store / Firebase uploads unconditionally on `main` pushes re-packages and re-publishes unchanged applications. Using `dorny/paths-filter@v3` in an early `detect-changes` job allows downstream build and deployment steps to run selectively per module while preserving manual dispatch and release tag behavior.
**Action:** When a workflow manages multiple deployable modules, detect changes early with `dorny/paths-filter@v3` and guard downstream artifact creation and publishing steps with module outputs (`if: needs.detect-changes.outputs.<module> == 'true' || github.event_name == 'workflow_dispatch' || startsWith(github.ref, 'refs/tags/v')`).

## [OPEN] 2026-07-24 - Synchronous file I/O on the Tk main thread in move/copy to Selection
**Where:** `src/photo_selector_toolbox/sharpness_gui.py` — `execute_move_to_selection`, `execute_copy_to_selection`
**Debt:** `execute_delete` already does its filesystem work in a background thread (per REQUIREMENTS §3.3 "Asynchronous Deletion"), updating the UI immediately so deletion feels instant. Move and Copy still call `f.rename(dest)` / `shutil.copy2` synchronously on the Tk main thread, for the image plus every related RAW/JPEG/XMP sibling. On a network share or a slow external drive this blocks the event loop for the whole transfer — the UI stops redrawing, keystrokes queue up, and on macOS it is a plausible contributor to the fullscreen viewer losing keyboard focus (see `.Jules/palette.md` 2026-07-24). The two functions are also near-identical: ~90 lines duplicated between move and copy, differing only in `f.rename` vs `shutil.copy2`.
**Proposal:** Extract the shared destination-resolution and RAW/JPEG/XMP subfolder-sorting logic into one helper taking a transfer callable (see `.skills/refactoring_guide/SKILL.md` "centralized constants" and "controller/view separation"), then move the transfer loop onto a background thread mirroring `execute_delete`'s pattern: update `candidates`/listbox on the main thread first, do I/O off-thread, report failures back via `parent.after(0, ...)`. Needs care around the pending-delete/undo semantics and the fullscreen viewer's list pruning.

## [OPEN] 2026-07-24 - Desktop GUI test suite cannot run without a real tkinter
**Where:** `tests/test_sharpness_gui_basic.py`, `tests/test_fullscreen_features.py`, `tests/test_gui.py`, `tests/conftest.py`
**Debt:** The GUI tests replace `tk.Toplevel`/`ttk.*` with dummies but still `import tkinter` at module scope, so they cannot be collected at all on a Python build without the Tk bindings (common in slim CI images and sandboxes). Additionally `tests/test_sharpness_gui_basic.py` only passes when run as part of the full suite: its module-scoped fixture installs a `MagicMock` into `sys.modules["photo_selector_toolbox.controllers"]` before the real submodule is ever imported, so `patch("photo_selector_toolbox.controllers.X")` fails with `AttributeError: module 'photo_selector_toolbox' has no attribute 'controllers'` unless an earlier test file happened to import it first. Running that file alone yields 18 collection errors.
**Proposal:** Make the mock fixture set the attribute on the parent package as well as `sys.modules` (or import the real module first and patch attributes on it), so each test file is independently runnable. Consider a shared `tests/_tk_stub.py` installed from `conftest.py` when `tkinter` is unavailable, so the pure-logic GUI tests stay runnable in minimal environments.
