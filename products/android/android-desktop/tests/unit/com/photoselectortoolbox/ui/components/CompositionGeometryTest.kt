package com.photoselectortoolbox.ui.components

import com.photoselectortoolbox.domain.model.SpiralOrientation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.math.abs

/**
 * Unit tests verifying vector composition geometry mathematics, aspect ratio bounding box solvers,
 * Golden Spiral 4-way orientation matrix flips, and diagonal orthogonality.
 */
class CompositionGeometryTest {

    // ── 1. Aspect Ratio Bounding Box Solver Tests ─────────────────────────────

    @Test
    fun `aspect ratio solver letterbox when container is taller than image`() {
        // Container: 1000 x 1000 (aspect 1.0)
        // Image: 3:2 (aspect 1.5) -> container is taller than image (1.0 < 1.5)
        // Expected: width binds to 1000, height = 1000 / 1.5 = 666.67
        // top = (1000 - 666.67) / 2 = 166.67, left = 0
        val bounds = CompositionGeometry.calculateDisplayedBounds(
            containerWidth = 1000f,
            containerHeight = 1000f,
            aspectRatio = 1.5f
        )

        assertEquals(1000f, bounds.width, 1e-3f)
        assertEquals(1000f / 1.5f, bounds.height, 1e-3f)
        assertEquals(0f, bounds.left, 1e-3f)
        assertEquals((1000f - 1000f / 1.5f) / 2f, bounds.top, 1e-3f)
    }

    @Test
    fun `aspect ratio solver pillarbox when container is wider than image`() {
        // Container: 1600 x 800 (aspect 2.0)
        // Image: 4:3 (aspect 1.3333334) -> container is wider than image (2.0 > 1.333)
        // Expected: height binds to 800, width = 800 * (4/3) = 1066.67
        // left = (1600 - 1066.67) / 2 = 266.67, top = 0
        val aspect = 4f / 3f
        val bounds = CompositionGeometry.calculateDisplayedBounds(
            containerWidth = 1600f,
            containerHeight = 800f,
            aspectRatio = aspect
        )

        assertEquals(800f, bounds.height, 1e-3f)
        assertEquals(800f * aspect, bounds.width, 1e-3f)
        assertEquals(0f, bounds.top, 1e-3f)
        assertEquals((1600f - 800f * aspect) / 2f, bounds.left, 1e-3f)
    }

    @Test
    fun `aspect ratio solver handles portrait image in landscape container`() {
        // Container: 1920 x 1080 (16:9 landscape)
        // Image: 2:3 portrait (aspect ~0.66667)
        // Height binds to 1080, width = 1080 * (2/3) = 720
        // left = (1920 - 720) / 2 = 600, top = 0
        val aspect = 2f / 3f
        val bounds = CompositionGeometry.calculateDisplayedBounds(
            containerWidth = 1920f,
            containerHeight = 1080f,
            aspectRatio = aspect
        )

        assertEquals(1080f, bounds.height, 1e-3f)
        assertEquals(720f, bounds.width, 1e-3f)
        assertEquals(600f, bounds.left, 1e-3f)
        assertEquals(0f, bounds.top, 1e-3f)
    }

    @Test
    fun `aspect ratio solver rejects non-positive dimensions`() {
        try {
            CompositionGeometry.calculateDisplayedBounds(0f, 100f, 1.5f)
            fail("Should throw IllegalArgumentException for zero width")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("positive"))
        }

