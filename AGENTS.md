# Agent Instructions

Welcome to the Image Metadata Analyzer project. When modifying this codebase, you must adhere to the following rules:

1. **Keep Requirements Updated:** You must read the `REQUIREMENTS.md` file in the root directory before starting work on new features, modifying existing features, or changing project architecture/behavior.
2. **Update After Changes:** If your task introduces new requirements, alters existing rules (e.g., UI layout, threading models, duplicate logic, dependency lists), or deprecates features, you MUST update `REQUIREMENTS.md` to reflect these changes before finalizing your work.
3. **Consistency:** Ensure that any code changes you make perfectly align with the rules specified in `REQUIREMENTS.md` unless the user explicitly instructs you to change those rules.
4. **Testing Context:** Review the testing and headless execution requirements in `REQUIREMENTS.md` (e.g., using `xvfb-run`) when running tests or debugging GUI components.

## Multi-Agent System

This project uses a multi-agent system with **4 specialized subagents**. The coordinator (default agent) automatically delegates work to the appropriate subagent based on the task.

### Agent Roster

| Agent | Scope | Config File |
|-------|-------|-------------|
| **`@backend_agent`** | Core Python logic: `reader.py`, `analyzer.py`, `sharpness.py`, `duplicates.py`, `utils.py`, `formatting.py`, `models.py`, `cli.py`, `visualizer.py` | `.gemini/agents/backend_agent.md` |
| **`@gui_agent`** | Tkinter GUI: `gui.py`, `sharpness_gui.py`, `controllers.py` | `.gemini/agents/gui_agent.md` |
| **`@test_agent`** | Testing: `tests/`, `benchmarks/` | `.gemini/agents/test_agent.md` |
| **`@build_agent`** | Build & CI: `scripts/`, `.github/workflows/`, `pyproject.toml` | `.gemini/agents/build_agent.md` |

### Coordinator Behavior

The coordinator agent (default) handles:

- **Auto-delegation:** Analyzes incoming requests and routes work to the appropriate subagent based on which files are affected. If a task spans multiple agents' scopes, the coordinator breaks it into subtasks and delegates each part.
- **Requirements maintenance:** After every change that modifies behavior, the coordinator ensures `REQUIREMENTS.md` is reviewed and updated to stay consistent with the codebase.
- **Cross-agent coordination:** When changes in one agent's scope affect another (e.g., a backend API change that impacts the GUI), the coordinator ensures both agents are informed and aligned.

### Delegation Rules

- Changes to **core algorithms, data models, CLI, or visualizations** → `@backend_agent`
- Changes to **GUI layout, Tkinter widgets, threading, or controllers** → `@gui_agent`
- Changes to **tests, coverage, or test infrastructure** → `@test_agent`
- Changes to **build scripts, CI/CD, dependencies, or packaging** → `@build_agent`
- **Cross-cutting changes** (e.g., refactoring that touches both backend and GUI) → Coordinator splits into subtasks and delegates to each relevant agent

### Shared Skill

All agents have access to the **refactoring guide** skill at `.gemini/skills/refactoring_guide/SKILL.md`. This documents the project's established patterns:
- Centralized constants
- Controller/View separation
- Thread pool sizing
- Image loading safety (RGB conversion)
- EXIF data contract
- Error handling conventions

Agents should read this skill before performing refactoring work.
