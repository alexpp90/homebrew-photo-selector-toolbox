# Linux Desktop — Requirements

> Product: **Linux Desktop** (`products/linux-desktop/`, Python 3.12+ + PyGObject + GTK4 / Libadwaita).  
> Target Platform: Debian 13 (Trixie) / GNOME 46+ (Wayland & X11).  
> Scope: This specification is authoritative for the native Linux desktop application. Requirements for macOS Desktop live in [`../macos-desktop/REQUIREMENTS.md`](../macos-desktop/REQUIREMENTS.md); legacy Desktop in [`../desktop/REQUIREMENTS.md`](../desktop/REQUIREMENTS.md); Android Desktop in [`../android-desktop/REQUIREMENTS.md`](../android-desktop/REQUIREMENTS.md); PhotoTok in [`../phototok/REQUIREMENTS.md`](../phototok/REQUIREMENTS.md).  
> Shared Parity & Feature Triage: [`../../shared/FEATURE_PARITY.md`](../../shared/FEATURE_PARITY.md).  
> Canonical Terminology: [`../../GLOSSARY.md`](../../GLOSSARY.md).  

---

## 1. Introduction

The **Linux Desktop** application is a high-performance native GNOME photograph culling and selection suite built for Debian 13 (Trixie) and modern Linux desktop environments. Implemented with Python 3.12+, PyGObject, GTK4, Libadwaita, GExiv2, and vectorized NumPy, it delivers zero-latency SD-card photo ingestion, large-scale RAW/JPEG triage, noise-corrected optical quality scoring, and strict adherence to GNOME Human Interface Guidelines (HIG).

The application is distributed as a native Debian `.deb` package hosted via an in-repo APT repository, mirroring the friction-free installation and updating workflow of modern packaging ecosystems.

---

## 2. Core Photographic Engine & Business Logic

### 2.1 Metadata Extraction & Optical Models (`R-LINUX-META`)

*   **R-LINUX-META-01: Supported File Formats**  
    The metadata reader and pipeline must natively recognize and process standard bitmap formats (`.jpg`, `.jpeg`, `.png`, `.heic`, `.tif`, `.tiff`, `.webp`) and professional camera RAW formats (`.cr2`, `.cr3`, `.nef`, `.arw`, `.dng`, `.raf`, `.rw2`, `.orf`, `.pef`, `.raw`). File extension checks must be case-insensitive.
*   **R-LINUX-META-02: Standardized `ExposureMetadata` Data Model**  
    Metadata parsed via `GExiv2` must populate a strictly-typed `ExposureMetadata` data class with fields:
    - `shutter_speed`: Decimal exposure duration in seconds (`Optional[float]`).
    - `aperture`: Lens $F$-number (`Optional[float]`).
    - `iso`: Sensor ISO sensitivity integer (`Optional[int]`).
    - `focal_length`: Lens focal length in millimeters (`Optional[float]`).
    - `lens`: Lens model identifier string (`Optional[str]`).
    - `camera_make`: Camera manufacturer string (`Optional[str]`).
    - `camera_model`: Camera model string (`Optional[str]`).
    - `is_fallback`: Boolean indicating whether values were recovered via fallback calculation.
*   **R-LINUX-META-03: Formatted Optical Exposure Strip & Zero-Placeholder Invariant**  
    Optical exposure parameters must format into clean photographic representations:
    - Shutter speeds strictly greater than zero and less than one second ($0.0\text{s} < t < 1.0\text{s}$) format as fractional strings (e.g. `1/250s`, `1/2000s`), with denominator calculated as $\lfloor 1.0/t + 0.5 \rfloor$.
    - Shutter speeds $\ge 1.0\text{s}$ format as decimal seconds (e.g. `1.5s`, `30s`).
    - Aperture values $> 0.0$ format with an `f/` prefix (e.g. `f/2.8`, `f/1.4`).
    - ISO sensitivity values $> 0$ format with an `ISO` prefix (e.g. `ISO 100`, `ISO 6400`).
    - Focal length values $> 0.0$ format with an `mm` suffix (e.g. `85mm`, `24.0mm`).
    - **Division-by-Zero & Bulb Mode Protection**: Shutter speeds less than or equal to zero ($t \le 0.0\text{s}$), non-finite floats (`NaN`, `Inf`), and indeterminate bulb exposures where duration was untracked or recorded as zero must never undergo fractional division. They must be treated as unparseable exposure metadata and omitted.
    - **Zero-Placeholder Invariant**: Any optical tag that is missing, corrupt, non-positive ($\le 0$), non-finite, or unparseable must be **completely omitted** from formatted strings and UI badges. Displaying `"Unknown"`, `"N/A"`, or `"None"` placeholders is strictly forbidden.
