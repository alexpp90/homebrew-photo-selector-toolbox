# Agent Instructions

Welcome to the Photo Selector Toolbox project. When modifying this codebase, you must adhere to the following rules:

1. **Keep Requirements Updated:** You must read the `REQUIREMENTS.md` file in the root directory before starting work on new features, modifying existing features, or changing project architecture/behavior.
2. **Update After Changes:** If your task introduces new requirements, alters existing rules (e.g., UI layout, threading models, duplicate logic, dependency lists), or deprecates features, you MUST update `REQUIREMENTS.md` to reflect these changes before finalizing your work.
3. **Consistency:** Ensure that any code changes you make perfectly align with the rules specified in `REQUIREMENTS.md` unless the user explicitly instructs you to change those rules.
4. **Testing Context:** Review the testing and headless execution requirements in `REQUIREMENTS.md` (e.g., using `xvfb-run`) when running tests or debugging GUI components.
5. **Write Proper Tests:** For every new feature, bug fix, or logic modification, you MUST write or update corresponding automated tests (unit tests or integration tests) to ensure that the code behaves exactly as expected. Tests must be executed and verify correctness before completing the task.
6. **No Scratch Files in the Repo:** Never commit temporary or working artifacts (e.g., `scratch*.py`, `pr_desc.txt`, lint/analysis report dumps, debug scripts). Keep them outside the repository or rely on `.gitignore`. Benchmarks belong in `benchmarks/`, never in the repository root.
7. **Single Source of Truth for Agent Scopes:** The per-agent config files in `.gemini/agents/*.md` are canonical for each agent's detailed scope and instructions. The tables below are a routing summary only — when an agent's scope changes, update the config file first, then this summary and `.gemini/settings.json` in the same change.

## Three Independent Solutions

This project targets **three independent solutions** that share the same repository:

| Solution | Code Directory / Module | Tech Stack | Primary Target |
|----------|--------------------------|------------|----------------|
| **Desktop** | `src/` (Python) | Python + Tkinter | Desktop workstations (macOS, Linux, Windows) |
| **Android Desktop** | `android/app/` (Kotlin) | Jetpack Compose + Room + OpenCV + Vico | Samsung DeX, large tablets (≥840dp), Chromebooks |
| **Android Phone** | `android/phototok/` (Kotlin) | Jetpack Compose + DataStore (lightweight) | Mobile phone form factor (<600dp), portrait only |

The solutions share high-level photographic domain concepts (like EXIF contracts and image score descriptions) but have **independent implementations** optimized for their respective environments and UX models. 

### Feature Sync & Coordination Policy

When a new feature is requested, the coordinator must evaluate which of the three solutions is the target (single target, multiple targets, or all three). 
- **Single Target**: Delegate exclusively to the subagents responsible for that specific solution. Do not modify or inject code into the other solutions.
- **Multiple/All Targets**: Split the task into distinct, independent subtasks, one for each targeted solution. Tailor the implementation to each solution's technology stack and UX guidelines. Do not force identical code or structures when they do not make sense for the target platform (e.g., Android Phone has a gesture-first interface while Desktop has a menu-driven interface).
- **Feature Feasibility & Exclusions**:
  - **Local AI (Ollama VLM)**: Desktop-only due to mobile computing constraints.
  - **OpenCV Analysis & Room DB Cache & Vico Charts**: Supported on Desktop and Android Desktop, but excluded from Android Phone to keep the application lightweight.
  - **Picture Shuffling/Randomization**: Android Phone only (built into the Settings of the phone client).

## Multi-Agent System

This project uses a multi-agent system with **10 specialized subagents** organized into platform groups plus shared consultants. The coordinator (default agent) automatically delegates work to the appropriate subagent based on the task and target solution.

### Agent Roster

#### Desktop Agents (Python/Tkinter)

| Agent | Scope | Config File |
|-------|-------|-------------|
| **`@backend_agent`** | Core Python logic: `reader.py`, `readers/`, `analyzer.py`, `sharpness.py`, `duplicates.py`, `utils.py`, `formatting.py`, `models.py`, `cli.py`, `visualizer.py`, `tools.py`, `ollama_tool.py`, `cache.py`, `config.py` | `.gemini/agents/backend_agent.md` |
| **`@gui_agent`** | Tkinter GUI: `gui.py`, `sharpness_gui.py`, `controllers.py`, `image_panels.py`, `fullscreen_viewer.py`, `gui_utils.py` | `.gemini/agents/gui_agent.md` |
| **`@test_agent`** | Desktop testing: `tests/` (incl. `tests/visual/`), `benchmarks/` | `.gemini/agents/test_agent.md` |
| **`@build_agent`** | Desktop build & CI: `scripts/`, desktop GitHub workflows (`build.yml`, `test-python.yml`, `requirements-check.yml`), `Formula/`, `Casks/`, `pyproject.toml` | `.gemini/agents/build_agent.md` |

#### Android Agents (Kotlin/Compose)

