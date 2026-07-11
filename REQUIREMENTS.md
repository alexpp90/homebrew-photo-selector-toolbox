# Photo Selector Toolbox - Requirements Documentation

This document serves as the central source of truth for the functional, architectural, and business requirements of the Photo Selector Toolbox project. It must be kept up-to-date as new features are added or existing features are modified.

## 1. Introduction
The Photo Selector Toolbox is a cross-platform desktop application designed to process folders of images. It extracts and analyzes EXIF metadata (Shutter Speed, Aperture, ISO, Focal Length, Lens Model), generates statistical distributions, and provides advanced tools such as a Photo Selector (for selecting and deleting images, with optional sharpness and noise analysis) and a Duplicate Finder.

## 2. Core Features & Business Logic

### 2.1 Metadata Extraction and Analysis
*   **EXIF Data Standardization:** Extracted EXIF data must standardize on explicit keys: `Aperture` and `Shutter Speed`. Downstream consumers (e.g., the GUI) must rely on these standardized keys, avoiding raw tags like `FNumber` or `ExposureTime`.
*   **Focal Length Exclusions:** Statistical aggregations and plotting for Focal Length must explicitly exclude values less than or equal to `0.0 mm` to prevent division-by-zero errors.
*   **Shutter Speed Formatting:** In the GUI, shutter speed values less than `1.0` and greater than `0` must be formatted as fractions (e.g., `1/200s`). Values `1.0` or greater must be appended with `s` (e.g., `1.5s`).
*   **Supported File Types:** Supported file extensions are defined centrally in `src/photo_selector_toolbox/reader.py` as `SUPPORTED_EXTENSIONS`. Other tools (like `duplicates.py`) must import this list and may extend it locally (e.g., appending `.bmp` and `.gif`).
*   **Subfolder Scanning Exclusions:** Recursive directory scanning in all tools (Metadata Analyzer, Photo Selector, and Duplicate Finder) must automatically exclude subfolders named `Selection` or `Selected` (case-insensitively), preventing previously selected images from being re-scanned, unless the `Selection` or `Selected` directory is specifically selected as the root scan folder.

### 2.2 Photo Selector (Sharpness & Noise Tool)
*   **Sharpness Algorithm:** The sharpness analysis algorithm crops the center 50% of the image, divides it into a configurable grid (default 8x8), and uses the maximum block variance score to determine overall sharpness.
*   **Noise Algorithm:** A noise analysis tool estimates image noise using the Median Absolute Deviation (MAD) of the image's Laplacian filter.
*   **Highlight Clipping Algorithm:** The highlight clipping analysis tool estimates the percentage of blown highlights by converting the image to grayscale and calculating the percentage of pixels with an intensity value $\ge 254$.
*   **Shadow Clipping Algorithm:** The shadow clipping analysis tool estimates the percentage of crushed shadows by converting the image to grayscale and calculating the percentage of pixels with an intensity value $\le 2$.
*   **Synchronous Pre-loading:** The application must synchronously pre-load all supported images from a selected folder for immediate side-by-side review on folder selection (without requiring a scan), displaying metadata initially. Scores that have not been calculated must be omitted entirely from the candidate listbox, thumbnail details, main metadata labels, and focus overlays (completely avoiding 'N/A' placeholder text). Only after a score is calculated should it be shown. The candidate listbox must be configured with a horizontal scrollbar, and calculated metrics inside this listing must be shown compactly using only their icons and values (e.g., "🎯 42.5" instead of "Sharpness: 42.5").
*   **Score Labeling:** The evaluation metrics must be explicitly labeled as "Sharpness Score", "Noise Level" (or "Noise"), "Highlight Clipping", and "Shadow Clipping" in the main GUI details/labels (rather than just "Score"). All score labels must harmonize and use the standard theme/system default text foreground color rather than hardcoded foreground colors to ensure readability on varying background themes.
*   **Optimized Image Grouping:** The application provides a "Group Similar Series" option with three configurable levels in the settings/UI:
    *   *Time & Filename:* Instant grouping of consecutive files sorted alphabetically if name prefixes match and modification time difference is $\le 30.0$ seconds (no image loading/hashing needed).
    *   *Time + Fast Similarity:* Groups consecutive files if modification time difference is $\le 30.0$ seconds, name prefixes match, and 8x8 dHash visual similarity Hamming distance is $\le 10$.
    *   *Detailed Similarity:* Groups consecutive files if modification time difference is $\le 30.0$ seconds, name prefixes match, and detailed 16x16 dHash visual similarity Hamming distance is $\le 24$.
    To optimize performance, dHash calculations are performed only for temporal candidate files (images shot within 30 seconds of a neighbor), preventing unnecessary decoding of up to 90% of non-candidate files. Both the grouping state and selected level are persistent across application launches.
    Visual similarity grouping calculations are run asynchronously in a background thread and are non-blocking. Progress is displayed in a dedicated grouping progress bar and status panel in the sidebar, with an option to cancel/abort. Aborting/cancelling reverts the grouping configuration to the last applied successful state. Grouping controls are disabled during active calculations or scans to prevent conflicting threads.

### 2.3 Path Resolution Utility
*   **SMB URL Support:** The application must support resolving `smb://` URLs to local mount points.
*   **Linux Resolution:** On Linux, `smb://server/share/path` must resolve to `/run/user/<uid>/gvfs/smb-share:server=<server>,share=<share>/<path>`.
*   **macOS Resolution:** On macOS, `smb://server/share/path` must resolve to `/Volumes/<share>/<path>`.
*   **Fallback:** On other platforms (e.g., Windows), or for malformed SMB URLs, the path should be returned as a standard `Path` object without transformation.
*   **URL Decoding:** The resolver must correctly unquote URL-encoded characters (e.g., `%20` to spaces) during the resolution process.

### 2.4 Duplicate Finder
*   **Detection Logic:** Duplicate image detection utilizes SHA256 hashing of file content.
*   **Deletion Strategy:** The utility first attempts to move files to the trash using `send2trash`.
*   **Deletion Error Handling:** If `send2trash` fails (e.g., on network drives), the backend utility (`move_to_trash`) must raise an exception rather than returning a boolean status. The GUI layer is responsible for catching this exception and prompting the user for a fallback to permanent deletion (`Path.unlink()`).