*   **R-LINUX-META-04: Companion RAW+JPEG, Sidecar & Edit Binding**  
    Directory ingestion must automatically bind related companion files residing in the same directory under a single primary `CandidatePhoto`:
    1. RAW+JPEG pairs sharing a stem (e.g. `DSC0001.ARW` + `DSC0001.JPG`), designating the RAW file as primary when both exist.
    2. Sidecar metadata files (`<filename>.xmp` and `<stem>.xmp`).
    3. Lightroom editing derivatives (`<stem>-Edit.<ext>`, `<stem>_Edit.<ext>`, `<stem>-Enhanced-NR.<ext>`).
*   **R-LINUX-META-05: Dynamic Selection Subfolder Exclusion Invariant**  
    Directory scanning must recursively, case-insensitively, and dynamically exclude:
    1. Standard default selection folder names: `Selection`, `Selected`, `PhotoTok_Selection`, and `PhotoTok_LeftSwipe`.
    2. The user's active custom selection folder name/path configured via preferences (`R-LINUX-PREF-02`, e.g. `"Picks"`, `"Keeps"`).
    Matching must be performed against exact individual path components (preventing false-positive exclusion of folders containing "Selection" as a substring, such as `Trip_Selection_Final/`). If any parent directory component in the relative path from the scan root matches an excluded name, its entire subtree must be pruned from enumeration. However, if the user explicitly opens an excluded folder directly as the root scan target, that root folder must be scanned without exclusion.
*   **R-LINUX-META-06: APEX Exposure Math Fallbacks**  
    When direct EXIF exposure tags (`ExposureTime`, `FNumber`) are missing, the metadata reader must compute optical values from APEX (Additive System of Photographic Exposure) tags:
    - Time Value ($T_v$): Shutter speed $t = 2^{-T_v}$.
    - Aperture Value ($A_v$): $F$-number $f = \sqrt{2^{A_v}} = 2^{A_v / 2}$.
    - Sensitivity: Read from `Exif.Photo.ISOSpeedRatings` array or `Exif.Photo.PhotographicSensitivity`.

---

### 2.2 Image Quality Scoring & Noise Compensation (`R-LINUX-SCORE`)

*   **R-LINUX-SCORE-01: Vectorized Laplacian Focus Convolution**  
    The focus scoring engine must compute sharpness variance using a 2D discrete Laplacian convolution with kernel:
    $$\begin{bmatrix} 0 & 1 & 0 \\ 1 & -4 & 1 \\ 0 & 1 & 0 \end{bmatrix}$$
    applied to luminance-converted pixel arrays pre-filtered by a 3x3 Gaussian smoothing kernel to attenuate high-frequency pixel rasterization artifacts. For interactive culling evaluation, convolution operates on the center 50% Region of Interest (ROI) proxy ($[0.25H:0.75H, 0.25W:0.75W]$, spanning $960 \times 540$ on a 1080p frame), matching photographic subject-centering standards and repository parity with Desktop and macOS Desktop.
*   **R-LINUX-SCORE-02: Median Absolute Deviation (MAD) Noise Floor Subtraction**  
    To prevent high-ISO sensor grain from falsely inflating sharpness scores, the engine must estimate the sensor noise floor $\sigma_{\text{noise}}$ via Median Absolute Deviation:
    $$\sigma_{\text{noise}} = 1.4826 \times \text{median}\left(|\nabla^2 I - \text{median}(\nabla^2 I)|\right)$$
    To eliminate $O(N)$ sorting bottlenecks over millions of pixels without loss of statistical accuracy, MAD estimation must sample a subset of $\le 2,048$ uniformly strided or low-discrepancy pixels from the Laplacian response (mirroring macOS Desktop). The raw Laplacian variance $\text{Var}(\nabla^2 I)$ must be corrected by subtracting $\sigma_{\text{noise}}^2$ prior to score mapping.
*   **R-LINUX-SCORE-03: Compressive Sigmoid Score Mapping & Categorization**  
    The noise-corrected variance must be mapped non-linearly to a normalized 0.0–100.0 score scale using a compressive saturation curve:
    $$\text{Score} = 100 \times \left(1 - \exp\left(-\frac{\sqrt{\max(0, \text{Var}_{\text{corrected}})}}{12}\right)\right)$$
    Scores must map to standardized photographic categories:
    - **Blurry**: Score $< 35.0$.
    - **Acceptable**: $35.0 \le \text{Score} < 70.0$.
    - **Sharp / Crisp**: $\text{Score} \ge 70.0$.
