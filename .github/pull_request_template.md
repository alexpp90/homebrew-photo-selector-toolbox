## Description

Summarize the changes proposed in this Pull Request and provide any relevant context or background.

## Targeted Solution(s)

Please select which independent solution(s) this PR modifies:

- [ ] **Desktop** (Python/Tkinter, files under `src/` or `tests/`)
- [ ] **Android Desktop** (Kotlin/Compose, files under `android/app/`)
- [ ] **Android Phone** (Kotlin/Compose, files under `android/phototok/`)

## Verification and Testing

Describe how you verified these changes.

### Automated Tests
Specify the commands run to test these changes:
- Python: `poetry run pytest` / `poetry run xvfb-run pytest`
- Android: `./gradlew testDebugUnitTest`

### Manual Verification
Describe any manual testing performed (especially for UI/UX changes, hardware integration, or edge cases).

## Checklist

- [ ] I have read the project's [CONTRIBUTING.md](CONTRIBUTING.md) and [REQUIREMENTS.md](REQUIREMENTS.md).
- [ ] If my changes introduce new requirements or modify existing ones, I have updated `REQUIREMENTS.md` accordingly.
- [ ] I have added/updated unit tests to cover my changes.
- [ ] All automated tests pass successfully.
- [ ] I have formatted my code using the project's style guidelines (e.g. Flake8, Kotlin style guides).
- [ ] Room database schema is exported and committed (if any database entities changed in `android/app`).
