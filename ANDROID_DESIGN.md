# Photo Selector Toolbox — Android Design Document

## 1. Overview

The Android variant of Photo Selector Toolbox brings the core photo culling, metadata analysis, and duplicate detection workflow to Android devices. The primary target is **large tablets and Samsung DeX desktop mode** (Galaxy Tab S11 Ultra, Galaxy S25 Ultra with DeX), with a secondary **phone mode** providing a streamlined subset of features.

The Android app is a **separate implementation** within the same repository, sharing photographic domain knowledge and algorithmic specifications with the desktop Python/Tkinter application but using a fully native Android stack optimized for touch interaction, battery efficiency, and the Android storage model.

## 2. Tech Stack

| Component | Choice | Rationale |
|-----------|--------|-----------|
| **Language** | Kotlin 2.0+ | Official Android language, coroutines, null safety, concise syntax |
| **UI Framework** | Jetpack Compose + Material 3 | Declarative, adaptive layouts built-in, Samsung DeX compatible |
| **Architecture** | MVVM + Clean Architecture | ViewModels survive config changes, clear layer separation |
| **Async** | Kotlin Coroutines + Flow | Battery-efficient structured concurrency, cancellation support |
| **DI** | Hilt | Standard Android DI, integrates with ViewModel, WorkManager |
| **Image Loading** | Coil 3 | Kotlin-first, Compose integration, efficient memory/disk cache |
| **Image Analysis** | OpenCV Android SDK 4.x | Direct port of desktop algorithms (Laplacian, MAD), native performance |
| **EXIF Extraction** | AndroidX ExifInterface | Supports JPEG, DNG, CR2, NEF, ARW, RAF, ORF, RW2, PEF, SRW, WebP, HEIF |
| **Database** | Room 2.6+ | SQLite abstraction with coroutine support, compile-time query validation |
| **Charts** | Vico | Compose-native charting library, Material 3 themed |
| **Navigation** | Navigation Compose | Type-safe navigation, adaptive destinations |
| **File Access** | Storage Access Framework (SAF) + MediaStore | Works with SD cards, USB, cloud providers, network shares |
| **Background Work** | WorkManager | Battery-aware long-running scans, survives process death |

### Excluded Technologies
- **ExifTool** — Perl binary, not feasible on Android
- **Ollama / Local LLM** — Excessive battery drain, insufficient compute on mobile devices
- **matplotlib** — Python-only; replaced by Vico for Compose-native charts

## 3. Architecture

```
┌─────────────────────────────────────────────────────┐
│                    Compose UI Layer                  │
│  ┌──────────┐ ┌──────────┐ ┌────────┐ ┌──────────┐ │
│  │ Selector │ │  Stats   │ │ Dupes  │ │ Settings │ │
│  └────┬─────┘ └────┬─────┘ └───┬────┘ └────┬─────┘ │
│       │             │           │            │       │
│  ┌────┴─────────────┴───────────┴────────────┴────┐ │
│  │              ViewModels (StateFlow)            │ │
│  └────────────────────┬──────────────────────────┘ │
├───────────────────────┼─────────────────────────────┤
│                Domain Layer                         │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐            │
│  │ UseCases │ │ Analysis │ │ Grouping │            │
│  └────┬─────┘ └────┬─────┘ └────┬─────┘            │
├───────┼─────────────┼────────────┼──────────────────┤
│                Data Layer                           │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐            │
│  │   Room   │ │  EXIF    │ │   SAF    │            │
│  │  Cache   │ │ Readers  │ │ Sources  │            │
│  └──────────┘ └──────────┘ └──────────┘            │
└─────────────────────────────────────────────────────┘
```

### Layer Responsibilities

**UI Layer:** Compose screens, adaptive layouts, gesture handling, Material 3 theming. No business logic. ViewModels expose `StateFlow` consumed via `collectAsStateWithLifecycle()`.

**Domain Layer:** Pure Kotlin use cases and analysis algorithms. No Android framework dependencies (except coroutine dispatchers). Portable and unit-testable.

**Data Layer:** Android-specific implementations — Room DAOs, ExifInterface wrappers, SAF/MediaStore queries, Coil integration. Implements repository interfaces defined in the domain layer.

## 4. Adaptive Layout Strategy

