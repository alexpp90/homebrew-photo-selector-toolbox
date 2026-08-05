# Shared

Rules that must hold for **more than one product**. This directory is intentionally small.

The default is *not* shared. The three products are independent implementations that happen to
live in one repository; they share photographic concepts, not code. A rule belongs here only if
breaking it in one product would make the products inconsistent in a way users or maintainers
would notice.

## What is here

| File | Applies to | Contents |
|---|---|---|
| [`ANDROID_PLATFORM.md`](ANDROID_PLATFORM.md) | Android Desktop + PhotoTok | The Android baseline both apps obey: tech stack, EXIF extraction contract, storage and file access, project structure |
| [`FEATURE_PARITY.md`](FEATURE_PARITY.md) | All three | The feature sync policy, the permanent exclusions, and the desktop → Android feature mapping |

## What is shared in code

Exactly one module: `products/android/core/` (`:core`, `com.photoselector.core`), holding the EXIF data
class, the EXIF reader strategies and external-storage detection — five files that are
byte-for-byte identical between the two Android products.

**Admission rule.** A file belongs in `:core` when it is identical for both Android products
*and would stay identical* if either product evolved independently. Files that merely look
similar today stay duplicated on purpose. `ImageItem`, for instance, is **not** shared:
Android Desktop's carries a `scanResult` field that PhotoTok has no concept of.

`:core` must not depend on Compose, Room, OpenCV, Vico or on either app module.

The Desktop product shares no code with the Android products — only the data *contract*.
`core/models.py`'s `ExifData` and `com.photoselector.core.model.ExifData` must describe the
same fields; changing one is a cross-product change.

## What is deliberately not shared

- **Themes.** Android Desktop and PhotoTok have different visual identities. Their `Color.kt`,
  `Theme.kt` and `Type.kt` differ and will keep differing.
- **Settings screens.** Same name, different products, different content.
- **Image sources and repositories.** Android Desktop scans and caches scores; PhotoTok
  discovers progressively and never scores. The similar file names are a coincidence of
  vocabulary, not a shared abstraction.
- **Anything in `products/desktop/src/`.** The Desktop product is a separate implementation in a separate
  language.
