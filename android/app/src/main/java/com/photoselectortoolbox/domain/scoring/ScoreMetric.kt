package com.photoselectortoolbox.domain.scoring

import com.photoselectortoolbox.data.model.ScanResult
import java.util.Locale

/**
 * The quality metrics a scan produces, together with everything needed to
 * present them so a user can tell what a number means.
 *
 * Before this existed the UI showed a bare icon and a number ("512", "3.2"),
 * which is unreadable without knowing the codebase. Each metric now carries a
 * short on-chip label, a full name, what it measures, and — crucially — which
 * direction is good.
 *
 * Kept free of Compose/Android types so the wording and formatting are
 * unit-testable.
 */
enum class ScoreMetric(
    /** Two-to-five character label rendered on the chip itself. */
    val shortLabel: String,
    /** Full name used in the legend and in accessibility descriptions. */
    val displayName: String,
    /** What the number measures, in plain language. */
    val description: String,
    /** How to read the value. */
    val direction: ScoreDirection,
    private val format: String,
) {
    SHARPNESS(
        shortLabel = "Sharp",
        displayName = "Sharpness",
        description = "Edge contrast in the sharpest part of the frame. Low values usually mean " +
            "motion blur or a missed focus.",
        direction = ScoreDirection.HIGHER_IS_BETTER,
        format = "%.1f",
    ),
    NOISE(
        shortLabel = "Noise",
        displayName = "Noise",
        description = "Estimated sensor noise in flat areas of the image. Rises with high ISO.",
        direction = ScoreDirection.LOWER_IS_BETTER,
        format = "%.1f",
    ),
    HIGHLIGHT_CLIPPING(
        shortLabel = "Highl",
        displayName = "Highlight clipping",
        description = "Share of pixels blown out to pure white — detail that cannot be recovered.",
        direction = ScoreDirection.LOWER_IS_BETTER,
        format = "%.1f%%",
    ),
    SHADOW_CLIPPING(
        shortLabel = "Shad",
        displayName = "Shadow clipping",
        description = "Share of pixels crushed to pure black — detail that cannot be recovered.",
        direction = ScoreDirection.LOWER_IS_BETTER,
        format = "%.1f%%",
    ),
    AESTHETIC(
        shortLabel = "Aesth",
        displayName = "Aesthetic score",
        description = "On-device AI rating of overall appeal, on a 1–10 scale.",
        direction = ScoreDirection.HIGHER_IS_BETTER,
        format = "%.1f",
    );

    /** Format a raw value for display, e.g. `512.3` or `1.4%`. */
    fun format(value: Double): String = String.format(Locale.US, format, value)

    /** Pull this metric's value out of a scan result, or null if not computed. */
    fun valueOf(result: ScanResult): Double? = when (this) {
        SHARPNESS -> result.sharpnessScore
        NOISE -> result.noiseLevel
        HIGHLIGHT_CLIPPING -> result.highlightClipping
        SHADOW_CLIPPING -> result.shadowClipping
        AESTHETIC -> result.aestheticScore
    }

    /** Screen-reader / tooltip text: name, value and how to read it. */
    fun accessibilityLabel(value: Double): String =
        "$displayName ${format(value)}, ${direction.hint}"

    companion object {
        /** Metrics that actually have a value in [result], in display order. */
        fun present(result: ScanResult?): List<Pair<ScoreMetric, Double>> {
            if (result == null) return emptyList()
            return entries.mapNotNull { metric ->
                metric.valueOf(result)?.let { metric to it }
            }
        }
    }
}

/** Which way is "good" for a metric. */
enum class ScoreDirection(val hint: String) {
    HIGHER_IS_BETTER("higher is better"),
    LOWER_IS_BETTER("lower is better"),
}