The app uses Material 3 Window Size Classes to adapt across three form factors:

### 4.1 Expanded (≥840dp) — Tablet Landscape / Samsung DeX

This is the **primary target**. The layout mirrors the desktop app's capability with adaptations for touch.

```
┌─────────────────────────────────────────────────────────┐
│ NavigationRail │              Content Area               │
│                │                                         │
│  📷 Selector   │  ┌─────────────────┬──────────────────┐ │
│  📊 Stats      │  │  Image Viewer   │  Metadata Panel  │ │
│  🔍 Duplicates │  │  (large)        │  + Score Cards   │ │
│  ⚙️ Settings   │  │                 │  + Actions       │ │
│                │  ├────────┬────────┤                  │ │
│                │  │  Prev  │  Next  │                  │ │
│                │  └────────┴────────┴──────────────────┘ │
│                │  ┌─────────────────────────────────────┐ │
│                │  │  Candidate Strip (horizontal scroll)│ │
│                │  └─────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────┘
```

- **NavigationRail** on the left for tool switching
- **Two-pane content** with image viewer + metadata/actions panel
- **Previous/Next thumbnails** below the main image
- **Horizontal candidate strip** at the bottom for quick scrubbing
- Keyboard shortcuts active when hardware keyboard connected (DeX)

### 4.2 Medium (600–840dp) — Tablet Portrait / Small Tablet

```
┌──────────────────────────────┐
│ NavigationRail │   Content    │
│                │              │
│  📷            │  ┌────────┐ │
│  📊            │  │ Image  │ │
│  🔍            │  │(large) │ │
│  ⚙️            │  ├────────┤ │
│                │  │Metadata│ │
│                │  │compact │ │
│                │  ├────────┤ │
│                │  │Cand.   │ │
│                │  │strip   │ │
│                │  └────────┘ │
└──────────────────────────────┘
```

- NavigationRail with icon-only labels
- Single-pane content, metadata collapsed below image
- Candidate strip as horizontal scrollable row

### 4.3 Compact (<600dp) — Phone

```
┌──────────────────┐
│    App Bar       │
├──────────────────┤
│                  │
│   Image Viewer   │
│   (swipeable)    │
│                  │
├──────────────────┤
│ Score chips row  │
├──────────────────┤
│ Quick actions    │
│ (select/delete)  │
├──────────────────┤
│ BottomNavigation │
│ 📷  📊  🔍  ⚙️   │
└──────────────────┘
```

- **BottomNavigation** replaces NavigationRail
- Full-width swipeable image viewer (HorizontalPager)
- Metadata condensed to **score chips** (icons + values only)
- Quick action buttons (Select, Delete) always visible
- Statistics show simplified single-column charts
- Duplicate finder uses a grid with checkboxes instead of detailed comparison
- **Omitted on phone:** Focus mode comparison view, detailed EXIF metadata panel (accessible via tap-to-expand)

## 5. Feature Mapping: Desktop → Android

| Desktop Feature | Android Tablet/DeX | Android Phone | Notes |
|----------------|-------------------|---------------|-------|
| Photo Selector (review & cull) | ✅ Full | ✅ Simplified | Phone uses swipe gestures instead of prev/next panels |
| Focus Mode (comparison) | ✅ Side-by-side | ❌ Omitted | Not practical on phone screens; tablet gets swipe-between comparison |
| Fullscreen Viewer | ✅ Full + gestures | ✅ Full + gestures | Pinch-to-zoom, swipe to navigate, double-tap zoom |
| Sharpness Analysis | ✅ Full | ✅ Full | Same algorithm, WorkManager for long scans |
| Noise Analysis | ✅ Full | ✅ Full | |
| Highlight/Shadow Clipping | ✅ Full | ✅ Full | |
| Image Library Statistics | ✅ Full charts | ✅ Simplified charts | Phone shows single-column scrollable charts |
| Duplicate Finder | ✅ Full | ✅ Grid view | Phone uses compact grid with batch select |
| Move/Copy to Selection | ✅ Full | ✅ Full | SAF-based folder selection |
| Collection Sorting (RAW/JPEG) | ✅ Full | ✅ Full | |
| Image Grouping (similarity) | ✅ Full | ✅ Time-only default | Phone defaults to fast time+filename grouping |
| Persistent Score Cache | ✅ Room DB | ✅ Room DB | Same 10,000 MRU limit |
| Local AI (Ollama VLM) | ❌ Excluded | ❌ Excluded | Battery/compute constraints |
| CLI | ❌ N/A | ❌ N/A | Android has no CLI equivalent |
| Homebrew Distribution | ❌ N/A | ❌ N/A | Distributed via APK/Play Store |
| SMB Path Resolution | ❌ Excluded | ❌ Excluded | Android handles network shares via SAF providers |
| ExifTool (bundled) | ❌ Excluded | ❌ Excluded | Replaced by AndroidX ExifInterface |