### 2.5 Local AI Aesthetic Evaluation (Ollama VLM)
*   **Ollama REST API Querying:** The application must query a locally running Ollama server via its standard HTTP REST API (defaulting to `http://localhost:11434/api/generate`) using Python's standard library `urllib` to avoid introducing external library dependencies.
*   **Settings Persistence:** User preferences for the Ollama URL, model name, and prompt must be stored in a config JSON file located at `~/.photo_selector_toolbox/settings.json`.
*   **Payload Optimization:** To ensure high performance and minimize transmission payloads, the image must be converted to RGB, resized to a maximum boundary of `400x400` pixels, and encoded as a base64 JPEG before being sent to the Ollama endpoint.
*   **Robust Score & Tag Parsing:** The tool must parse the first valid floating-point number found in the Ollama response text and map it to a scale of `1.0` to `10.0` as the aesthetic score, and parse a one or two-word description tag in the format `[ANALYSIS: tag]` (falling back to `"N/A"` if not present). If no numeric score can be parsed, or if a connection timeout/error occurs, the tool must raise a descriptive `RuntimeError` to be cleanly logged in the GUI's scan status logs. When the tag is available, the GUI must display it in parentheses next to the score.
*   **Aesthetic Rating Calibration & Prompting:** The default Ollama prompt must employ an expert persona and enforce Chain-of-Thought (reasoning-first) step-by-step evaluation (Subject/Intent, Technical Quality, Composition/Lighting) before concluding with the numerical score and description tag to ensure maximum reliability. The prompt must define a calibrated rating scale: Extremely Poor (1.0 - 2.5), Average (3.0 - 5.5), Good (6.0 - 8.0), and Outstanding (8.5 - 10.0). It must include strict negative guidelines specifying that completely uniform or blank images (e.g., a plain grey sky, solid wall, floor, or lens cap) must be penalized and rated extremely poor (1.0 to 1.5) due to lack of a subject and composition.
*   **Interactive Diagnostic Support:** The GUI configuration dialog must provide an interactive "Test Connection" command that checks server availability, queries the `/api/tags` endpoint, and alerts the user if the selected model is not pulled, with diagnostic instructions.

### 2.6 Persistent Score Cache
*   **Persistent SQLite Cache:** The application must maintain a persistent SQLite database cache at `~/.photo_selector_toolbox/scores_cache.db` to store and restore calculated analysis values (Sharpness, Noise, Highlight/Shadow Clipping, and Aesthetic Scores).
*   **MRU Limit:** The cache must be automatically pruned to hold only the most recently used 10,000 images, ordered by access time.
*   **No Automatic Scanning on Load:** When a folder is opened, the application must immediately restore any cached values and load the images as quickly as possible for immediate review, without automatically initiating background scans or calculations.
*   **Skip Calculation:** Manual scans must check the cache first and skip executing any analysis tools whose values have already been successfully calculated for the target file.
*   **Cache Deletion / Clearing:** The application must provide a way to delete all cached scores via a menu action in the "Tools" menu (labeled "Clear Cached Scores..."). When clicked, this action must prompt the user with a confirmation dialog, wipe all entries from the persistent SQLite database, and immediately clear all loaded/calculated scores in the GUI's memory (updating the candidate listbox, metadata labels, and focus mode overlays in real-time).

## 3. User Interface (GUI) Requirements

### 3.1 Layout and Rendering Constraints
*   **Responsive Scaling:** To prevent infinite resize loops, image labels that dynamically scale must be wrapped in `ttk.Frame` containers using `pack_propagate(False)` and `grid_propagate(False)`.
*   **Grid Weights:** Parent frames containing these constrained child frames must have explicit `rowconfigure` and `columnconfigure` weights assigned so the frame does not collapse and hide its contents.
*   **Resize Optimization:** Image resizing via `<Configure>` events must be optimized by caching `_last_width` and `_last_height` to prevent redundant processing, and the events must be debounced to stop continuous loops.
*   **Unscaled References:** The application must explicitly store the raw, unscaled `pil_image` to support high-quality responsive resizing when window adjustments occur.
*   **Preloader Cache:** The background image preloader cache size (`CACHE_SIZE`) must be strictly set to 1200x900 to guarantee high-resolution images for responsive UI scaling.
*   **Menu Bar Navigation:** To ensure the tools do not take up space in standard view, the sidebar is removed. Navigation between the tools (Photo Selector, Image Library Statistics, and Duplicate Finder) is handled via the top menu bar's "Tools" menu, next to a "Help" menu.
*   **Dialog Layout and Centering:** To ensure proper readability and consistent user experience, all modal/toplevel dialogs (Scan Settings, Ollama Aesthetic Settings, Collection Settings, About Dialog, and Delete Confirmation) must be centered relative to their parent window using root coordinates (`winfo_rootx()`, `winfo_rooty()`, `winfo_width()`, and `winfo_height()`). Their sizes must be computed dynamically using `update_idletasks()` and size constraints (minimum height/width rules). Dialogs must bind the `<Escape>` key to safely close/dismiss the window.

### 3.2 Display Modes (Sharpness Tool)
*   **Standard Mode Layout:** The current image is centered in the top row, with previous/next images side-by-side in the bottom row.
*   **Focus Mode Layout:** Uses a grid layout where the top row is split evenly (weight=1 for both columns) between the current image and controls panel. The bottom row splits the previous and next images evenly, ensuring all three displayed images share the exact same dimensions.
*   **Image Reloading:** Switching views (Standard vs. Focus) triggers a reload of the image triplet to ensure the correct resolution is generated for the specific layout.
*   **Navigation Interactions:** In Focus Mode, clicking a non-central image (Previous/Next) must open it in a fullscreen viewer (via `tk.Toplevel`) instead of making it the new current image.
*   **Fullscreen Viewer:**
    *   **Delete Option:** The fullscreen viewer must include a visible "Delete (Delete)" button and bind the `<Delete>` and `<BackSpace>` keys to prompt a delete confirmation dialog (transient to the fullscreen window). A second press of `<Delete>` or `<BackSpace>` confirms deletion. On confirmation, the image is moved to the trash (or permanently deleted if moving to trash fails), removed from list structures, and the viewer advances to the next image (or closes if no images remain).
    *   **Move to Selection:** The fullscreen viewer must include a "Move to Selection (M)" button and bind `<m>` and `<M>` to move the current image and related files to the selection destination folder. The destination folder path/name is configurable (stored in settings, defaulting to `"Selection"`). If the configured path is relative, it is resolved relative to the scanned folder; if absolute, it is used directly.
    *   **Copy to Selection:** The fullscreen viewer must include a "Copy to Selection (C)" button and bind `<c>` and `<C>` to copy the current image and related files to the selection destination folder. It follows the same configurability and path resolution rules as "Move to Selection". Original files are kept in the candidate list, but the active selection advances to the next candidate image.
    *   **Collection Subfolder Sorting and Lightroom Edit Grouping:** Within the selection destination folder, sorting can be optionally configured (enabled by default). When sorting is enabled, RAW files must be sorted into a `RAW` subfolder, JPEG/JPG files into a `JPEG` subfolder, `.xmp` sidecar files into the same subfolder as their corresponding image file, and other files into the root of the destination folder. In addition, Lightroom editing files (e.g. `<stem>-Edit.*` files) and their sidecars must also be sorted into the `RAW` subfolder. If sorting is disabled, all files are moved or copied directly to the root of the destination folder.
    *   **Parent Synchronization:** Navigating or deleting images in Fullscreen Viewer must update and synchronize the parent window's active selection.
    *   **Focus Mode Persistence:** If entering Fullscreen from Focus Mode, exiting the viewer (via Escape or Close button) must return the application to Focus Mode.
    *   **Metadata and Calculated Values Display:** When viewing an image in fullscreen, the viewer must display a clean, non-intrusive metadata panel overlay in the bottom-left corner. This panel must display the file name, standardized EXIF metadata (ISO, Shutter Speed, Aperture, Focal Length), Lens Model, and all active/calculated quality scores (Sharpness Score, Noise Level, Highlight Clipping, Shadow Clipping, and Aesthetic Score). Uncalculated scores must be entirely omitted from this display (completely avoiding "N/A" placeholders), and the panel must update dynamically as the user navigates through images.
