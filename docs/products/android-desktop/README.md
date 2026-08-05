# Android Desktop

The tablet / Samsung DeX / Chromebook product: a comparison workspace that approaches desktop
parity on large screens. Quality scoring with OpenCV, a Room-backed score cache, statistics
charts and a duplicate finder.

| | |
|---|---|
| Code | `products/android/android-desktop/` |
| Gradle module | `:android-desktop` |
| Package | `com.photoselectortoolbox` |
| Target | Samsung DeX, tablets ≥ 840 dp, Chromebooks (Medium and Expanded window size classes) |
| Artifacts | `photo-selector-toolbox-android-release.apk` / `.aab` |

## Documents

- [`REQUIREMENTS.md`](REQUIREMENTS.md) — authoritative behaviour specification
- [`ARCHITECTURE.md`](ARCHITECTURE.md) — layers, adaptive layout strategy, performance, theme implementation
- [`DESIGN.md`](DESIGN.md) — the visual specification: layout maths, tokens, screens, component sheet
- [`DESIGN_PROMPT.md`](DESIGN_PROMPT.md) — the same design expressed as a brief for a design tool
- Shared with PhotoTok: [`../../shared/ANDROID_PLATFORM.md`](../../shared/ANDROID_PLATFORM.md)
- Build & release: [`../../build/ANDROID_BUILD_AND_RELEASE.md`](../../build/ANDROID_BUILD_AND_RELEASE.md)

`DESIGN.md` and `DESIGN_PROMPT.md` overlap substantially — the prompt is a derivative of the
specification, kept because it is fed to design tooling verbatim. If they disagree, `DESIGN.md`
wins.

## Build and test

```bash
cd android
./gradlew :android-desktop:testDebugUnitTest        # JVM unit tests
./gradlew :android-desktop:assembleDebugAndroidTest # compiles instrumented tests — the step people skip
./gradlew :android-desktop:assembleDebug
```

Or, from the repository root, the CI mirror: `./scripts/run_tests.sh --android`.

## Relationship to the other products

Android Desktop shares the EXIF model and readers with PhotoTok through the `:core` module
(`products/android/core/`, `com.photoselector.core`). Everything else — layouts, ViewModels, theme,
storage flow — is independent. Do not copy composables between `:android-desktop` and `:phototok`.

Excluded here: the Ollama VLM aesthetic tool (Desktop-only), the CLI, ExifTool bundling and SMB
path resolution. The on-device TFLite NIMA score is this product's aesthetic engine. See
[`../../shared/FEATURE_PARITY.md`](../../shared/FEATURE_PARITY.md).
