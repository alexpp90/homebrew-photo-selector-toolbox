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

// ── Calibrated Darkroom & Studio Palette ──────────────────────────────────
//
// Calibrated for photographic evaluation on OLED / high-gamut displays:
// - Studio surfaces provide a neutral 1.5% pedestal above true black (#000)
//   without inducing color cast on adjacent skin tones or shadows.
// - Laser Cyan focus peaking accents avoid the retinal S-cone fatigue and
//   simultaneous chromatic contrast (Chevreul effect) caused by deep Indigo.
// - Curatorial Amber is reserved exclusively for keeper selection & star ratings.

/** Deep neutral canvas pedestal; zero chromatic cast. */
val StudioCanvas = Color(0xFF0C0D10)

/** Panel & readout shell background (e.g. 88dp navigation sidebar, details pane). */
val StudioPanel = Color(0xFF141519)

/** Hardware control block card fill & telemetry tiles. */
val StudioCard = Color(0xFF1C1E24)

/** Structural hairline border divider (1dp). */
val StudioBorder = Color(0xFF2D3139)

/** Active border / highlighted hairline divider (1dp). */
val StudioBorderActive = Color(0xFF434854)

// ── Laser Cyan (Focus Peaking & Optical Chrome) ───────────────────────────
/** Active 2dp hero frame border & primary CTA accent. */
val FocusCyan = Color(0xFF0284C7)

/** Optical focus diamond indicator, peaking telemetry, and glowing reticles. */
val FocusCyanGlow = Color(0xFF38BDF8)

/** Tonal cyan fill for filled-tonal buttons at rest. */
val FocusCyanTonal = Color(0x290284C7)

/** Tonal cyan fill behind active navigation destinations and rail indicators. */
val FocusCyanNav = Color(0x1F0284C7)

/** Text and icon color on tonal cyan fills. */
val FocusCyanText = Color(0xFFBAE6FD)

// ── Curatorial Amber (Keeper Triage & Ratings) ───────────────────────────
/** Keeper selection, star ratings, and curatorial actions. */
val CurationAmber = Color(0xFFF59E0B)

/** Tonal amber fill behind curatorial actions. */
val CurationAmberTonal = Color(0x24F59E0B)

// ── High-Luminance Micro-Telemetry ────────────────────────────────────────
/** Good / passing value on a 2dp score bar or 6dp dot (Emerald-400). */
val ScoreGood = Color(0xFF34D399)

/** Middling value on a 2dp score bar or 6dp dot (Amber-400). */
val ScoreWarn = Color(0xFFFBBF24)

/** Poor value on a 2dp score bar or 6dp dot; reject action tint (Rose-400). */
val ScoreBad = Color(0xFFF87171)

// Standard semantic fallbacks
val ErrorRed = Color(0xFFEF4444)
val SuccessGreen = Color(0xFF22C55E)
val WarningAmber = Color(0xFFF59E0B)

// ── Backward-compatible aliases ───────────────────────────────────────────
val PanelSurface = StudioPanel
val Indigo500 = FocusCyan
val Indigo400 = FocusCyanGlow
val Indigo600 = Color(0xFF0369A1)
val Indigo700 = Color(0xFF075985)
val Indigo200 = FocusCyanText
val TonalIndigo = FocusCyanTonal
val TonalIndigoHover = Color(0x470284C7)
val TonalIndigoNav = FocusCyanNav
