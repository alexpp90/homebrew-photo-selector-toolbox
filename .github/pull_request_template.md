## Description

Summarize the changes proposed in this Pull Request and provide any relevant context or background.

## Targeted Solution(s)

Please select which independent solution(s) this PR modifies:

- [ ] **Desktop** (Python/Tkinter, files under `products/desktop/src/` or `products/desktop/tests/`)
- [ ] **Android Desktop** (Kotlin/Compose, files under `products/android/android-desktop/`)
- [ ] **Android Phone** (Kotlin/Compose, files under `products/android/phototok/`)

## Verification and Testing

Describe how you verified these changes.

### Automated Tests
Specify the commands run to test these changes:
- Python: `poetry run pytest` / `poetry run xvfb-run pytest`
- Android: `./gradlew testDebugUnitTest` **and** `./gradlew assembleDebugAndroidTest`
- Or the CI mirror for everything: `./scripts/run_tests.sh`

### Manual Verification
Describe any manual testing performed (especially for UI/UX changes, hardware integration, or edge cases).

## Checklist

- [ ] I have read [CONTRIBUTING.md](../CONTRIBUTING.md) and the target product's requirements under [docs/products/](../docs/README.md).
- [ ] I named the target product (Desktop / Android Desktop / PhotoTok) in the description, and did not change the others.
- [ ] If my changes introduce new requirements or modify existing ones, I updated that product's `docs/products/<product>/REQUIREMENTS.md` (or `docs/shared/` if it crosses products).
- [ ] I have added/updated unit tests to cover my changes.
- [ ] All automated tests pass successfully.
- [ ] I have formatted my code using the project's style guidelines (e.g. Flake8, Kotlin style guides).
- [ ] Room database schema is exported and committed (if any database entities changed in `products/android/android-desktop`).
