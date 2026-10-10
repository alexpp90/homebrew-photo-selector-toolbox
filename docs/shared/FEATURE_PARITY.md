# Shared — Feature Parity & Sync Policy

> How a feature added to one product is evaluated across all five products
> (four active native solutions alongside the archived legacy Desktop).
> The at-a-glance ecosystem summary lives in the root [`README.md`](../../README.md);
> this file holds the authoritative sync policy, the Dual-Desktop Parity & Feature Triage Protocol,
> and the cross-product feature mapping matrix.

---

## 1. Feature Sync Policy

Sync is **multidirectional**. A feature or pattern proven in any of the five products is
evaluated for the others — Desktop → Android was the historical direction, but PhotoTok,
Android Desktop, macOS Desktop, and Linux Desktop originate work too, and it must travel across. The evaluation is a mandatory step of the
[`retrospective`](../../ai/skills/retrospective/SKILL.md) skill, not an optional courtesy.

When a new feature is added to any product, it must be evaluated for inclusion in the others:
*   **macOS Desktop:** Should implement natively utilizing Swift 6, SwiftUI, Accelerate, and Apple Vision.
*   **Linux Desktop:** Should implement natively utilizing Python 3.12+, PyGObject, GTK4, Libadwaita, GExiv2, and NumPy, adhering strictly to GNOME Human Interface Guidelines (HIG).
*   **Android Desktop (Tablet/DeX mode):** Should include the feature if technically feasible on Android tablets ($\ge 840$ dp) and Samsung DeX.
*   **PhotoTok (Phone mode):** Should include the feature if it works well on small portrait screens ($< 600$ dp); may omit with documented rationale.
*   **Excluded features** (e.g. Ollama VLM on mobile/macOS/Linux, ExifTool on mobile/macOS/Linux, Apple Vision on non-macOS platforms) are permanently excluded from those platforms.
*   Feature sync evaluations are documented in the mapping table below, and the resulting
    behaviour in the owning product's `docs/products/<product>/REQUIREMENTS.md`.

---

## 2. Dual-Desktop Parity & Feature Triage Protocol (macOS Desktop & Linux Desktop)

The repository provides two premier desktop workstation products:
- **macOS Desktop** (`products/macos-desktop/`, Swift 6 + SwiftUI + AppKit)
- **Linux Desktop** (`products/linux-desktop/`, Python 3.12+ + PyGObject + GTK4 / Libadwaita)

Both desktop applications serve professional and hobbyist photographers demanding extreme throughput, tactile culling, and instant decision-making on high-resolution displays. To maintain conceptual integrity and effortless muscle memory transition between Mac and Linux workstations while respecting native platform design philosophies, the following protocol governs all desktop feature planning and implementation.

### 2.1 Core Architectural Principles

1.  **Workstation Parity Without Artificial Homogeneity**:
    macOS Desktop and Linux Desktop share photographic mental models, file system contracts, optical data structures, and keyboard muscle memory. However, each is an authentic, first-class citizen of its native desktop environment. Code is **never** copied or shared between desktop products; only contracts, invariants, and behaviors are synchronized:
    *   **Sub-2ms Focus Scoring**: Optical scoring achieves $< 2.0\text{ms}$ reference compute SLA (< 2.0 ms median on dedicated CPU; CI runner tolerance up to 4.0 ms) via algorithmic acceleration: center 50% ROI cropping ($960 \times 540$) and strided low-discrepancy MAD sampling ($\le 2,048$ samples).
    *   **Dynamic Scanner Exclusion**: Directory traversal dynamically excludes both standard selection folders (`Selection`, `Selected`, `PhotoTok_Selection`, `PhotoTok_LeftSwipe`) and user-configured custom targets (`R-LINUX-PREF-02`) by exact component matching.
    *   **Two-Phase Staged Transactional Moves**: Cross-mount moves (`EXDEV`) enforce a two-phase staged protocol with atomic destination rollback on failure, guaranteeing zero source media data loss and preventing torn companion sets.
    *   **Robust Keyboard Routing**: Window-level key event controllers intercept hotkeys with case-insensitivity and modifier lock masking (`state & ~(LOCK_MASK | MOD2_MASK)`), preventing silent failure under CapsLock or NumLock.
