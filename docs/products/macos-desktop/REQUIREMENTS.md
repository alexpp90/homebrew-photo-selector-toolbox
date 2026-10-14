# macOS Desktop — Requirements

> Product: **macOS Desktop** (`products/macos-desktop/`, Swift 6 + SwiftUI).
> Scope: this file is authoritative for the native macOS desktop application. Requirements for the legacy Python desktop live in [`../desktop/REQUIREMENTS.md`](../desktop/REQUIREMENTS.md); Android requirements live under [`../android-desktop/`](../android-desktop/) and [`../phototok/`](../phototok/).
> Terminology: [`../../GLOSSARY.md`](../../GLOSSARY.md).

## 1. Introduction

The macOS Desktop application is a high-performance native photo culling and selection application built specifically for macOS 14+ and 15+ using Swift 6, SwiftUI, and Apple system frameworks. It is designed for frictionless, rapid SD-card photo ingestion, large-scale RAW/JPEG triage, and instant decision-making.

## 2. Core Features & Business Logic

### 2.1 Metadata Extraction & Supported Formats
* **[REQ-MAC-EXIF.01] ImageIO Standardized Reading:** Metadata is extracted by merging container-level properties (`CGImageSourceCopyProperties`) and image-level properties (`CGImageSourceCopyPropertiesAtIndex`), ensuring camera RAW formats (CR3, ARW, NEF) and JPEG/HEIC files expose complete optical metadata.
* **[REQ-MAC-EXIF.02] Optical Exposure Values:** Standardized extraction of `ShutterSpeed` (with APEX $T_v = -\log_2(t)$ fallback), `Aperture` (with APEX $A_v = 2\log_2(f)$ fallback), `ISO` (from `ISOSpeedRatings` array or `PhotographicSensitivity`), `FocalLength`, `LensModel`, and camera `Model`/`Make`.
* **[REQ-MAC-EXIF.03] Shutter Speed Formatting:** Shutter speeds < 1.0s and > 0s format as fractions (e.g. `1/250s`). Values $\ge 1.0s$ format with seconds (e.g. `1.5s`). Fallback and missing metadata are explicitly indicated without "Unknown" placeholders.
* **[REQ-MAC-EXIF.04] Supported File Types:** Native support for `.jpg`, `.jpeg`, `.png`, `.heic`, `.tiff`, `.tif`, and camera RAW formats (`.arw`, `.cr2`, `.cr3`, `.nef`, `.dng`, `.raf`, `.rw2`).
* **[REQ-MAC-EXIF.05] Companion File Discovery:** Automatic grouping of primary images with companion files: RAW+JPEG pairs, `.xmp` sidecar files, and Lightroom editing derivatives (`<stem>-Edit.*`).
* **[REQ-MAC-EXIF.06] Exclusion of Selection Directories:** Directory scanning automatically and strictly excludes subfolders named `Selection`, `Selected`, `PhotoTok_Selection`, and `PhotoTok_LeftSwipe` (case-insensitively), unless the folder is specifically selected as the root scan folder.

### 2.2 Native Image Quality Scoring
* **[REQ-MAC-SCORE.01] Apple Accelerate Focus Metric:** Hardware-accelerated focus score (0.0–100.0) computed using 2D Laplacian convolution (`vImageConvolve_PlanarF`) followed by spatial variance calculation (`vDSP.meanSquare`). Sensor noise floor is estimated via Median Absolute Deviation (MAD) and subtracted to prevent high-ISO sensor grain from artificially inflating sharpness scores. Compute SLA is $< 2.0\text{ ms}$ per 1080p frame in release mode.
* **[REQ-MAC-SCORE.02] Exposure Clipping Analysis:** Highlight clipping ($\ge 254$) and shadow clipping ($\le 2$) percentages are computed using hardware 256-bin `vImage` histograms.
* **[REQ-MAC-SCORE.03] Pure Apple Vision Aesthetics:** On-device aesthetic evaluation powered exclusively by `VNCalculateImageAestheticsScoresRequest` (macOS 15+ Neural Engine accelerated). No external model files, servers, Python bridges, or network connections required.

