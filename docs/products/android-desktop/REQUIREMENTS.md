# Android Desktop — Requirements

> Product: **Android Desktop** (`products/android/android-desktop/`, Gradle module `:android-desktop`, package
> `com.photoselectortoolbox`). Target: Samsung DeX, large tablets (≥ 840 dp), Chromebooks.
> Scope: this file is authoritative for the Android Desktop app only.
> Requirements it shares with PhotoTok (Android platform baseline, EXIF, storage) live in
> [`../../shared/ANDROID_PLATFORM.md`](../../shared/ANDROID_PLATFORM.md); build and release
> rules in [`../../build/ANDROID_BUILD_AND_RELEASE.md`](../../build/ANDROID_BUILD_AND_RELEASE.md).
> Visual design: [`DESIGN.md`](DESIGN.md). Architecture: [`ARCHITECTURE.md`](ARCHITECTURE.md).

## 1. Platform Targets & Tech Stack

*   **Primary Targets:** Samsung Galaxy Tab S11 Ultra (tablet), Samsung Galaxy S25 Ultra with DeX (desktop mode). The application must provide a feature-rich experience on large screens (≥840dp) that approaches desktop parity.
*   **Secondary Target:** Phone form factor (<600dp) with a streamlined UI optimized for touch-first interaction.
*   **Language & Framework:** Kotlin 2.0+ with Jetpack Compose and Material Design 3.
*   **Architecture:** MVVM with Clean Architecture (UI → ViewModel → UseCase → Repository → DataSource). All layers are separated by interfaces.
*   **Async Model:** Kotlin Coroutines with Flow for reactive data streams. Use `Dispatchers.IO` for file I/O, `Dispatchers.Default` for CPU-intensive analysis. Never block the main thread.
*   **Dependency Injection:** Hilt for all injectable classes.
*   **Android SDK:** minSdk 26, targetSdk 36, compileSdk 36.

## 2. Adaptive Layout Requirements

