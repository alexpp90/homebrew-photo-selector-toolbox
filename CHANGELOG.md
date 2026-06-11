# Changelog

All notable changes to the Photo Selector Toolbox project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Upcoming]

- Refactored `OllamaAestheticTool` to return a tuple containing the float score and a parsed one or two-word analysis reason tag.
- Added a calibration scale to the default Ollama prompt to reduce score randomness.
- Updated prompt migration logic to migrate legacy default prompts to the new default calibrated prompt.
- Updated Standard Mode metadata panel, Focus Mode panel, and Fullscreen Viewer metadata overlay to display the aesthetic tag in parentheses next to the score.

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
