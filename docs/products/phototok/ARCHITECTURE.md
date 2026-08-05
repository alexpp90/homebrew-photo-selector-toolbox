# PhotoTok — Architecture

> Product: **PhotoTok** (`products/android/phototok/`, `:phototok`, `com.phototok`).
> PhotoTok is an independent implementation, not a variant of Android Desktop: no
> OpenCV, no Room, no Vico, DataStore instead of a database, and a gesture-first feed
> rather than a comparison workspace.
> Architecture conventions that are enforced (typed settings, pure domain logic, image
> source abstraction, progressive discovery) are specified in
> [`REQUIREMENTS.md`](REQUIREMENTS.md) § 3.
> Visual design: [`DESIGN.md`](DESIGN.md).

## Theme Implementation

### 10.2 PhotoTok Theme (:phototok)
A modern, design-centric Material 3 custom lavender-based tonal palette for the phone client, optimized for visual appeal:

```kotlin
val PtDarkColorScheme = darkColorScheme(
    primary = Color(0xFFC0C1FF),          // PtPrimary (lavender accent)
    primaryContainer = Color(0xFF8083FF), // PtPrimaryContainer
    onPrimary = Color(0xFF1000A9),        // PtOnPrimary
    onPrimaryContainer = Color(0xFF0D0096),
    secondary = Color(0xFFC8C6C9),        // PtSecondary
    secondaryContainer = Color(0xFF47464A),
    onSecondary = Color(0xFF303033),
    onSecondaryContainer = Color(0xFFB6B4B8),
    tertiary = Color(0xFFC7C5CE),         // PtTertiary
    tertiaryContainer = Color(0xFF77767E),
    surface = Color(0xFF131316),          // PtSurface
    surfaceVariant = Color(0xFF353438),   // PtSurfaceVariant
    onSurface = Color(0xFFE4E1E6),        // PtOnSurface
    onSurfaceVariant = Color(0xFFC7C4D7), // PtOnSurfaceVariant
    outline = Color(0xFF908FA0),          // PtOutline
    outlineVariant = Color(0xFF464554),   // PtOutlineVariant
    background = Color(0xFF131316),       // PtBackground
    onBackground = Color(0xFFE4E1E6),     // PtOnBackground
    error = Color(0xFFFFB4AB),            // PtError
    errorContainer = Color(0xFF93000A),   // PtErrorContainer
    onError = Color(0xFF690005),
    onErrorContainer = Color(0xFFFFDAD6),
)
```
