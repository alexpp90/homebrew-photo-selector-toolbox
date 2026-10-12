package com.photoselectortoolbox.domain.model

/**
 * Types of photographic composition vector guides supported by the inspection workstation.
 */
sealed class CompositionGuideType {
    /** Rule of thirds grid at 1/3 and 2/3 with power point nodes at intersections. */
    data object RuleOfThirds : CompositionGuideType()

    /** Golden Ratio Phi grid at 0.382 and 0.618 with harmonic intersections. */
    data object GoldenRatio : CompositionGuideType()

    /**
     * Fibonacci / Golden spiral geometry with 4-way focal eye orientation.
     *
     * @property orientation The quadrant in which the spiral converges.
     */
    data class FibonacciSpiral(
        val orientation: SpiralOrientation = SpiralOrientation.BOTTOM_RIGHT
    ) : CompositionGuideType()

    /** Dynamic symmetry armature: Baroque and Sinister corner-to-corner diagonals with reciprocal perpendiculars. */
    data object BaroqueSinister : CompositionGuideType()

    /** Geometric center crosshair with calibration ticks and leveling horizon reference line. */
    data object CrosshairHorizon : CompositionGuideType()
}