        try {
            CompositionGeometry.calculateDisplayedBounds(100f, -10f, 1.5f)
            fail("Should throw IllegalArgumentException for negative height")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("positive"))
        }

        try {
            CompositionGeometry.calculateDisplayedBounds(100f, 100f, 0f)
            fail("Should throw IllegalArgumentException for non-positive aspect")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("positive"))
        }
    }

    // ── 2. Rule of Thirds Tests ───────────────────────────────────────────────

    @Test
    fun `rule of thirds lines and power points exact fractional positions`() {
        val w = 900f
        val h = 600f

        val lines = CompositionGeometry.getRuleOfThirdsLines(w, h)
        assertEquals(4, lines.size)

        // 2 vertical lines
        assertEquals(300f, lines[0].startX, 1e-3f)
        assertEquals(300f, lines[0].endX, 1e-3f)
        assertEquals(600f, lines[1].startX, 1e-3f)
        assertEquals(600f, lines[1].endX, 1e-3f)

        // 2 horizontal lines
        assertEquals(200f, lines[2].startY, 1e-3f)
        assertEquals(200f, lines[2].endY, 1e-3f)
        assertEquals(400f, lines[3].startY, 1e-3f)
        assertEquals(400f, lines[3].endY, 1e-3f)

        val powerPoints = CompositionGeometry.getRuleOfThirdsPowerPoints(w, h)
        assertEquals(4, powerPoints.size)
        assertEquals(CompositionGeometry.Point2D(300f, 200f), powerPoints[0])
        assertEquals(CompositionGeometry.Point2D(600f, 200f), powerPoints[1])
        assertEquals(CompositionGeometry.Point2D(300f, 400f), powerPoints[2])
        assertEquals(CompositionGeometry.Point2D(600f, 400f), powerPoints[3])
    }

    // ── 3. Golden Ratio (Phi Grid) Tests ──────────────────────────────────────

    @Test
    fun `golden ratio grid uses exact phi section ratios`() {
        val w = 1000f
        val h = 1000f

        val lines = CompositionGeometry.getGoldenRatioLines(w, h)
        assertEquals(4, lines.size)

        val expectedD1 = 1000f * CompositionGeometry.PHI_INV_SQ // ~381.966
        val expectedD2 = 1000f * CompositionGeometry.PHI_INV // ~618.034

        assertEquals(expectedD1, lines[0].startX, 1e-2f)
        assertEquals(expectedD2, lines[1].startX, 1e-2f)
        assertEquals(expectedD1, lines[2].startY, 1e-2f)
        assertEquals(expectedD2, lines[3].startY, 1e-2f)

        // Section sum must equal 1.0
        assertEquals(1.0f, CompositionGeometry.PHI_INV + CompositionGeometry.PHI_INV_SQ, 1e-5f)

        val pts = CompositionGeometry.getGoldenRatioIntersections(w, h)
        assertEquals(4, pts.size)
        assertEquals(expectedD1, pts[0].x, 1e-2f)
        assertEquals(expectedD1, pts[0].y, 1e-2f)
        assertEquals(expectedD2, pts[3].x, 1e-2f)
        assertEquals(expectedD2, pts[3].y, 1e-2f)
    }

    // ── 4. Fibonacci Spiral 4-Way Orientation Matrix Flips ─────────────────────

    @Test
    fun `fibonacci spiral focal eye converges into correct quadrant for all 4 orientations`() {
        val w = 1000f
        val h = 1000f

        val ptsBR = CompositionGeometry.getFibonacciSpiralPoints(SpiralOrientation.BOTTOM_RIGHT, w, h)
        val eyeBR = ptsBR.last()
        assertTrue("BR eye must have x > 0.5w", eyeBR.x > 500f)
        assertTrue("BR eye must have y > 0.5h", eyeBR.y > 500f)

        val ptsBL = CompositionGeometry.getFibonacciSpiralPoints(SpiralOrientation.BOTTOM_LEFT, w, h)
        val eyeBL = ptsBL.last()
        assertTrue("BL eye must have x < 0.5w", eyeBL.x < 500f)
        assertTrue("BL eye must have y > 0.5h", eyeBL.y > 500f)

        val ptsTR = CompositionGeometry.getFibonacciSpiralPoints(SpiralOrientation.TOP_RIGHT, w, h)
        val eyeTR = ptsTR.last()
        assertTrue("TR eye must have x > 0.5w", eyeTR.x > 500f)
        assertTrue("TR eye must have y < 0.5h", eyeTR.y < 500f)

        val ptsTL = CompositionGeometry.getFibonacciSpiralPoints(SpiralOrientation.TOP_LEFT, w, h)
        val eyeTL = ptsTL.last()
        assertTrue("TL eye must have x < 0.5w", eyeTL.x < 500f)
        assertTrue("TL eye must have y < 0.5h", eyeTL.y < 500f)
    }

    @Test
    fun `fibonacci spiral reflection matrix identities are mathematically exact`() {
        val w = 800f
        val h = 600f

        val ptsBR = CompositionGeometry.getFibonacciSpiralPoints(SpiralOrientation.BOTTOM_RIGHT, w, h)
        val ptsBL = CompositionGeometry.getFibonacciSpiralPoints(SpiralOrientation.BOTTOM_LEFT, w, h)
        val ptsTR = CompositionGeometry.getFibonacciSpiralPoints(SpiralOrientation.TOP_RIGHT, w, h)
        val ptsTL = CompositionGeometry.getFibonacciSpiralPoints(SpiralOrientation.TOP_LEFT, w, h)

        assertEquals(ptsBR.size, ptsBL.size)
        assertEquals(ptsBR.size, ptsTR.size)
        assertEquals(ptsBR.size, ptsTL.size)

        for (i in ptsBR.indices) {
            val pBR = ptsBR[i]
            val pBL = ptsBL[i]
            val pTR = ptsTR[i]
            val pTL = ptsTL[i]

            // Points must stay inside [0, w] x [0, h]
            assertTrue(pBR.x in 0f..w)
            assertTrue(pBR.y in 0f..h)

            // Reflection symmetries
            assertEquals("BL x must reflect across center", w - pBR.x, pBL.x, 1e-3f)
            assertEquals("BL y must match BR y", pBR.y, pBL.y, 1e-3f)

            assertEquals("TR x must match BR x", pBR.x, pTR.x, 1e-3f)
            assertEquals("TR y must reflect across center", h - pBR.y, pTR.y, 1e-3f)

            assertEquals("TL x must reflect across center", w - pBR.x, pTL.x, 1e-3f)
            assertEquals("TL y must reflect across center", h - pBR.y, pTL.y, 1e-3f)
        }
    }

    // ── 5. Baroque and Sinister Diagonal Orthogonality Tests ──────────────────

    @Test
    fun `baroque diagonals and reciprocal vectors are strictly orthogonal across all aspect ratios`() {
        val aspectRatios = listOf(
            Pair(1000f, 1000f), // 1:1 Square
            Pair(1500f, 1000f), // 3:2 Classic 35mm
            Pair(1600f, 900f),  // 16:9 Widescreen
            Pair(1200f, 900f),  // 4:3 Micro Four Thirds
            Pair(800f, 1200f)   // 2:3 Portrait orientation
        )

        for ((w, h) in aspectRatios) {
            val diagonals = CompositionGeometry.getBaroqueDiagonals(w, h)
            val main = diagonals[0]
            val rTL = diagonals[1]
            val rBR = diagonals[2]

            // Verify orthogonality: main diagonal dot product with reciprocals must be zero
            assertTrue(
                "Baroque main diagonal must be orthogonal to Top-Left reciprocal at aspect ${w / h}",
                CompositionGeometry.isOrthogonal(main, rTL)
            )
            assertTrue(
                "Baroque main diagonal must be orthogonal to Bottom-Right reciprocal at aspect ${w / h}",
                CompositionGeometry.isOrthogonal(main, rBR)
            )

            // Verify intersection lies on main Baroque line: x/W + y/H = 1
            val t1X = rTL.endX / w
            val t1Y = rTL.endY / h
            assertEquals(1.0f, t1X + t1Y, 1e-4f)

            val t2X = rBR.endX / w
            val t2Y = rBR.endY / h
            assertEquals(1.0f, t2X + t2Y, 1e-4f)
        }
    }

    @Test
    fun `sinister diagonals and reciprocal vectors are strictly orthogonal across all aspect ratios`() {
        val aspectRatios = listOf(
            Pair(1000f, 1000f), // 1:1
            Pair(1500f, 1000f), // 3:2
            Pair(1920f, 1080f), // 16:9
            Pair(1200f, 900f),  // 4:3
            Pair(900f, 1350f)   // 2:3 Portrait
        )

        for ((w, h) in aspectRatios) {
            val diagonals = CompositionGeometry.getSinisterDiagonals(w, h)
            val main = diagonals[0]
            val rTR = diagonals[1]
            val rBL = diagonals[2]

            assertTrue(
                "Sinister main diagonal must be orthogonal to Top-Right reciprocal at aspect ${w / h}",
                CompositionGeometry.isOrthogonal(main, rTR)
            )
            assertTrue(
                "Sinister main diagonal must be orthogonal to Bottom-Left reciprocal at aspect ${w / h}",
                CompositionGeometry.isOrthogonal(main, rBL)
            )

            // Verify intersection lies on main Sinister line: y/H = x/W -> x/W - y/H = 0
            val t3X = rTR.endX / w
            val t3Y = rTR.endY / h
            assertEquals(0.0f, t3X - t3Y, 1e-4f)

            val t4X = rBL.endX / w
            val t4Y = rBL.endY / h
            assertEquals(0.0f, t4X - t4Y, 1e-4f)
        }
    }

    // ── 6. Center Crosshair Tests ─────────────────────────────────────────────

    @Test
    fun `center crosshair passes exactly through center point with symmetric ticks`() {
        val w = 1200f
        val h = 800f

        val lines = CompositionGeometry.getCenterCrosshairLines(w, h)
        assertEquals(2, lines.size)

        val horiz = lines[0]
        val vert = lines[1]

        assertEquals(400f, horiz.startY, 1e-3f)
        assertEquals(400f, horiz.endY, 1e-3f)
        assertEquals(600f, vert.startX, 1e-3f)
        assertEquals(600f, vert.endX, 1e-3f)

        val ticks = CompositionGeometry.getCrosshairCalibrationTicks(w, h, 10f)
        assertEquals(16, ticks.size) // 4 offsets * 2 directions * 2 axes = 16 ticks
    }

    // ── 7. Adversarial & Extreme Geometry Stress Tests ─────────────────────────

    @Test
    fun `aspect ratio solver stress test with extreme ultra-wide ratios`() {
        val ultraWideRatios = listOf(
            21f / 9f,  // ~2.3333333f (Cinema 21:9)
            32f / 9f,  // ~3.5555556f (Super Ultra-wide 32:9)
            100f       // 100:1 extreme panoramic strip
        )

        val containerW = 2560f
        val containerH = 1080f

        for (aspect in ultraWideRatios) {
            val bounds = CompositionGeometry.calculateDisplayedBounds(containerW, containerH, aspect)

            // Must fit completely inside container
            assertTrue("Width must not exceed container", bounds.width <= containerW + 1e-3f)
            assertTrue("Height must not exceed container", bounds.height <= containerH + 1e-3f)
            assertTrue("Left offset must be non-negative", bounds.left >= -1e-3f)
            assertTrue("Top offset must be non-negative", bounds.top >= -1e-3f)

            // Centering invariance
            assertEquals("Horizontally centered", containerW / 2f, bounds.left + bounds.width / 2f, 1e-3f)
            assertEquals("Vertically centered", containerH / 2f, bounds.top + bounds.height / 2f, 1e-3f)

            // Exact aspect ratio preservation
            assertEquals(aspect, bounds.width / bounds.height, 1e-2f * aspect)
        }
    }

    @Test
    fun `aspect ratio solver stress test with extreme ultra-tall ratios`() {
        val ultraTallRatios = listOf(
            9f / 21f,  // ~0.42857143f (Extreme vertical 9:21)
            9f / 32f,  // ~0.28125f
            0.01f      // 1:100 extreme vertical ribbon
        )

        val containerW = 1080f
        val containerH = 2560f

        for (aspect in ultraTallRatios) {
            val bounds = CompositionGeometry.calculateDisplayedBounds(containerW, containerH, aspect)

            assertTrue("Width must not exceed container", bounds.width <= containerW + 1e-3f)
            assertTrue("Height must not exceed container", bounds.height <= containerH + 1e-3f)
            assertTrue("Left offset must be non-negative", bounds.left >= -1e-3f)
            assertTrue("Top offset must be non-negative", bounds.top >= -1e-3f)

            // Centering invariance
            assertEquals("Horizontally centered", containerW / 2f, bounds.left + bounds.width / 2f, 1e-3f)
            assertEquals("Vertically centered", containerH / 2f, bounds.top + bounds.height / 2f, 1e-3f)

            // Exact aspect ratio preservation
            assertEquals(aspect, bounds.width / bounds.height, 1e-2f * aspect)
        }
    }

    @Test
    fun `aspect ratio solver stress test with square 1 to 1 across diverse containers`() {
        // Landscape container: pillarbox
        val landBounds = CompositionGeometry.calculateDisplayedBounds(1920f, 1080f, 1.0f)
        assertEquals(1080f, landBounds.height, 1e-3f)
        assertEquals(1080f, landBounds.width, 1e-3f)
        assertEquals(0f, landBounds.top, 1e-3f)
        assertEquals((1920f - 1080f) / 2f, landBounds.left, 1e-3f)

        // Portrait container: letterbox
        val portBounds = CompositionGeometry.calculateDisplayedBounds(1080f, 1920f, 1.0f)
        assertEquals(1080f, portBounds.width, 1e-3f)
        assertEquals(1080f, portBounds.height, 1e-3f)
        assertEquals(0f, portBounds.left, 1e-3f)
        assertEquals((1920f - 1080f) / 2f, portBounds.top, 1e-3f)

        // Square container: full fill
        val sqBounds = CompositionGeometry.calculateDisplayedBounds(1000f, 1000f, 1.0f)
        assertEquals(1000f, sqBounds.width, 1e-3f)
        assertEquals(1000f, sqBounds.height, 1e-3f)
        assertEquals(0f, sqBounds.left, 1e-3f)
        assertEquals(0f, sqBounds.top, 1e-3f)
    }

    @Test
    fun `aspect ratio solver rejects NaN and Infinite values`() {
        val invalidFloats = listOf(
            Float.NaN,
            Float.NEGATIVE_INFINITY,
            -0.0001f,
            -1000f
        )

        for (bad in invalidFloats) {
            try {
                CompositionGeometry.calculateDisplayedBounds(bad, 100f, 1.5f)
                fail("Should reject bad containerWidth=$bad")
            } catch (e: IllegalArgumentException) {
                // Expected
            }

            try {
                CompositionGeometry.calculateDisplayedBounds(100f, bad, 1.5f)
                fail("Should reject bad containerHeight=$bad")
            } catch (e: IllegalArgumentException) {
                // Expected
            }

            try {
                CompositionGeometry.calculateDisplayedBounds(100f, 100f, bad)
                fail("Should reject bad aspectRatio=$bad")
            } catch (e: IllegalArgumentException) {
                // Expected
            }
        }
    }

    @Test
    fun `diagonals and reciprocals maintain strict orthogonality at extreme aspect ratios`() {
        val extremeRatios = listOf(
            Pair(2100f, 900f),  // 21:9
            Pair(3200f, 900f),  // 32:9
            Pair(900f, 2100f),  // 9:21
            Pair(1000f, 1000f), // 1:1
            Pair(5000f, 100f),  // 50:1
            Pair(100f, 5000f)   // 1:50
        )

        for ((w, h) in extremeRatios) {
            // Baroque
            val baroque = CompositionGeometry.getBaroqueDiagonals(w, h)
            assertTrue("Baroque TL orthogonal for ${w}x$h", CompositionGeometry.isOrthogonal(baroque[0], baroque[1]))
            assertTrue("Baroque BR orthogonal for ${w}x$h", CompositionGeometry.isOrthogonal(baroque[0], baroque[2]))

            // Sinister
            val sinister = CompositionGeometry.getSinisterDiagonals(w, h)
            assertTrue("Sinister TR orthogonal for ${w}x$h", CompositionGeometry.isOrthogonal(sinister[0], sinister[1]))
            assertTrue("Sinister BL orthogonal for ${w}x$h", CompositionGeometry.isOrthogonal(sinister[0], sinister[2]))
        }
    }

    @Test
    fun `fibonacci spiral points remain strictly within bounds for extreme aspect ratios`() {
        val extremeSizes = listOf(
            Pair(2100f, 900f),  // 21:9
            Pair(900f, 2100f),  // 9:21
            Pair(1000f, 1000f), // 1:1
            Pair(5000f, 100f)   // 50:1
        )

        for ((w, h) in extremeSizes) {
            for (orientation in SpiralOrientation.entries) {
                val points = CompositionGeometry.getFibonacciSpiralPoints(orientation, w, h, steps = 150)
                assertEquals(150, points.size)

                for (pt in points) {
                    assertFalse("X must not be NaN", pt.x.isNaN())
                    assertFalse("Y must not be NaN", pt.y.isNaN())
                    assertTrue("X in [0, w] was ${pt.x}", pt.x in -1e-3f..(w + 1e-3f))
                    assertTrue("Y in [0, h] was ${pt.y}", pt.y in -1e-3f..(h + 1e-3f))
                }
            }
        }
    }
}
