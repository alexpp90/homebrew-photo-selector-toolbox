# Photo Selector Linux

High-performance native GNOME photograph culling and selection suite targeting Debian 13 (Trixie) and modern Linux desktop environments.

## Overview

Photo Selector Linux is the 4th active product in the Photo Selector Toolbox repository. Built with Python 3.12+, PyGObject, GTK4, Libadwaita, GExiv2, and vectorized NumPy, it provides zero-latency SD-card ingestion, companion RAW+JPEG pairing, noise-corrected focus scoring, and tactile culling keyboard shortcuts adhering strictly to the GNOME Human Interface Guidelines (HIG).

## Architecture

The codebase enforces strict separation of concerns:

- `photo_selector_linux.core`:
  - `models`: Typed photographic data models (`CandidatePhoto`, `ExposureMetadata`, `QualityScores`, `CullingRecord`).
  - `scanner`: Recursive progressive directory scanner with companion binding, Lightroom derivative detection, and dynamic `Selection/` subfolder exclusion.
  - `metadata`: Optical metadata extraction via `GExiv2` with Pillow fallback, APEX math conversions ($T_v \to 2^{-T_v}$, $A_v \to 2^{A_v/2}$), and fractional shutter formatting.
  - `file_ops`: Transactional file relocations with Two-Phase Staged Transactions for cross-mount moves, safe copy-undo preserving original source media, and FreeDesktop Trash integration (`Gio.File.trash()`).
  - `scoring`: Pure NumPy focus scoring engine utilizing 2D discrete Laplacian convolution, 3x3 Gaussian smoothing, center 50% ROI cropping, and strided Median Absolute Deviation (MAD) sensor noise compensation (< 2.0 ms SLA).
  - `duplicate_finder`: Two-stage duplicate detection using byte size pre-filtering and chunked 64 KB streaming SHA-256 hashing.
  - `statistics`: Linear single-pass $O(N)$ library metadata aggregator.
- `photo_selector_linux.ui`:
  - `application`: `Adw.Application` lifecycle controller with `org.photoselector.Linux` application icon registration and CSS provider injection.
  - `window`: `Adw.ApplicationWindow` with `.studio-headerbar` (`Adw.HeaderBar`), segmented view switcher, and toast notifications.
  - `canvas`: Maximized viewport (>85% area) supporting 1-Up (1:1 actual-pixel zoom toggle on `Space`), 2-Up side-by-side comparison, and 3-Up Focus Mode (sliding triplet), with contextual slot role pills (`ACTIVE`, `CHAMPION`/`CHALLENGER`, `PREVIOUS`/`ACTIVE`/`NEXT`), companion badges (`RAW+JPG`, `XMP`), and an Obsidian Darkroom empty-state hero card.
  - `info_hud`: Glassmorphic top-left optical metadata HUD (`InfoHUD`) with companion badges, camera/lens model line, normalized sharpness goodness meter bar, semantic quality badges (`Sharp` / `Acceptable` / `Soft`), and strict Metric Omission Rule compliance.
  - `boundary_slot`: `BoundarySlotPane` indicators for start/end album bounds (`First Photograph` / `Last Photograph`).
  - `action_bar`: Persistent bottom `Gtk.ActionBar` (`.studio-actionbar`) with semantic status counter pills (`Selected`, `Trashed`, `Remaining`) and tactile Move, Copy, Trash, and Undo buttons.
  - `preferences`: Modal `Adw.PreferencesWindow` (`Ctrl+,`).
  - `keyboard_router`: Root window `Gtk.EventControllerKey` in `CAPTURE` phase with modifier lock masking (`state & ~(LOCK_MASK | MOD2_MASK)`) and text entry bypass.
  - `styling`: Obsidian Darkroom & Optical Cobalt CSS palette (`#141417` surface, `#181920` elevated chrome, `#1C1C20` cards, `#3584E4` / `#06B6D4` Optical Cobalt & Cyan accents, `#6366F1` Iris Indigo, `#2EC27E` Emerald Keeper, `#E5A50A` Amber Optical, `#E01B24` Crimson Cull) enforcing $\ge 7:1$ AAA contrast.

## Keyboard Shortcuts

| Shortcut | Action |
|---|---|
| `←` / `H` / `K` | Previous photograph |
| `→` / `L` / `J` | Next photograph |
| `M` | Move photo and companions to `Selection/` and auto-advance |
| `C` | Copy photo and companions to `Selection/` and auto-advance |
| `Delete` / `Backspace` | Move photo and companions to FreeDesktop Trash and auto-advance |
| `1` / `2` / `3` | Switch comparison modes (1-Up, 2-Up, 3-Up Focus Mode) |
| `Space` | Toggle 100% 1:1 actual-pixel zoom in 1-Up mode |
| `Escape` | Reset zoom to fit / dismiss |
| `I` | Toggle floating optical metadata & quality score HUD |
| `Ctrl+Z` | Undo last culling operation (atomic reversal) |
| `Ctrl+O` | Open folder / SD card directory |
| `Ctrl+D` | Open Duplicate Finder |
| `Ctrl+L` | Open Library Statistics |
| `Ctrl+,` | Open Preferences |

## Installation (Debian 13 Trixie)

Add the repository keyring and `deb822` source:

```bash
sudo mkdir -p /etc/apt/keyrings
curl -fsSL https://alexanderpatz.github.io/photo-selector/apt/photo-selector-archive-keyring.gpg | sudo tee /etc/apt/keyrings/photo-selector-archive-keyring.gpg > /dev/null

sudo tee /etc/apt/sources.list.d/photo-selector.sources << EOF
Types: deb
URIs: https://alexanderpatz.github.io/photo-selector/apt
Suites: trixie
Components: main
Signed-By: /etc/apt/keyrings/photo-selector-archive-keyring.gpg
EOF

sudo apt update
sudo apt install photo-selector-linux
```

## Running & Testing

To run the application locally:

```bash
python3 -m photo_selector_linux [folder_path]
```

To run the automated headless unit test suite:

```bash
pytest tests/unit/
```

To run the CI parity verification mirror:

```bash
./scripts/run_tests.sh --linux
```
