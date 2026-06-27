# Changelog

All notable changes to the Photo Selector Toolbox project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.4.0] - 2026-06-27

### Added
- **Visual Regression Tests:** Added screenshot capture and baseline pixel-matching regression test suite for main desktop GUI panels.
- **Keyboard Shortcut Discoverability:** Appended keyboard shortcut hints directly onto action buttons (e.g., Copy, Move, Delete) in the Tkinter GUI and fullscreen viewer.

### Changed & Fixed
- **Google Play & Firebase deployment:** Updated Google Play action and service account format, ensuring robust automatic publishing of AABs for both the main app and Photo-Tok.
- **Android Cloud Project Isolation:** Isolated Google Cloud / Firebase projects, signing keys, and secrets between the main app (`:app`) and Photo-Tok (`:phototok`).
- **Keyboard Accessibility:** Configured explicit focus states mapping for `TCheckbutton` in the Tkinter clam theme to restore keyboard navigation visibility.
- **Robust CI Pipelines:** Optimized wait-on-check preflight gating to prevent CI blockages and added manual build-android triggers.

## [0.3.0] - 2026-06-27

### Added
- **Android Platform & UI Improvements:**
  - Added sample mockup card slideshow layout to the Phone Mode landing screen using custom landscape, portrait, and architectural photography assets for enhanced design aesthetics.
  - Configured explicit Web Client ID in Google Drive authentication to support multiple package names/SHA-1 signatures.
  - Integrated launcher foreground logos into navigation components, top bar, and hero cards.
  - Added full-screen image loading indicators and orientation-responsive safety padding for image viewing.
- **Distribution & CI/CD Pipelines:**
  - Integrated Google Play Store publishing to automate uploading AABs of both apps to the `internal` testing track.
  - Documented over-the-air installation procedures (via Obtainium direct from GitHub Releases, or via Firebase App Tester app) in the project documentation.

### Changed & Fixed
- Hidden the bottom navigation bar when on the landing/sources screen to prevent empty navigation actions.

## [0.2.0] - 2026-06-21

### Added
- **Android Platform & Google Drive Integration:**
  - Full Google Drive file browsing, silent sign-in sync, and folder picking on both Android Phone (`phototok`) and Tablet/Desktop (`app`) clients.
  - Custom `gdrive://` URI schema implementation in Coil (`DriveCoilFetcher`) to handle background downloading and caching.
  - Support for discovering RAW files (e.g., `.CR2`, `.NEF`) on Google Drive by fallback mapping of `application/octet-stream` MIME types with supported image extensions.
  - Detailed error stream diagnostics logging for Google Drive REST API calls (debugging 403 Forbidden and other network issues).
  - Native 16KB page size alignment compatibility on Android 16+ devices, using NDK 28's precompiled `libc++_shared.so` binaries and restricting default ABI filters to 64-bit architectures (`arm64-v8a` and `x86_64`).
- **Desktop Features & Distribution:**
  - Three configurable grouping levels (Time & Filename, Time + Fast Similarity, Detailed Similarity) with a persistent GUI selector.
  - Calibrated Ollama VLM prompt structure and aesthetic reason tag extraction display.
  - Homebrew Formula support for stable and nightly CLI-only builds (`photo-selector-toolbox` and `photo-selector-toolbox@nightly`) for macOS and Linux.
  - Added Linux installation shell script `scripts/install-linux.sh`.

### Changed & Optimized
- Optimized visual similarity grouping by restricting dHash checks to temporal burst candidates (reducing image loading by up to 90% in large folders).
- Optimized raw image preview loading on desktop using embedded JPEG camera thumbnail extraction.
- Automatic filtering to exclude hidden files and macOS Apple Double metadata files (starting with `.`) during both local and Google Drive image discovery.
- Automated version and checksum updates in GitHub Actions build workflows for Homebrew tap files.

---

## [0.1.0] - 2026-06-07

### Added
- **Photo Selector Tool**:
  - Center-cropped 8x8 block variance grid sharpness analysis.
  - Noise analysis estimation via Median Absolute Deviation (MAD) of the Laplacian filter.
  - Blown highlights ($\ge 254$ intensity) and crushed shadows ($\le 2$ intensity) percentage evaluation.
  - Multi-image layout comparison showing standard and focus mode triplets.
  - Fullscreen viewer with dynamic metadata overlay, delete with confirm, and move-to-selection functionality.
  - Dynamic file type filtering for candidates (RAW vs. JPEG vs. All Supported).
  - Parallel background analysis scanner with logging progress tab.
- **Duplicate Finder**:
  - Content-based SHA256 duplicate detection.
  - Safe trashing system (using `send2trash` with fallback warning to permanent deletion).
- **Metadata Analyzer**:
  - EXIF extraction using unified `ExifData` schema (Shutter Speed, Aperture, ISO, Focal Length, Lens Model).
  - EXIF statistics visualization and plotting (excluding values $\le 0.0$ mm for focal length).
- **Path Resolution Utility**:
  - Network share path resolver (`smb://` URLs) to local mount points for macOS and Linux.
- **Packaging and Distribution**:
  - Dual-track Homebrew Tap cask support (`photo-selector-toolbox` and `photo-selector-toolbox@nightly`).
  - Standalone executable bundling with bundled `exiftool` binary.
  - Multi-platform CI build matrix (Linux x64, macOS Apple Silicon, Windows x64).
  - Multi-platform CI build matrix (Linux x64, macOS Apple Silicon, Windows x64).
