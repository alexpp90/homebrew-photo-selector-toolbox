package com.phototok.ui.theme

import androidx.compose.ui.graphics.Color

// ── M3 Tonal Palette (from design mockups) ──────────────────────────────

// Primary
val PtPrimary = Color(0xFFC0C1FF)
val PtPrimaryContainer = Color(0xFF8083FF)
val PtOnPrimary = Color(0xFF1000A9)
val PtOnPrimaryContainer = Color(0xFF0D0096)
val PtPrimaryFixed = Color(0xFFE1E0FF)
val PtPrimaryFixedDim = Color(0xFFC0C1FF)
val PtOnPrimaryFixed = Color(0xFF07006C)
val PtOnPrimaryFixedVariant = Color(0xFF2F2EBE)
val PtInversePrimary = Color(0xFF494BD6)

// Secondary
val PtSecondary = Color(0xFFC8C6C9)
val PtSecondaryContainer = Color(0xFF47464A)
val PtOnSecondary = Color(0xFF303033)
val PtOnSecondaryContainer = Color(0xFFB6B4B8)
val PtSecondaryFixed = Color(0xFFE4E1E5)
val PtSecondaryFixedDim = Color(0xFFC8C6C9)

// Tertiary
val PtTertiary = Color(0xFFC7C5CE)
val PtTertiaryContainer = Color(0xFF77767E)
val PtOnTertiary = Color(0xFF303037)
val PtOnTertiaryContainer = Color(0xFF040509)
val PtTertiaryFixed = Color(0xFFE3E1EA)
val PtTertiaryFixedDim = Color(0xFFC7C5CE)

// Surface
val PtSurface = Color(0xFF131316)
val PtSurfaceDim = Color(0xFF131316)
val PtSurfaceBright = Color(0xFF39393C)
val PtSurfaceContainerLowest = Color(0xFF0E0E11)
val PtSurfaceContainerLow = Color(0xFF1B1B1E)
val PtSurfaceContainer = Color(0xFF1F1F22)
val PtSurfaceContainerHigh = Color(0xFF2A2A2D)
val PtSurfaceContainerHighest = Color(0xFF353438)
val PtSurfaceVariant = Color(0xFF353438)
val PtSurfaceTint = Color(0xFFC0C1FF)
val PtOnSurface = Color(0xFFE4E1E6)
val PtOnSurfaceVariant = Color(0xFFC7C4D7)
val PtInverseSurface = Color(0xFFE4E1E6)
val PtInverseOnSurface = Color(0xFF303033)

// Outline
val PtOutline = Color(0xFF908FA0)
val PtOutlineVariant = Color(0xFF464554)

// Background
val PtBackground = Color(0xFF131316)
val PtOnBackground = Color(0xFFE4E1E6)

// Error
val PtError = Color(0xFFFFB4AB)
val PtErrorContainer = Color(0xFF93000A)
val PtOnError = Color(0xFF690005)
val PtOnErrorContainer = Color(0xFFFFDAD6)

// Semantic (kept for direct use in feedback animations)
val SuccessGreen = Color(0xFF22C55E)
val WarningAmber = Color(0xFFF59E0B)

// ── Legacy aliases (deprecated – migrate to PtXxx or MaterialTheme) ─────
// Kept temporarily so existing code compiles while we migrate references.

@Deprecated("Use MaterialTheme.colorScheme.primary", ReplaceWith("PtPrimary"))
val Indigo500 = PtPrimary
@Deprecated("Use MaterialTheme.colorScheme.primaryContainer", ReplaceWith("PtPrimaryContainer"))
val Indigo600 = PtPrimaryContainer
@Deprecated("Use PtPrimary", ReplaceWith("PtPrimary"))
val Indigo400 = Color(0xFF818CF8)
@Deprecated("Use PtInversePrimary", ReplaceWith("PtInversePrimary"))
val Indigo700 = PtInversePrimary

@Deprecated("Use MaterialTheme.colorScheme.onSurfaceVariant", ReplaceWith("PtOnSurfaceVariant"))
val Zinc400 = PtOnSurfaceVariant
@Deprecated("Use MaterialTheme.colorScheme.outline", ReplaceWith("PtOutline"))
val Zinc700 = PtOutlineVariant
@Deprecated("Use MaterialTheme.colorScheme.surfaceContainer", ReplaceWith("PtSurfaceContainer"))
val Zinc800 = PtSurfaceContainerHigh
@Deprecated("Use MaterialTheme.colorScheme.surface", ReplaceWith("PtSurface"))
val Zinc900 = PtSurface
@Deprecated("Use MaterialTheme.colorScheme.surfaceContainerLowest", ReplaceWith("PtSurfaceContainerLowest"))
val Zinc950 = PtSurfaceContainerLowest
@Deprecated("Use MaterialTheme.colorScheme.onSurface", ReplaceWith("PtOnSurface"))
val Zinc50 = PtOnSurface
@Deprecated("Use PtInverseSurface")
val Zinc100 = PtInverseSurface
@Deprecated("Use PtOnSurface")
val Zinc200 = Color(0xFFE4E4E7)
@Deprecated("Use PtOnSurface")
val Zinc300 = Color(0xFFD4D4D8)
@Deprecated("Use PtOutline")
val Zinc500 = PtOutline
@Deprecated("Use PtOutlineVariant")
val Zinc600 = PtOutlineVariant

@Deprecated("Use MaterialTheme.colorScheme.error", ReplaceWith("PtError"))
val ErrorRed = PtError