2.  **GNOME HIG Look-and-Feel Supremacy Over Feature Bloat**:
    On Linux, user experience strictly conforms to GNOME Human Interface Guidelines (HIG) and Libadwaita conventions:
    * Clean `AdwHeaderBar` with unified window controls and view switchers.
    * `AdwPreferencesWindow` (`Ctrl+,`) for application settings.
    * Integrated Libadwaita studio dark styling via `AdwStyleManager`.
    * System font rendering, standard GNOME spacing tokens, and `AdwToast` non-blocking notifications.
    * *Core Rule*: If a secondary macOS feature or control layout would require awkward, non-native widgets, persistent drawers, or violate GNOME HIG conventions, **GNOME HIG elegance, simplicity, and distraction-free viewing take precedence over forced visual parity**.
3.  **Platform-Exclusive Capabilities Recognized**:
    Platform-specific hardware and OS frameworks (such as Apple Vision Neural Engine or Apple Accelerate) must not be forcibly replicated on Linux using brittle, bloated, or external server dependencies.

---

### 2.2 Four-Tier Feature Triage Matrix

Every desktop feature proposal or modification is classified into one of four distinct tiers:

```
┌────────────────────────────────────────────────────────────────────────┐
│                        FEATURE TRIAGE PIPELINE                         │
├────────────────────────────────────────────────────────────────────────┤
│ Tier 1: Core Photographic Invariants (Mandatory 100% Parity)          │
│  - Ingestion, companion RAW+JPEG binding, .xmp preservation,           │
│    Selection/ subfolder exclusion, atomic Move/Copy, Trash,            │
│    transactional copy-undo safety, zero-latency keyboard routing,      │
│    APEX EXIF optical metadata, focus scoring 0.0–100.0 (Laplacian+MAD).│
│  ==> RULE: Non-negotiable 100% parity across both desktops.            │
├────────────────────────────────────────────────────────────────────────┤
│ Tier 2: Platform-Native Idiomatic Parity (Adapted to Native HIG)       │
│  - Settings presentation (macOS Settings ⌘, vs AdwPreferencesWindow)   │
│  - Secondary tools access (macOS Menu Bar vs GNOME Primary Menu)       │
│  - Window chrome & studio dark palette (Apple HIG vs Libadwaita)       │
│  - System Trash (NSWorkspace vs GFile.trash / FreeDesktop Trash spec)  │
│  ==> RULE: Functional parity achieved via native platform idioms.       │
├────────────────────────────────────────────────────────────────────────┤
│ Tier 3: Platform-Exclusive Capabilities (Documented Exclusions)        │
│  - Apple Vision Neural Engine aesthetic evaluation on macOS 15+        │
│  - Linux FreeDesktop / Wayland portal & GIO integrations               │
│  ==> RULE: Formally documented in FEATURE_PARITY.md; no fake shims.    │
├────────────────────────────────────────────────────────────────────────┤
│ Tier 4: High-Friction User Consultation Triggers                       │
│  - Features requiring heavy background services or daemons             │
│  - Features introducing >100MB runtime bloat (e.g. PyTorch, Ollama)    │
│  - Controls or layouts that conflict with GNOME HIG                    │
│  ==> RULE: STOP AND CONSULT USER before implementation.                │
└────────────────────────────────────────────────────────────────────────┘
```

#### Detailed Tier Specifications