| Agent | Scope | Config File |
|-------|-------|-------------|
| **`@android_ui_agent`** | Jetpack Compose UI: All files under `android/app/src/main/.../ui/` (Android Desktop) and `android/phototok/src/main/java/com/phototok/ui/` (Android Phone) | `.gemini/agents/android_ui_agent.md` |
| **`@android_core_agent`** | Android data & domain layers: `android/app/src/main/.../data/`, `android/app/src/main/.../domain/` (Android Desktop) and `android/phototok/src/main/java/com/phototok/data/`, `android/phototok/src/main/java/com/phototok/viewmodel/` (Android Phone) | `.gemini/agents/android_core_agent.md` |
| **`@android_build_agent`** | Android build: `android/build.gradle.kts`, `android/app/build.gradle.kts`, `android/phototok/build.gradle.kts`, `android/settings.gradle.kts`, `android/gradle/`, GitHub Actions Android workflows, ProGuard/R8 | `.gemini/agents/android_build_agent.md` |

#### Shared Consultant Agents (Cross-Platform)

| Agent | Scope | Config File |
|-------|-------|-------------|
| **`@photo_researcher_agent`** | Photographic science, image quality metrics, aesthetics, and requirement elucidation. Consulted by both desktop and Android agents. | `.gemini/agents/photo_researcher_agent.md` |
| **`@ux_agent`** | Professional design, UX flows, ergonomics, and pattern analysis. Provides solution-specific design guidance (Desktop mouse/keyboard vs. Android Desktop tablet/DeX multi-pane vs. Android Phone portrait/touch-first). | `.gemini/agents/ux_agent.md` |
| **`@publish_agent`** | Google Play publishing & compliance: `docs/phototok/` (release checklist = single source of truth for open release tasks, privacy policy, Impressum), `LegalLinks.kt`, OAuth scope policy (`drive.file` only), DSA non-trader status, Data Safety answers, target-API deadline watch. | `.gemini/agents/publish_agent.md` |

### Coordinator Behavior

The coordinator agent (default) handles:

- **Target Solution Detection**: Evaluates which file paths, modules, or description clauses are affected. Files under `src/` or `tests/` belong to Desktop; files under `android/app/` belong to Android Desktop; files under `android/phototok/` belong to Android Phone.
- **Auto-delegation**: Automatically routes work to the appropriate subagents. When a task spans multiple solutions, the coordinator breaks it down into subtasks and delegates each portion.
- **Consultation on Vagueness**: Consults `@photo_researcher_agent` for photographic science, and `@ux_agent` for visual styling and interaction flows (always specifying which of the 3 target solutions is being worked on).
- **Requirements Maintenance**: Reviews and updates `REQUIREMENTS.md` after any change that modifies behavior, ensuring it stays consistent.
- **Cross-agent Alignment**: Coordinates between backend/core algorithms and GUI/UI view updates.

### Delegation Rules

#### Desktop
- Changes to **core algorithms, data models, CLI, or visualizations** → `@backend_agent`
- Changes to **GUI layout, Tkinter widgets, threading, or controllers** → `@gui_agent`
- Changes to **desktop tests, coverage, or test infrastructure** → `@test_agent`
- Changes to **desktop build scripts, CI/CD, dependencies, or packaging** → `@build_agent`

#### Android Desktop
- Changes to **Compose UI, navigation rail, widescreen layouts, tablet components** → `@android_ui_agent` (Scope: `android/app/.../ui`)
- Changes to **Room DB caching, OpenCV image analysis, WorkManager, usecases, domain** → `@android_core_agent` (Scope: `android/app/.../data`, `android/app/.../domain`)

#### Android Phone
- Changes to **compact Compose UI, swipe navigation, gesture tutorial, PhoneModeScreen** → `@android_ui_agent` (Scope: `android/phototok/.../ui`)
- Changes to **DataStore preferences, phone viewmodels, lightweight EXIF strategy** → `@android_core_agent` (Scope: `android/phototok/.../data`, `android/phototok/.../viewmodel`)

#### Cross-Platform & Build
- Changes to **Android Gradle build files, CI workflow, or ProGuard rules** → `@android_build_agent`
- Research on **algorithms, raw files, or metadata standards** → `@photo_researcher_agent`
- Custom **UX mockups, ergonomic analysis, or layout wireframes** → `@ux_agent` (requires target solution context)
- Changes touching **release/compliance artifacts** (`docs/phototok/`, `LegalLinks.kt`, OAuth scopes, permissions, new network endpoints or SDKs, store metadata) → `@publish_agent` (also consulted as a reviewer whenever a change alters what data the app accesses or transmits)

### Learned Lessons (`.Jules/`)

The `.Jules/` directory is the project's persistent memory of hard-won lessons. Read the relevant file before working in a related area, and append a new entry (same format: date, Learning/Vulnerability, Action/Prevention) when you learn something non-obvious that future agents should know:

- `.Jules/bolt.md` — Performance (e.g., avoiding `pathlib`/`lru_cache` overhead in hot file loops, single-pass metadata extraction).
- `.Jules/palette.md` — UI & accessibility (e.g., Tkinter 'clam' theme focus-state pitfalls, keyboard shortcut discoverability).
- `.Jules/sentinel.md` — Security (e.g., zip/tar slip prevention on `extractall`, SMB URL path traversal, `posixpath.normpath` for URLs).

### Shared Skills

All agents have access to the **refactoring guide** skill at `.gemini/skills/refactoring_guide/SKILL.md`. This documents the project's established patterns:
- Centralized constants
- Controller/View separation
- Thread pool sizing
- Image loading safety (RGB conversion)
- EXIF data contract
- Error handling conventions

Android agents should also read `ANDROID_DESIGN.md` for Android-specific architectural decisions, adaptive layout strategies, and platform constraints.

Agents should read the relevant skill before performing refactoring work.
