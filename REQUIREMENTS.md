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
*   **Publishing Strategy:** The project utilizes `softprops/action-gh-release` to automatically publish artifacts to the `nightly` tag on every push to the `main` branch.
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

## 6. Testing Requirements

*   **Path Resolution Tests:** The `resolve_path` utility must be tested across simulated platforms (Linux, macOS, Windows) using mocking for `sys.platform` and `os.getuid`.
*   **GUI Unit Tests:** Tests validating GUI components (e.g., `tests/test_sharpness_gui_basic.py`) require extensive mocking of `tkinter`, `PIL`, and `photo_selector_toolbox` dependencies due to the lack of a display environment in CI runners.
*   **Headless Execution:** To run or test standalone Tkinter GUI scripts headlessly in the development environment, developers must use `xvfb-run` (e.g., `poetry run xvfb-run python3 script.py`) to avoid `_tkinter.TclError` exceptions.
*   **Execution Command:** Tests should be executed using `poetry run pytest tests/` after ensuring dependencies are installed via `poetry install`.
