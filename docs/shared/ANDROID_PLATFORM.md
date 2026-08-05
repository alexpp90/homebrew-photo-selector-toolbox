# Shared — Android Platform Baseline

> Applies to **both** Android products: Android Desktop (`:android-desktop`) and PhotoTok (`:phototok`).
> Anything in this file must hold for both apps. Product-specific rules belong in
> [`../products/android-desktop/`](../products/android-desktop/) or
> [`../products/phototok/`](../products/phototok/) instead — do not add single-product
> requirements here.

## 1. EXIF Extraction

*   **Primary Reader:** AndroidX ExifInterface — supports JPEG, DNG, CR2, NEF, ARW, RAF, ORF, RW2, PEF, SRW, WebP, HEIF. Employs `ParcelFileDescriptor` for local files to enable random seek access, falling back to sequential stream reads when PFD is unavailable.
*   **Fallback Reader:** MediaStore columns for basic metadata when ExifInterface fails.
*   **Standardized Keys:** Output uses the same standardized `ExifData` data class as desktop (shutter speed, aperture, focal length, focal length 35mm, ISO, lens, isFallback).
*   **Asynchronous Loading:** EXIF data for the current image and its immediate neighbors (previous/next) must be loaded dynamically and asynchronously in the background as the user navigates, updating the UI reactively on completion.

## 2. Storage & File Access

*   **Primary Access:** Storage Access Framework (SAF) via `Intent.ACTION_OPEN_DOCUMENT_TREE`. To comply with Google Play permissions policies, the application declares no global storage permissions (such as `MANAGE_EXTERNAL_STORAGE` or `READ_EXTERNAL_STORAGE`) in the manifest.
*   **Permission Persistence:** URI permissions are persisted across restarts using `ContentResolver.takePersistableUriPermission`. ViewModels must handle `SecurityException` gracefully and clear any persisted folder URI if access has been revoked or the directory is deleted.
*   **File Discovery:** `DocumentFile` API for folder traversal. Same exclusion rules as desktop: skip "Selection" and "Selected" subfolders (case-insensitive) unless specifically selected as root.
*   **Selection Destination:** Configurable subfolder name (default "Selection"). RAW/JPEG/XMP sorting into subfolders follows desktop logic. Lightroom edit files (*-Edit.*) sorted to RAW subfolder.
*   **Deletion:** Use `MediaStore.createTrashRequest()` on Android 11+ for recoverable deletion. Fall back to `DocumentFile.delete()`.

## 3. Tech Stack (both apps)

## 2. Tech Stack

| Component | Choice | Rationale |
|-----------|--------|-----------|
| **Language** | Kotlin 2.0+ | Official Android language, coroutines, null safety, concise syntax |
| **UI Framework** | Jetpack Compose + Material 3 | Declarative, adaptive layouts built-in, Samsung DeX compatible |
| **Architecture** | MVVM + Clean Architecture | ViewModels survive config changes, clear layer separation |
| **Async** | Kotlin Coroutines + Flow | Battery-efficient structured concurrency, cancellation support |
| **DI** | Hilt | Standard Android DI, integrates with ViewModel, WorkManager |
| **Image Loading** | Coil 2 | Kotlin-first, Compose integration, efficient memory/disk cache |
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
