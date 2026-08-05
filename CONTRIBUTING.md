# Contributing to Photo Selector Toolbox

Thank you for your interest in contributing to Photo Selector Toolbox! This document outlines the guidelines and steps to help you get started with contributing to this open-source project.

## Code of Conduct

By participating in this project, you agree to abide by our [Code of Conduct](CODE_OF_CONDUCT.md). Please report any unacceptable behavior to the project maintainers.

## Project Structure (Three Independent Solutions)

This repository contains **three independent solutions** targeting different platforms, sharing the same repository:

| Solution | Code Directory | Tech Stack | Primary Target |
|----------|-----------------|------------|----------------|
| **Desktop** | `products/desktop/src/` | Python + Tkinter | Desktop (macOS, Linux, Windows) |
| **Android Desktop** | `products/android/android-desktop/` | Jetpack Compose + Room + OpenCV + Vico | Samsung DeX, Tablets (>= 840dp) |
| **PhotoTok** | `products/android/phototok/` | Jetpack Compose (Lightweight) | Mobile Phone (< 600dp, portrait) |

### Feature Sync & Coordination Policy

Features should be tailored specifically to the targeted form factors:
- **Single Target**: Do not inject or modify code in other solutions.
- **Multiple/All Targets**: Split the work into distinct tasks tailored to each platform's UX patterns. (e.g. PhotoTok uses gesture-first navigation, whereas Desktop uses menu/keyboard controls).
- **Platform Limitations**: 
  - Ollama VLM (Aesthetic scoring) is Desktop-only.
  - Room DB, OpenCV analysis, and Vico charts are omitted from PhotoTok (`phototok`) to keep it lightweight.

---

## Desktop Development (Python)

### Prerequisites
- Python 3.10+
- Tkinter (on Linux: `sudo apt install python3-tk`, on macOS: `brew install python-tk`)
- [Poetry](https://python-poetry.org/) for package management

### Getting Started
1. Install dependencies:
   ```bash
   poetry install
   ```
2. Launch the GUI:
   ```bash
   poetry run photo-selector-gui
   ```
3. Run the CLI:
   ```bash
   poetry run photo-selector-toolbox /path/to/photos
   ```

### Running Tests
We use `pytest` for unit testing:
```bash
poetry run pytest
```
If you are running tests in a headless Linux environment, use `xvfb-run` to prevent Tkinter display errors:
```bash
poetry run xvfb-run pytest
```

### Static Analysis
We enforce strict Mypy types and Flake8 formatting:
```bash
poetry run mypy src tests
poetry run flake8 src tests
```

---

## Android Development (Kotlin)

### Prerequisites
- Android Studio Ladybug (or newer)
- JDK 17
- Android SDK 36

### Getting Started
1. Open the `products/android/` directory in Android Studio.
2. Build the debug APK:
   ```bash
   cd android
   ./gradlew assembleDebug
   ```

### Running Tests
- **JVM Unit Tests**:
  ```bash
  ./gradlew testDebugUnitTest
  ```
- **Instrumented Emulator Tests**:
  ```bash
  ./gradlew connectedDebugAndroidTest
  ```

### Room Database Schemas
If you modify Room database entities in the `app` module, make sure to export the updated database schema:
```bash
./gradlew kspDebugKotlin
```
Commit any updated schema files located under `products/android/android-desktop/schemas/`.

---

## Working with AI coding agents

This repository ships an agent framework under [`ai/`](ai/README.md) — agent definitions,
skills, hooks and commands — used by Claude Code, Antigravity, Gemini CLI and Jules. Two
things affect you even if you never invoke an agent:

**Symlinks.** `.claude/`, `.gemini/` and `.agents/` are symlinks into `ai/`. Cloning needs
`core.symlinks=true` — the default on macOS and Linux. On Windows, enable Developer Mode or
run `git config core.symlinks true` before cloning. **Always edit the canonical file under
`ai/`,** never through the mirrored directories.

**Blocking hooks.** When you work through an agent, `ai/hooks/` refuses writes that break the
repository's structural rules: scratch files and report dumps, writes through the mirrored
directories, hand-edits of the generated `.gemini/settings.json`, and code at the repository
root. It also blocks committing staged scratch artifacts and pushing a chain of speculative
`fix(ci)` commits. Each denial explains the alternative. In a genuine emergency,
`PST_SKIP_HOOKS=1` bypasses all of them.

Framework changes have their own definition of done:

```bash
python3 ai/skills/sync-framework/scripts/gen_gemini_settings.py
python3 ai/skills/sync-framework/scripts/validate_framework.py
```

Both run as gates inside `./scripts/run_tests.sh`.

---

## Submitting Pull Requests

1. **Check the Requirements**: Always check the owning product's `docs/products/<product>/REQUIREMENTS.md` before making changes (there is no repository-root `REQUIREMENTS.md`). If your change alters existing requirements or behavior, update that file in the same commit. Cross-product policy lives in `docs/shared/`.
2. **Branch Naming**: Use descriptive branch names: `feature/your-feature` or `fix/your-fix`.
3. **Commit Messages**: Use clean, descriptive commit messages adhering to conventional commits (e.g., `feat(android): add picture randomization settings`).
4. **Run Tests**: Run `./scripts/run_tests.sh` — the local mirror of every gate CI enforces. Bare `pytest` or `gradlew testDebugUnitTest` is a strict subset and is not sufficient evidence. See [`docs/build/CI_PARITY.md`](docs/build/CI_PARITY.md).
5. **PR Template**: Complete the pull request template in full.