*   **Tier 1: Core Photographic Invariants (100% Mandatory Parity)**
    These invariants are non-negotiable across macOS Desktop and Linux Desktop:
    *   **Directory Ingestion**: Asynchronous directory scanning of mounted SD cards (`/Volumes/<Card>` on macOS; `/media/$USER/<Card>` or `/run/media/$USER/<Card>` on Linux) and local folders with natural alphanumeric sorting (e.g. `DSC_0002` before `DSC_0010`). Supported formats include standard bitmaps (`.jpg`, `.jpeg`, `.png`, `.heic`, `.tif`, `.tiff`) and major camera RAW formats (`.cr2`, `.cr3`, `.nef`, `.arw`, `.dng`, `.raf`, `.rw2`, `.orf`, `.pef`, `.raw`).
    *   **Companion RAW+JPEG & Sidecar Binding**: Automatic grouping of primary images with companion files in the same directory: RAW+JPEG pairs, `.xmp` sidecars (`<file>.xmp` and `<stem>.xmp`), and Lightroom derivatives (`<stem>-Edit.*`, `<stem>_Edit.*`).
    *   **Selection Subfolder Exclusion**: Strict case-insensitive exclusion of default subfolders (`Selection`, `Selected`, `PhotoTok_Selection`, `PhotoTok_LeftSwipe`) and any user-configured custom selection folder (`R-LINUX-PREF-02`), matching exact directory components to prevent circular re-scanning while preserving substring folders (`Trip_Selection/`), unless explicitly chosen as the scan root.
    *   **Atomic Move / Copy Operations**: Culling a photo via Move (`M`) or Copy (`C`) to `Selection/` operates atomically on the primary image and all bound companion files simultaneously, enforcing all-or-nothing rollback on cross-filesystem transfers (`EXDEV`) to guarantee zero source data loss and prevent torn companion bundles.
    *   **FreeDesktop / OS Trash Integration**: Trashing (`Delete` / `Backspace`) safely moves the primary image and all bound companions to the operating system trash (macOS `NSWorkspace` / `FileManager.trashItem`, Linux FreeDesktop Trash specification via `Gio.File.trash()`). Permanent file deletion is never performed without explicit user intervention.
    *   **Transactional Copy-Undo Safety**: The undo stack (`⌘Z` on macOS, `Ctrl+Z` on Linux) maintains absolute transactional safety. Undoing a copy operation strictly deletes only the destination replica in `Selection/`; original source files on SD cards or local disks are **never** touched or deleted.
    *   **Zero-Latency Keyboard Routing**: Window-level key event interception capturing keystrokes before widget focus traversal: Left/Right arrows, `H`/`L`, `J`/`K` (navigation); `M` (Move to Selection); `C` (Copy to Selection); `Delete` / `Backspace` (Trash); `1`, `2`, `3` (1-Up, 2-Up, 3-Up Focus Mode); `Space` (100% 1:1 pixel zoom toggle); `Escape` (reset zoom / dismiss); `Tab` (cycle slot focus); `F` (filmstrip toggle); `I` (HUD toggle); `S` (synchronized zoom/pan lock). Keystrokes yield to default behavior only when an editable text entry is actively focused. Keystroke evaluation must be case-insensitive (treating 'm' and 'M', 'c' and 'C' identically) and mask out modifier lock states (ignoring CapsLock and NumLock) to prevent silent culling failures.
    *   **Standardized APEX EXIF Metadata**: Standardized optical properties (`shutterSpeed`, `aperture`, `iso`, `focalLength`, `lens`, `cameraModel`, `isFallback`). Exposure values use APEX conversions when raw tags are missing ($T_v \to 2^{-T_v}$, $A_v \to 2^{A_v/2}$). Fractions format $< 1.0\text{s}$ (e.g. `1/250s`) and decimals $\ge 1.0\text{s}$ (e.g. `1.5s`). Missing tags produce clean omissions rather than "Unknown" placeholder text.
    *   **Focus Quality Scoring & Noise Subtraction**: Optical focus score normalized to 0.0–100.0 computed via 2D Laplacian convolution with 3x3 Gaussian pre-filter smoothing on a center 50% ROI proxy ($960 \times 540$), spatial variance calculation, and sensor noise floor subtraction estimated via strided low-discrepancy Median Absolute Deviation (MAD, $\le 2,048$ samples), ensuring the $< 2.0\text{ms}$ reference compute SLA is met on modern CPUs (with explicit tolerance up to $< 4.0\text{ms}$ median on shared/virtualized CI runners). Standardized thresholds:
        - **Blurry**: Score $< 35.0$
        - **Acceptable**: $35.0 \le \text{Score} < 70.0$
        - **Sharp / Crisp**: $\text{Score} \ge 70.0$
    *   **Metric Omission Rule**: Quality metrics not yet computed must be completely omitted from UI badges, cards, and HUD overlays (never showing "N/A" or "0.0" placeholders).

