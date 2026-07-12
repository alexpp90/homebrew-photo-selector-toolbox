# Changelog

All notable changes to the Photo Selector Toolbox project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Changed & Fixed
- **Photo-Tok: removed the dedicated Google Drive integration in favor of SAF (Storage Access Framework):**
  - Cloud folders (including Google Drive) are now selected through the standard system folder picker, whose document providers expose cloud storage directly — verified working on-device. One generic code path covers local, SD-card, and cloud sources.
  - Deleted the entire Drive stack: Google Sign-In/auth, Drive REST client, Drive image source and Coil fetcher, the WebView-hosted Google Picker, picked-selection persistence (`gdrive-picked://` URIs), and the `SourceUris`/`ImageItem.isRemote` scheme classification.
  - Dropped the `play-services-auth` / `kotlinx-coroutines-play-services` dependencies, the Picker `BuildConfig` credentials (`PHOTOTOK_PICKER_API_KEY`, `PHOTOTOK_GCP_PROJECT_NUMBER`), the corresponding CI verification step, and the `INTERNET` permission — the app performs no network I/O of its own.
  - Removed the Drive-only "Trash Confirmation" dialog and setting; all deletions use the existing Direct Delete Confirmation flow with the revert window.
- **Photo-Tok Google Drive: switched to the non-restricted `drive.file` scope (Play-Store readiness):**
  - The app no longer requests the restricted full `drive` scope, removing the need for Google restricted-scope verification and the annual CASA security assessment when publishing to Google Play.
  - The in-app Drive folder browser (`DriveFolderPickerDialog`/`DriveFolderPickerViewModel`) was removed — under `drive.file` the app cannot browse the user's Drive. Users now grant access by multi-selecting photos in the WebView-hosted **Google Picker** (`DrivePhotoPickerDialog` + `DrivePickerViewModel`); folder selection is disabled because a folder grant does not extend to its children under `drive.file`.
  - Picked photo sets are persisted as named selections (`DrivePickedStore`/`DrivePickedCodec`, capped at 10) and addressed as `gdrive-picked://<key>` source URIs, so they work with recents and last-folder restore. Re-opening a selection intersects the stored IDs with a flat `listAccessibleImages()` listing.
  - Collection copy/move on picked sources defaults to an app-created `PhotoTok` folder in the Drive root; Drive moves now resolve the file's real parents via the API (previously the destination folder was wrongly assumed to be the old parent) and fall back to copy + trash when a move is rejected.
  - Picker credentials come from `BuildConfig` (`PHOTOTOK_PICKER_API_KEY`, `PHOTOTOK_GCP_PROJECT_NUMBER` env vars); without them, Drive photo picking is disabled with an explanatory error instead of a crash.
- **Photo-Tok Play-Store compliance groundwork:**
  - Added bilingual privacy policy and Impressum pages (`docs/phototok/*.html`, personal-data placeholders to fill in) plus a step-by-step release checklist with Data Safety form answers (`docs/phototok/PLAY_RELEASE_CHECKLIST.md`).
  - Settings screen now links "Privacy Policy" and "Legal Notice (Impressum)" (URLs centralized in `com.phototok.domain.LegalLinks`), satisfying Play's in-app privacy-policy requirement and § 5 DDG.
- **Photo-Tok Architecture Refactor:**
  - Introduced an `ImageSource` abstraction (local SAF + Google Drive) with a single resolver; all URI-scheme dispatch now lives in the data layer and `ImageRepository` no longer takes `Context` parameters. Cross-source copy/move is rejected explicitly.
  - Split `PhoneModeViewModel`: the read-only selection viewer moved to `SelectionViewerViewModel` (folder traversal moved into the local image source), and the Drive folder picker to `DriveFolderPickerViewModel`; Compose no longer receives `GoogleDriveClient`/`GoogleDriveAuth` objects (sign-in state is part of the UI state).
  - Replaced the fragile 12/13-flow positional `combine` of settings with a single typed `PhoneSettings` flow mapped from one DataStore snapshot (both feed and settings ViewModels).
  - Copy/move actions now track per-file results: partial failures are reported ("Moved 1 of 2 …, 1 failed") and only successfully moved files leave the feed.
  - Image dimensions are applied to the UI state in batches instead of per image, bounding recompositions in folders with thousands of photos.
  - Per-folder last positions are now keyed by the full URI (collision-free; legacy hash keys read as fallback and cleaned up) and pruned when a folder falls out of the recents list.
  - Google Drive client: all HTTP verbs (including downloads and uploads) share one authorized-request helper with a single token-refresh retry on 401; recursive folder listings run concurrently (bounded); corrected the upload documentation (multipart, not resumable).
- **Photo-Tok Reliability Refactor:**
  - Fixed a bug where sibling files (e.g. `.ARW` next to `.JPG`) of a pending deletion were not removed from disk when the app was closed during the undo window; pending deletions now finalize on an application-scoped coroutine so they survive screen exit.
  - Fixed a coroutine leak where re-selecting a source folder started a new image-discovery collector without cancelling the previous one.
  - Failed file copies no longer leave zero-byte placeholder files in the destination folder.
  - Google Drive: user folder names are now escaped in Drive queries (names containing `'` no longer break folder lookup); HTTP 401 responses trigger a one-time token refresh and retry; the Drive download cache is capped at 512 MB with LRU eviction.
  - Replaced raw setting strings ("delete"/"copy"/"move", "all"/"raw"/"jpg") with typed enums (`SwipeAction`, `CollectionAction`, `FileTypeFilter`); DataStore wire format is unchanged.
  - Extracted pending-delete list transitions (`PendingDeleteLogic`), feed filtering, and portrait-split computation into the pure `domain` layer with unit-test coverage; deduplicated RAW/JPEG extension sets (`PhotoExtensions`).
- **CI:** Unit-test reports are now uploaded for both Android modules (previously `:phototok` reports were dropped); added an advisory Android Lint job; the Android build gate now fails when no test run is found; `versionName` can be overridden from CI via environment variables.
- **Repo Hygiene:** Removed committed scratch/debug files and report dumps; moved stray root benchmarks into `benchmarks/`; added `CLAUDE.md`/`GEMINI.md` agent stubs and a REQUIREMENTS.md drift-check workflow.

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
