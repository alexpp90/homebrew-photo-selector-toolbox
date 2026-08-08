package com.photoselectortoolbox.ui.selector

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The arithmetic the whole screen rests on.
 *
 * Two revisions of this selector shipped with frames far smaller than the
 * display allowed, and neither was caught, because "the images look a bit
 * small" is not something a test suite notices. These assertions turn the
 * layout argument into numbers that fail a build.
 */
class FrameGeometryTest {

    // Galaxy Tab S11 Ultra, landscape, minus the sidebar and outer padding.
    private val referenceWidth = 1376.dp
    private val referenceHeight = 908.dp

    @Test
    fun `one over two beats a row by a wide margin on the reference device`() {
        val oneOverTwo = FrameGeometry.frameSize(
            regionWidth = referenceWidth,
            regionHeight = referenceHeight,
            columns = 2,
            rows = 2,
        )
        val inARow = FrameGeometry.frameSize(
            regionWidth = referenceWidth,
            regionHeight = referenceHeight,
            columns = 3,
            rows = 1,
        )

        val stackedArea = oneOverTwo.width.value * oneOverTwo.height.value
        val rowArea = inARow.width.value * inARow.height.value

        assertTrue(
            "one-over-two gives ${stackedArea.toInt()} dp² but a row gives ${rowArea.toInt()} dp²",
            stackedArea > rowArea * 1.4f,
        )
    }

    @Test
    fun `the reference device is height-bound, which is why chrome goes sideways`() {
        val size = FrameGeometry.frameSize(referenceWidth, referenceHeight)

        // Two rows must fit the height exactly; the width has slack left over.
        val heightUsed = size.height.value * 2 + FrameGeometry.Gap.value
        assertEquals(referenceHeight.value, heightUsed, 1f)

        val widthUsed = size.width.value * 2 + FrameGeometry.Gap.value
        assertTrue(
            "expected spare width, used ${widthUsed}dp of ${referenceWidth.value}dp",
            widthUsed < referenceWidth.value,
        )
    }

    @Test
    fun `frames clear the minimum height on the reference device`() {
        val size = FrameGeometry.frameSize(referenceWidth, referenceHeight)

        assertTrue(
            "frame is only ${size.height.value}dp tall",
            size.height >= FrameGeometry.MinimumReferenceFrameHeight,
        )
    }

    @Test
    fun `frames stay 4 to 3 whichever constraint binds`() {
        listOf(
            referenceWidth to referenceHeight,
            600.dp to 900.dp,   // narrow: width binds
            2000.dp to 700.dp,  // wide: height binds
        ).forEach { (w, h) ->
            val size = FrameGeometry.frameSize(w, h)
            val ratio = size.width.value / size.height.value

            assertEquals("aspect drifted at ${w.value}x${h.value}", 4f / 3f, ratio, 0.01f)
        }
    }

    @Test
    fun `a narrow window is width-bound and shrinks rather than overflowing`() {
        // Tablet portrait. The solver must pick the width constraint here; if
        // it assumed height binds it would return frames wider than the window.
        val size = FrameGeometry.frameSize(regionWidth = 700.dp, regionHeight = 1100.dp)

        assertTrue(size.width.value * 2 + FrameGeometry.Gap.value <= 700f)
    }

    @Test
    fun `maximising one frame is worth several times the area`() {
        val threeUp = FrameGeometry.frameSize(referenceWidth, referenceHeight)
        val maximised = FrameGeometry.maximisedFrameSize(referenceWidth, referenceHeight)

        val gain = (maximised.width.value * maximised.height.value) /
            (threeUp.width.value * threeUp.height.value)

        assertTrue("maximising only gains ${gain}x", gain > 3.5f)
    }

    @Test
    fun `portrait frames use the reciprocal aspect without breaking the fit`() {
        val size = FrameGeometry.frameSize(
            regionWidth = referenceWidth,
            regionHeight = referenceHeight,
            aspect = FrameGeometry.PortraitAspect,
        )

        assertEquals(3f / 4f, size.width.value / size.height.value, 0.01f)
        assertTrue(size.height.value * 2 + FrameGeometry.Gap.value <= referenceHeight.value + 1f)
    }

    @Test
    fun `3 to 2 photos use 3 to 2 aspect ratio without breaking the fit`() {
        val size = FrameGeometry.frameSize(
            regionWidth = referenceWidth,
            regionHeight = referenceHeight,
            aspect = 1.5f,
        )

        assertEquals(1.5f, size.width.value / size.height.value, 0.01f)
        assertTrue(size.height.value * 2 + FrameGeometry.Gap.value <= referenceHeight.value + 1f)
        // Two rows of 450dp use the full 908dp height (with gap of 8dp)
        val heightUsed = size.height.value * 2 + FrameGeometry.Gap.value
        assertEquals(referenceHeight.value, heightUsed, 1f)
    }

    @Test
    fun `neighbour values overlay the frame on the reference device`() {
        val size = FrameGeometry.frameSize(referenceWidth, referenceHeight)

        assertFalse(FrameGeometry.overlayFitsOutside(referenceWidth, size.width))
    }

    @Test
    fun `neighbour values move outside the frame when the window is wide enough`() {
        // A wide DeX window. Same rule, different answer — which is the point
        // of expressing placement as a measurement rather than a constant.
        val wide = 2400.dp
        val size = FrameGeometry.frameSize(wide, referenceHeight)

        assertTrue(FrameGeometry.overlayFitsOutside(wide, size.width))
    }

    @Test
    fun `a degenerate window produces no negative sizes`() {
        val size = FrameGeometry.frameSize(regionWidth = 0.dp, regionHeight = 0.dp)

        assertTrue(size.width.value >= 0f)
        assertTrue(size.height.value >= 0f)
    }
}
