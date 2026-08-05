# PhotoTok

The phone product: a gesture-first, vertically paged feed for fast culling. Deliberately
lightweight — no OpenCV, no Room, no charts, no background scanning. Time-to-first-swipe is the
metric it optimises.

| | |
|---|---|
| Code | `products/android/phototok/` |
| Gradle module | `:phototok` |
| Package | `com.phototok` |
| Target | Phones < 600 dp, portrait, touch-first (Compact window size class) |
| Artifacts | `phototok-android-release.apk` / `.aab` |

## Documents

- [`REQUIREMENTS.md`](REQUIREMENTS.md) — authoritative behaviour specification, including the architecture conventions that are enforced in review
- [`ARCHITECTURE.md`](ARCHITECTURE.md) — theme and structural notes
- [`DESIGN.md`](DESIGN.md) — the visual specification (colour system, landing, viewer, gesture tutorial)
- Shared with Android Desktop: [`../../shared/ANDROID_PLATFORM.md`](../../shared/ANDROID_PLATFORM.md)
- Build & release: [`../../build/ANDROID_BUILD_AND_RELEASE.md`](../../build/ANDROID_BUILD_AND_RELEASE.md)

## Build and test

```bash
cd android
./gradlew :phototok:testDebugUnitTest
./gradlew :phototok:assembleDebugAndroidTest
./gradlew :phototok:assembleDebug
```

Or, from the repository root: `./scripts/run_tests.sh --android`.

## Design constraints that are not negotiable

- **SAF only.** No Google Sign-In, no Drive REST client, no Picker, no OAuth scopes. Cloud
  storage is reached exclusively through Storage Access Framework document providers. This is a
  Play-compliance boundary.
- **Legal links must ship.** Settings exposes "Privacy Policy" and "Legal Notice (Impressum)"
  from `com.phototok.domain.LegalLinks`.
- **Gestures must explain themselves.** First-run action explanations, mid-swipe indicator
  labels and the coach-mark controls guide name the action the user has *configured*, never a
  default or a euphemism.
- **Nothing blocks on file I/O.** Copy, move and delete update the feed optimistically and
  reverse on failure.

## Relationship to the other products

PhotoTok shares the EXIF model and readers with Android Desktop through `:core`
(`products/android/core/`, `com.photoselector.core`). It is **not** a compact variant of Android Desktop
— different interaction model, different theme, independent implementation. See
[`../../shared/FEATURE_PARITY.md`](../../shared/FEATURE_PARITY.md).

## Missing

`RELEASE_CHECKLIST.md` — the single source of truth for open release tasks, referenced by the
publish agent — has not been written yet. It is needed before the next store submission.