*   **R-LINUX-SCORE-04: Exposure Histogram Clipping Analysis**  
    The engine must compute 256-bin luminance histograms over candidate frames and report:
    - Highlight clipping percentage: Proportion of pixels with luminance $\ge 254$.
    - Shadow clipping percentage: Proportion of pixels with luminance $\le 2$.
*   **R-LINUX-SCORE-05: Metric Omission Rule**  
    Any quality score or clipping metric that has not yet completed computation must be completely omitted from candidate cards, preview badges, and HUD overlays. Displaying `"0.0"` or `"N/A"` for uncomputed scores is strictly prohibited.
*   **R-LINUX-SCORE-06: Compute Latency SLA & Algorithmic Acceleration**  
    The optical focus scoring computation on a 1080p frame must execute with a median latency of $< 2.0\text{ ms}$ on standard modern workstation reference hardware (dedicated x86_64 or Apple Silicon CPU) using vectorized NumPy operations, enabling background scoring worker threads to keep pace with rapid browsing. To achieve this latency in pure Python/NumPy, the engine must employ algorithmic acceleration:
    1. **Center 50% ROI Proxy**: Computation evaluates the center 50% Region of Interest ($960 \times 540$ on 1080p frames, $[0.25H:0.75H, 0.25W:0.75W]$), reducing pixel payload by 75% while focusing on the primary photographic subject.
    2. **Strided MAD Sampling**: Sensor noise floor estimation samples $\le 2,048$ strided elements, restricting median computation to $< 0.1\text{ ms}$ instead of full-array partitioning.
    3. **Background Full-Frame Budget**: Where uncropped full-frame 1080p scoring is evaluated in background worker threads, strided MAD sampling ($\le 2,048$ elements) must be used to ensure full-frame execution completes within a background latency budget of $< 10.0\text{ ms}$ (strictly bounded at $< 25.0\text{ ms}$).
    4. **CI & Virtualized Environment Tolerance**: In shared, containerized, or virtualized test runner environments (such as GitHub Actions Ubuntu runners, or test suite runs subject to host CPU scheduling contention and page allocation jitter), test benchmarks must tolerate measurement jitter up to $< 4.0\text{ ms}$ median (and $< 5.0\text{ ms}$ average), preventing false-positive CI failures while strictly verifying the algorithmic acceleration invariants and reference $< 2.0\text{ ms}$ compute capability.

---

### 2.3 SD Card Ingestion & Culling Engine (`R-LINUX-CULL`)

*   **R-LINUX-CULL-01: Progressive Asynchronous Streaming Ingestion**  
    Folder scanning must run asynchronously in background worker threads without blocking the GTK main loop. Candidates must stream progressively to the workspace UI in batches, presenting the first candidate immediately upon discovery.
*   **R-LINUX-CULL-02: Natural Alphanumeric Sorting**  
    Candidate images must sort using natural alphanumeric ordering based on file stems (e.g. `DSC_0002` precedes `DSC_0010`), ensuring burst sequences maintain strict chronological sequence regardless of file creation timestamps.
*   **R-LINUX-CULL-03: Atomic Multi-File Move & Cross-Filesystem Rollback Safety**  
    Executing Move (`M`) must relocate the primary candidate file and **all bound companion files** (`.xmp`, RAW, JPEG, `-Edit.*`) to the configured selection destination folder in a single atomic transaction:
    - **Intra-Filesystem**: When source and destination share the same filesystem (`st_dev`), move via atomic POSIX `rename()` / `os.replace`. If any companion rename fails, previously renamed companions must be restored to their original source paths before raising an error.
    - **Cross-Filesystem**: When moving across different mount points or devices (e.g. SD card at `/media/$USER/...` to internal storage), moves must enforce a Two-Phase Staged Transaction:
      1. *Pre-flight*: Verify destination filesystem free capacity via `statvfs` against total bundle size. If insufficient, abort before any disk mutation.
      2. *Staging*: Replicate each companion file to destination without unlinking any source file. If ANY companion transfer fails mid-flight (e.g. `ENOSPC`, `EACCES`, `EIO`, cancellation), abort immediately, unlink all newly created destination replicas, and leave all original source files 100% untouched and intact.
      3. *Commit*: Only after all files in the candidate bundle are verified written and flushed at destination, unlink the original source files.
    - **Self-Move Guard**: If source and destination resolve to identical canonical paths, the operation must be an idempotent no-op preserving the file intact without deletion or replication.
*   **R-LINUX-CULL-04: Atomic Multi-File Copy to Selection & Staged Rollback**  
    Executing Copy (`C`) must replicate the primary candidate file and all bound companion files to the configured selection destination folder in a single transaction. If copying any companion file fails mid-flight, all newly created destination replicas from that operation must be cleaned up (unlinked), preventing orphaned partial companion sets in the selection directory. Original source files remain strictly untouched.