*   **Tier 2: Platform-Native Idiomatic Parity (Adapted to Native HIG)**
    Functional capabilities map directly to the idiomatic conventions of each desktop platform:
    *   **Visual Atmosphere**: Studio dark palette highlighting photo colors. macOS uses `.preferredColorScheme(.dark)` with system vibrancy; Linux uses `AdwStyleManager` with dark palette surfaces (`#141417`, `#1C1C20`, `#3A3A42`) and high-contrast typography (`#FFFFFF` headings, `#D9D9D9` metadata).
    *   **Window Chrome**: macOS uses native AppKit title bar and toolbar items; Linux uses standard `AdwHeaderBar` with view switcher and window buttons.
    *   **Settings Window**: macOS houses settings in the standard Settings scene (`⌘,`); Linux presents a modal `AdwPreferencesWindow` (`Ctrl+,`) using standard `AdwPreferencesGroup` and `AdwActionRow` widgets.
    *   **Secondary Utilities**: macOS exposes Duplicate Finder (`⌘D`) and Library Statistics (`⌘L`) under the Menu Bar "Tools" menu; Linux exposes them via the GNOME Primary Menu button (`AdwMenuButton`) and global accelerators (`Ctrl+D`, `Ctrl+L`).

*   **Tier 3: Platform-Exclusive Capabilities (Documented Exclusions)**
    Features leveraging platform-exclusive hardware or system runtime frameworks:
    *   **Apple Vision Aesthetic Scoring**: macOS 15+ provides `VNCalculateImageAestheticsScoresRequest` accelerated on-device via Apple Neural Engine (ANE) with zero external dependencies. Linux has no built-in operating system aesthetic scoring API.
    *   **Exclusion Policy**: We do **not** bundle gigabytes of PyTorch, CUDA, or local Ollama server dependencies into Linux Desktop. On Linux, aesthetic scoring is omitted from the core culling workflow, preserving an ultra-compact package footprint (~2 MB `.deb`), instant cold start, and sub-second execution. If aesthetic evaluation is introduced on Linux in the future, it must be an optional, lightweight plug-in (e.g. ONNX Runtime with MobileNet NIMA) that never impedes core culling throughput.

*   **Tier 4: High-Friction User Consultation Triggers**
    An explicit protocol that prevents developer agents from unilaterally introducing bloated, awkward, or non-native features to Linux.

---

### 2.3 User Consultation Protocol

When evaluating a feature for Linux Desktop or porting from macOS, the developer agent **MUST STOP AND CONSULT THE USER** if any of the following trigger conditions are met:

1.  **Heavy Dependency Trigger**: Realizing the feature on Linux requires introducing background daemons, external server runtimes (e.g. Ollama, Docker), non-standard system packages, or $> 100\text{ MB}$ runtime dependencies (e.g. PyTorch, TensorFlow) not available in the base Debian 13 (Trixie) main repository.
2.  **GNOME HIG Contradiction Trigger**: The feature demands persistent multi-column drawer sidebars, cluttered status bar panels, or non-native UI widgets that contradict GNOME HIG's minimalist, content-focused design principles.
3.  **Disproportionate Engineering Overhead Trigger**: Implementing the feature on Linux requires $> 3\times$ the implementation effort or creates ongoing maintenance risks due to platform divergences (e.g. specialized Wayland protocol extensions not universally supported).

#### Structured Consultation Template

When triggered, the agent must present the user with a structured assessment:
```
**Feature Name**: [Name of proposed feature]
**Originating Product**: [macOS Desktop / Other]
**Friction Trigger**: [Heavy Dependencies / GNOME HIG Conflict / High Overhead]
**Detailed Hurdle**: [Specific explanation of why the feature resists clean Linux implementation]
**Proposed Options**:
- Option A (Recommended): Omit from Linux Desktop; record as macOS-exclusive in FEATURE_PARITY.md.
- Option B (Lightweight Native): Implement a simplified GNOME HIG-compliant alternative.
- Option C (Full Port With Costs): Implement full feature accepting the runtime/UI tradeoffs.
**Team Recommendation**: [Explicit justification prioritizing speed, stability, and GNOME HIG elegance]
```

---

### 2.4 Comparative Desktop Subsystem Parity Table