*   **Keyboard Controls (Review & Focus Modes):**
    *   **Shortcut Collision Avoidance:** Key event handlers (Escape, Left, Right, Delete, BackSpace) in the main window must ignore events occurring within other Toplevel windows (like `FullscreenViewer` or confirmation dialogs) to prevent unexpected layout transitions and shortcut interference.
    *   **Escape (`<Escape>`):** Exits Focus mode. Does nothing in Standard mode.
    *   **Left Arrow (`<Left>`):** Navigates to the previous image.
    *   **Right Arrow (`<Right>`):** Navigates to the next image.
    *   **Delete (`<Delete>` and `<BackSpace>`):** Triggers moving the current image to the trash, prompting a confirmation dialogue. A second press of `<Delete>` or `<BackSpace>` confirms the deletion (acts as an alternative to clicking "Yes" or pressing Enter).

### 3.3 State and Interaction Management
*   **Thread Safety:** PIL Image objects must be loaded in background threads. Unscaled PIL images are returned and dynamically converted to `ImageTk.PhotoImage` in the main thread during `<Configure>` events. Image analysis scanning (sharpness, noise, clipping) runs on a background `ThreadPoolExecutor` to prevent duplicate GUI window spawning and resource conflicts on macOS.
*   **Selection Debouncing:** Image triplet loading must be debounced by 100ms on selection change to avoid redundant disk and CPU work during rapid scrolling.
*   **Stale Load Discards:** Background loaded images must be verified against the current panel selection paths before rendering. If the paths do not match, the stale result must be discarded.
*   **Asynchronous EXIF Loading:** EXIF data retrieval must run in a background thread if not already cached, updating the UI upon completion to avoid blocking the main thread (tests/mocks may run synchronously).
*   **Asynchronous Deletion:** File deletion in the Photo Selector must immediately remove items from candidates and listbox controls on the main thread to make deletion feel instantaneous, while the trashing process runs asynchronously in a background thread.
*   **Tkinter Variable Access:** Access to Tkinter variables (`StringVar`, `IntVar`) must occur only in the main thread. Values must be passed as arguments to worker threads.
*   **Data Formatting Safety:** UI elements processing dynamically loaded scores must utilize explicit type checks (e.g., `isinstance(score_val, float)`) to safely format numerical data and prevent errors from 'N/A' string defaults.
*   **State Transitions:** The Photo Selector starts directly on the main preview and review screen. Selecting a folder loads the images immediately without scanning. An optional sharpness/noise analysis scan is offered via a modal configuration dialog, and progress is logged in a separate tab without locking the main photo review interface.
*   **Error Reporting:** Analysis errors in the GUI must be displayed via a popup alert, and the progress bar state must reflect the failure (it must not auto-complete).
*   **Image RAW Loading:** The utility function `utils.load_image_preview` must explicitly convert images to `RGB` mode to handle 16-bit RAW data (`I;16`) that otherwise causes `ImageTk` crashes. Furthermore, any PIL Image created from a rawpy postprocessed numpy array must be copied (e.g., via `.copy()`) before the `rawpy.imread` context closes to prevent dangling references to recycled LibRaw memory buffers, which leads to visual artifacts and image mixing.
*   **Linux Modern File Selector (Zenity):** On Linux platforms, if `zenity` is available in the system PATH, the application must prioritize it to open a native directory selection dialog using `zenity --file-selection --directory` with custom titles and persistency of the initial directory (formatted with a trailing slash). Modality is handled by temporarily disabling and afterwards restoring the parent window's disabled state. If `zenity` is missing, returns a non-zero exit code (excluding 1, which represents user cancellation), or throws an exception, the application must fall back cleanly to `filedialog.askdirectory`.

### 3.4 Visual Aesthetics and Dark Theme System
*   **Application Dark Theme:** The application enforces a professional dark theme built on top of the `clam` style system. The base background is set to `#18181B` (Zinc-900), frame/card containers to `#27272A` (Zinc-800), active buttons/highlights to `#6366F1` (Indigo-500) with hover highlights of `#4F46E5` (Indigo-600) to match the logo colors, and foreground text to `#FAFAFA` (Zinc-50). Unicode emojis/icons are used adjacent to button text and major headers to improve usability and match the repository banner layout.
*   **Custom Widget Configuration:** Native Tkinter widgets (like `tk.Listbox`, `tk.Text`, and custom `tk.Toplevel` dialog frames) must be manually styled with matching background, foreground, selection, and border colors to preserve a seamless interface theme.
*   **Dynamic Visual Placeholders:** Missing previews, loading states, and empty panels must not display plain text labels. They must utilize a dynamically drawn PIL gradient image showing a camera outline icon and anti-aliased status text to fill the container.
*   **Image Statistics Dashboard Overview:** The Image Library Statistics interface must provide an interactive "Overview" notebook tab displaying instructional dashboard cards when the app is first launched.
*   **Matplotlib Theme Integration:** Figures rendered inside the statistics plots must dynamically map tick marks, labels, text elements, background panels, and distribution bars to the application's dark theme palette.
*   **Duplicate Finder Empty State:** The Duplicate Finder scrollable list must display a clear empty-state card when no duplicate scanning is active or completed.
*   **Custom About Dialog:** The Help -> About menu option must launch a custom-styled, dark-themed `tk.Toplevel` modal showing the high-resolution logo (resized dynamically via PIL), application title, version number, feature highlights, and MIT license credits, completely matching the application's visual zinc-900 dark theme.