*   **R-LINUX-CULL-05: FreeDesktop Safe Trash Integration**  
    Executing Trash (`Delete` / `Backspace`) must relocate the primary candidate file and all bound companion files to the system trash via `Gio.File.trash()` adhering strictly to the FreeDesktop.org Trash specification (`trash://`). Hard filesystem deletions (`unlink` / `rm`) are strictly prohibited during normal culling.
*   **R-LINUX-CULL-06: Transactional Copy-Undo Safety Invariant**  
    The undo engine must maintain absolute transactional safety for copy operations. Undoing a copy operation strictly removes **only the destination replica** in `Selection/`. The original source media on SD cards or local storage is **never touched, modified, or deleted**.
*   **R-LINUX-CULL-07: Transactional Move and Trash Undo**  
    Undoing a move or trash operation must atomically restore the primary image and all companions to their exact original paths and restore the active candidate index in the workspace.

---

### 2.4 Caching & Prefetching Performance (`R-LINUX-CACHE`)

*   **R-LINUX-CACHE-01: Bounded In-Memory LRU Cache**  
    The application must maintain an in-memory Least Recently Used (LRU) decoded image cache capped at a configurable memory ceiling (default 512 MB). Exceeding the ceiling evicts the oldest non-visible decoded pixbufs.
*   **R-LINUX-CACHE-02: Asymmetric Sliding Prefetch Window**  
    The background cache loader must prefetch and decode candidate images in an asymmetric sliding window relative to the active index $i$:
    $$[i-2, \dots, i+3]$$
    biased in the current browsing direction to ensure instantaneous 60fps frame transitions during sequential navigation.
*   **R-LINUX-CACHE-03: Modal Teardown & Memory Reclamation**  
    Upon closing a folder or exiting comparison modes, all full-resolution decoded buffers outside the active view must be released immediately to prevent memory bloat.

---

### 2.5 Secondary Utilities (`R-LINUX-TOOLS`)

*   **R-LINUX-TOOLS-01: Streaming Cryptographic Duplicate Finder**  
    The duplicate finder must identify identical files by first comparing exact byte lengths, followed by streaming chunked SHA-256 cryptographic hashing. Duplicates must be clustered by hash and presented in a dedicated dialog with safe review and trashing affordances.
*   **R-LINUX-TOOLS-02: Library Statistics Engine**  
    The statistics engine must aggregate optical metadata across all candidate photos in the loaded library and generate distribution histograms for:
    - Focal length distribution (grouped by standard prime/zoom ranges).
    - Aperture distribution ($F$-stops).
    - ISO sensitivity distribution.
    - Shutter speed distribution.

---

## 3. User Interface (UI/UX) & GNOME HIG Libadwaita Compliance

### 3.1 Studio Dark Styling & Readability (`R-LINUX-UI`)

*   **R-LINUX-UI-01: GNOME HIG Libadwaita Architecture**  
    The user interface must be constructed entirely using native Libadwaita and GTK4 widgets (`AdwApplicationWindow`, `AdwHeaderBar`, `AdwToolbarView`, `AdwPreferencesWindow`, `AdwToast`).
*   **R-LINUX-UI-02: Studio Dark Scheme & High-Contrast Typography**  
    The application must enforce a studio dark color palette (`AdwColorScheme.FORCE_DARK` or system dark style via `AdwStyleManager`):
    - Surface background: `#141417`
    - Card / Pane background: `#1C1C20`
    - Separator / Border stroke: `#3A3A42`
    - Primary typography: `#FFFFFF` (pure white for headings and active indicators)
    - Secondary typography: `#D9D9D9` (high-contrast light gray for optical metadata)
    - **Prohibition**: Low-contrast dark-on-dark text (e.g. `#555555` on `#1C1C20`) is strictly prohibited anywhere in the interface.
*   **R-LINUX-UI-03: Viewport Canvas Allocation**  
    The main window must dedicate $> 80–85\%$ of its total window area directly to photograph previews. Persistent settings panels, tuning drawers, or configuration sliders are strictly prohibited in the culling workspace.

---

### 3.2 Comparison Modes & Focus Viewports

*   **R-LINUX-UI-04: 1-Up Mode (Single View)**  
    Displays the active candidate photo filling the maximum viewport canvas for broad composition, exposure, and color evaluation.
*   **R-LINUX-UI-05: 2-Up Mode (Side-by-Side Comparison)**  
    Splits the viewport into a 50/50 horizontal side-by-side comparison between the Champion photo (left) and Challenger photo (right) for pairwise elimination.