## 6. Touch Interaction Design

### 6.1 Photo Selector Gestures

| Gesture | Action | Context |
|---------|--------|---------|
| Horizontal swipe | Navigate prev/next image | Image viewer |
| Vertical swipe up | Move to Selection | Image viewer (configurable) |
| Vertical swipe down | Dismiss / back | Fullscreen viewer |
| Pinch | Zoom in/out | Fullscreen viewer |
| Double-tap | Toggle zoom (fit ↔ 100%) | Fullscreen viewer |
| Long-press | Show context menu (delete, move, copy, info) | Image viewer, candidate strip |
| Tap on candidate strip | Jump to image | Candidate strip |
| Two-finger swipe | Scroll candidate strip | When strip is focused |

### 6.2 Keyboard Shortcuts (DeX / Hardware Keyboard)

When a hardware keyboard is detected, the same shortcuts as desktop apply:

| Key | Action |
|-----|--------|
| ← / → | Previous / Next image |
| Delete / Backspace | Delete (with confirmation) |
| M | Move to Selection |
| C | Copy to Selection |
| Escape | Exit fullscreen / comparison mode |
| F | Enter fullscreen viewer |
| Space | Toggle Focus/comparison mode (tablet only) |

## 7. Android-Specific UX Deviations from Desktop

### 7.1 Navigation
- **Desktop:** Menu bar with "Tools" dropdown for switching between Photo Selector, Statistics, Duplicate Finder
- **Android:** NavigationRail (tablet/DeX) or BottomNavigation (phone) — always visible, one-tap switching

### 7.2 Folder Selection
- **Desktop:** Native OS file dialog (`filedialog.askdirectory`)
- **Android:** SAF `Intent.ACTION_OPEN_DOCUMENT_TREE` — grants persistent URI permission. Recent folders shown in a quick-access list.

### 7.3 Scan Configuration
- **Desktop:** Modal dialog with checkboxes and options
- **Android (tablet):** Side sheet or dialog with Material 3 switches and sliders
- **Android (phone):** Bottom sheet with compact vertical layout

### 7.4 Progress & Status
- **Desktop:** Log tab with scrollable text output
- **Android:** Compact progress bar in app bar + expandable notification for background scans via WorkManager. Minimal log view accessible via "Details" expansion.

### 7.5 Delete Confirmation
- **Desktop:** Dialog with second Delete keypress to confirm
- **Android:** Snackbar with "Undo" (30s timeout) for single deletes. Dialog confirmation for batch deletes. Leverages Android's trash/recycle via MediaStore `createTrashRequest()` on Android 11+.

### 7.6 Settings
- **Desktop:** Embedded in scan config dialog + Help menu
- **Android:** Dedicated Settings screen following Material 3 settings patterns. Grouped sections: Storage, Analysis, Display, About.

### 7.7 Image Loading Strategy
- **Desktop:** Synchronous preload of all images in folder on selection
- **Android:** Lazy loading via Coil with progressive placeholder. Only metadata and thumbnails loaded initially. Full-resolution decoded on-demand with aggressive recycling.

## 8. Performance & Battery Strategy

### 8.1 Thread Pool Sizing
```kotlin
val analysisWorkers = minOf(4, Runtime.getRuntime().availableProcessors())
// Reduced from desktop's min(8, cpuCount + 4) to conserve battery
```