## 4. Technical & Architectural Requirements

### 4.1 Required Dependencies
*   **Runtime:** `opencv-python` and `rawpy` must be declared as main project dependencies (not development dependencies) to ensure they are bundled correctly by PyInstaller for runtime sharpness analysis.
*   **Type Hinting:** Development environments require `types-setuptools`, `types-tqdm`, `types-ExifRead`, `types-Pillow`, `types-requests`, and `types-Send2Trash` to pass Mypy static analysis.
*   **Backward Compatibility:** `reader.py` must use a `hasattr` check and type ignore for `_getexif` to support older Pillow versions while satisfying strict Mypy checks.

### 4.2 Build Scripts
*   **Splash Screen:** The application startup includes a PyInstaller splash screen (`assets/logo.png`) configured via the PyInstaller `--splash` argument (on supported platforms like Windows/Linux). In addition, to support immediate startup feedback (especially on macOS where PyInstaller splash is unsupported, and in development), `MainApp` displays a custom, dark-themed Tkinter splash screen displaying the application logo, title, loading status text, and a modern indeterminate progress bar. The PyInstaller splash is dismissed immediately upon showing the Tkinter splash, and the Tkinter splash is destroyed after GUI initialization completes, before deiconifying the main window.
*   **Application Naming:** The built GUI desktop application must be named exactly `Photo Selector Toolbox` (yielding `Photo Selector Toolbox.app` on macOS).
*   **macOS Icon:** The build script must generate and bundle a macOS-compatible `logo.icns` file from primary assets and configure PyInstaller to apply it to the macOS application bundle.
*   **ExifTool Bundling:** The build script (`scripts/build.py`) relies on a hardcoded ExifTool version (e.g., 13.51) downloaded from SourceForge. This requires manual updates if SourceForge removes older releases. Windows builds must utilize the `_64` suffixed ExifTool binary.
*   **macOS Security:** The build script must apply ad-hoc code signing (`codesign -s -`) to the macOS `.app` bundle to prevent Gatekeeper from flagging it as damaged on Apple Silicon.

### 4.3 Architectural Patterns
*   **Typed EXIF Data Models:** Raw `dict` formats for metadata are deprecated. All code must utilize the typed `ExifData` dataclass (defined in `models.py`) to access EXIF values.
*   **Analysis Tool Registry:** Adding new analysis tools must use the dynamic `ToolRegistry` pattern defined in `tools.py`. Tools must inherit from `AnalysisTool` and register themselves.
*   **EXIF Reader Strategy Pattern:** Metadata extraction is organized into strategies (`ExifReader` implementations in the `readers/` subpackage). The main entry point `reader.get_exif_data` delegates extraction to these strategies sequentially.
*   **GUI Decomposition:** High-responsibility GUI classes are decoupled: `FullscreenViewer` is separated into `fullscreen_viewer.py`, and panel-resize / background-loading mixin logic resides in `image_panels.py` to keep GUI layout classes clean.
*   **Unified Logging:** Debug and execution printing must go through the standard `logging` library. The GUI redirects log messages using a custom `QueueHandler` registered to the root logger during scanning sessions.

## 5. Build & Deployment (CI/CD)

### 5.1 GitHub Actions Workflow
*   **Matrix Strategy:** The build workflow uses an `architecture` matrix strategy to explicitly define `x64` or `arm64` Python environments.
*   **Runner Requirements:** Windows builds target the `windows-latest` runner (x64). MacOS builds must strictly target `macos-latest` (Apple Silicon/ARM64); legacy Intel and macOS v15+ specific builds are deprecated.
*   **Archive Creation:** The build workflow must use `zip -r -y` on Unix systems (to preserve symlinks) and `shutil.make_archive` on Windows for creating release archives.
*   **Artifact Naming:** Artifacts must be named exactly as follows:
    *   `photo-selector-toolbox-linux-x64`
    *   `photo-selector-toolbox-windows-x64`
    *   `photo-selector-toolbox-macos-apple-silicon`

### 5.2 Release Management
*   **Permissions:** GitHub Actions require `permissions: contents: write` to allow the workflow to create and update releases.
*   **Publishing Strategy:** The project utilizes `softprops/action-gh-release` to automatically publish artifacts (both desktop archives and Android release APK/AAB) to the `nightly` tag on every push to the `main` branch, and to the stable release tag on every tag push matching `v*`.
*   **Changelog and Upcoming Changes:** All notable changes must be documented in a central `CHANGELOG.md` file in the root of the project using the Keep a Changelog format. Changes implemented in development between releases must be documented under an `[Upcoming]` section.
*   **GitHub Release Preservation:** Every tagged stable release (tags matching `v*`) must be published as a new GitHub Release and preserved indefinitely. No old releases or release assets may be pruned or deleted from the GitHub Releases page.
*   **Older Version Access Documentation:** The `README.md` must maintain up-to-date instructions on how to access and install older versions of the standalone executables and the Homebrew Casks.

### 5.3 Homebrew Distribution (macOS & Linux)
*   **Tap Repository:** The project maintains a single-repo Homebrew Tap at `alexpp90/photo-selector-toolbox` containing Cask definitions (macOS GUI) and Formula definitions (Linux GUI/CLI and macOS CLI) for both stable releases and nightly builds.
*   **Release Selection:**
    *   **macOS GUI (Cask):** Users can install the stable release (`brew install --cask photo-selector-toolbox`) or nightly build (`brew install --cask photo-selector-toolbox@nightly`).
    *   **Linux / macOS CLI (Formula):** Users can install the stable release (`brew install photo-selector-toolbox`) or nightly build (`brew install photo-selector-toolbox@nightly`).
*   **Auto-Update:** The CI workflow automatically updates both Cask and Formula configurations using a Python utility script `scripts/update_formula.py`:
    *   For nightly builds (on pushes to `main`), the nightly Cask and Formula SHA256 hashes are updated.
    *   For stable releases (on tag pushes matching `v*`), the stable Cask and Formula versions and SHA256 hashes are updated.
