package com.photoselectortoolbox.domain.analysis

import com.photoselector.core.model.ExifData
import com.photoselectortoolbox.data.model.ScanResult
import com.photoselectortoolbox.domain.format.ExifFormatter
import com.photoselectortoolbox.domain.model.CoachingTip
import com.photoselectortoolbox.domain.model.PhotoCoachReport
import com.photoselectortoolbox.domain.model.TipSeverity
import javax.inject.Inject

/**
 * Educational exposure and technique analysis engine.
 *
 * Correlates EXIF metadata (shutter speed, aperture, ISO, focal length) with measured
 * image quality metrics (sharpness, sensor noise, highlight/shadow clipping) to provide
 * actionable photography coaching tips.
 */
interface PhotoCoachEngine {
    /**
     * Evaluates photograph metadata and optical metrics, producing an educational [PhotoCoachReport].
     *
     * @param exif EXIF metadata parsed from the image file, or null if unavailable.
     * @param scanResult Image quality analysis metrics (sharpness, noise, clipping), or null if unanalyzed.
     * @return Complete evaluation report containing coaching tips and risk metrics.
     */
    fun evaluate(exif: ExifData?, scanResult: ScanResult?): PhotoCoachReport
}

/**
 * Pure Kotlin implementation of [PhotoCoachEngine] with zero Android/OpenCV dependencies.
 *
 * Implements genuine photographic heuristics:
 * 1. Reciprocal Rule ($1 / f$) camera shake risk vs measured Laplacian sharpness.
 * 2. Sensor noise & high ISO critique with aperture/shutter mitigation advice.
 * 3. Dynamic range clipping with concrete EV compensation recommendations.
 * 4. Optical diffraction warnings (f/16, f/22) and wide-open shallow DoF critiques.
 */
class PhotoCoachEngineImpl @Inject constructor() : PhotoCoachEngine {

    companion object {
        private const val SHARPNESS_BLUR_THRESHOLD = 40.0
        private const val SHARPNESS_CRITICAL_BLUR = 20.0

        private const val ISO_ELEVATED_THRESHOLD = 3200
        private const val ISO_CRITICAL_THRESHOLD = 12800

        private const val HIGHLIGHT_CLIPPING_THRESHOLD_PCT = 2.0
        private const val HIGHLIGHT_CLIPPING_SEVERE_PCT = 5.0
        private const val SHADOW_CLIPPING_THRESHOLD_PCT = 5.0

        private const val APERTURE_DIFFRACTION_THRESHOLD = 16.0
        private const val APERTURE_DIFFRACTION_CRITICAL = 22.0
        private const val APERTURE_WIDE_OPEN_THRESHOLD = 1.8
    }