| Subsystem / Feature | macOS Desktop (`products/macos-desktop/`) | Linux Desktop (`products/linux-desktop/`) | Triage Tier | Parity Status |
|---|---|---|---|---|
| **Tech Stack** | Swift 6 + SwiftUI + AppKit | Python 3.12+ + PyGObject + GTK4 / Libadwaita | Native Stacks | Independent native codebases |
| **Directory Ingestion** | `DirectoryScanner` async stream | Progressive asynchronous directory scanner | Tier 1 | ✅ 100% Invariant Parity |
| **Companion Pairing** | RAW+JPEG, `.xmp`, `-Edit.*` binding | RAW+JPEG, `.xmp`, `-Edit.*` binding | Tier 1 | ✅ 100% Invariant Parity |
| **Selection Subfolder Exclusion** | Case-insensitive exclusion of `Selection` & custom | Dynamic exclusion of `Selection` & custom | Tier 1 | ✅ 100% Invariant Parity |
| **Atomic Culling (M, C, Del)** | `AsyncCullingActor` FIFO queue | Asynchronous culling queue with EXDEV rollback | Tier 1 | ✅ 100% Invariant Parity |
| **Transactional Copy-Undo** | Removes Selection copy, protects source | Removes Selection copy, protects source | Tier 1 | ✅ 100% Invariant Parity |
| **Zero-Latency Keyboard Routing** | `NSEvent` local monitor + `.regular` activation policy (verified by `ui_smoke_test.sh`) | `GtkEventControllerKey` root capture | Tier 1 | ✅ 100% Invariant Parity |
| **Comparison Modes (1/2/3-Up)** | 1-Up, 2-Up, 3-Up one-over-two (`FocusTripletLayout`: current full-width top, prev bottom-left, next bottom-right) | 1-Up, 2-Up, 3-Up three columns (R-LINUX-UI-06) | Tier 1 | ◐ Gap — Linux 3-Up still three columns |
| **Boundary Slot Indicators** | `BoundarySlotPane` ("First" / "Last") | `BoundarySlotPane` ("First" / "Last") | Tier 1 | ✅ 100% Invariant Parity |
| **1:1 Actual-Pixel Zoom** | Space toggle, Escape reset | Space toggle, Escape reset | Tier 1 | ✅ 100% Invariant Parity |
| **Optical EXIF Model** | `ExifData` with APEX fallbacks | `ExposureMetadata` with APEX fallbacks | Tier 1 | ✅ 100% Invariant Parity |
| **Focus Metric (Laplacian+MAD)** | Accelerate `vImage` / `vDSP` ($< 2.0\text{ms}$) | Vectorized NumPy with 50% ROI & strided MAD ($< 2.0\text{ms}$ ref, CI tol) | Tier 1 | ✅ 100% Invariant Parity |
| **Exposure Clipping Analysis** | 256-bin hardware histogram ($\ge 254, \le 2$) | 256-bin NumPy histogram ($\ge 254, \le 2$) | Tier 1 | ✅ 100% Invariant Parity |
| **Aesthetic Scoring (AI)** | Native Apple Vision (`VNCalculateImageAestheticsScores`) | Omitted (No native OS neural engine runtime) | Tier 3 | ❌ macOS-Exclusive Capability |
| **Window Chrome & Palette** | Studio Dark `.preferredColorScheme(.dark)` | Studio Dark Libadwaita (`#141417`, `#1C1C20`) | Tier 2 | ✅ Platform HIG Idiomatic |
| **Settings Presentation** | macOS Settings Scene (`⌘,`) | `AdwPreferencesWindow` (`Ctrl+,`) | Tier 2 | ✅ Platform HIG Idiomatic |
| **Secondary Tools Access** | macOS Menu Bar "Tools" (`⌘D`, `⌘L`) | GNOME Primary Menu / Shortcuts (`Ctrl+D`, `Ctrl+L`) | Tier 2 | ✅ Platform HIG Idiomatic |
| **Duplicate Finder** | CryptoKit streaming SHA-256 | Streaming SHA-256 duplicate engine | Tier 2 | ✅ Platform HIG Idiomatic |
| **Library Statistics** | SwiftUI EXIF distribution charts | Libadwaita EXIF distribution charts | Tier 2 | ✅ Platform HIG Idiomatic |
| **Distribution Pipeline** | Homebrew Tap (`Cask` & `Formula`) | In-Repo Debian 13 APT Repository (`.deb`) | Tier 2 | ✅ Platform Native Distribution |

---

## 3. Cross-Product Feature Mapping Matrix

The full matrix covers all five products in the repository:

| Feature | Desktop (Python/Tk) | macOS Desktop (Swift) | Linux Desktop (GTK4/Adw) | Android Desktop (Tablet/DeX) | PhotoTok (Phone) | Notes |
|---|---|---|---|---|---|---|
| **Photo Selector (Review & Cull)** | ✅ Full | ✅ Full | ✅ Full | ✅ Full | ✅ Simplified | PhotoTok uses gesture feed instead of buttons |
| **Focus Mode (Comparison)** | ✅ Standard / Focus | ✅ 1-Up / 2-Up / 3-Up Triplet | ✅ 1-Up / 2-Up / 3-Up Triplet | ✅ Side-by-side | ❌ Omitted | Desktops: current on top, neighbours below (Linux pending, see §4) |
| **Fullscreen / 1:1 Pixel Zoom** | ✅ Full | ✅ Full (>85% canvas) | ✅ Full (>85% canvas) | ✅ Full + gestures | ✅ Full + gestures | Spacebar toggle on desktop; pinch on mobile |
| **Sharpness Analysis** | ✅ Full (OpenCV) | ✅ Full (Accelerate vImage) | ✅ Full (NumPy Laplacian) | ✅ Full (OpenCV) | ❌ Omitted | Noise-corrected Laplacian variance (<2ms ref SLA via 50% ROI & strided MAD, CI tol) |
| **Noise Analysis (MAD)** | ✅ Full (MAD) | ✅ Full (vImage MAD) | ✅ Full (NumPy MAD) | ✅ Full (MAD) | ❌ Omitted | Median Absolute Deviation sensor noise floor |
| **Highlight & Shadow Clipping** | ✅ Full (Grayscale) | ✅ Full (vImage 256-bin) | ✅ Full (NumPy 256-bin) | ✅ Full | ❌ Omitted | Thresholds $\ge 254$ (blown) and $\le 2$ (crushed) |
| **Library Statistics** | ✅ Full (Matplotlib) | ✅ Full (SwiftUI Charts) | ✅ Full (Libadwaita Charts) | ✅ Full (Vico) | ✅ Simplified | Optical EXIF distribution histograms |
| **Duplicate Finder** | ✅ Full (SHA-256) | ✅ Full (CryptoKit SHA-256) | ✅ Full (Streaming SHA-256) | ✅ Full | ✅ Grid view | Streaming chunked hashing with size pre-filter |
| **Move / Copy to Selection** | ✅ Full | ✅ Full (AsyncCullingActor) | ✅ Full (GIO Async) | ✅ Full (SAF) | ✅ Full (SAF) | Atomic companion syncing with rollback safety |
| **Companion Pairing (RAW/JPEG/XMP)** | ✅ Full | ✅ Full (ImageMetadataReader) | ✅ Full (GExiv2 Reader) | ✅ Full | ✅ Full | Automated companion grouping and culling |
| **Image Grouping (Similarity)** | ✅ Full (dHash) | ✅ Full (Duplicate clusters) | ✅ Full (Duplicate clusters) | ✅ Full | ✅ Time-only | Burst sequence grouping |
| **Persistent Score Cache** | ✅ SQLite MRU | ❌ (In-Memory LRU) | ❌ (In-Memory LRU) | ✅ Room DB | ❌ (Lightweight) | Desktops rely on sub-2ms compute & memory LRU |
| **Apple Vision Aesthetics** | ✅ (PyObjC Bridge) | ✅ (Pure Native ANE) | ❌ Excluded (Tier 3) | ❌ Excluded | ❌ Excluded | On-device Neural Engine via Apple Vision |
| **Local AI (Ollama VLM)** | ✅ Full | ❌ Excluded (Tier 3) | ❌ Excluded (Tier 3/4) | ❌ Excluded | ❌ Excluded | Excluded from native apps to prevent runtime bloat |
| **Zero-Latency Keyboard Routing** | ✅ Tkinter Bindings | ✅ NSEvent Local Monitor | ✅ GtkEventControllerKey | ✅ Hardware Listener | ❌ N/A (Touch) | Intercepts keys before widget focus traps |
| **Native Settings Window** | ❌ (Inline Panel) | ✅ macOS Settings (`⌘,`) | ✅ AdwPreferences (`Ctrl+,`)| ✅ Dialog | ✅ Dialog | Clean separation from main culling canvas |
| **Secondary Tools Separation** | ❌ (Main Window) | ✅ Menu Bar "Tools" | ✅ Primary Menu Button | ✅ Menu | ❌ N/A | Kept off primary workspace to prevent clutter |
| **CLI / Headless Mode** | ✅ Full | ❌ Headless Kit Only | ❌ Standalone App | ❌ N/A | ❌ N/A | Scripting entry points |
| **Package Distribution** | ✅ Brew Formula+Cask | ✅ Native Homebrew Cask | ✅ Debian 13 APT (.deb) | ❌ Play Store / APK | ❌ Play Store / APK | Platform-native package management |
| **SMB / Remote Path Resolution** | ✅ Network share path | ✅ Darwin POSIX | ✅ GIO / GVFS Mounts | ❌ (SAF Provider) | ❌ (SAF Provider) | Handled transparently by OS file system / GVFS |
| **EXIF Extraction** | ✅ Bundled ExifTool | ✅ Apple ImageIO Native | ✅ GExiv2 Native C++ | ❌ (AndroidX) | ❌ (AndroidX) | Robust optical metadata parsing with APEX math |