*   **Install Artifacts:**
    *   The macOS Cask installs the `Photo Selector Toolbox.app` bundle to `/Applications` and symlinks the CLI binary to Homebrew's bin directory.
    *   The macOS Formula installs the CLI binary `photo-selector-toolbox` to Homebrew's bin directory.
    *   The Linux Formula installs both the CLI binary `photo-selector-toolbox` and the GUI binary `photo-selector-toolbox-gui` to Homebrew's bin directory.

## 6. Testing Requirements (Desktop)

*   **Path Resolution Tests:** The `resolve_path` utility must be tested across simulated platforms (Linux, macOS, Windows) using mocking for `sys.platform` and `os.getuid`.
*   **GUI Unit Tests:** Tests validating GUI components (e.g., `tests/test_sharpness_gui_basic.py`) require extensive mocking of `tkinter`, `PIL`, and `photo_selector_toolbox` dependencies due to the lack of a display environment in CI runners.
*   **Headless Execution:** To run or test standalone Tkinter GUI scripts headlessly in the development environment, developers must use `xvfb-run` (e.g., `poetry run xvfb-run python3 script.py`) to avoid `_tkinter.TclError` exceptions.
*   **Execution Command:** Tests should be executed using `poetry run pytest tests/` after ensuring dependencies are installed via `poetry install`.

## 7. Android Application Requirements

### 7.1 Platform Targets & Tech Stack
*   **Primary Targets:** Samsung Galaxy Tab S11 Ultra (tablet), Samsung Galaxy S25 Ultra with DeX (desktop mode). The application must provide a feature-rich experience on large screens (≥840dp) that approaches desktop parity.
*   **Secondary Target:** Phone form factor (<600dp) with a streamlined UI optimized for touch-first interaction.
*   **Language & Framework:** Kotlin 2.0+ with Jetpack Compose and Material Design 3.
*   **Architecture:** MVVM with Clean Architecture (UI → ViewModel → UseCase → Repository → DataSource). All layers are separated by interfaces.
*   **Async Model:** Kotlin Coroutines with Flow for reactive data streams. Use `Dispatchers.IO` for file I/O, `Dispatchers.Default` for CPU-intensive analysis. Never block the main thread.
*   **Dependency Injection:** Hilt for all injectable classes.
*   **Android SDK:** minSdk 26, targetSdk 36, compileSdk 36.

### 7.2 Adaptive Layout Requirements
*   **Window Size Classes:** Every screen must support three `WindowWidthSizeClass` breakpoints:
    *   **Compact (<600dp):** Single-pane layouts, BottomNavigation, simplified controls, swipe-based navigation.
    *   **Medium (600–840dp) and Expanded (≥840dp):** NavigationRail, widescreen horizontal three-column side-by-side comparison layout showing the Previous, Current, and Next images in equal dimensions with filenames, compact EXIF metadata, quality scores, and actions (Move, Copy, Delete under the Current active image).
*   **Samsung DeX:** Treat as Expanded window size class. Support resizable windows. Enable hardware keyboard shortcuts (arrow keys, Delete, Backspace, M, C, Escape) in both standard and fullscreen modes.
*   **Edge-to-Edge:** All screens must use `enableEdgeToEdge()` and properly handle `WindowInsets` for system bar padding.
*   **DeX Metadata & Layout Limits:** The manifest must include `com.samsung.android.keepalive.density` metadata and `resizeableActivity="true"`. To optimize desktop window launching (e.g. on 10-inch Chromebooks or DeX), explicit layout size limits (default width 1024dp, default height 768dp, min width 800dp, min height 600dp) are configured.

### 7.3 Feature Set (Android)
The Android app includes all desktop features except:
*   **Excluded:** Local AI Aesthetic Evaluation (Ollama VLM) — battery drain and insufficient compute on mobile.
*   **Excluded:** CLI interface — not applicable on Android.
*   **Excluded:** Homebrew distribution — uses APK/Play Store instead.
*   **Excluded:** ExifTool bundling — replaced by AndroidX ExifInterface.
*   **Excluded:** SMB path resolution — Android handles network shares via SAF providers.

Phone mode may additionally omit:
*   Focus Mode / side-by-side comparison view (not practical on small screens).
*   Detailed EXIF metadata panel (accessible via tap-to-expand on phone).

Phone Mode (Phone Tok) includes:
*   **Picture Randomization:** A toggle in settings allows users to randomize the order of pictures shown in the viewer. Enabling this setting shuffles the loaded pictures and overrides orientation-based sorting.

### 7.4 Touch Interaction Patterns
*   **Photo Navigation:**
    - **Tablet / Large Screens:** Tap on prev/next thumbnails or use side controls.
    - **Phone Client (PhotoTok):** Vertical swipe via `VerticalPager` (up/down) to navigate between images in the feed, and horizontal swipe gestures for keep/discard.
*   **Fullscreen Viewer / Image Zoom:**
    - **Tablet / Desktop Fullscreen:** Pinch-to-zoom, double-tap to toggle fit/100%, swipe-down to dismiss, horizontal swipe to navigate.
    - **Phone Client (PhotoTok):**
      - Pinch-to-zoom (two fingers) and double-tap zoom (to 2.5x) are supported.
      - When zoomed in (`scale > 1.05f`), dragging with a single finger pans/moves the image frame, constrained within image boundaries so it does not go off-screen.
      - While zoomed, normal swipe gestures (vertical paging scroll and horizontal keep/discard swipes) are disabled to prevent conflicts.
      - Resetting zoom (double-tapping again, or pinch-releasing below the `1.05f` threshold) smoothly animates the scale and offset back to fit-screen size and re-enables swiping mode.
*   **Selection/Deletion:** Long-press for context menu. Swipe gestures configurable. On the phone client (Phototok), swiping left defaults to a non-blocking temporary deletion where the image is immediately hidden from the viewer and a "Revert Deletion" button is shown. Navigating away from the current image or leaving the viewer finalizes the deletion and deletes the file from disk/Google Drive. Before putting the image into the temporary deletion state, a confirmation dialogue is shown:
    - If trashing is supported (Google Drive files), the **Trash Confirmation** dialog is shown. It includes a "Do not show this dialogue again" checkbox, which toggles the **Trash Confirmation** setting.
    - If trashing is not supported (local files via SAF), the **Direct Delete Confirmation** dialog is shown. It does not have a checkbox in the dialog, but hints that it can be disabled in settings. In settings, this **Direct Delete Confirmation** option is offered with a warning indication.
    - In both cases, the revert option is preserved immediately after confirmation.
    Alternatively, in settings, the user can configure a different behavior for the left swipe: Copy to Custom Folder or Move to Custom Folder, matching the behavior of the right swipe. When configured as Copy or Move, the operation is executed immediately (showing a success feedback message and folder icon flash animation) and targets a user-defined folder. If no custom folder is selected, it defaults to a subfolder named `"PhotoTok_LeftSwipe"` inside the source folder.
    For other clients or operations, delete uses Snackbar with Undo (30s) or confirmation dialogs.