*   **R-LINUX-UI-06: 3-Up Focus Mode (Sliding Triplet)**  
    Presents three horizontal comparison slots:
    - Left slot: Previous candidate (`currentIndex - 1`).
    - Center slot: Active candidate (`currentIndex`), highlighted with a prominent 2.5px blue focus border (`.accent-border`), an `ACTIVE` pill, and the optical exposure strip.
    - Right slot: Next candidate (`currentIndex + 1`).
    - Navigating slides the entire triplet forward or backward across the album. Culling actions (M, C, Delete) operate directly on the center candidate and auto-advance.
*   **R-LINUX-UI-07: BoundarySlotPane Indicators**  
    In 3-Up Focus Mode, when the active candidate is at album boundary index 0 or index $N-1$, outer slots must render dedicated `BoundarySlotPane` indicator cards ("First Photograph" / "Last Photograph") with clean iconography, preventing index out-of-bounds errors or blank slots.
*   **R-LINUX-UI-08: 1:1 Actual-Pixel Zoom Inspection**  
    Pressing `Space` must toggle between Fit-to-Window scaling and actual-pixel 1:1 (100%) zoom centered at the cursor position. Pressing `Space` again or `Escape` must instantly reset the view to Fit-to-Window.
*   **R-LINUX-UI-09: Floating Translucent Metadata & Score HUD**  
    Key optical EXIF metadata (`1/2000s · f/1.4 · ISO 100 · 85mm`) and computed quality scores must be rendered as a lightweight, translucent floating HUD card anchored to the active preview pane without displacing layout margins. Toggled via `I`.
*   **R-LINUX-UI-10: Tactile Bottom Action Bar**  
    A persistent bottom `GtkActionBar` must display tactile, high-contrast action buttons:
    - **Move to Selection (M)**: Styled with `.suggested-action` (green).
    - **Copy to Selection (C)**: Styled with primary accent (blue).
    - **Move to Trash (Delete)**: Styled with `.destructive-action` (red).
    - **Undo (Ctrl+Z)**: Styled with action counter pill.
    - Status counters: `X Selected · Y Trashed · Z Remaining`.

---

### 3.3 Zero-Latency Keyboard Routing (`R-LINUX-KEY`)

*   **R-LINUX-KEY-01: Root Window Key Event Capture, Modifier Masking & Case Normalization**  
    Keyboard shortcuts must be intercepted at the root `AdwApplicationWindow` level using a `GtkEventControllerKey` attached with `GtkPropagationPhase.CAPTURE`. Key events must be processed and dispatched before child widget focus traversal or button rings can trap them.
    - **Modifier Lock Masking**: The key event handler must explicitly mask out active lock state flags, ignoring CapsLock (`Gdk.ModifierType.LOCK_MASK`) and NumLock (`Gdk.ModifierType.MOD2_MASK`) when evaluating modifier combinations:
      $$\text{effective\_state} = \text{state} \ \& \sim(\text{Gdk.ModifierType.LOCK\_MASK} \mid \text{Gdk.ModifierType.MOD2\_MASK})$$
      Active lock states must never prevent shortcut triggering.
    - **Case-Insensitive Key Normalization**: All letter key events must be normalized to lowercase via `Gdk.keyval_to_lower(keyval)` prior to matching, ensuring identical behavior regardless of whether CapsLock or Shift is active. Single-letter culling and navigation actions must trigger reliably without requiring accelerator modifiers (`Ctrl`, `Alt`, `Super`).
*   **R-LINUX-KEY-02: Navigation Hotkeys**  
    - `←` (Left Arrow / `GDK_KEY_Left`), `H` / `h` (`GDK_KEY_H` / `GDK_KEY_h`), `K` / `k` (`GDK_KEY_K` / `GDK_KEY_k`): Navigate to previous candidate photo.
    - `→` (Right Arrow / `GDK_KEY_Right`), `L` / `l` (`GDK_KEY_L` / `GDK_KEY_l`), `J` / `j` (`GDK_KEY_J` / `GDK_KEY_j`): Navigate to next candidate photo.
    - Key evaluations must be case-insensitive, must ignore CapsLock and NumLock states, and must operate without requiring modifier keys.
*   **R-LINUX-KEY-03: Culling Hotkeys**  
    - `M` / `m` (`GDK_KEY_M` / `GDK_KEY_m`): Move candidate photo and companions to `Selection/` and auto-advance. Must treat `M` and `m` identically.
    - `C` / `c` (`GDK_KEY_C` / `GDK_KEY_c`): Copy candidate photo and companions to `Selection/` and auto-advance. Must treat `C` and `c` identically.
    - `Delete` / `Backspace` (`GDK_KEY_Delete` / `GDK_KEY_BackSpace`): Move candidate photo and companions to Trash and auto-advance.
    - Culling shortcuts must execute with zero latency, require no accelerator modifiers (`Ctrl`, `Alt`, `Super`), and never fail silently due to active CapsLock or NumLock states.