### 2.3 SD Card Ingestion & Culling Engine
* **[REQ-MAC-CULL.01] Non-Blocking Progressive Scanning — first photo first:** Files stream progressively into the workspace model without freezing the UI. The **first batch holds a single photo** and later batches ramp ×4 up to 30 (`ScanBatcher`), so the first photograph is on screen as soon as the first directory listing returns. Discovery lists each directory once (prefetched resource keys), groups RAW/JPEG/sidecar/edit companions in memory, sorts in natural order, and performs **no per-file EXIF read, realpath or companion directory listing** before photos are published. EXIF is enriched lazily off the main actor after the scan (batches of 64) and on demand for the displayed photo. Target: first photo drawn < 2 s after opening a folder (asserted by the UI smoke test).
* **[REQ-MAC-CULL.02] Loading Feedback & Visual Progress:** Every photo is displayed progressively: an instant 320 px placeholder (embedded thumbnail/preview when present) followed by the 2048 px preview; RAW previews prefer the embedded camera preview over a full RAW development; the filmstrip uses embedded thumbnails. Selecting a folder displays an immediate modal `FolderLoadingView` scanning HUD overlay (with animated indicators, loaded item count, and cancel button) when the library is initially empty, and streams a lightweight navigation bar progress pill as subsequent photo batches load.
* **[REQ-MAC-CULL.03] Actor-Isolated File Operations:** All disk mutations (`Move to Selection/`, `Copy to Selection/`, and `Trash`) execute asynchronously on `AsyncCullingActor`.
* **[REQ-MAC-CULL.04] Transactional Undo Safety:** The undo stack maintains transactional guarantees. Undoing an in-flight or completed copy operation removes only the destination replica, strictly guaranteeing zero data loss on original SD card or local files.
* **[REQ-MAC-CULL.05] Companion Synchronization:** Moving or trashing an image moves or trashes all associated companion files (RAW, JPEG, `.xmp`, Lightroom edits) atomically.

### 2.4 Duplicate Finder
* **[REQ-MAC-DUPE.01] Cryptographic Hashing:** Identifies identical duplicate images using streaming SHA-256 via `CryptoKit`. Files are compared by size first, then hashed.

### 2.5 Library Statistics Engine
* **[REQ-MAC-STATS.01] EXIF Distribution Analysis:** Aggregates distribution histograms for focal length, aperture, ISO, and shutter speed.

## 3. User Interface (UI/UX) Requirements

### 3.1 Studio Dark Mode & Readability Standards
* **[REQ-MAC-THEME.01] Enforced Dark Scheme:** The application strictly enforces `.preferredColorScheme(.dark)` across all main windows, settings panels, sheets, and modal views.
* **[REQ-MAC-THEME.02] High-Contrast Text Hierarchy:** Primary labels and headings use pure white (`Color.white` / `#FFFFFF`), and secondary descriptions use high-contrast light gray (`Color(white: 0.85)` / `#D9D9D9`). Black or muted dark text on dark gray backgrounds is strictly forbidden anywhere in the application.
* **[REQ-MAC-THEME.03] Studio Chrome Backgrounds:** Uses neutral studio dark surfaces (`#0B0B0D` canvas background, `#141417` and `#1C1C20` cards) with clean contrasting borders (`#3A3A42`) to highlight photograph colors accurately without peripheral chromatic fatigue.

### 3.2 Workspace Layout & Comparison Modes
* **[REQ-MAC-LAYOUT.01] Viewport Real Estate:** The main culling canvas maximizes preview area directly to image previews while providing persistent, tactile controls.
* **[REQ-MAC-LAYOUT.02] Comparison Modes:**
  - **1-Up (Single View):** Displays active candidate photo filling the maximum screen area.
  - **2-Up (Side-by-Side):** Side-by-side 50/50 horizontal split comparing two candidate photographs (Challenger vs Champion pairwise comparison).
  - **3-Up Focus Mode (Sliding Triplet) — one-over-two, NOT three columns:** the **current** candidate photo spans the **full canvas width on top** (60 % of the canvas height); below it sit the immediately preceding photo **bottom-left** (`currentIndex - 1`) and the immediately succeeding photo **bottom-right** (`currentIndex + 1`), two equal halves in chronological left-to-right order. Rationale: most photographs are landscape; three landscape frames in a row are width-bound and shrink the current photo to the size of its neighbours. Geometry comes exclusively from `FocusTripletLayout` (Kit, unit-tested); the view must not re-derive it. If the current photo is at the boundary of the album, dedicated `BoundarySlotPane` indicators ("First Photograph" / "Last Photograph") fill the bottom slot. The UI smoke test asserts the rendered frames (current above previous and next, previous left of next).
