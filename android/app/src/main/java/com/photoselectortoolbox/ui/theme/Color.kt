package com.photoselectortoolbox.ui.theme

import androidx.compose.ui.graphics.Color

// Zinc palette (backgrounds / text)
val Zinc50 = Color(0xFFFAFAFA)
val Zinc100 = Color(0xFFF4F4F5)
val Zinc200 = Color(0xFFE4E4E7)
val Zinc300 = Color(0xFFD4D4D8)
val Zinc400 = Color(0xFFA1A1AA)
val Zinc500 = Color(0xFF71717A)
val Zinc600 = Color(0xFF52525B)
val Zinc700 = Color(0xFF3F3F46)
val Zinc800 = Color(0xFF27272A)
val Zinc900 = Color(0xFF18181B)
val Zinc950 = Color(0xFF09090B)

// Indigo palette (accent)
val Indigo400 = Color(0xFF818CF8)
val Indigo500 = Color(0xFF6366F1)
val Indigo600 = Color(0xFF4F46E5)
val Indigo700 = Color(0xFF4338CA)

// Indigo tints used by the selector refresh
/** Text/icon colour on tonal indigo fills (buttons, active settings rows). */
val Indigo200 = Color(0xFFC7D2FE)

// Semantic
val ErrorRed = Color(0xFFEF4444)
val SuccessGreen = Color(0xFF22C55E)
val WarningAmber = Color(0xFFF59E0B)

// ── Selector design tokens ───────────────────────────────────────────────
//
// The selector refresh separates surfaces with 1dp outlines and small value
// steps rather than elevation, so it needs one surface value *between* the
// canvas (Zinc900) and the card fill (Zinc800). Semantic score colours are
// lighter than the generic ErrorRed/SuccessGreen/WarningAmber above because
// they sit on small 2dp bars and 6dp dots, where the darker variants lose
// contrast against Zinc700.

/** Subtle panel fill: details panel and the shared action-row shell. */
val PanelSurface = Color(0xFF1C1C1F)

/** Good / passing value on a score bar or dot. */
val ScoreGood = Color(0xFF34D399)

/** Middling value on a score bar or dot. */
val ScoreWarn = Color(0xFFFBBF24)

/** Poor value on a score bar or dot; also the destructive action tint. */
val ScoreBad = Color(0xFFF87171)

/** Tonal indigo fill for filled-tonal buttons at rest. */
val TonalIndigo = Color(0x296366F1)

/** Tonal indigo fill on hover / press. */
val TonalIndigoHover = Color(0x476366F1)

/** Tonal indigo fill behind an active navigation destination. */
val TonalIndigoNav = Color(0x1F6366F1)
