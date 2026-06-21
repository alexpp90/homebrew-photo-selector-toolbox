<div align="center">
  <img src="assets/logo.png" alt="Photo Selector Toolbox Logo" width="160" />

  # Photo Selector Toolbox

  **Professional photo culling, analysis, and organization — right on your desktop.**

  [![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
  [![Python 3.10+](https://img.shields.io/badge/Python-3.10%2B-3776AB?logo=python&logoColor=white)](https://www.python.org/)
  [![Build](https://img.shields.io/github/actions/workflow/status/alexpp90/homebrew-photo-selector-toolbox/build.yml?branch=main&label=Build&logo=github)](https://github.com/alexpp90/homebrew-photo-selector-toolbox/actions)
  [![Latest Release](https://img.shields.io/github/v/release/alexpp90/homebrew-photo-selector-toolbox?label=Stable&logo=github)](https://github.com/alexpp90/homebrew-photo-selector-toolbox/releases/latest)
  [![Nightly](https://img.shields.io/github/release-date-pre/alexpp90/homebrew-photo-selector-toolbox?label=Nightly&logo=github)](https://github.com/alexpp90/homebrew-photo-selector-toolbox/releases/tag/nightly)
  [![macOS](https://img.shields.io/badge/macOS-000000?logo=apple&logoColor=white)](#-installation)
  [![Windows](https://img.shields.io/badge/Windows-0078D4?logo=windows&logoColor=white)](#-installation)
  [![Linux](https://img.shields.io/badge/Linux-FCC624?logo=linux&logoColor=black)](#-installation)

  <br />
  <img src="assets/banner.png" alt="Photo Selector Toolbox Dashboard Banner" width="800" style="border-radius: 12px; box-shadow: 0 4px 20px rgba(0,0,0,0.3);" />
</div>

---

## ✨ What is Photo Selector Toolbox?

Photo Selector Toolbox is a **cross-platform desktop application** for photographers who need to efficiently cull, analyze, and organize large photo libraries. It extracts EXIF metadata, calculates sharpness and noise scores, detects duplicates, and helps you keep only your best shots — all without uploading a single image to the cloud.

> **Zero cloud. Zero subscriptions. Your photos stay on your machine.**

---

## 🎯 Key Features

### 📸 Photo Selector & Review
- **Side-by-side comparison** — View previous, current, and next shots simultaneously
- **Fullscreen review** — Inspect images at full resolution with metadata overlay
- **Keyboard-driven workflow** — Navigate, select, move, copy, and delete with shortcuts
- **Move or Copy to Selection** — Organize picks into `Selection/` subfolders, automatically sorting RAW and JPEG files

### 🔬 Image Quality Analysis
- **Sharpness scoring** — Center-cropped 8×8 block variance grid analysis
- **Noise estimation** — Median Absolute Deviation (MAD) of the Laplacian filter
- **Highlight clipping** — Percentage of blown highlights (≥ 254 intensity)
- **Shadow clipping** — Percentage of crushed shadows (≤ 2 intensity)
- **AI aesthetic scoring** — Local Ollama VLM integration for compositional analysis (no cloud required)

### 📊 EXIF Metadata Statistics
- **Distribution charts** — Shutter Speed, Aperture, ISO, Focal Length, Lens Model
- **Dark-themed Matplotlib plots** — Seamlessly integrated into the app's visual design
- **Interactive dashboard** — Overview cards and tabbed chart navigation

### 🔍 Duplicate Finder
- **SHA256 content hashing** — Finds true duplicates regardless of filename
- **Safe deletion** — Moves to trash first, with fallback to permanent delete

### ⚙️ Additional Capabilities
- **RAW format support** — `.ARW`, `.NEF`, `.CR2`, `.DNG`, and more (via bundled ExifTool)
- **Network path resolution** — Seamlessly resolves `smb://` share URLs to local mount points
- **Dynamic file type filtering** — Focus your review on specific formats (JPEG, RAW, or all)
- **Persistent score cache** — SQLite-backed cache so you never recalculate scores
- **Multi-threaded processing** — Parallel metadata extraction and background analysis
- **Professional dark theme** — Zinc-based dark UI designed for prolonged editing sessions

---

## 📥 Installation

### Homebrew (macOS — Recommended)

The easiest way to install on macOS. Choose between **stable** and **nightly** builds:

<table>
<tr>
<th>Stable Release</th>
<th>Nightly Build (Latest Features)</th>
</tr>
<tr>
<td>

```bash
brew tap alexpp90/photo-selector-toolbox
brew install --cask photo-selector-toolbox
```

</td>
<td>

```bash
brew tap alexpp90/photo-selector-toolbox
brew install --cask photo-selector-toolbox@nightly
```

</td>
</tr>
<tr>
<td>

```bash
# Upgrade
brew upgrade --cask photo-selector-toolbox
```

</td>
<td>

```bash
# Upgrade (requires --greedy for unversioned casks)
brew upgrade --cask --greedy photo-selector-toolbox@nightly
```

</td>
</tr>
</table>

**One-liner install** (stable, interactive):

```bash
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/alexpp90/homebrew-photo-selector-toolbox/main/scripts/install-mac.sh)"
```

---

### Standalone Executable (Windows, Linux & macOS)

Pre-built binaries are available for every platform — no Python installation required.

| Platform | Download |
|:---------|:---------|
| **Windows** (x64) | [📦 Download ZIP](https://github.com/alexpp90/homebrew-photo-selector-toolbox/releases/download/nightly/photo-selector-toolbox-windows-x64.zip) |
| **macOS** (Apple Silicon) | [📦 Download ZIP](https://github.com/alexpp90/homebrew-photo-selector-toolbox/releases/download/nightly/photo-selector-toolbox-macos-apple-silicon.zip) |
| **Linux** (x64) | [📦 Download ZIP](https://github.com/alexpp90/homebrew-photo-selector-toolbox/releases/download/nightly/photo-selector-toolbox-linux-x64.zip) |

> **Stable releases** are available on the [Releases](https://github.com/alexpp90/homebrew-photo-selector-toolbox/releases) page.

**Getting started:**

1. Download and extract the ZIP for your platform
2. Launch the application:
   - **GUI** — Double-click `photo-selector-gui` (or `Photo Selector Toolbox.app` on macOS)
   - **CLI** — Run `./photo-selector-toolbox` from a terminal

The standalone build comes with **ExifTool bundled** — no additional dependencies needed.

---

### Run from Source

For contributors and advanced users:

```bash
# Clone the repository
git clone https://github.com/alexpp90/homebrew-photo-selector-toolbox.git
cd homebrew-photo-selector-toolbox

# Install dependencies via Poetry
poetry install

# Launch the GUI
poetry run photo-selector-gui

# Or use the CLI
poetry run photo-selector-toolbox /path/to/photos [--output <dir>] [--show-plots] [--debug]
```

**System requirements:**
- Python 3.10+
- Tkinter (`brew install python-tk` on macOS, `sudo apt install python3-tk` on Linux)
- [ExifTool](https://exiftool.org) (recommended for RAW support; auto-detected from PATH)

---

## ⬇️ Accessing Older Releases

All previous versions are preserved indefinitely on GitHub.

<details>
<summary><strong>Standalone Executables</strong></summary>

1. Visit the [GitHub Releases](https://github.com/alexpp90/homebrew-photo-selector-toolbox/releases) page
2. Scroll to your desired version tag (e.g., `v0.1.0`)
3. Download the ZIP for your platform from the **Assets** section

</details>

<details>
<summary><strong>Homebrew Cask (macOS)</strong></summary>

To install a specific historical version:

1. Find the commit hash where `Casks/photo-selector-toolbox.rb` was updated for that version in the [tap repository](https://github.com/alexpp90/homebrew-photo-selector-toolbox) history
2. Install using the raw Cask URL:
   ```bash
   brew install --cask https://raw.githubusercontent.com/alexpp90/homebrew-photo-selector-toolbox/<COMMIT_HASH>/Casks/photo-selector-toolbox.rb
   ```

</details>

---

## 🛠️ Development

### Building Locally

```bash
# Build the standalone executable (downloads ExifTool automatically)
poetry run python scripts/build.py
```

Output is placed in the `dist/` directory.

### Running Tests

```bash
poetry run pytest
```

> **Headless environments:** Use `xvfb-run` for GUI tests without a display:
> ```bash
> poetry run xvfb-run pytest
> ```

---

## 📄 License

This project is licensed under the [MIT License](LICENSE).

---

## 🙏 Acknowledgments

Photo Selector Toolbox is built on the shoulders of excellent open-source software:

| Library | License |
|:--------|:--------|
| [ExifTool](https://exiftool.org/) by Phil Harvey | Artistic License |
| [PyExifTool](https://github.com/sylikc/pyexiftool) | BSD |
| [Pillow](https://python-pillow.org/) | HPND |
| [Matplotlib](https://matplotlib.org/) | BSD-compatible |
| [OpenCV](https://opencv.org/) | Apache 2.0 |
| [tqdm](https://tqdm.github.io/) | MIT / MPL |
| [ExifRead](https://github.com/ianare/exif-py) | BSD |

See [THIRDPARTY_NOTICES.txt](THIRDPARTY_NOTICES.txt) for full license details.
