# Photo Selector Toolbox

This tool analyzes image metadata (EXIF) from a given root folder, including all subfolders. It provides statistics and generates graphs for:
- Shutter Speed
- Aperture (F-Number)
- ISO
- Focal Length
- Lens Model

It also includes advanced utilities for culling photos:
- **Blurry Image Finder**: Detects out-of-focus or motion-blurred shots.
- **Duplicate Finder**: Finds identical image files by SHA256 content hashing.

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

   # CLI
   poetry run photo-selector-toolbox /path/to/photos
   ```

---

## Features

- **No external dependencies** required for the standalone build.
- **Cross-platform**: Runs on Windows, Linux, and macOS.
- **RAW Support**: Handles common RAW formats (.ARW, .NEF, .CR2, etc.) using `exiftool` (bundled in the executable).
- **Fast Analysis**: Uses optimized metadata extraction.
- **Blurry Image Finder**: Automatically detects blurry images using Laplacian Variance analysis, helping you cull low-quality shots.
- **Duplicate Finder**: Identifies duplicate images by file size and content (SHA256 hash), with safety features to prevent accidental deletion of all copies.

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