*   **Minimum Touch Target:** 48dp for all interactive elements.
*   **Gesture Tutorial Overlay:** When the full-screen gesture tutorial overlay is visible, the top app bar (containing the logo and settings button) and the bottom navigation bar must be hidden to prevent layout overlapping with the overlay text and layout obscuring of the "Got it" button at the bottom. Additionally, the overlay container must apply appropriate status bar and navigation bar padding to prevent clipping by system decorations.
*   **Context Menus:** Long-press activated. No hover-dependent interactions.
*   **Desktop/Input Enhancements**: Mouse pointer cursors automatically display hand pointer shapes (`PointerIcon.Hand`) when hovering over interactive components (buttons, clickable images, list thumbnails). Folder drag-and-drop capability is supported, enabling users to drag a photo folder from external file managers directly into the app window to load it automatically.

### 7.5 Image Analysis (Algorithm Parity)
All analysis algorithms must produce equivalent results to the desktop Python implementation:
*   **Sharpness:** Center 50% crop → 8×8 grid → max Laplacian variance (OpenCV Android: `Imgproc.Laplacian` with `CV_64F`).
*   **Noise:** MAD of Laplacian filter: σ = median(|∇²I - median(∇²I)|) / 0.6745.
*   **Highlight Clipping:** Percentage of grayscale pixels ≥ 254.
*   **Shadow Clipping:** Percentage of grayscale pixels ≤ 2.
*   **Duplicate Detection:** SHA-256 file content hashing via streaming `DigestInputStream`.
*   **Image Grouping:** Three levels matching desktop: Time & Filename, Time + Fast Similarity (8×8 dHash, Hamming ≤ 10), Detailed Similarity (16×16 dHash, Hamming ≤ 24).

### 7.6 EXIF Extraction (Android)
*   **Primary Reader:** AndroidX ExifInterface — supports JPEG, DNG, CR2, NEF, ARW, RAF, ORF, RW2, PEF, SRW, WebP, HEIF. Employs `ParcelFileDescriptor` for local files to enable random seek access, falling back to sequential stream reads when PFD is unavailable.
*   **Fallback Reader:** MediaStore columns for basic metadata when ExifInterface fails.
*   **Standardized Keys:** Output uses the same standardized `ExifData` data class as desktop (shutter speed, aperture, focal length, focal length 35mm, ISO, lens, isFallback).
*   **Asynchronous Loading:** EXIF data for the current image and its immediate neighbors (previous/next) must be loaded dynamically and asynchronously in the background as the user navigates, updating the UI reactively on completion.

### 7.7 Storage & File Access
*   **Primary Access:** Storage Access Framework (SAF) via `Intent.ACTION_OPEN_DOCUMENT_TREE`. To comply with Google Play permissions policies, the application declares no global storage permissions (such as `MANAGE_EXTERNAL_STORAGE` or `READ_EXTERNAL_STORAGE`) in the manifest.
*   **Permission Persistence:** URI permissions are persisted across restarts using `ContentResolver.takePersistableUriPermission`. ViewModels must handle `SecurityException` gracefully and clear any persisted folder URI if access has been revoked or the directory is deleted.
*   **File Discovery:** `DocumentFile` API for folder traversal. Same exclusion rules as desktop: skip "Selection" and "Selected" subfolders (case-insensitive) unless specifically selected as root.
*   **Selection Destination:** Configurable subfolder name (default "Selection"). RAW/JPEG/XMP sorting into subfolders follows desktop logic. Lightroom edit files (*-Edit.*) sorted to RAW subfolder.
*   **Deletion:** Use `MediaStore.createTrashRequest()` on Android 11+ for recoverable deletion. Fall back to `DocumentFile.delete()`.

### 7.8 Persistent Cache (Android)
*   **Room Database:** SQLite cache at app-internal storage storing sharpness, noise, highlight/shadow clipping scores.
*   **MRU Limit:** 10,000 entries, pruned by last access time (matching desktop).
*   **Cache-First Loading:** On folder open, restore cached values immediately from Room DB. Scores are pre-populated into `ImageItem.scanResult` on discovery, validated against file size and modification time. Manual scans skip already-cached entries.
*   **Clear Cache:** Available in Settings screen with confirmation dialog.

### 7.9 Performance & Battery
*   **Thread Pool:** `minOf(4, availableProcessors)` — reduced from desktop's `min(8, cpuCount + 4)` for battery conservation. Both the default value and the settings UI slider enforce a maximum of 4.
*   **Parallel Image Scanning:** Multiple images are analyzed concurrently using a `Semaphore`-gated coroutine pool (sized by the thread count setting). Within each image, sharpness, noise, and clipping analysis run in parallel via `async`/`await`. Clipping analysis (highlight + shadow) uses a combined single-pass method to avoid duplicate bitmap→Mat and grayscale conversions.
*   **Image Decoding:** Use `BitmapFactory.Options.inSampleSize` or `ImageDecoder` for memory-efficient downsampled decode. Hardware bitmaps for display, software bitmaps for analysis. Forced decoding in `ARGB_8888` for analysis to avoid pixel quantization that truncates bright highlights and dark shadows.
*   **Battery Awareness (Deferred):** Reduce worker threads when battery < 20%. Pause scan and notify when battery < 10%. Use `WorkManager` with `Constraints.Builder().setRequiresBatteryNotLow(true)` for background scans. *Status: WorkManager dependency is declared but feature is not yet implemented.*
*   **Memory Management:** Coil handles display image caching. Analysis images decoded at max 2048px on longest edge using `ImageDecoder` on API >= 28 (supporting RAW formats) and `BitmapFactory` on older APIs. SHA-256 uses streaming reads. Mat objects released immediately after use. EXIF data cache uses bounded LRU eviction (max 50 entries). dHash values are cached per grouping pass to avoid redundant bitmap decoding.