*   **R-LINUX-KEY-04: Comparison Mode Switcher Hotkeys**  
    - `1`: Switch to 1-Up (Single View) mode.
    - `2`: Switch to 2-Up (Side-by-Side) mode.
    - `3`: Switch to 3-Up Focus Mode (Sliding Triplet).
*   **R-LINUX-KEY-05: Inspection Hotkeys**  
    - `Space`: Toggle 100% 1:1 pixel zoom vs Fit-to-Window.
    - `Escape`: Reset 100% zoom to Fit-to-Window / dismiss dialog.
    - `Tab`: Cycle active comparison slot focus in multi-up modes.
    - `F`: Toggle thumbnail filmstrip drawer.
    - `I`: Toggle floating metadata/score HUD overlay.
    - `S`: Toggle synchronized pan/zoom lock between compared slots.
*   **R-LINUX-KEY-06: System & Utility Hotkeys**  
    - `Ctrl+Z`: Transactional undo of the last culling operation.
    - `Ctrl+O`: Open folder / SD-card selection dialog.
    - `Ctrl+D`: Launch Duplicate Finder utility.
    - `Ctrl+L`: Launch Library Statistics utility.
    - `Ctrl+,`: Open Preferences dialog.
    - `?` or `Ctrl+?`: Open Keyboard Shortcuts cheat sheet (`GtkShortcutsWindow`).
*   **R-LINUX-KEY-07: Editable Text Entry Exception**  
    When keyboard focus is inside an editable `GtkEntry` or `GtkSearchEntry` widget, all hotkey interception must be bypassed, allowing standard text editing without triggering culling or navigation actions.

---

### 3.4 Preferences & Settings (`R-LINUX-PREF`)

*   **R-LINUX-PREF-01: Decoupled `AdwPreferencesWindow`**  
    All application configuration options must be housed in a modal `AdwPreferencesWindow` opened via `Ctrl+,` or the Primary Menu, keeping the main workspace 100% uncluttered.
*   **R-LINUX-PREF-02: Configurable Application Preferences**  
    The preferences dialog must provide:
    - **General**: Configurable Selection subfolder name (default `"Selection"`), auto-advance after culling toggle.
    - **Quality & Scoring**: Focus score threshold sliders (Blurry cutoff, Sharp cutoff).
    - **Performance & Cache**: In-memory cache ceiling slider (default 512 MB), prefetch window size slider.

---

## 4. Packaging & Debian 13 APT Distribution (`R-LINUX-PKG`)

*   **R-LINUX-PKG-01: Debian 13 (Trixie) Packaging Metadata**  
    The product must supply standard Debian packaging definitions in `products/linux-desktop/debian/`:
    - `debian/control`: Defining package dependencies (`python3`, `python3-gi`, `gir1.2-gtk-4.0`, `gir1.2-adw-1`, `gir1.2-gexiv2-0.10`, `python3-pillow`, `python3-numpy`), package architecture (`all`), standards version (`4.6.2`), and description.
    - `debian/rules`: Utilizing debhelper with Meson (`dh $@ --buildsystem=meson --with python3`).
    - `debian/changelog`: Version history with target distribution `trixie`.
*   **R-LINUX-PKG-02: Debian Free Software Guidelines (DFSG) Compliance**  
    The package build must be fully reproducible, contain zero non-free binary blobs, and link strictly against packages available in Debian 13 main.
*   **R-LINUX-PKG-03: In-Repo APT Repository Architecture**  
    The repository must provide a compliant APT repository layout under `apt/` deployable via GitHub Pages:
    - `dists/trixie/main/binary-all/Packages` and `Packages.gz`.
    - `dists/trixie/Release` providing `SHA256` and `SHA512` digests.
    - Cryptographically signed inline `dists/trixie/InRelease` and detached `Release.gpg`.
    - Staged binary `.deb` archives in `pool/main/p/photo-selector-linux/`.
*   **R-LINUX-PKG-04: Modern `deb822` Repository Source Configuration**  
    Installation documentation must specify modern Debian 13 `deb822` source entries placed in `/etc/apt/sources.list.d/photo-selector.sources` with dedicated keyrings in `/etc/apt/keyrings/photo-selector-archive-keyring.gpg`. Legacy `apt-key add` is strictly prohibited.
*   **R-LINUX-PKG-05: Automated Packaging & Distribution Pipeline**  
    The project must include an automated script `scripts/package_debian.sh` to compile `.deb` packages and generate signed APT repository metadata in one command.

---

## 5. Traceability Matrix

Every requirement ID maps to its implementation module and verification test suite:

| Requirement ID | Requirement Scope | Target Implementation Module | Verification Test Target |
|---|---|---|---|
| `R-LINUX-META-01` | Supported Formats | `src/core/scanner.py` | `tests/unit/test_scanner.py::test_supported_extensions` |
| `R-LINUX-META-02` | `ExposureMetadata` Model | `src/core/models.py` | `tests/unit/test_models.py::test_exposure_metadata_defaults` |
| `R-LINUX-META-03` | Exposure Formatting & Zero Placeholders | `src/core/models.py` | `tests/unit/test_models.py::test_zero_placeholder_invariant` |
| `R-LINUX-META-04` | Companion File Binding | `src/core/scanner.py` | `tests/unit/test_scanner.py::test_companion_raw_jpeg_pairing` |
| `R-LINUX-META-05` | Dynamic Selection Exclusion | `src/core/scanner.py` | `tests/unit/test_scanner.py::test_dynamic_selection_exclusion_defaults` |
| `R-LINUX-META-06` | APEX Exposure Math Fallbacks | `src/core/metadata.py` | `tests/unit/test_metadata.py::test_apex_tv_conversion` |
| `R-LINUX-SCORE-01` | Laplacian 2D Convolution | `src/core/scoring.py` | `tests/unit/test_scoring.py::test_laplacian_convolution_flat_vs_textured` |
| `R-LINUX-SCORE-02` | MAD Noise Floor Subtraction | `src/core/scoring.py` | `tests/unit/test_scoring.py::test_mad_noise_subtraction` |
| `R-LINUX-SCORE-03` | Sigmoid Mapping & Categories | `src/core/scoring.py` | `tests/unit/test_scoring.py::test_score_mapping_and_thresholds` |
| `R-LINUX-SCORE-04` | Highlight & Shadow Clipping | `src/core/scoring.py` | `tests/unit/test_scoring.py::test_exposure_clipping_histograms` |
| `R-LINUX-SCORE-05` | Score Metric Omission Rule | `src/ui/info_hud.py` | `tests/unit/test_scoring.py::test_uncomputed_score_omission` |
| `R-LINUX-SCORE-06` | Compute Latency SLA (< 2.0ms ref SLA via ROI & Strided MAD) | `src/core/scoring.py` | `tests/unit/test_scoring.py::test_scoring_latency_benchmark` |
| `R-LINUX-CULL-01` | Progressive Streaming Ingestion | `src/core/scanner.py` | `tests/unit/test_scanner.py::test_progressive_streaming_batch_sizes` |
| `R-LINUX-CULL-02` | Natural Alphanumeric Sorting | `src/core/scanner.py` | `tests/unit/test_scanner.py::test_natural_alphanumeric_sorting` |
| `R-LINUX-CULL-03` | Atomic Move & Cross-Mount Rollback | `src/core/file_ops.py` | `tests/unit/test_file_ops.py::test_cross_filesystem_two_phase_staging_and_rollback` |
| `R-LINUX-CULL-04` | Atomic Copy & Rollback | `src/core/file_ops.py` | `tests/unit/test_file_ops.py::test_atomic_copy_to_selection` |
| `R-LINUX-CULL-05` | FreeDesktop Safe Trash | `src/core/file_ops.py` | `Planned (Milestone 4: tests/unit/test_file_ops.py::test_freedesktop_trash)` |
| `R-LINUX-CULL-06` | Transactional Copy-Undo Safety | `src/core/file_ops.py` | `tests/unit/test_file_ops.py::test_copy_undo_source_preservation_invariant` |
| `R-LINUX-CULL-07` | Transactional Move & Trash Undo | `src/core/file_ops.py` | `tests/unit/test_file_ops.py::test_move_undo_restores_all_companions` |
| `R-LINUX-CACHE-01` | Bounded In-Memory LRU Cache | `src/core/cache.py` | `Planned (Milestone 4: tests/unit/test_cache.py::test_lru_cache_eviction)` |
| `R-LINUX-CACHE-02` | Sliding Prefetch Window | `src/core/cache.py` | `Planned (Milestone 4: tests/unit/test_cache.py::test_sliding_prefetch_window)` |
| `R-LINUX-CACHE-03` | Memory Ceiling Reclamation | `src/core/cache.py` | `Planned (Milestone 4: tests/unit/test_cache.py::test_cache_teardown_reclamation)` |
| `R-LINUX-TOOLS-01` | Cryptographic Duplicate Finder | `src/core/duplicate_finder.py` | `tests/unit/test_duplicate_finder.py::test_duplicate_detection_by_content` |
| `R-LINUX-TOOLS-02` | Library Statistics Engine | `src/core/statistics.py` | `tests/unit/test_statistics.py::test_optical_exif_histograms` |
| `R-LINUX-UI-01` | Libadwaita Architecture | `src/ui/window.py` | `Planned (Milestone 4: tests/unit/test_culling_workspace.py::test_libadwaita_components)` |
| `R-LINUX-UI-02` | Studio Dark Scheme & Palette | `data/style.css` | `Planned (Milestone 4: tests/unit/test_culling_workspace.py::test_studio_dark_palette)` |
| `R-LINUX-UI-03` | Viewport Canvas Allocation | `src/ui/canvas.py` | `Planned (Milestone 4: tests/unit/test_culling_workspace.py::test_canvas_area_allocation)` |
| `R-LINUX-UI-04` | 1-Up Single View Mode | `src/ui/canvas.py` | `tests/unit/test_culling_workspace.py::test_comparison_mode_transitions` |
| `R-LINUX-UI-05` | 2-Up Side-by-Side Mode | `src/ui/canvas.py` | `tests/unit/test_culling_workspace.py::test_comparison_mode_transitions` |
| `R-LINUX-UI-06` | 3-Up Focus Mode Triplet | `src/ui/canvas.py` | `tests/unit/test_culling_workspace.py::test_comparison_mode_transitions` |
| `R-LINUX-UI-07` | BoundarySlotPane Indicators | `src/ui/boundary_slot.py` | `tests/unit/test_culling_workspace.py::test_boundary_slot_conditions` |
| `R-LINUX-UI-08` | 1:1 Actual-Pixel Zoom Toggle | `src/ui/canvas.py` | `tests/unit/test_keyboard_router.py::test_inspection_hotkeys` |
| `R-LINUX-UI-09` | Floating Metadata & Score HUD | `src/ui/info_hud.py` | `tests/unit/test_scoring.py::test_uncomputed_score_omission` |
| `R-LINUX-UI-10` | Tactile Bottom Action Bar | `src/ui/action_bar.py` | `tests/unit/test_keyboard_router.py::test_culling_hotkeys_case_insensitivity_and_locks` |
| `R-LINUX-KEY-01` | Root Key Capture & Modifier Masking | `src/ui/keyboard_router.py` | `tests/unit/test_keyboard_router.py::test_root_key_capture_and_modifier_masking` |
| `R-LINUX-KEY-02` | Navigation Hotkeys | `src/ui/keyboard_router.py` | `tests/unit/test_keyboard_router.py::test_navigation_hotkeys_case_insensitivity` |
| `R-LINUX-KEY-03` | Culling Hotkeys (M, C, Del) | `src/ui/keyboard_router.py` | `tests/unit/test_keyboard_router.py::test_culling_hotkeys_case_insensitivity_and_locks` |
| `R-LINUX-KEY-04` | View Mode Switchers (1, 2, 3)| `src/ui/keyboard_router.py`| `tests/unit/test_keyboard_router.py::test_view_mode_hotkeys` |
| `R-LINUX-KEY-05` | Inspection Hotkeys | `src/ui/keyboard_router.py`| `tests/unit/test_keyboard_router.py::test_inspection_hotkeys` |
| `R-LINUX-KEY-06` | Utility Hotkeys (Undo, etc.) | `src/ui/keyboard_router.py`| `tests/unit/test_keyboard_router.py::test_utility_hotkeys` |
| `R-LINUX-KEY-07` | Text Entry Input Exception | `src/ui/keyboard_router.py`| `tests/unit/test_keyboard_router.py::test_text_entry_bypass` |
| `R-LINUX-PREF-01` | `AdwPreferencesWindow` Dialog | `src/ui/preferences.py` | `tests/unit/test_keyboard_router.py::test_utility_hotkeys` |
| `R-LINUX-PREF-02` | Configurable Preferences | `src/ui/preferences.py` | `tests/unit/test_scanner.py::test_dynamic_selection_exclusion_custom_folder` |
| `R-LINUX-PKG-01` | Debian 13 Packaging Control | `debian/control` | `tests/unit/test_packaging.py::test_debian_control_metadata` |
| `R-LINUX-PKG-02` | DFSG Compliance | `debian/copyright` | `tests/unit/test_packaging.py::test_dfsg_compliance` |
| `R-LINUX-PKG-03` | In-Repo APT Repository Tree | `apt/` structure | `tests/unit/test_packaging.py::test_apt_repository_structure` |
| `R-LINUX-PKG-04` | deb822 `.sources` Format | `apt/photo-selector.sources` | `tests/unit/test_packaging.py::test_deb822_sources_format` |
| `R-LINUX-PKG-05` | Packaging Pipeline Script | `scripts/build_deb.sh` | `tests/unit/test_packaging.py::test_package_deb_builder` |