---

## 4. Pattern Parity: Interaction & Performance

Features are not the only thing that ports. A pattern proven in any product — an interaction
model, a data-access fix, a guidance approach — is evaluated for the other products and the
decision recorded here, including a decision not to port.

Ported means **reimplemented in the target's own stack and UX model**. Copying a file between
products is a defect ([`ai/ROUTING.md`](../../ai/ROUTING.md), the separation rule).

| Pattern | Source | Desktop | macOS Desktop | Linux Desktop | Android Desktop | PhotoTok | Decided |
|---|---|---|---|---|---|---|---|
| Progressive SAF enumeration | PhotoTok 2026-07-31 | ❌ N/A | ❌ N/A | ❌ N/A | ✅ ported 2026-08-08 | ✅ origin | 2026-08-08 |
| Optimistic filing + deferred deletion with undo | PhotoTok 2026-07-31 | ◐ partial | ✅ ported 2026-10-07 | ✅ ported 2026-10-09 | ✅ ported 2026-08-08 | ✅ origin | 2026-08-08 |
| Coach marks in place, replacing shortcut list | PhotoTok 2026-07-31 | ❌ solved differently | ❌ solved differently | ❌ solved differently | ✅ ported 2026-08-08 | ✅ origin | 2026-08-08 |
| First-run explanations derived from live settings | PhotoTok 2026-07-24 | ❌ solved differently | ❌ solved differently | ❌ solved differently | ✅ ported 2026-08-08 | ✅ origin | 2026-08-08 |
| Symmetric queuing of conflicting long passes | Desktop 2026-07-24 | ✅ origin | ❌ N/A (Actor queue) | ❌ N/A (Async queue) | ✅ ported 2026-08-08 | ❌ N/A | 2026-08-08 |
| Neighbour image prefetch | Desktop (`preload`) | ✅ origin | ✅ independent | ✅ ported 2026-10-09 | ✅ ported 2026-08-08 | ✅ independent | 2026-08-08 |
| Filing wording names configured Selection folder | Android Desktop 2026-08-08 | ◐ gap | ✅ ported 2026-10-07 | ✅ ported 2026-10-09 | ✅ origin | ◐ gap | 2026-08-08 |
| Shared "Selection" folder standard & exclusions | Cross-product 2026-10-03 | ✅ default "Selection" | ✅ default "Selection" | ✅ default "Selection" | ✅ default "Selection" | ✅ default "Selection" | 2026-10-03 |
| Bounded LRU image cache with teardown on exit | Desktop 2026-10-03 | ✅ origin | ✅ independent | ✅ ported 2026-10-09 | ✅ independent | ✅ independent | 2026-10-03 |
| Noise-robust sharpness (Laplacian + MAD noise floor) | Desktop 2026-10-05 | ✅ origin | ✅ ported 2026-10-07 | ✅ ported 2026-10-09 | ⬜ evaluate | ❌ N/A | 2026-10-05 |
| Borderless maximized preview (>80–85% window space) | macOS Desktop 2026-10-07 | ⬜ evaluate | ✅ origin | ✅ ported 2026-10-09 | ✅ independent | ❌ N/A | 2026-10-07 |
| Actor-isolated / async transactional culling undo | macOS Desktop 2026-10-07 | ⬜ evaluate | ✅ origin | ✅ ported 2026-10-09 | ⬜ evaluate | ⬜ evaluate | 2026-10-07 |
| One-over-two 3-Up (current full-width on top, previous bottom-left, next bottom-right) with boundary slots | macOS Desktop 2026-10-07, corrected 2026-10-10 | ✅ independent (top/bottom grid, REQ-DESK-VIEW.02) | ✅ origin | ◐ gap — R-LINUX-UI-06 still three columns | ✅ independent (REQ-AND-LAYOUT.03) | ❌ N/A | 2026-10-10 |
| Keyboard delivery verified in the running app (activation/focus precondition asserted, real key events posted) | macOS Desktop 2026-10-10 | ◐ partial (focus re-assert after delegation, palette 2026-07-24; no running-app test) | ✅ origin | ⬜ evaluate (Wayland focus; no real-event test) | ⬜ evaluate (hardware-keyboard instrumented test on DeX) | ❌ N/A (touch) | 2026-10-10 |
| Zoom gestures on the untransformed viewport; controls clickable while zoomed; 1:1 computed from pixel size | macOS Desktop 2026-10-10 | ❌ N/A (Tk canvas cannot overflow its widget) | ✅ origin | ⬜ evaluate | ◐ partial (clipToBounds + pointerInput(Unit), palette 2026-10-03; verify controls while zoomed and the 2.5× "100%") | ⬜ evaluate | 2026-10-10 |
| One directory listing per folder, companions grouped in memory, first batch of 1, deferred EXIF, thumbnail-first decode | macOS Desktop 2026-10-10 | ◐ mitigated (mtime-keyed lru_cache listing in `find_related_files`) | ✅ origin | ◐ gap — `scan_stream` calls `find_companion_files` per file (`os.scandir(parent)` + `Path.resolve()` per entry), first batch 30 | ✅ independent (one SAF query per directory, REQ-AND-PERF.03) | ✅ independent (SAF origin, bolt 2026-07-31) | 2026-10-10 |
| Zero-latency window-level keyboard shortcut routing | macOS Desktop 2026-10-07 | ◐ partial | ✅ origin | ✅ ported 2026-10-09 | ✅ independent | ❌ N/A | 2026-10-07 |
| Parent-scoped sibling pairing to prevent data loss | PhotoTok 2026-10-09 | ⬜ evaluate | ✅ ported 2026-10-07 | ✅ ported 2026-10-09 | ⬜ evaluate | ✅ origin | 2026-10-09 |
| Studio dark theme with pure white text hierarchy | macOS Desktop 2026-10-07 | ⬜ evaluate | ✅ origin | ✅ ported 2026-10-09 | ⬜ evaluate | ⬜ evaluate | 2026-10-07 |