* **[REQ-MAC-LAYOUT.03] Sliding Navigation & Culling Semantics:**
  - In 3-Up Focus Mode, navigating via Left/Right arrow keys, Next/Prev buttons, or the filmstrip slides the entire triplet forward or backward across the album.
  - Culling actions (Move `M`, Copy `C`, Trash `Delete`) target the centered candidate photo and automatically slide the triplet forward to the next candidate.
* **[REQ-MAC-LAYOUT.04] Slot Roles & Visual Focus Indicators:** The current (top, in 3-Up) / active slot pane is bordered by camera viewfinder autofocus corner brackets in luminous cyan (`#38BDF8`) with an `ACTIVE / CURRENT` badge and full EXIF optical exposure strip; contextual adjacent slots display subtle dark borders and quick focus affordances.
* **[REQ-MAC-LAYOUT.05] Floating Metadata HUD:** Key EXIF data and quality scores are presented via lightweight, non-intrusive translucent overlays that do not consume structural layout margins.
* **[REQ-MAC-LAYOUT.06] Zero On-Screen Settings Clutter:** 0 persistent configuration sliders, drawers, or tuning inputs in the main culling workspace (100% R5 compliance).

### 3.3 Persistent Affordances & Tactile Controls
* **[REQ-MAC-UI.01] Top Control & Navigation Bar:**
  - `Open Folder… (⌘O)` button and current SD card / folder volume name.
  - Consolidated photo navigation cluster with position counter (`◀ Photo X of Y ▶`).
  - View mode segmented buttons: `[1-Up (1)]`, `[2-Up (2)]`, `[Focus 3-Up (3)]`.
  - Zero-truncation compact toggles: `[⌕ 1:1]` toggling to `[Fit]` (Space or Esc), `Filmstrip (F)`, `Info HUD (I)`, and `Guide [?]`.
* **[REQ-MAC-UI.02] Floating Glassmorphic Culling Action Dock:**
  - Floating HUD dock (`.ultraThinMaterial`, `RoundedRectangle(cornerRadius: 14)`, `#3A3A42` border) with embedded hotkey badges and Obsidian Studio semantic palette:
    - **Move to Selection (M)** (Subdued emerald `#082F1E` / `#10B981`)
    - **Copy to Selection (C)** (Subdued cobalt `#112240` / `#3B82F6`)
    - **Move to Trash (⌫)** (Subdued crimson `#3B0D11` / `#EF4444`)
    - **Undo (⌘Z)** (Neutral `#222228` / `#E4E4E7`)
  - Real-time progress counters HUD: `X Selected · Y Trashed · Z Remaining`.
  - Replaces redundant 28px bottom hotkeys strip to maximize photographic viewing canvas (>88% of screen).
* **[REQ-MAC-UI.03] Automated Workspace Snapshot:** Supports `--screenshot <path>` CLI launch option to capture and export a 1440x900 viewport PNG render of the active workspace after progressive loading for documentation and visual regression validation.

