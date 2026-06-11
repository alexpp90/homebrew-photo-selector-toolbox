# Photo Selector Toolbox

This tool analyzes image metadata (EXIF) from a given root folder, including all subfolders. It provides statistics and generates graphs for:
- Shutter Speed
- Aperture (F-Number)
- ISO
- Focal Length
- Lens Model

It also includes advanced utilities for culling photos:
- **Photo Selector**:
  - **Sharpness & Noise Analysis**: Evaluates image sharpness (using a center-cropped block variance grid) and estimates noise (using Median Absolute Deviation).
  - **Highlight & Shadow Clipping**: Automatically identifies percentage of blown highlights and crushed shadows.
  - **Multi-Image & Fullscreen View**: View the active image side-by-side with its neighbors or inspect it in fullscreen.
  - **Selection Organizing**: Move selected files (automatically separating RAW and JPEG files into their respective subfolders) or delete them safely via the trash bin.
  - **Dynamic File Type Filtering**: Focus your review by filtering by specific image formats (e.g. only JPEG or RAW files).
- **Duplicate Finder**: Identifies duplicate images by content (SHA256 hash) with safe deletion options.
- **Path Resolution Utility**: Resolves network share paths (`smb://`) to local mount points automatically.

---


## Installation & Usage

### Option 1: Homebrew (macOS — Recommended)

You can choose between the stable release (recommended for general use) and the nightly build (for the absolute latest features).

#### Stable Release
```bash
# Tap the repository
brew tap alexpp90/photo-selector-toolbox

# Install the stable Cask
brew install --cask photo-selector-toolbox

# To upgrade the stable release later:
brew upgrade --cask photo-selector-toolbox
```

#### Nightly Build (Latest Features)
```bash
# Tap the repository
brew tap alexpp90/photo-selector-toolbox

# Install the nightly Cask
brew install --cask photo-selector-toolbox@nightly

# To upgrade the nightly build later (requires --greedy because it is unversioned):
brew upgrade --cask --greedy photo-selector-toolbox@nightly
```

Alternatively, run the interactive installer script (installs the stable release by default):
```bash
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/alexpp90/homebrew-photo-selector-toolbox/main/scripts/install-mac.sh)"
```

---

### Option 2: Standalone Executable (Windows & Linux)

#### Stable Release
Go to the [Releases](https://github.com/alexpp90/homebrew-photo-selector-toolbox/releases) page to download the latest stable version.

#### Nightly Build (Latest Features)
For the absolute latest version built automatically from the `main` branch:

[![Build Date](https://img.shields.io/github/release-date/alexpp90/homebrew-photo-selector-toolbox/nightly?label=Last%20Build)](https://github.com/alexpp90/homebrew-photo-selector-toolbox/releases/tag/nightly)

**Download:**
*   **Windows (x64)**: [Download ZIP](https://github.com/alexpp90/homebrew-photo-selector-toolbox/releases/download/nightly/photo-selector-toolbox-windows-x64.zip)
*   **macOS (Apple Silicon)**: [Download ZIP](https://github.com/alexpp90/homebrew-photo-selector-toolbox/releases/download/nightly/photo-selector-toolbox-macos-apple-silicon.zip) (Use Homebrew Cask instead unless installing manually)
*   **Linux (x64)**: [Download ZIP](https://github.com/alexpp90/homebrew-photo-selector-toolbox/releases/download/nightly/photo-selector-toolbox-linux-x64.zip)

**Instructions:**
1. Download and extract the ZIP file.
2. Run the executable:
   - **GUI**: Double-click `photo-selector-gui` (or `photo-selector-gui.app` on macOS).
   - **CLI**: Run `./photo-selector-toolbox` from the terminal.

The standalone executable comes with `exiftool` bundled, so you don't need to install anything else.

---

### Accessing Older Releases

All previous versions and release histories are kept indefinitely on GitHub.

#### Standalone Executable (All Platforms)
To download a standalone executable for an older version:
1. Visit the [GitHub Releases](https://github.com/alexpp90/homebrew-photo-selector-toolbox/releases) page.
2. Scroll to locate your desired version tag (e.g., `v0.1.0`).
3. Under the **Assets** section of that release, download the ZIP file for your platform (Windows, macOS, or Linux).

#### Homebrew Cask (macOS)
Homebrew installs the latest stable version by default. If you need to install a specific historical version, you can install it directly using the Cask raw file URL from the tap repository's git history:
1. Find the commit hash associated with the version you want in the [homebrew-photo-selector-toolbox](https://github.com/alexpp90/homebrew-photo-selector-toolbox) repository's history (specifically when `Casks/photo-selector-toolbox.rb` was updated for that version).
2. Install it by passing the raw URL of that Cask version to `brew install`:
   ```bash
   brew install --cask https://raw.githubusercontent.com/alexpp90/homebrew-photo-selector-toolbox/<COMMIT_HASH>/Casks/photo-selector-toolbox.rb
   ```
   *(Replace `<COMMIT_HASH>` with the actual commit hash, e.g., the commit where that version's Cask was committed).*

---

### Option 3: Run from Source

1. Clone the repository.
2. Install dependencies using [Poetry](https://python-poetry.org/):

   ```bash
   poetry install
   ```

3. **System Requirements**:
   - **Python 3.10+**
   - **Tkinter**: Required for the GUI.
     - Linux: `sudo apt-get install python3-tk`
     - macOS: `brew install python-tk`
   - **ExifTool**: Recommended for RAW file support. Install it via your package manager or download from [exiftool.org](https://exiftool.org). The app will automatically find it if it's in your PATH.

4. Run the application:

   ```bash
   # GUI
   poetry run photo-selector-gui

   # CLI (Extract metadata and generate plots)
   poetry run photo-selector-toolbox /path/to/photos [--output <output_dir>] [--show-plots] [--debug]
   ```

---

## Features

- **No external dependencies** required for the standalone build.
- **Cross-platform**: Runs on Windows, Linux, and macOS.
- **RAW Support**: Handles common RAW formats (`.ARW`, `.NEF`, `.CR2`, etc.) using `exiftool` (bundled in the executable).
- **Parallel Analysis**: Uses multi-threaded extraction to speed up metadata processing.
- **Photo Selector Tool**: Analyze sharpness, noise levels, and highlight/shadow clipping. Compare files side-by-side or fullscreen, apply dynamic format filters, and move/delete selections cleanly.
- **Network Path Resolution**: Seamlessly resolves `smb://` URLs to macOS/Linux local mount points.
- **Duplicate Finder**: Identifies duplicate images by size and SHA256 checksum with robust deletion fallback safety.

---

## Development

### Building the Executable Locally

You can use the provided Python script to build the standalone executable. This script automatically downloads the correct `exiftool` binary for your platform and bundles it.

```bash
poetry run python scripts/build.py
```

The executables will be placed in the `dist/` folder.

### Running Tests

```bash
poetry run pytest
```

---

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

## Third-Party Credits

This application uses open-source software:

- **ExifTool** by Phil Harvey (Artistic License). [https://exiftool.org/](https://exiftool.org/)
- **PyExifTool** (BSD License).
- **Pillow** (HPND License).
- **Matplotlib** (BSD compatible).
- **tqdm** (MIT/MPL).
- **ExifRead** (BSD License).

See [THIRDPARTY_NOTICES.txt](THIRDPARTY_NOTICES.txt) for full license details.