---

## 5. Permanent Exclusions Rationale

To prevent future agents from re-litigating settled architectural decisions:

*   **Ollama VLM on macOS, Linux, and Mobile**: Permanently excluded from macOS Desktop, Linux Desktop, Android Desktop, and PhotoTok. Loading multi-gigabyte neural weights or demanding local daemon servers creates prohibitive battery drain, thermal throttling, and multi-gigabyte package footprints incompatible with streamlined native distributions.
*   **ExifTool on macOS, Linux, and Mobile**: Permanently excluded from macOS Desktop, Linux Desktop, Android Desktop, and PhotoTok. Native platform frameworks (Apple ImageIO on macOS, GExiv2 on Linux, AndroidX ExifInterface on Android) execute in-process without Perl runtime dependencies or child process spawning overhead.
*   **Apple Vision on Linux Desktop**: Permanently excluded as a core dependency. Apple Vision is proprietary to macOS/iOS. Linux Desktop relies on CPU-vectorized NumPy Laplacian convolution with center 50% ROI cropping and strided MAD noise subtraction ($\le 2,048$ samples), delivering $<2.0\text{ms}$ reference latency (< 4.0ms on shared CI runners) without external neural runtime bloat.
*   **Persistent SQLite/Room Score Database on Desktop Native Apps**: macOS Desktop and Linux Desktop rely on ultra-fast sub-2ms compute SLAs and bounded in-memory LRU caches. Serializing quality scores to persistent disk databases adds disk I/O contention without user-perceptible benefits for fast SD card culling workflows.