### 3.4 Keyboard Navigation & Shortcuts
* **[REQ-MAC-KEY.01] Zero-Latency Dispatch via `KeyboardShortcutRouter`:**
  - **Keyboard delivery precondition:** at launch the app forces activation policy `.regular` (`ForegroundActivation.ensureKeyboardCapable`), activates itself and makes the workspace window key. An unbundled SwiftPM binary otherwise starts as `.prohibited`, never becomes the active app, and receives **no key events at all** (the root cause of dead arrow keys while buttons still worked). `scripts/build_app.sh` assembles a proper `PhotoSelector.app`.
  - Sheets (Guide, Duplicate Finder, Statistics) own the keyboard while presented: the router ignores key events from a sheet or from a window presenting one.
  - Verified end-to-end by `scripts/ui_smoke_test.sh` (in-app `--ui-self-test`), which executes completely backgrounded off-screen without stealing window focus unless `PST_SHOW_GUI=1` is explicitly passed.
  - Registered via `NSEvent.addLocalMonitorForEvents(matching: .keyDown)` to intercept key events directly before AppKit first-responder or SwiftUI button-focus routing, preventing arrow keys from being trapped by UI buttons.
  - Supports standard keycodes (123 `←`, 124 `→`, 125 `↓`, 126 `↑`), NSEvent `specialKey` matching, Unicode function keys (`0xF700`–`0xF703`), and Vim keys (`H`/`L`, `J`/`K`).
  - `←` / `K` / `H`: Navigate previous candidate.
  - `→` / `J` / `L`: Navigate next candidate.
  - `M`: Move candidate (and companions) to `Selection/`.
  - `C`: Copy candidate (and companions) to `Selection/`.
  - `Delete` / `Backspace`: Move candidate (and companions) to system Trash.
  - `⌘Z`: Instant atomic undo.
  - `1`, `2`, `3`: Instant layout mode toggle (1-Up, 2-Up, Focus 3-Up).
  - `Tab`: Cycle comparison slot focus in 2-Up mode.
  - `Space`: 100% 1:1 zoom toggle.
  - `Escape`: Leave **any** zoom (100 % or pinch) back to Fit-to-Window; when nothing is zoomed, Escape is not consumed.
  - `S`: Toggle synchronized comparison pan lock (the zoom level itself is always shared).
  - `?` / `/`: Present interactive Shortcuts & Guide sheet.

### 3.4a Zoom
* **[REQ-MAC-ZOOM.01] Honest, escapable zoom:**
  - `100%` means one original image pixel per device pixel (`ZoomGeometry.oneToOneScale`, using the original pixel size and the display backing scale); while zoomed the full-resolution image is decoded and released again on return to Fit.
  - Trackpad pinch zooms continuously (capped at 8×); a pinch ending at ≤ 1.02× snaps back to Fit. Panning is clamped so the photo always covers the viewport.
  - Zoom applies in every mode (1-Up, 2-Up, Focus 3-Up); the zoom level is global and persists across navigation, the pan offset resets per photo.
  - **Every control keeps working while zoomed.** Gestures attach to the untransformed viewport (the scaled image does not hit-test), so toolbar, action bar, filmstrip and mode buttons stay clickable.
  - **Visible exits:** a floating pill (`Zoom 2.5× · Drag to pan · Fit (Esc)`) appears whenever any zoom is active; Escape, Space, double-click and the toolbar `Fit` button also return to Fit.

### 3.5 macOS Native Settings Scene (`⌘,`)
* **[REQ-MAC-SETTINGS.01] Native Settings Scene (`⌘,`):** All secondary application settings live in the native macOS Settings window (`⌘,`):
  - **General Tab**: Selection destination folder name, auto-advance on culling toggle, and sound feedback.
  - **Scoring & Aesthetics Tab**: Focus score threshold, aesthetic score cutoff, and exposure alert warnings.
  - **Performance & Cache Tab**: Memory cache limit and background prefetch window size.

### 3.6 Menu Bar "Tools" Menu
* **[REQ-MAC-MENU.01] Menu Bar "Tools" Menu:** Secondary utilities are decoupled from the main workspace and housed under the macOS menu bar:
  - **Duplicate Finder (`⌘D`)**: Opens standalone duplicate review studio.
  - **Library Statistics (`⌘L`)**: Opens distribution dashboard with metadata charts.
  - **Clear Cache**: Clears in-memory and thumbnail caches.

## 4. Distribution & Packaging

### 4.1 Homebrew Distribution
* **[REQ-MAC-BREW.01] Homebrew Cask Distribution:** The native macOS desktop application is distributed as the primary macOS desktop Cask (`photo-selector-toolbox`) via the project tap `alexpp90/photo-selector-toolbox` (or `alexpp90/tap`). It packages the compiled `PhotoSelector.app` bundle inside `photo-selector-macos-apple-silicon.zip` and installs it to `/Applications/PhotoSelector.app`.
* **[REQ-MAC-BREW.02] Coexistence with Legacy Desktop:** The native macOS Cask is fully isolated from the legacy Python/Tkinter Cask (`photo-selector-toolbox-legacy`), allowing both applications to be installed, launched, and upgraded side-by-side on the same Mac without bundle collisions, binary overwrite, or preferences corruption.