    override fun evaluate(exif: ExifData?, scanResult: ScanResult?): PhotoCoachReport {
        val resolvedExif = exif ?: scanResult?.exifData
        val tips = mutableListOf<CoachingTip>()

        // 1. Reciprocal Rule & Camera Shake Risk
        val effectiveFocalLength = resolvedExif?.focalLength35mm ?: resolvedExif?.focalLength
        val safeShutterSpeed = if (effectiveFocalLength != null && effectiveFocalLength.isFinite() && effectiveFocalLength > 0.0) {
            1.0 / effectiveFocalLength
        } else {
            null
        }

        var cameraShakeRisk = 0.0f
        val shutterSpeed = resolvedExif?.shutterSpeed
        val sharpness = scanResult?.sharpnessScore

        if (shutterSpeed != null && shutterSpeed > 0.0 && safeShutterSpeed != null && effectiveFocalLength != null) {
            val ratio = shutterSpeed / safeShutterSpeed
            val formattedActual = ExifFormatter.shutterSpeed(shutterSpeed)
            val formattedSafe = ExifFormatter.shutterSpeed(safeShutterSpeed)
            val focalLengthStr = ExifFormatter.focalLength(effectiveFocalLength)

            if (ratio > 1.0001) {
                // Slower than the reciprocal rule 1/f handheld threshold
                if (sharpness != null) {
                    if (sharpness < SHARPNESS_BLUR_THRESHOLD) {
                        val isCritical = ratio >= 3.0 || sharpness < SHARPNESS_CRITICAL_BLUR
                        val severity = if (isCritical) TipSeverity.CRITICAL else TipSeverity.WARNING
                        cameraShakeRisk = if (isCritical) {
                            (0.7f + (ratio.toFloat() * 0.05f)).coerceIn(0.7f, 1.0f)
                        } else {
                            0.6f
                        }

                        tips.add(
                            CoachingTip(
                                category = "Camera Shake",
                                title = if (isCritical) "Severe Camera Shake Blur Likely" else "Camera Shake Blur Likely",
                                description = "Shutter speed ($formattedActual) was slower than the safe handheld minimum " +
                                    "($formattedSafe for $focalLengthStr) and measured sharpness is low (${sharpness.toInt()}).",
                                recommendation = "Increase shutter speed to at least $formattedSafe, open aperture, " +
                                    "or raise ISO to prevent camera shake motion blur.",
                                severity = severity
                            )
                        )
                    } else {
                        // Sharpness maintained despite slow shutter speed -> Effective stabilization
                        cameraShakeRisk = 0.1f
                        tips.add(
                            CoachingTip(
                                category = "Stabilization",
                                title = "Effective Handheld Stabilization",
                                description = "Image sharpness is high (${sharpness.toInt()}) despite shooting at $formattedActual, " +
                                    "which is slower than the recommended $formattedSafe handheld threshold for $focalLengthStr.",
                                recommendation = "Effective optical stabilization (OIS/IBIS) or steady hand support maintained sharpness.",
                                severity = TipSeverity.INFO
                            )
                        )
                    }
                } else {
                    cameraShakeRisk = (0.2f + (ratio.toFloat() * 0.1f)).coerceIn(0.3f, 0.7f)
                    tips.add(
                        CoachingTip(
                            category = "Camera Shake",
                            title = "Slow Shutter Speed Warning",
                            description = "Shutter speed ($formattedActual) is slower than the reciprocal safe threshold " +
                                "($formattedSafe for $focalLengthStr).",
                            recommendation = "Increase shutter speed to at least $formattedSafe or ensure camera stabilization is active.",
                            severity = TipSeverity.WARNING
                        )
                    )
                }
            } else {
                // Shutter speed is at or faster than safe reciprocal threshold
                cameraShakeRisk = 0.0f
                if (sharpness != null && sharpness < SHARPNESS_BLUR_THRESHOLD) {
                    tips.add(
                        CoachingTip(
                            category = "Focus",
                            title = "Soft Image at Safe Shutter Speed",
                            description = "Image is soft (${sharpness.toInt()}) despite a safe shutter speed ($formattedActual). " +
                                "Softness is likely due to missed focus or shallow depth of field rather than camera shake.",
                            recommendation = "Verify autofocus point placement or stop down aperture for greater depth of field.",
                            severity = TipSeverity.INFO
                        )
                    )
                }
            }
        }

        // 2. Sensor Noise & ISO Critique
        val iso = resolvedExif?.iso
        val noise = scanResult?.noiseLevel

        if (iso != null && iso >= ISO_ELEVATED_THRESHOLD && noise != null && noise.isFinite()) {
            val isElevated = noise > 3.0
            if (isElevated) {
                val isCritical = iso >= ISO_CRITICAL_THRESHOLD || noise >= 25.0
                val severity = if (isCritical) TipSeverity.CRITICAL else TipSeverity.WARNING
                val aperture = resolvedExif.aperture
                val apertureStr = aperture?.let { ExifFormatter.aperture(it) } ?: "current aperture"

                tips.add(
                    CoachingTip(
                        category = "Sensor Noise",
                        title = if (isCritical) "Critical High-ISO Sensor Noise" else "Elevated High-ISO Sensor Noise",
                        description = "High sensor noise ($noise) detected at ISO $iso.",
                        recommendation = "Widen aperture (currently $apertureStr) or lower shutter speed (if stabilized) " +
                            "to capture more light and lower ISO.",
                        severity = severity
                    )
                )
            }
        }

        // 3. Dynamic Range & Clipping Critique
        val highlight = scanResult?.highlightClipping
        val shadow = scanResult?.shadowClipping

        // ScanResult clipping values are percentage scale (0.0..100.0%) from ClippingAnalyzer.
        // Support fractional scale (0.0..1.0) only when unambiguous (e.g. legacy test values < 0.1).
        val hasFractionCandidate = (highlight != null && highlight.isFinite() && highlight in 0.015..0.095) ||
            (shadow != null && shadow.isFinite() && shadow in 0.015..0.095)
        val allSubTenth = (highlight == null || !highlight.isFinite() || (highlight >= 0.0 && highlight < 0.1)) &&
            (shadow == null || !shadow.isFinite() || (shadow >= 0.0 && shadow < 0.1))
        val isFractionalScale = hasFractionCandidate && allSubTenth

        val highlightPct = when {
            highlight == null -> null
            isFractionalScale -> highlight * 100.0
            else -> highlight
        }
        val shadowPct = when {
            shadow == null -> null
            isFractionalScale -> shadow * 100.0
            else -> shadow
        }

        var recommendedEv: Double? = null

        if (highlightPct != null && shadowPct != null &&
            highlightPct > HIGHLIGHT_CLIPPING_THRESHOLD_PCT &&
            shadowPct > SHADOW_CLIPPING_THRESHOLD_PCT
        ) {
            tips.add(
                CoachingTip(
                    category = "Dynamic Range",
                    title = "Scene Contrast Exceeds Dynamic Range",
                    description = "Both highlight and shadow clipping detected. The dynamic range of the scene exceeds sensor latitude.",
                    recommendation = "Recommend bracketed HDR exposure (+/- 2 EV) or graduated neutral density (GND) filter.",
                    severity = TipSeverity.WARNING
                )
            )
            recommendedEv = null
        } else if (highlightPct != null && highlightPct > HIGHLIGHT_CLIPPING_THRESHOLD_PCT) {
            val severe = highlightPct > HIGHLIGHT_CLIPPING_SEVERE_PCT
            recommendedEv = if (severe) -1.0 else -0.7
            tips.add(
                CoachingTip(
                    category = "Exposure",
                    title = "Blown Highlights Detected",
                    description = "Highlight clipping detected in bright areas of the frame. Detail in clipped highlights is permanently unrecoverable.",
                    recommendation = "Apply negative EV compensation ($recommendedEv EV) or use ETTR (Expose To The Right) to protect highlight details.",
                    severity = TipSeverity.WARNING
                )
            )
        } else if (shadowPct != null && shadowPct > SHADOW_CLIPPING_THRESHOLD_PCT) {
            recommendedEv = 0.7
            tips.add(
                CoachingTip(
                    category = "Exposure",
                    title = "Crushed Shadows Detected",
                    description = "Shadow clipping detected in dark areas of the frame. Shadow details are lost in deep blacks.",
                    recommendation = "Apply positive EV compensation (+0.7 EV) or use fill light/reflector to lift shadow details.",
                    severity = TipSeverity.WARNING
                )
            )
        }

        // 4. Optical Sweet Spot & Diffraction Warnings
        val aperture = resolvedExif?.aperture
        if (aperture != null && aperture.isFinite()) {
            if (aperture >= APERTURE_DIFFRACTION_THRESHOLD) {
                val isNarrow = aperture >= APERTURE_DIFFRACTION_CRITICAL
                tips.add(
                    CoachingTip(
                        category = "Optics",
                        title = "Optical Diffraction Softening",
                        description = "Shooting at narrow aperture (${ExifFormatter.aperture(aperture)}) causes optical diffraction, " +
                            "softening fine details across the sensor.",
                        recommendation = "Use lens sweet spot (f/5.6 - f/8) for optimal sharpness unless maximum depth of field is required.",
                        severity = if (isNarrow) TipSeverity.WARNING else TipSeverity.INFO
                    )
                )
            } else if (aperture > 0.0 && aperture <= APERTURE_WIDE_OPEN_THRESHOLD && sharpness != null && sharpness < SHARPNESS_BLUR_THRESHOLD) {
                tips.add(
                    CoachingTip(
                        category = "Optics",
                        title = "Wide-Open Softness / Shallow Depth of Field",
                        description = "Shooting wide open at ${ExifFormatter.aperture(aperture)} results in a razor-thin depth of field " +
                            "and potential optical softness.",
                        recommendation = "Stop down 1-2 stops (e.g. to f/2.8) for whole-scene sharpness, " +
                            "or verify critical focus on subject eye/details.",
                        severity = TipSeverity.INFO
                    )
                )
            }
        }

        return PhotoCoachReport(
            tips = tips,
            cameraShakeRisk = cameraShakeRisk,
            safeShutterSpeed = safeShutterSpeed,
            recommendedEvCompensation = recommendedEv
        )
    }
}
