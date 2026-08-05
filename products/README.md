# Products

One directory per product. Same shape in each, whatever the language.

```
products/
  desktop/              Desktop — Python + Tkinter
  android/              The Android platform group (one Gradle build)
    android-desktop/    Android Desktop — :android-desktop, tablets and Samsung DeX
    phototok/           PhotoTok — :phototok, phones
    core/               :core — the only code the two Android products share
```

## The uniform shape

Every product directory exposes the same four things:

| | |
|---|---|
| `src/` | Sources. Nothing else. |
| `tests/` | Tests. `tests/unit/` everywhere; Android additionally has `tests/instrumented/`. |
| build config | `pyproject.toml` for Desktop, `build.gradle.kts` for the Android modules. |
| `README.md` | What the product is, how to build it, how to test it, what it must not do. |

The Android modules do not use Gradle's `src/main | src/test | src/androidTest` convention.
Each one overrides `sourceSets` in its `build.gradle.kts` so its layout matches Desktop's.
That is a deliberate trade: the repository reads consistently, at the cost of a five-line
block per module that Android tooling does not expect. Keep the three copies in sync.

`res/`, `jniLibs/` and `AndroidManifest.xml` sit at the Android module root next to `src/`,
for the same reason.

## Why `android/` groups two products

Android Desktop and PhotoTok are independent products, but they are built by **one** Gradle
build — one `settings.gradle.kts`, one wrapper, one version catalog, one CI workflow — and
they share one library module. Splitting them into two sibling directories would mean either
two Gradle builds or a settings file pointing sideways into unrelated directories. Grouping
them under `products/android/` keeps the build root next to the things it builds.

That grouping is a *build* boundary, not a product boundary. The two apps still do not share
code beyond `core/`, do not share layouts, and are owned by separate agents. See
[`../ai/ROUTING.md`](../ai/ROUTING.md).

## What is not here

- **Documentation** — in [`../docs/`](../docs/README.md), organised by the same product slugs.
- **Agent configuration** — in [`../ai/`](../ai/README.md).
- **`Formula/` and `Casks/`** — these must stay at the repository root. This repository is a
  Homebrew tap, and `brew tap` only looks for formulae at the tap root. Moving them into
  `products/desktop/` would break installation for every existing user.
- **`scripts/run_tests.sh`** — cross-product (it drives both the Python and the Gradle gates),
  so it lives in the root `scripts/`. Desktop's own build scripts are in
  `desktop/scripts/`.
