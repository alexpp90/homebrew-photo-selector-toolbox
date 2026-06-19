# Agent Instructions

Welcome to the Photo Selector Toolbox project. When modifying this codebase, you must adhere to the following rules:

1. **Keep Requirements Updated:** You must read the `REQUIREMENTS.md` file in the root directory before starting work on new features, modifying existing features, or changing project architecture/behavior.
2. **Update After Changes:** If your task introduces new requirements, alters existing rules (e.g., UI layout, threading models, duplicate logic, dependency lists), or deprecates features, you MUST update `REQUIREMENTS.md` to reflect these changes before finalizing your work.
3. **Consistency:** Ensure that any code changes you make perfectly align with the rules specified in `REQUIREMENTS.md` unless the user explicitly instructs you to change those rules.
4. **Testing Context:** Review the testing and headless execution requirements in `REQUIREMENTS.md` (e.g., using `xvfb-run`) when running tests or debugging GUI components.

## Project Platforms

This project targets **two platforms** that share the same repository:

| Platform | Root Directory | Tech Stack | Primary Target |
|----------|---------------|------------|----------------|
| **Desktop (macOS/Linux/Windows)** | `src/` | Python + Tkinter | Desktop workstations |
| **Android** | `android/` | Kotlin + Jetpack Compose | Samsung DeX / large tablets (primary), phones (secondary) |

The platforms share photographic domain knowledge and algorithmic specifications but have **independent implementations** optimized for their respective environments. The Android version deliberately deviates from desktop UX where touch interaction patterns, battery constraints, or mobile usage workflows demand it.

### Feature Sync Policy

When a new feature is added to either platform, the coordinator must evaluate whether it should also be added to the other platform. Desktop features are the superset; Android tablet/DeX mode should match desktop feature parity where feasible. Android phone mode may omit features that don't work well on small screens. The **Local AI (Ollama VLM)** feature is **excluded from Android** entirely due to battery and compute constraints.

## Multi-Agent System

This project uses a multi-agent system with **9 specialized subagents** organized into two platform groups plus shared consultants. The coordinator (default agent) automatically delegates work to the appropriate subagent based on the task and target platform.

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
| **`@android_ui_agent`** | Jetpack Compose UI: all files under `android/app/src/main/.../ui/`, adaptive layouts, Material 3 theming, navigation, gesture handling | `.gemini/agents/android_ui_agent.md` |
| **`@android_core_agent`** | Android data & domain layers: `android/app/src/main/.../data/`, `android/app/src/main/.../domain/`, EXIF extraction, image analysis algorithms, Room database, repositories | `.gemini/agents/android_core_agent.md` |
| **`@android_build_agent`** | Android build: `android/build.gradle.kts`, `android/app/build.gradle.kts`, `android/gradle/`, GitHub Actions Android workflow, signing, ProGuard/R8 | `.gemini/agents/android_build_agent.md` |

#### Shared Consultant Agents (Cross-Platform)

| Agent | Scope | Config File |
|-------|-------|-------------|
| **`@photo_researcher_agent`** | Photographic science, image quality metrics, aesthetics, and requirement elucidation. Consulted by both desktop and Android agents. | `.gemini/agents/photo_researcher_agent.md` |
| **`@ux_agent`** | Professional design, UX flow, user patterns, and flow analysis. Provides platform-specific design guidance for both desktop (mouse/keyboard) and Android (touch/gesture). | `.gemini/agents/ux_agent.md` |

### Coordinator Behavior

The coordinator agent (default) handles:

- **Auto-delegation:** Analyzes incoming requests and routes work to the appropriate subagent based on which files and platform are affected. If a task spans multiple agents' scopes or platforms, the coordinator breaks it into subtasks and delegates each part.
- **Platform detection:** Determines whether a task targets desktop, Android, or both based on file paths, feature descriptions, and explicit user instructions. Tasks affecting `android/` go to Android agents; tasks affecting `src/` go to desktop agents.
- **Parallel platform work:** When a feature should be implemented on both platforms, the coordinator delegates to the respective platform agents independently. Each platform agent implements the feature in the idiomatic way for its stack.
- **Consultation on vagueness:** If a request is vague, subjective, or requires deep photographic domain knowledge, the coordinator consults `@photo_researcher_agent` first. If a request involves user workflows or styling, the coordinator consults `@ux_agent` — specifying the target platform so UX guidance is platform-appropriate.
- **Feature sync checks:** After implementing a feature on one platform, the coordinator evaluates whether the feature should be synced to the other platform and creates follow-up tasks if so.
- **Requirements maintenance:** After every change that modifies behavior, the coordinator ensures `REQUIREMENTS.md` is reviewed and updated to stay consistent with the codebase.
- **Cross-agent coordination:** When changes in one agent's scope affect another (e.g., a backend algorithm change that impacts both desktop GUI and Android UI), the coordinator ensures all affected agents are informed and aligned.

### Delegation Rules

#### Desktop
- Changes to **core algorithms, data models, CLI, or visualizations** → `@backend_agent`
- Changes to **GUI layout, Tkinter widgets, threading, or controllers** → `@gui_agent`
- Changes to **desktop tests, coverage, or test infrastructure** → `@test_agent`
- Changes to **desktop build scripts, CI/CD, dependencies, or packaging** → `@build_agent`

#### Android
- Changes to **Compose UI, adaptive layouts, navigation, or gestures** → `@android_ui_agent`
- Changes to **Android data models, EXIF extraction, image analysis, Room DB, or repositories** → `@android_core_agent`
- Changes to **Android Gradle build, CI workflow, signing, or ProGuard** → `@android_build_agent`

#### Cross-Platform
- **Vague requirements, research on photographic algorithms, or artistic/composition specifications** → `@photo_researcher_agent`
- **UI/UX design, visual styling guidelines, layout ergonomics, or user pattern analysis** → `@ux_agent` (specify platform context)
- **Cross-cutting changes** (e.g., algorithm change affecting both platforms) → Coordinator splits into subtasks and delegates to each relevant agent
- **New feature implementation** → Coordinator delegates to the primary platform's agents, then evaluates sync to the other platform

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
