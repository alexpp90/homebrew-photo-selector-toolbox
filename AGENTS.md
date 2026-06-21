# Agent Instructions

Welcome to the Photo Selector Toolbox project. When modifying this codebase, you must adhere to the following rules:

1. **Keep Requirements Updated:** You must read the `REQUIREMENTS.md` file in the root directory before starting work on new features, modifying existing features, or changing project architecture/behavior.
2. **Update After Changes:** If your task introduces new requirements, alters existing rules (e.g., UI layout, threading models, duplicate logic, dependency lists), or deprecates features, you MUST update `REQUIREMENTS.md` to reflect these changes before finalizing your work.
3. **Consistency:** Ensure that any code changes you make perfectly align with the rules specified in `REQUIREMENTS.md` unless the user explicitly instructs you to change those rules.
4. **Testing Context:** Review the testing and headless execution requirements in `REQUIREMENTS.md` (e.g., using `xvfb-run`) when running tests or debugging GUI components.

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

This project uses a multi-agent system with **9 specialized subagents** organized into platform groups plus shared consultants. The coordinator (default agent) automatically delegates work to the appropriate subagent based on the task and target solution.

### Agent Roster

#### Desktop Agents (Python/Tkinter)

| Agent | Scope | Config File |
|-------|-------|-------------|
| **`@backend_agent`** | Core Python logic: `reader.py`, `analyzer.py`, `sharpness.py`, `duplicates.py`, `utils.py`, `formatting.py`, `models.py`, `cli.py`, `visualizer.py` | `.gemini/agents/backend_agent.md` |
| **`@gui_agent`** | Tkinter GUI: `gui.py`, `sharpness_gui.py`, `controllers.py`, `image_panels.py`, `fullscreen_viewer.py` | `.gemini/agents/gui_agent.md` |
| **`@test_agent`** | Desktop testing: `tests/`, `benchmarks/` | `.gemini/agents/test_agent.md` |
| **`@build_agent`** | Desktop build & CI: `scripts/`, `.github/workflows/`, `pyproject.toml` | `.gemini/agents/build_agent.md` |

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
