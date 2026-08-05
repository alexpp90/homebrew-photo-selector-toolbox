# Glossary — Canonical Product Names

The repository ships three products that have historically been called by six or seven
different names. This file fixes the vocabulary. Use the **canonical name** in commits, pull
requests, documentation, agent output and issue titles. "The Android app" is ambiguous and
therefore wrong — there are two of them.

## The three products

| Canonical name | Slug | Code | Gradle module | Package / distribution | Target |
|---|---|---|---|---|---|
| **Desktop** | `desktop` | `products/desktop/src/photo_selector_toolbox/` | — | `photo-selector-toolbox` (PyPI-style dist name), Homebrew Formula + Cask | macOS, Linux, Windows workstations |
| **Android Desktop** | `android-desktop` | `products/android/android-desktop/` | `:android-desktop` | `com.photoselectortoolbox` | Samsung DeX, tablets ≥ 840 dp, Chromebooks |
| **PhotoTok** | `phototok` | `products/android/phototok/` | `:phototok` | `com.phototok` | Phones < 600 dp, portrait, gesture-first |

Plus one shared library, which is not a product:

| Canonical name | Code | Gradle module | Package |
|---|---|---|---|
| **Android Core** | `products/android/core/` | `:core` | `com.photoselector.core` |

## Deprecated names — do not use

| Do not write | Write instead | Why |
|---|---|---|
| "the Android app" | Android Desktop, or PhotoTok | There are two Android products |
| "Toolbox" (on its own) | Android Desktop, or Desktop | `:android-desktop`'s artifacts are named `photo-selector-toolbox-android-*`, but so is the Python product |
| `Android Phone`, `Phone mode`, `Phone Tok` | PhotoTok | One product, one name |
| `Photo Tok`, `Photo-Tok` | PhotoTok | One spelling, no space, capital T |
| "the desktop app" for `:android-desktop` | Android Desktop | `:android-desktop` runs on Android in a desktop-shaped window; the Python product is Desktop |
| "tablet mode" / "phone mode" as product names | Android Desktop / PhotoTok | These are window size classes, not products |

The names baked into build identifiers (`:android-desktop`, `com.photoselectortoolbox`,
`photo-selector-toolbox-android-release.apk`, the `TOOLBOX_*` CI secrets) are **not** being
renamed: they are load-bearing for Google Play, Firebase and the signing pipeline, where a
rename means a new app listing. The table above is the mapping between the readable name and
those identifiers. Prose uses the canonical name; build configuration keeps the identifier.

## Terms of art

| Term | Meaning |
|---|---|
| **Selection** | The destination subfolder (default name `Selection`) that kept photos are moved or copied into. Same concept in all three products. |
| **Selector** | The comparison workspace: previous / current / next, with quality scores. Desktop and Android Desktop only. |
| **Feed** | PhotoTok's vertically paged stream of photos. PhotoTok only. |
| **Score** | One of sharpness, noise, highlight clipping, shadow clipping — computed identically in Desktop and Android Desktop (see [`shared/FEATURE_PARITY.md`](shared/FEATURE_PARITY.md)). PhotoTok computes none of them. |
| **Window size class** | Compact (< 600 dp), Medium (600–840 dp), Expanded (≥ 840 dp). A layout dimension, never a product name. |
