<div align="center">
  <img src="assets/logo.png" alt="Photo Selector Toolbox Logo" width="160" />

  # Photo Selector Toolbox

  **The fastest way to cull, analyze, and organize your photos — across desktop and Android.**

  [![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
  [![Python 3.10+](https://img.shields.io/badge/Python-3.10%2B-3776AB?logo=python&logoColor=white)](https://www.python.org/)
  [![Kotlin](https://img.shields.io/badge/Kotlin-2.0%2B-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
  [![Build](https://img.shields.io/github/actions/workflow/status/alexpp90/homebrew-photo-selector-toolbox/build.yml?branch=main&label=Build&logo=github)](https://github.com/alexpp90/homebrew-photo-selector-toolbox/actions)
  [![Latest Release](https://img.shields.io/github/v/release/alexpp90/homebrew-photo-selector-toolbox?label=Stable&logo=github)](https://github.com/alexpp90/homebrew-photo-selector-toolbox/releases/latest)
  [![Nightly](https://img.shields.io/github/release-date-pre/alexpp90/homebrew-photo-selector-toolbox?label=Nightly&logo=github)](https://github.com/alexpp90/homebrew-photo-selector-toolbox/releases/tag/nightly)
  [![macOS](https://img.shields.io/badge/macOS-000000?logo=apple&logoColor=white)](#-desktop-installation)
  [![Windows](https://img.shields.io/badge/Windows-0078D4?logo=windows&logoColor=white)](#-desktop-installation)
  [![Linux](https://img.shields.io/badge/Linux-FCC624?logo=linux&logoColor=black)](#-desktop-installation)
  [![Android](https://img.shields.io/badge/Android-3DDC84?logo=android&logoColor=white)](#-android)

  <br />
  <img src="assets/banner.png" alt="Photo Selector Toolbox Dashboard Banner" width="800" style="border-radius: 12px; box-shadow: 0 4px 20px rgba(0,0,0,0.3);" />
</div>

---

## Overview

Photo Selector Toolbox provides a professional, high-velocity photo culling and analysis workflow. Designed to process directories of images, it automatically extracts EXIF metadata, estimates image quality (sharpness, noise, highlight/shadow clipping), groups similar series, and finds duplicate files.

This project delivers **three independent, native solutions** tailored to different form factors and interaction models, sharing a single codebase repository.

> **Privacy First:** Your images are processed entirely on-device. No subscriptions. No cloud uploads. No telemetry.

---

## The Three Solutions

| Solution | Code Module | Target Environment | Primary UI Paradigm | Key Tech Stack |
| :--- | :--- | :--- | :--- | :--- |
| **Desktop** | `src/` | macOS, Windows, Linux | Menu & Keyboard-driven desktop | Python 3.10+, Tkinter (clam custom), OpenCV, rawpy, Pillow, Matplotlib |
| **Android Desktop** | `android/app/` | Samsung DeX, Large Tablets ($\ge 840$dp), Chromebooks | Mouse, Keyboard & Multi-Pane Touch | Kotlin 2.0+, Compose, Room DB, OpenCV SDK, Vico Charts, Coil 2 |
| **Android Phone** | `android/phototok/` | Portrait Mobile Phones ($< 600$dp) | Swipe-centric, Gesture-first ("Photo Tok") | Kotlin 2.0+, Compose, DataStore (lightweight), AndroidX ExifInterface, Coil 2 |

### Platform Feature Sync Matrix

| Feature | Desktop | Android Desktop | Android Phone (Photo Tok) | Notes |
| :--- | :---: | :---: | :---: | :--- |
| **Image Review Layouts** | Standard / Focus | 3-Column / Focused | Vertical Pager | Desktop uses side-by-side panels; Phone utilizes gesture pager. |
| **Center Sharpness Score** | ✅ | ✅ | ✅ | center 50% crop variance check. |
| **Laplacian Noise (MAD)** | ✅ | ✅ | ✅ | Estimating noise with Median Absolute Deviation. |
| **Highlights/Shadows Clipping** | ✅ | ✅ | ✅ | Grayscale pixel thresholds $\ge 254$ and $\le 2$. |
| **SQLite Score Caching** | ✅ | ✅ | ❌ | Phone mode avoids local DB overhead to stay lightweight. |
| **Ollama Local AI VLM** | ✅ | ❌ | ❌ | Excluded from mobile to conserve battery and compute. |
| **Matplotlib / Vico Charts** | ✅ | ✅ | ❌ | Phone mode relies on simplified scrollable details. |
| **dHash Grouping Levels** | ✅ | ✅ | ❌ (Time-only) | Phone mode uses simple temporal burst checks. |
| **SMB Path Resolution** | ✅ | ❌ | ❌ | Android delegates remote directory shares via SAF. |
| **ExifTool Integration** | ✅ | ❌ (ExifInterface) | ❌ (ExifInterface) | Android uses native AndroidX ExifInterface. |
| **Picture Randomization** | ❌ | ❌ | ✅ | Phone settings toggle to shuffle loaded assets. |

---

## 💻 1. The Desktop Solution (Python)

A full-featured Python command-line utility and Tkinter graphical interface.

### Dynamic Review Layouts
- **Standard Mode:** Center-focused layout with Current image on top and Previous/Next images side-by-side below.
- **Focus Mode:** Top row is split between the Current image and a Controls/Metadata panel. Bottom row splits the Previous and Next images in equal dimensions.
- **Fullscreen Viewer:** Accessible via `F` (or double-click). Features a non-intrusive metadata panel overlay showing Lens, EXIF metrics, and quality scores.

### Quality Assessment Algorithms
- **Sharpness Score:** Crops the center 50% of the image, divides it into an 8x8 grid, and calculates the maximum block variance of the Laplacian.
- **Noise Level:** Evaluates image noise using the Median Absolute Deviation (MAD) of the Laplacian filter:
  $$\sigma = \frac{\text{median}(|\nabla^2 I - \text{median}(\nabla^2 I)|)}{0.6745}$$
- **Clipping Indices:** Measures percentage of blown highlights ($\ge 254$ intensity) and crushed shadows ($\le 2$ intensity).
- **Ollama AI Evaluation:** Offline local Vision Language Model querying (via `urllib` to avoid external dependencies). Resizes the preview to $400 \times 400$, encodes to base64, and prompts the local server. Calibrates output to a $1.0 - 10.0$ score, and extracts `[ANALYSIS: tag]` descriptions.

### Utilities & Operations
- **Smart Image Grouping:** Three settings (Time & Filename, Time + Fast Similarity, Detailed Similarity) using 8x8 or 16x16 dHash metrics. Restricts visual dHash calculations to temporal burst candidates (modification time diff $\le 30.0$s) to bypass up to 90% of file decoding.
- **Duplicate Finder:** SHA-256 content hashing to identify true duplicates. Moves files safely using `send2trash`, falling back to permanent delete (`unlink`) with user approval on failure (e.g. network drives).
- **SMB url Resolver:** Resolves network `smb://` share URLs to local filesystem mount paths dynamically for macOS (`/Volumes/...`) and Linux (`/run/user/.../gvfs/...`).

---

## 🖥️ 2. The Android Desktop Solution (Tablet / DeX)

Built for Samsung DeX desktop mode and expanded tablets, providing parity with the desktop culling workflow.

### Tablet Multi-Pane Layout
- **Toggleable Viewports:**
  - **Three-Column View:** Displays Previous, Current, and Next images side-by-side in equal dimensions for quick comparative culling.
  - **Focused View:** Focuses on the Current image (55% height weight) on top, with Previous and Next images side-by-side at the bottom (45% height weight).
- **Control Bar:** Provides actions (Move, Copy, Delete) and details directly beneath the active image.
- **Scrub Strip:** Bounded horizontal thumbnail candidate list at the bottom for quick folder indexing.

### Desktop-Grade Interactivity
- **Samsung DeX Support:** Configured with density-keepalive metadata and explicit minimum launch bounds ($800 \times 600$dp).
- **Mouse & Keyboard Bindings:** Hovering over buttons/images activates the hand pointer shape (`PointerIcon.Hand`). Binds hardware keyboard shortcuts matching the desktop app (Left/Right to navigate, Delete/Backspace to trash, M/C to organize, Escape to exit).
- **Folder Drag-and-Drop:** Allows dragging folder directories from native file managers directly into the app window to import them instantly.
- **Persistent Room Database Cache:** Saves evaluated metrics locally. Automatically prunes records past a 10,000 image MRU limit.

---

## 📱 3. The Android Phone Solution ("Photo Tok")

A lightweight, gesture-first, touch-optimized portrait client designed for quick culling on mobile phones.

### TikTok-Style Culling Pager
- **Vertical Navigation:** Browse through local folders by swiping up/down (`HorizontalPager` adapted to vertical scrolling).
- **Orientation-Aware Sorting:** Orders landscape photos first, followed by portrait, separating them with a visual rotation prompt. Optional **Picture Randomization** toggle overrides this, shuffling the collection.
- **Snackbar Undo:** Supports a 30-second snackbar undo window for single deletions, avoiding modal dialog interruptions.

### Tactile Touch Gestures
- **Swipe-Left to Discard:** Progressive drag reveals a large, pulsing red trash indicator at the right edge. Card tilts ($2^\circ$ counter-clockwise) and fades. Releasing past the threshold triggers system-level trashing (`MediaStore.createTrashRequest()`).
- **Double-Tap to Collect:** Instantly copies or moves the photo to the selection folder, accompanied by an animated green checkmark overlay with a bounce-in scale effect.
- **One-Tap HUD Toggle:** Tapping the main screen toggles the visibility of the glassmorphic metadata panel, page counters, and navigation bars.
- **Gesture Onboarder:** Displayed on first launch to teach users the culling controls.

---

## ⚙️ Core Architecture & Infrastructure

### Unified EXIF Data Contract
Both desktop and Android solutions enforce typed contracts. Raw dictionaries are mapped to the standard `ExifData` class/data class (ISO, Shutter Speed, Aperture, Focal Length, Lens model). Focal length plotting and stats calculations automatically exclude values $\le 0.0$ mm.

### Android Cloud Project Isolation
The two Android modules (`:app` and `:phototok`) are **fully isolated**. They operate with:
- Dedicated Google Cloud / Firebase Projects (`photo-selector-tb-dist` and `phototok-app`).
- Isolated Android OAuth client registrations (with separate debug/Play SHA-1 signatures).
- Bounded GitHub Actions variables (`TOOLBOX_*` and `PHOTOTOK_*`) representing independent keystores, passwords, and distribution endpoints.

---

## 🚀 Installation

### Desktop Installation

#### Homebrew (macOS & Linux)
Install the **stable** releases or rolling **nightly** builds:

##### macOS Cask (Includes GUI & CLI)
```bash
# Tap the repository
brew tap alexpp90/photo-selector-toolbox

# Install Stable
brew install --cask photo-selector-toolbox

# Install Nightly (includes latest features)
brew install --cask photo-selector-toolbox@nightly
```

##### Linux & macOS Formula (CLI-only, GUI on Linux)
```bash
# Tap and Install Stable
brew tap alexpp90/photo-selector-toolbox
brew install photo-selector-toolbox

# Install Nightly
brew install photo-selector-toolbox@nightly
```

#### Standalone Executables (Pre-built)
Download the standalone ZIP archives from our latest [GitHub Releases](https://github.com/alexpp90/homebrew-photo-selector-toolbox/releases) page. Bundled with **ExifTool** out of the box (no Python required).

| Operating System | Target Architecture | Archive Link |
| :--- | :--- | :--- |
| **Windows** | x64 | [Download ZIP](https://github.com/alexpp90/homebrew-photo-selector-toolbox/releases/download/nightly/photo-selector-toolbox-windows-x64.zip) |
| **macOS** | Apple Silicon (ARM64) | [Download ZIP](https://github.com/alexpp90/homebrew-photo-selector-toolbox/releases/download/nightly/photo-selector-toolbox-macos-apple-silicon.zip) |
| **Linux** | x64 | [Download ZIP](https://github.com/alexpp90/homebrew-photo-selector-toolbox/releases/download/nightly/photo-selector-toolbox-linux-x64.zip) |

---

### Android Installation

#### Option A: Obtainium (Direct Auto-Updates)
1. Install [Obtainium](https://github.com/ImranOmarRashid/Obtainium) on your Android device.
2. In Obtainium, click **Add App** and paste the URL of this repository.
3. Select your target release track (`stable` or `nightly`). Obtainium will download and install updates directly from GitHub Release assets with one click.

#### Option B: Firebase App Tester (OTA Previews)
Join our testing community to receive over-the-air previews via the **Firebase App Tester** application (available for invited developers/testers).

---

## 🛠️ Run from Source

### Desktop Setup (Python)
1. Ensure Python 3.10+ and Tkinter are installed:
   - **macOS:** `brew install python-tk`
   - **Linux:** `sudo apt install python3-tk`
2. Clone the repository and install dependencies using Poetry:
   ```bash
   git clone https://github.com/alexpp90/homebrew-photo-selector-toolbox.git
   cd homebrew-photo-selector-toolbox
   poetry install
   ```
3. Run the application:
   ```bash
   # Run GUI
   poetry run photo-selector-gui

   # Or use the CLI
   poetry run photo-selector-toolbox /path/to/photos [--output <dir>] [--show-plots] [--debug]
   ```

### Android Setup (Kotlin)
1. Open the `/android` directory inside **Android Studio Ladybug** (or newer).
2. Ensure JDK 17 and Android SDK 36 are configured.
3. Build the debug binaries:
   ```bash
   ./gradlew assembleDebug
   ```
   Debug APKs are output to:
   - `:app` (Toolbox): `android/app/build/outputs/apk/debug/`
   - `:phototok` (Photo Tok): `android/phototok/build/outputs/apk/debug/`

---

## 🧪 Testing

### Desktop Tests
Run unit and integration tests using pytest:
```bash
poetry run pytest
```
> [!NOTE]
> When executing GUI tests in headless Linux development environments, run pytest using `xvfb-run` to prevent display exceptions:
> `poetry run xvfb-run pytest`

### Android Tests
- **JVM Unit Tests:**
  ```bash
  ./gradlew testDebugUnitTest
  ```
- **Instrumented Emulator Tests:**
  ```bash
  ./gradlew connectedDebugAndroidTest
  ```

---

## 📄 License
This project is licensed under the terms of the [MIT License](LICENSE).
Third-party notices and licenses are detailed in [THIRDPARTY_NOTICES.txt](THIRDPARTY_NOTICES.txt).