*   **Window Size Classes:** Every screen must support three `WindowWidthSizeClass` breakpoints:
    *   **Compact (<600dp):** Single-pane layouts, BottomNavigation, simplified controls, swipe-based navigation.
    *   **Medium (600–840dp) and Expanded (≥840dp):** NavigationRail with two selectable comparison layouts: a stacked "focused" layout (large Current image on top, Previous/Next side-by-side below) and a three-column side-by-side layout showing Previous, Current, and Next images in equal dimensions with filenames, compact EXIF metadata, quality scores, and actions (Move, Copy, Delete under/around the Current active image).
    *   **Default Comparison Layout:** The Medium/Expanded selector must default to the space-efficient stacked "focused" layout. The chosen layout is persisted (DataStore) and restored across launches. A clearly visible, comfortably-sized (≥48dp touch target) toggle control switches between the stacked and three-column layouts.
    *   **Action Button Sizing:** Move/Copy/Delete/Fullscreen controls in the comparison layouts must use comfortably-sized touch targets (≥48dp) and be positioned around — not overlapping — the images.
    *   **No Overlapping Controls:** The layout toggle must never be rendered as a free-floating overlay on top of another layout's control cluster. Each comparison layout owns the toggle inside its own control group: the focused layout places it in the right-hand action rail, the three-column layout places it in the Move/Copy/Delete action row. No two interactive controls may share screen bounds.
    *   **Focused Layout Space Efficiency:** The focused layout must maximise image area on tablet-class screens (reference device: Galaxy Tab S11 Ultra, 1480×924dp, 16:10, predominantly 4:3 photos). A 4:3 image fitted into a full-width region is height-limited and leaves an empty letterbox column on each side; the controls must occupy that dead space as two 56dp-wide vertical rails — Previous/Next plus a vertically stacked position counter on the left, image actions on the right (Move, Copy, Delete, Fullscreen · divider · layout toggle, details toggle, filmstrip toggle) — so they consume no vertical space and never cover a photo. Rails are 1dp-outlined, 10dp radius, with 48dp controls at 8dp radius. Outer padding is 6dp. The filmstrip and the details panel are both collapsible from the action rail so the photographs can use the full height.
    *   **Equal Frame Sizing (non-negotiable):** In the focused layout the image region is a column of **two equal-height rows** (current frame + details panel on top; Previous and Next sharing the second row), so all three frames resolve to the same height. The active frame is marked **only** by a 2dp Indigo-500 border; neighbours must never be dimmed, scrimmed, shrunk or blurred — a dimmed or smaller neighbour cannot be judged for exposure or sharpness, which is the task. Image tiles are the image: each tile is `aspectRatio` 4:3 (3:4 portrait) resolved **height-first**, filling the row height, so no empty surface is ever visible around a frame. The three-column layout is the one exception: it keeps a fixed 4:3 footprint per column and pillarboxes portrait frames inside it, so a portrait frame mid-burst cannot reflow the grid.
    *   **Details Panel:** A 296dp fixed-width panel sits beside the current frame in the letterbox column (1dp outline, 10dp radius, `#1C1C1F` fill, 12dp padding): filename, then a label/value metadata grid (Shutter Speed, Aperture, Focal Length, 35mm Equiv., ISO, Lens — "Unknown" is a legitimate value), a rule, the score chip row, and an amber `error_outline` "limited metadata" marker when EXIF came from the fallback reader. Visibility is persisted (DataStore `details_visible`).
    *   **Filmstrip:** 76dp tall, 1dp top border, horizontally scrolling, 56dp-tall thumbnails (78dp wide landscape / 44dp portrait, 3dp radius, 1dp outline, .85 opacity; the current thumbnail is 2dp Indigo-500 at full opacity). Each thumbnail carries a single 6dp sharpness dot tinted by goodness and no text. With Group Similar Series on, thumbnails in the current burst carry a 2dp Indigo-600 underline and each new burst starts after a 10dp gap. A mono caption at the right end reports the visible range (`115–156 of 842`). Visibility is persisted (DataStore `filmstrip_visible`).
    *   **App Bar:** 44dp tall (below Material's default, because vertical space is the scarce axis), 1dp bottom border: folder icon (tooltip = full path) · folder name · divider · `127 / 842` in tabular mono · burst chip (`burst 3/7`, only when Group Similar Series is on) · spacer · filled-tonal **Scan Images** button · Group Similar Series toggle · score legend info (once any frame has scores) · overflow. **Scanning variant:** the Scan button is replaced in place by a `Scanning 412 / 842` counter and a red **Cancel** action, with a 2dp determinate progress line directly under the bar. A scan must never take the screen — culling continues while it runs.
    *   **First-Run Navigation Hint:** Because the layout draws no arrows over photographs, a single centred pill ("Tap either side to browse" + "Got it") is shown once, 16dp above the bottom of the image region, to teach that the neighbour tiles navigate. It is the **only** permitted image-adjacent overlay in the selector. The seen flag is persisted (`hasSeenNavHint`) and the hint never returns.
    *   **Context Menu:** Long-press or right-click on any frame opens a 230dp menu at the pointer with Move to Selection `M`, Copy to Selection `C`, Delete `Del` (destructive colouring), Photo details, and Open fullscreen `F`; 44dp rows with mono key hints.
    *   **Action Feedback:** Actions confirm through a centred snackbar 96dp above the bottom edge (min 340dp, 1dp outline, 10dp radius) carrying the message and a 2dp Indigo countdown line that drains over 30 seconds. Messages: "Moved to Selection", "Copied to Selection", "1 image deleted", "Scan complete · N images analysed".
    *   **Motion:** 150–200ms standard easing; score bar widths animate over 180ms. Image transitions **crossfade only** — never slide, scale or parallax. The user is judging sharpness, and movement lies.
*   **Samsung DeX:** Treat as Expanded window size class. Support resizable windows. Enable hardware keyboard shortcuts in both standard and fullscreen modes: `←`/`→` previous/next · `M` move · `C` copy · `Del`/`Backspace` delete · `F` fullscreen · `Space` toggle comparison layout · `Esc` exit fullscreen / close sheet / close menu. **Shortcuts are suppressed while any sheet or dialog is open, with the sole exception of `Esc`** — a stray `M` while configuring a scan must not move the frame behind the sheet. Key hints appear on hover only, as 9dp mono superscripts that fade in over 150ms.
*   **Edge-to-Edge:** All screens must use `enableEdgeToEdge()` and properly handle `WindowInsets` for system bar padding.
*   **DeX Metadata & Layout Limits:** The manifest must include `com.samsung.android.keepalive.density` metadata and `resizeableActivity="true"`. To optimize desktop window launching (e.g. on 10-inch Chromebooks or DeX), explicit layout size limits (default width 1024dp, default height 768dp, min width 800dp, min height 600dp) are configured.

## 3. Feature Set & Exclusions

The Android app includes all desktop features except:
*   **Excluded:** Local AI Aesthetic Evaluation via **Ollama VLM** — battery drain and insufficient compute on mobile. (The Android Desktop app instead offers a lightweight on-device **TFLite NIMA** aesthetic score — see below.)
*   **Android Desktop — On-Device Aesthetic Score:** An opt-in ("AI Aesthetic Score (beta)" in the scan configuration) on-device aesthetic score computed by a small NIMA (MobileNet) model via TensorFlow Lite, normalized to a 1.0–10.0 scale and cached in Room (schema v2 `aestheticScore` column). It is gated behind the cheap OpenCV sharpness metric (only sharp images are scored) to protect battery, and degrades gracefully (no score shown) when no model asset is bundled. Surfaced as an "Aesthetic" `ScoreChip` in the comparison layouts and the fullscreen viewer. **PhotoTok / PhotoTok remains fully excluded** from aesthetic scoring to keep the phone client lightweight.
*   **Excluded:** CLI interface — not applicable on Android.
*   **Excluded:** Homebrew distribution — uses APK/Play Store instead.
*   **Excluded:** ExifTool bundling — replaced by AndroidX ExifInterface.
*   **Excluded:** SMB path resolution — Android handles network shares via SAF providers.

## 4. Touch, Pointer & Keyboard Interaction

*   **Photo Navigation:**
    - **Tablet / Large Screens:** Tap on prev/next thumbnails or use side controls.
*   **Fullscreen Viewer / Image Zoom:**
    - **Tablet / Desktop Fullscreen:** Pinch-to-zoom, double-tap to toggle fit/100%, swipe-down to dismiss, horizontal swipe to navigate.
*   **Minimum Touch Target:** 48dp for all interactive elements.
*   **Score Badge Legibility (Android Desktop):** Post-scan quality badges must not be bare icon-and-number pairs. Every metric is defined once in `com.photoselectortoolbox.domain.scoring.ScoreMetric` (short chip label, display name, plain-language description, value format, whether higher or lower is better, and its normalisation range) and rendered via the shared `ScoreChip`/`ScoreChipRow` components. Chips show icon + short label + formatted value; the label may be dropped only where space is genuinely unavailable (thumbnail overlays, fullscreen), in which case the accessibility description still carries the full name, value and direction. Value formatting must be `Locale.US` so decimal separators do not vary by device locale. An info action in the selector app bar — shown once any image has scores — opens a legend sheet explaining every metric and its direction, closing with a worked three-chip example captioned "Compare the bars, not the numbers."
*   **Score Direction Bar & Best-of-Three (Android Desktop):** Every chip renders a **2dp direction bar** beneath its value. `ScoreMetric.goodness(value)` normalises the raw value onto 0..1 where 1 is always good regardless of which way the underlying number runs (sharpness 5–100 higher-better; noise 0.2–7, highlight 0–10, shadow 0–14 lower-better; aesthetic 1–10 higher-better). Bar length is `6% + goodness × 94%` on a Zinc-700 track and is tinted `#34D399 ≥ .62 / #FBBF24 ≥ .33 / #F87171` — so three frames are comparable across metrics on wildly different scales without reading digits. The frame holding the best value for a metric **among the frames currently visible** gets a 5dp Indigo dot after its value; `bestIndexOf` returns null when fewer than two frames carry a value ("best of one" is not information) and resolves ties to the earliest frame so the marker cannot flicker. The accessibility label states the name, the value, the direction, and "best of the three visible frames" where applicable. A frame with no scores shows one dashed "Not scanned" ghost chip rather than an empty row.
*   **Context Menus:** Long-press activated. No hover-dependent interactions.
*   **Desktop/Input Enhancements**: Mouse pointer cursors automatically display hand pointer shapes (`PointerIcon.Hand`) when hovering over interactive components (buttons, clickable images, list thumbnails). Folder drag-and-drop capability is supported, enabling users to drag a photo folder from external file managers directly into the app window to load it automatically.

## 5. Image Analysis (Algorithm Parity)

All analysis algorithms must produce equivalent results to the desktop Python implementation:
*   **Sharpness:** Center 50% crop → 8×8 grid → max Laplacian variance (OpenCV Android: `Imgproc.Laplacian` with `CV_64F`).
*   **Noise:** MAD of Laplacian filter: σ = median(|∇²I - median(∇²I)|) / 0.6745.
*   **Highlight Clipping:** Percentage of grayscale pixels ≥ 254.
*   **Shadow Clipping:** Percentage of grayscale pixels ≤ 2.
*   **Duplicate Detection:** SHA-256 file content hashing via streaming `DigestInputStream`.
*   **Image Grouping:** Three levels matching desktop: Time & Filename, Time + Fast Similarity (8×8 dHash, Hamming ≤ 10), Detailed Similarity (16×16 dHash, Hamming ≤ 24).

## 6. Persistent Cache (Room)

*   **Room Database:** SQLite cache at app-internal storage storing sharpness, noise, highlight/shadow clipping scores.
*   **MRU Limit:** 10,000 entries, pruned by last access time (matching desktop).
*   **Cache-First Loading:** On folder open, restore cached values immediately from Room DB. Scores are pre-populated into `ImageItem.scanResult` on discovery, validated against file size and modification time. Manual scans skip already-cached entries.
*   **Clear Cache:** Available in Settings screen with confirmation dialog.

## 7. Performance & Battery

*   **Thread Pool:** `minOf(4, availableProcessors)` — reduced from desktop's `min(8, cpuCount + 4)` for battery conservation. Both the default value and the settings UI slider enforce a maximum of 4.
*   **Parallel Image Scanning:** Multiple images are analyzed concurrently using a `Semaphore`-gated coroutine pool (sized by the thread count setting). Within each image, sharpness, noise, and clipping analysis run in parallel via `async`/`await`. Clipping analysis (highlight + shadow) uses a combined single-pass method to avoid duplicate bitmap→Mat and grayscale conversions.
*   **Image Decoding:** Use `BitmapFactory.Options.inSampleSize` or `ImageDecoder` for memory-efficient downsampled decode. Hardware bitmaps for display, software bitmaps for analysis. Forced decoding in `ARGB_8888` for analysis to avoid pixel quantization that truncates bright highlights and dark shadows.
*   **Battery Awareness (Deferred):** Reduce worker threads when battery < 20%. Pause scan and notify when battery < 10%. Use `WorkManager` with `Constraints.Builder().setRequiresBatteryNotLow(true)` for background scans. *Status: WorkManager dependency is declared but feature is not yet implemented.*
*   **Memory Management:** Coil handles display image caching. Analysis images decoded at max 2048px on longest edge using `ImageDecoder` on API >= 28 (supporting RAW formats) and `BitmapFactory` on older APIs. SHA-256 uses streaming reads. Mat objects released immediately after use. EXIF data cache uses bounded LRU eviction (max 50 entries). dHash values are cached per grouping pass to avoid redundant bitmap decoding.

## 8. Visual Theme

*   **Dark Theme Only:** Matches desktop's dark theme. Material 3 custom `darkColorScheme`:
    *   `surface` = Zinc-900 (#18181B), `surfaceVariant` = Zinc-800 (#27272A)
    *   `primary` = Indigo-500 (#6366F1), `primaryContainer` = Indigo-600 (#4F46E5)
    *   `onSurface` = Zinc-50 (#FAFAFA), `onSurfaceVariant` = Zinc-400 (#A1A1AA)
    *   `outline` = Zinc-700 (#3F3F46)
*   **Selector Surface Tokens:** The selector refresh adds one surface value between the canvas and the card fill, plus semantic score colours lighter than the generic `ErrorRed`/`SuccessGreen`/`WarningAmber` (which lose contrast on 2dp bars and 6dp dots): `PanelSurface` #1C1C1F (details panel, action-row shell), `ScoreGood` #34D399, `ScoreWarn` #FBBF24, `ScoreBad` #F87171, `Indigo200` #C7D2FE (text on tonal fills), `TonalIndigo` rgba(99,102,241,.16) → `TonalIndigoHover` .28, `TonalIndigoNav` .12. Fullscreen canvas is Zinc-950 (#09090B), never pure black — a #000 canvas makes deep shadows in the photograph look lifted by comparison.
*   **No Elevation:** Surfaces are separated by 1dp outlines and value steps only. No drop shadows anywhere in the selector. Radii: 3 (thumbnail) · 4 (image tile) · 8 (chips, buttons) · 10 (rails, panels) · 12 (sheets, action row).
*   **Fullscreen Chrome:** Chrome auto-hides after 3s of inactivity and returns on tap, fading over 200ms. The top bar is a 52dp **bar** with a 1dp bottom border — never a gradient scrim, which would darken the sky in a way the photographer reads as part of the photograph — carrying close, filename, position and the full EXIF line plus lens. Compact score chips sit bottom-left; Move/Copy/Delete sit bottom-centre when *Show Fullscreen Action Buttons* is on. While zoomed, a 104×78dp navigator thumbnail with an Indigo viewport rectangle sits bottom-right. A one-time gesture card (persisted `seen_fullscreen_gesture_hint`) lists pinch / double-tap / swipe / Esc.
*   **Charts:** Vico library with Indigo-500 bars, Zinc-400 labels, Zinc-700 grid lines on Zinc-800 backgrounds.
*   **App Launcher Icon:** The application uses a custom app launcher icon matching the desktop logo. It specifies both legacy and adaptive versions. The adaptive icon utilizes a solid Zinc-900 background (`#18181B`) and a centered, transparent foreground logo scaled to fit within the safe zone (72dp on a 108dp canvas).
