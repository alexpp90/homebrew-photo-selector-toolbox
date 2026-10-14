# Contributing to Photo Selector Toolbox

Thank you for your interest in contributing to Photo Selector Toolbox! This document outlines the guidelines and steps to help you get started with contributing to this open-source project.

## Code of Conduct

By participating in this project, you agree to abide by our [Code of Conduct](CODE_OF_CONDUCT.md). Please report any unacceptable behavior to the project maintainers.

## Project Structure (Four Independent Solutions)

This repository contains **four independent solutions** targeting different platforms, sharing the same repository:

| Solution | Code Directory | Tech Stack | Primary Target |
|----------|-----------------|------------|----------------|
| **Desktop** | `products/desktop/src/` | Python + Tkinter | Desktop (macOS, Linux, Windows) |
| **macOS Desktop** | `products/macos-desktop/` | Swift 6 + SwiftUI + Vision + Accelerate | macOS 14+ / 15+ (Apple Silicon & Intel) |
| **Android Desktop** | `products/android/android-desktop/` | Jetpack Compose + Room + OpenCV + Vico | Samsung DeX, Tablets (>= 840dp) |
| **PhotoTok** | `products/android/phototok/` | Jetpack Compose (Lightweight) | Mobile phones of every tier, portrait |

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

AI coding agents are welcome. [`AGENTS.md`](AGENTS.md) is the entry point every common agent
reads (Claude Code via `CLAUDE.md`, Gemini/Antigravity via `GEMINI.md`, Jules, Copilot,
Cursor): it names the five products, the rule that code never crosses product boundaries,
and the definition of done (`./scripts/run_tests.sh`).

The maintainer additionally uses a private agent framework, requirement specifications and
design prompts that are kept in a separate private repository and linked into his checkout
under `private/`. They are not needed to build, test or contribute. Paths such as `ai/`,
`.claude/`, `.agents/`, `.gemini/`, `private/` and `docs/**/REQUIREMENTS.md` are therefore
ignored by `.gitignore`, and `scripts/check_private_paths.py` fails CI if one of them is
committed — please do not add files there.

---

## Submitting Pull Requests

1. **Describe the Behaviour Change**: Read the owning product's `README.md` and `docs/products/<product>/` documentation before making changes. If your change alters observable behaviour, describe it precisely in the pull request — the maintainer keeps the requirement specifications in sync. Cross-product policy lives in `docs/shared/`.
2. **Branch Naming**: Use descriptive branch names: `feature/your-feature` or `fix/your-fix`.
3. **Commit Messages**: Use clean, descriptive commit messages adhering to conventional commits (e.g., `feat(android): add picture randomization settings`).
4. **Run Tests**: Run `./scripts/run_tests.sh` — the local mirror of every gate CI enforces. Bare `pytest` or `gradlew testDebugUnitTest` is a strict subset and is not sufficient evidence. See [`docs/build/CI_PARITY.md`](docs/build/CI_PARITY.md).
5. **PR Template**: Complete the pull request template in full.