### 7.10 Android Build & CI
*   **Build System:** Gradle with Kotlin DSL and version catalog (`gradle/libs.versions.toml`).
*   **CI Workflow:** `.github/workflows/build-android.yml` — runs on `ubuntu-latest` with JDK 17. Triggers on pushes to `main`, tags matching `v*`, and PRs modifying `android/**`. Runs lint and unit tests before building. Produces signed APKs and AABs for both the `:app` (Photo Selector Toolbox) and `:phototok` (Photo Tok) modules.
*   **Version Code Generation & Offsets:** Version codes in CI are computed as `github.run_number * 100 + github.run_attempt` (set into `VERSION_CODE` by the "Compute version code" step in `build-android.yml`). Including the run attempt makes version codes re-run-safe: re-running a failed workflow reuses the run number, which previously produced Google Play "Version code N has already been used" errors when an earlier attempt had already published. Codes remain monotonically increasing across runs (run 78 attempt 1 → 7801, run 79 attempt 1 → 7901). To resolve any remaining collisions, version codes can be adjusted using repository variables or environment variables: `VERSION_CODE_OFFSET` (global offset applied to both apps), `TOOLBOX_VERSION_CODE_OFFSET` (app-specific offset), and `PHOTOTOK_VERSION_CODE_OFFSET` (phototok-specific offset). These are added to the base version code.
*   **Artifact Naming:**
    *   **Photo Selector Toolbox (`:app`):** `photo-selector-toolbox-android-release.apk` and `photo-selector-toolbox-android-release.aab`.
    *   **Photo Tok (`:phototok`):** `phototok-android-release.apk` and `phototok-android-release.aab`.
*   **R8 Optimization:** Release builds enable R8 full mode with minification and resource shrinking. OpenCV native libraries excluded from stripping.
*   **ABI Filter:** Release APKs include only `arm64-v8a` (covers Galaxy S25 Ultra and Tab S11 Ultra).
*   **Cloud Project Isolation:** Each app is fully isolated — its own Google Cloud / Firebase project, OAuth consent screen + Android OAuth client, signing key, service accounts, and CI secrets. Nothing is shared between `:app` and `:phototok`. This allows the apps to diverge independently (different API access, scopes, release cadence, ownership) without coupling. See `ANDROID_CLOUD_SETUP.md` for the full rationale and `ANDROID_CLOUD_SETUP_INSTRUCTIONS.md` for the step-by-step setup.
*   **Signing:** Debug uses default keystore. Each app has its **own** release upload key, configured in `build.gradle.kts` via app-scoped environment variables: `:app` reads `TOOLBOX_KEYSTORE_FILE`, `TOOLBOX_STORE_PASSWORD`, `TOOLBOX_KEY_ALIAS`, `TOOLBOX_KEY_PASSWORD`; `:phototok` reads the `PHOTOTOK_*` equivalents. In GitHub Actions CI, each keystore is decoded from its own base64 secret (`TOOLBOX_KEYSTORE_FILE` / `PHOTOTOK_KEYSTORE_FILE`) into a temporary file before compilation.
*   **Firebase App Distribution:** Release builds pushed to the `main` branch are uploaded to each app's own Firebase project (in its own GCP project) for over-the-air tester updates, each with its own service-account credential secret:
    *   **Photo Selector Toolbox:** App ID from the `photo-selector-tb-dist` project, supplied via the `TOOLBOX_FIREBASE_APP_ID` repository variable, credential `TOOLBOX_FIREBASE_APP_DISTRIBUTION_CREDENTIALS`.
    *   **Photo Tok:** App ID from the dedicated `phototok-app` project, supplied via the `PHOTOTOK_FIREBASE_APP_ID` repository variable, credential `PHOTOTOK_FIREBASE_APP_DISTRIBUTION_CREDENTIALS`.
    Both distribution pipelines target the `testers` group.
*   **Google Play Publishing:** Release builds (AABs) triggered by pushes to `main` or release tags (starting with `v*`) are automatically published to the Google Play Store's **internal testing track**, each app using its own publishing service account secret. The packages and secrets are:
    *   **Photo Selector Toolbox (`:app`):** Package `com.photoselectortoolbox`, secret `TOOLBOX_GP_SERVICE_ACCOUNT_JSON`, `internal` track.
    *   **Photo Tok (`:phototok`):** Package `com.phototok`, secret `PHOTOTOK_GP_SERVICE_ACCOUNT_JSON`, `internal` track.