### 8.2 Image Decode Optimization
- Use `BitmapFactory.Options.inSampleSize` to decode at analysis resolution (center crop → grid doesn't need full resolution)
- Use hardware bitmaps (`Bitmap.Config.HARDWARE`) for display, software bitmaps only for analysis
- Release `Mat` objects immediately after OpenCV operations

### 8.3 Battery-Aware Scanning
- Monitor `BatteryManager.EXTRA_STATUS` during long scans
- Reduce worker thread count when battery < 20%
- Pause scan and notify user when battery < 10%
- Use `WorkManager` with battery-not-low constraint for background scans

### 8.4 Memory Management
- Coil handles display image caching with configurable memory/disk limits
- Analysis images decoded at reduced resolution (max 2048px on longest edge for analysis)
- SHA-256 hashing uses streaming `DigestInputStream` — never loads entire file into memory
- Room cursor windows for large result sets

## 9. File Access & Storage

### 9.1 Storage Access Framework (SAF)
The app uses SAF as the primary file access mechanism:
1. User selects a folder via `Intent.ACTION_OPEN_DOCUMENT_TREE`
2. App receives a content URI with persistent read/write access
3. `DocumentFile` API used to enumerate and access files
4. URI permissions persisted across app restarts

### 9.2 Selection Destination
Same concept as desktop — configurable subfolder name (default: "Selection"). When using SAF:
- If the selection folder doesn't exist under the scanned folder, create it via `DocumentFile.createDirectory()`
- RAW/JPEG/XMP sorting uses the same logic as desktop but operates on `DocumentFile` objects

### 9.3 Supported File Types
Same as desktop (`SUPPORTED_EXTENSIONS`) minus formats not readable by AndroidX ExifInterface. Additional Android-specific formats:
- HEIF/HEIC (native Android support)
- WebP (native Android support)

## 10. Theme Specification

Material 3 custom color scheme matching desktop's dark theme:

```kotlin
val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF6366F1),         // Indigo-500 (accent)
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF4F46E5), // Indigo-600 (accent hover)
    secondary = Color(0xFFA1A1AA),        // Zinc-400 (muted text)
    surface = Color(0xFF18181B),          // Zinc-900 (base background)
    surfaceVariant = Color(0xFF27272A),   // Zinc-800 (card/panel background)
    onSurface = Color(0xFFFAFAFA),        // Zinc-50 (primary text)
    onSurfaceVariant = Color(0xFFA1A1AA), // Zinc-400 (secondary text)
    outline = Color(0xFF3F3F46),          // Zinc-700 (borders)
    background = Color(0xFF18181B),       // Zinc-900
    onBackground = Color(0xFFFAFAFA),     // Zinc-50
)
```

## 11. Project Structure

```
android/
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   └── java/com/photoselectortoolbox/
│       │       ├── PhotoSelectorApp.kt          # Application class (Hilt)
│       │       ├── MainActivity.kt              # Single-activity entry
│       │       ├── data/
│       │       │   ├── model/                   # ExifData, ScanResult, etc.
│       │       │   ├── repository/              # ImageRepository, CacheRepository
│       │       │   ├── cache/                   # Room DB, DAOs, entities
│       │       │   ├── reader/                  # EXIF strategies
│       │       │   └── source/                  # MediaStore/SAF data sources
│       │       ├── domain/
│       │       │   ├── analysis/                # Sharpness, Noise, Clipping
│       │       │   ├── duplicates/              # SHA-256 detection
│       │       │   ├── grouping/                # Time/filename/dHash grouping
│       │       │   └── usecase/                 # Business logic use cases
│       │       ├── di/                          # Hilt modules
│       │       ├── ui/
│       │       │   ├── theme/                   # Material 3 theme
│       │       │   ├── navigation/              # Adaptive nav host
│       │       │   ├── selector/                # Photo selector screens
│       │       │   ├── statistics/              # Statistics screens
│       │       │   ├── duplicates/              # Duplicate finder screens
│       │       │   ├── settings/                # Settings screens
│       │       │   └── components/              # Shared composables
│       │       └── viewmodel/                   # Screen ViewModels
│       ├── test/                                # Unit tests
│       └── androidTest/                         # Instrumented tests
├── build.gradle.kts                             # Root build file
├── settings.gradle.kts
├── gradle.properties
└── gradle/
    ├── wrapper/
    │   ├── gradle-wrapper.jar
    │   └── gradle-wrapper.properties
    └── libs.versions.toml                       # Version catalog
```
