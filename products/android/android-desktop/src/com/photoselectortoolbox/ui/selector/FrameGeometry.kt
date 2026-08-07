package com.photoselectortoolbox.ui.selector

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

/**
 * How large the three frames are, and why that is the number it is.
 *
 * ## The one calculation this screen is built on
 *
 * Three equal 4:3 frames have to fit in the window. There are two arrangements
 * that keep them equal, and they are not close:
 *
 * ```
 * three in a row:   3w ≤ W  →  w ≤ 493 dp,  h = 370   (width binds; 554 dp of height wasted)
 * one over two:     2h ≤ H  →  h ≤ 462 dp,  w = 616   (height binds; 124 dp of width spare)
 * ```
 *
 * On a 1480 × 924 dp tablet the row arrangement is width-bound and abandons 60 %
 * of the display's height *by construction* — no amount of trimming chrome
 * fixes it. One over two is height-bound and uses all of it, for 56 % more area
 * per frame before any chrome is considered.
 *
 * The consequence is the layout rule for the whole screen: **height is the
 * scarce axis, width is surplus.** Anything that consumes height costs 2 dp of
 * frame height for every 3 dp it occupies, on all three frames at once. Anything
 * that consumes width, up to the slack, is free. That is why the readouts and
 * the controls flank the current frame instead of stacking above and below it,
 * why the sidebar can afford words, and why there is no app bar.
 *
 * ## Why this is a solver and not an `aspectRatio` modifier
 *
 * `Modifier.fillMaxHeight().aspectRatio(4f / 3f)` inside a weighted `Row`
 * resolves the *width* constraint first by default, so each tile derives its own
 * height and a wide frame ends up taller than a narrow one. The difference is
 * small enough to survive code review and eyeballing while quietly defeating the
 * comparison the screen exists for — that is the 2026-07-27 lesson in
 * `ai/memory/palette.md`. Computing one [DpSize] here and handing the same value
 * to all three tiles removes the constraint-order question entirely.
 */
object FrameGeometry {

    /** Gap between the two neighbour frames, and between the rows. */
    val Gap: Dp = 8.dp

    /** Padding around the whole image region. */
    val OuterPadding: Dp = 8.dp

    /** Width of the readout block and of the control block flanking the current frame. */
    val FlankWidth: Dp = 388.dp

    /** Width of a neighbour's value overlay. */
    val OverlayWidth: Dp = 148.dp

    /**
     * Free width beside a neighbour at which its values move out of the frame.
     *
     * A measurement rather than a hard-coded choice: on the reference tablet the
     * bottom row leaves 84 dp per side so the values overlay the photograph, but
     * in a wide DeX window or tablet portrait they sit outside it, and the same
     * code decides both.
     */
    val OverlayOutsideThreshold: Dp = OverlayWidth + 8.dp

    /** Landscape frames are 4:3; portrait frames are the reciprocal. */
    const val LandscapeAspect: Float = 4f / 3f
    const val PortraitAspect: Float = 3f / 4f

    /**
     * The size all three frames share, for a region of [regionWidth] ×
     * [regionHeight] and a frame aspect of [aspect].
     *
     * Both constraints are evaluated and the binding one wins — which one that
     * is depends on the window, and assuming either is how this screen broke
     * before. In the maximised state ([rows] = 1, [columns] = 1) the same
     * function gives the single-frame size, so there is one geometry rule for
     * both states rather than two that can disagree.
     */
    fun frameSize(
        regionWidth: Dp,
        regionHeight: Dp,
        aspect: Float = LandscapeAspect,
        rows: Int = 2,
        columns: Int = 2,
    ): DpSize {
        val availableWidth = (regionWidth - Gap * (columns - 1)).coerceAtLeast(0.dp)
        val availableHeight = (regionHeight - Gap * (rows - 1)).coerceAtLeast(0.dp)

        val widthBound = availableWidth / columns
        val heightBound = availableHeight / rows

        // The frame is the smaller of "as wide as the columns allow" and "as
        // wide as the row height allows once the aspect ratio is applied".
        val width = minOf(widthBound, heightBound * aspect)
        val height = width / aspect

        return DpSize(width.coerceAtLeast(0.dp), height.coerceAtLeast(0.dp))
    }

    /**
     * The size of a frame filling the whole region, for the maximised state.
     *
     * On the reference tablet this is 1211 × 908 dp against 600 × 450 in
     * three-up — 4.1× the area — which is what "as large as possible" means once
     * the other two frames are off screen.
     */
    fun maximisedFrameSize(
        regionWidth: Dp,
        regionHeight: Dp,
        aspect: Float = LandscapeAspect,
    ): DpSize = frameSize(regionWidth, regionHeight, aspect, rows = 1, columns = 1)

    /**
     * Whether a neighbour's values fit beside its frame rather than on it.
     *
     * [rowWidth] is the full width available to the bottom row and [frameWidth]
     * the width the two frames actually take; what is left over is split between
     * them.
     */
    fun overlayFitsOutside(rowWidth: Dp, frameWidth: Dp): Boolean {
        val leftover = rowWidth - (frameWidth * 2) - Gap
        return leftover / 2 >= OverlayOutsideThreshold
    }

    /**
     * The minimum frame height the reference device must produce.
     *
     * Asserted by a UI test. A layout change that quietly reintroduces a top
     * bar, a bottom action row or a full-width filmstrip will drop below this,
     * and the point is that it fails the build rather than waiting to be noticed
     * by eye — the previous two revisions of this screen were both shipped with
     * frames far smaller than the display allowed.
     */
    val MinimumReferenceFrameHeight: Dp = 440.dp
}