*   **Google Play API & First Release Constraints:** To publish using the automated pipeline, the **Google Play Android Developer API** must be enabled in each app's Google Cloud project. Additionally, the **very first upload** of an app bundle (AAB) for any package must be performed **manually** via the Google Play Console UI before the automated Google Play API publishing steps can succeed.
*   **Public Distribution Strategy:** The supported, user-facing distribution channel is **GitHub Releases** — signed APKs and AABs attached to the `nightly` (rolling, from `main`) and tagged (stable) releases. [Obtainium](https://github.com/ImranOmarRashid/Obtainium) is supported only as a *consumer* of those GitHub release assets (it auto-updates by reading the Releases page); it requires no dedicated server or project-side configuration and is not a managed channel. **Google Play (internal track)** serves as a development/preview build for invited testers. Firebase App Distribution remains in the CI pipeline as a legacy internal step and is **not** a publicly documented installation channel.


### 7.11 Android Visual Theme
*   **Dark Theme Only:** Matches desktop's dark theme. Material 3 custom `darkColorScheme`:
    *   `surface` = Zinc-900 (#18181B), `surfaceVariant` = Zinc-800 (#27272A)
    *   `primary` = Indigo-500 (#6366F1), `primaryContainer` = Indigo-600 (#4F46E5)
    *   `onSurface` = Zinc-50 (#FAFAFA), `onSurfaceVariant` = Zinc-400 (#A1A1AA)
    *   `outline` = Zinc-700 (#3F3F46)
*   **Charts:** Vico library with Indigo-500 bars, Zinc-400 labels, Zinc-700 grid lines on Zinc-800 backgrounds.
*   **App Launcher Icon:** The application uses a custom app launcher icon matching the desktop logo. It specifies both legacy and adaptive versions. The adaptive icon utilizes a solid Zinc-900 background (`#18181B`) and a centered, transparent foreground logo scaled to fit within the safe zone (72dp on a 108dp canvas).

### 7.12 Photo-Tok Architecture Conventions (`:phototok`)
*   **Typed Settings:** User-facing choice settings are typed enums in `com.phototok.domain` (`SwipeAction` delete/copy/move, `CollectionAction` copy/move, `FileTypeFilter` all/raw/jpg). Raw strings must not be passed through ViewModels or UI; the DataStore wire format is the enum's `key` (backward compatible with previously persisted values).
*   **Typed Settings Flow:** All simple phone-mode settings are exposed by `SettingsRepository` as one typed flow (`phoneSettings: Flow<PhoneSettings>`) mapped from the single DataStore `Preferences` snapshot. ViewModels must collect this flow instead of `combine`-ing individual setting flows with positional array casts.
*   **Image Source Abstraction:** Every image backend implements `com.phototok.data.source.ImageSource` (`LocalImageSourceImpl` for SAF/DocumentFile, `GoogleDriveImageSource` for Drive). URI-scheme dispatch for per-image operations happens exclusively in `ImageSourceResolver`/`ImageRepositoryImpl`; ViewModels and UI must not branch on URI schemes (UI-level source classification uses `SourceUris.isRemote` / `ImageItem.isRemote`). Cross-source copy/move is rejected by the repository. Adding a new backend means one new `ImageSource` implementation plus registration in the resolver.
*   **ViewModel Boundaries:** ViewModels must not hold `Context` or data-source clients (`GoogleDriveClient`, `GoogleDriveAuth` objects must not reach Compose). Folder-name resolution and permission persistence go through `ImageRepository.prepareSourceFolder`/`resolveFolderName`. The read-only selection viewer is backed by its own `SelectionViewerViewModel` (listing via `ImageRepository.listSelectionImages`, local-only), and the Google Drive photo picker by `DrivePickerViewModel`.
*   **Pure Domain Logic:** List/ordering logic must live in `com.phototok.domain` free of Android dependencies so it is unit-testable: `PhoneFeedOrdering` (ordering, file-type filtering, portrait-split computation), `PendingDeleteLogic` (pending-delete/revert list transitions), `RelatedFiles` (sibling detection), `PhotoExtensions` (single source of truth for RAW/JPEG extension sets), `PhotoFolders` (app-managed folder names), `CopyMoveFeedback` (copy/move feedback messages incl. partial failures), `SourceUris` (scheme classification).
*   **Pending Deletion Lifecycle:** Finalizing a pending deletion (primary image AND its related siblings) must run on the injected application-scoped `CoroutineScope` (`@ApplicationScope`), never on an ad-hoc scope, so deletions complete even when the ViewModel is cleared mid-undo-window.
*   **Folder Discovery:** Only one image-discovery collection may be active at a time; selecting a new source folder cancels the previous discovery `Job`.
*   **File Copies:** A failed copy must not leave a zero-byte or partially written destination file behind.
*   **Copy/Move Feedback:** Copy/move actions track each file's result individually: only successfully moved files leave the feed, and partial failures are reported ("Moved 1 of 2 …, 1 failed") as error feedback.
*   **State Update Batching:** Asynchronously loaded image dimensions are applied to the UI state in batches (`PhoneModeViewModel.DIMENSION_BATCH_SIZE`), not per image, to bound recompositions in large folders.
*   **Per-Folder Positions:** Last-viewed positions are stored under URI-keyed preferences (`folder_pos_v2_<uri>`; legacy `hashCode`-based keys are read as fallback and cleaned up on write). When a folder is evicted from the recents list, its stored position is removed so the preferences file does not grow unboundedly.
*   **Google Drive Scope Policy (`drive.file` ONLY):** Photo-Tok must request only the non-restricted `drive.file` OAuth scope (plus basic profile/email from sign-in). The full `drive` and `drive.readonly` scopes are *restricted* scopes (Google verification + annual CASA security assessment) and must never be requested. Consequences that the architecture must respect:
    - The app cannot browse the user's Drive. Users grant access to pre-existing photos by multi-selecting them in the **Google Picker**, hosted in a WebView (`DrivePhotoPickerDialog` + `DrivePickerViewModel`); selecting whole folders is disabled because a folder grant does **not** extend to the files inside it under `drive.file`.
    - Picker configuration (API key `PHOTOTOK_PICKER_API_KEY`, Cloud project number `PHOTOTOK_GCP_PROJECT_NUMBER`) is injected via `BuildConfig`; when missing, the picker shows a "not configured in this build" error instead of crashing. Values are injected into the picker HTML via `JSONObject.quote` (never raw string interpolation).
    - A picked set of files is persisted as a `DrivePickedSelection` (via `DrivePickedStore`/`DrivePickedCodec`, newest-first, capped at 10) and addressed as `gdrive-picked://<key>`; `gdrive://<id>` remains for individual files and app-created folders. Re-opening a picked selection intersects the stored file IDs with `GoogleDriveClient.listAccessibleImages()` (a flat, parent-less listing — under `drive.file` the API returns only app-accessible files).
    - Collection copy/move actions on picked sources default to the app-created `PhotoTok` folder in the Drive root (`PhotoFolders.DRIVE_APP_FOLDER`). Moves resolve the file's actual parents via the API (the old parent cannot be assumed readable) and fall back to copy + trash when the move is rejected.
*   **Google Drive Client:** User-supplied values interpolated into Drive query strings must be escaped (`'` and `\`). Every request (GET/POST/PATCH, downloads, uploads) goes through a single authorized helper that invalidates the cached token and retries exactly once on HTTP 401. Recursive folder listing runs subfolder listings concurrently, bounded by a semaphore (4). Uploads use a single multipart request. The Drive download cache is capped at 512 MB with LRU (last-access) eviction; the Coil disk cache does not double-store Drive files (the Drive fetcher serves cache files directly).
*   **Auth Stack Note:** Google sign-in currently uses the deprecated `GoogleSignIn`/`GoogleAuthUtil` APIs; the migration to Credential Manager + `AuthorizationClient` is intentionally deferred to a separate, device-testable change.
*   **Legal Links (Play compliance):** The settings screen must expose "Privacy Policy" and "Legal Notice (Impressum)" links (Google Play requires an in-app privacy-policy link; the Impressum addresses § 5 DDG). The public URLs live in `com.phototok.domain.LegalLinks` (single source of truth); the documents themselves are maintained in `docs/phototok/` and hosted publicly (see `docs/phototok/PLAY_RELEASE_CHECKLIST.md`). The privacy policy documents the on-device processing model and the `drive.file`-only Drive access and must be updated when either changes.

### 7.13 Feature Sync Policy
When a new feature is added to the desktop application, it must be evaluated for inclusion in the Android app:
*   **Tablet/DeX mode:** Should include the feature if technically feasible on Android.
*   **Phone mode:** Should include the feature if it works well on small screens; may omit with documented rationale.
*   **Excluded features** (Ollama VLM, CLI, ExifTool, SMB paths) are permanently excluded regardless of desktop changes.
*   Feature sync evaluations are documented in `ANDROID_DESIGN.md` §5 (Feature Mapping).
