package com.photoselectortoolbox.domain.analysis

import android.graphics.Bitmap
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc

class ClippingAnalyzerTest {

    private lateinit var analyzer: ClippingAnalyzer

    @Before
    fun setUp() {
        analyzer = spyk(ClippingAnalyzer())
        mockkStatic(Utils::class)
        mockkStatic(Imgproc::class)
        mockkStatic(Core::class)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `analyzeHighlightClipping calculates highlight clipping percentage correctly`() {
        val bitmap = mockk<Bitmap>()
        val mockMat = mockk<Mat>(relaxed = true)

        every { analyzer.createMat() } returns mockMat
        every { mockMat.total() } returns 100L
        every { mockMat.channels() } returns 4

        every { Utils.bitmapToMat(bitmap, mockMat) } returns Unit
        every { Imgproc.cvtColor(mockMat, mockMat, Imgproc.COLOR_RGBA2GRAY) } returns Unit
        every { Imgproc.threshold(mockMat, mockMat, 253.0, 255.0, Imgproc.THRESH_BINARY) } returns 253.0
        every { Core.countNonZero(mockMat) } returns 25

        val result = analyzer.analyzeHighlightClipping(bitmap)

        assertEquals(25.0, result, 1e-4)

        verify { Utils.bitmapToMat(bitmap, mockMat) }
        verify { Imgproc.threshold(mockMat, mockMat, 253.0, 255.0, Imgproc.THRESH_BINARY) }
        verify { Core.countNonZero(mockMat) }
    }

    @Test
    fun `analyzeHighlightClipping returns 0 when total pixels is 0`() {
        val bitmap = mockk<Bitmap>()
        val mockMat = mockk<Mat>(relaxed = true)

        every { analyzer.createMat() } returns mockMat
        every { mockMat.total() } returns 0L
        every { mockMat.channels() } returns 1

        every { Utils.bitmapToMat(bitmap, mockMat) } returns Unit
        every { mockMat.copyTo(mockMat) } returns Unit

        val result = analyzer.analyzeHighlightClipping(bitmap)

        assertEquals(0.0, result, 1e-4)
    }

    @Test
    fun `analyzeShadowClipping calculates shadow clipping percentage correctly`() {
        val bitmap = mockk<Bitmap>()
        val mockMat = mockk<Mat>(relaxed = true)

        every { analyzer.createMat() } returns mockMat
        every { mockMat.total() } returns 200L
        every { mockMat.channels() } returns 1

        every { Utils.bitmapToMat(bitmap, mockMat) } returns Unit
        every { mockMat.copyTo(mockMat) } returns Unit
        every { Imgproc.threshold(mockMat, mockMat, 2.0, 255.0, Imgproc.THRESH_BINARY_INV) } returns 2.0
        every { Core.countNonZero(mockMat) } returns 50 // 50 / 200 = 25%

        val result = analyzer.analyzeShadowClipping(bitmap)

        assertEquals(25.0, result, 1e-4)

        verify { Utils.bitmapToMat(bitmap, mockMat) }
        verify { Imgproc.threshold(mockMat, mockMat, 2.0, 255.0, Imgproc.THRESH_BINARY_INV) }
        verify { Core.countNonZero(mockMat) }
    }

    @Test
    fun `analyzeShadowClipping returns 0 when total pixels is 0`() {
        val bitmap = mockk<Bitmap>()
        val mockMat = mockk<Mat>(relaxed = true)

        every { analyzer.createMat() } returns mockMat
        every { mockMat.total() } returns 0L
        every { mockMat.channels() } returns 4

        every { Utils.bitmapToMat(bitmap, mockMat) } returns Unit
        every { Imgproc.cvtColor(mockMat, mockMat, Imgproc.COLOR_RGBA2GRAY) } returns Unit

        val result = analyzer.analyzeShadowClipping(bitmap)

        assertEquals(0.0, result, 1e-4)
    }

    @Test
    fun `analyzeClipping calculates both highlight and shadow clipping correctly`() {
        val bitmap = mockk<Bitmap>()
        val mockMat = mockk<Mat>(relaxed = true)

        every { analyzer.createMat() } returns mockMat
        every { mockMat.total() } returns 100L
        every { mockMat.channels() } returns 4

        every { Utils.bitmapToMat(bitmap, mockMat) } returns Unit
        every { Imgproc.cvtColor(mockMat, mockMat, Imgproc.COLOR_RGBA2GRAY) } returns Unit
        every { Imgproc.threshold(mockMat, mockMat, 253.0, 255.0, Imgproc.THRESH_BINARY) } returns 253.0
        every { Imgproc.threshold(mockMat, mockMat, 2.0, 255.0, Imgproc.THRESH_BINARY_INV) } returns 2.0

        every { Core.countNonZero(mockMat) } returns 30 andThen 10

        val (highlight, shadow) = analyzer.analyzeClipping(bitmap)

        assertEquals(30.0, highlight, 1e-4)
        assertEquals(10.0, shadow, 1e-4)

        verify { Utils.bitmapToMat(bitmap, mockMat) }
        verify { Imgproc.threshold(mockMat, mockMat, 253.0, 255.0, Imgproc.THRESH_BINARY) }
        verify { Imgproc.threshold(mockMat, mockMat, 2.0, 255.0, Imgproc.THRESH_BINARY_INV) }
    }

    @Test
    fun `analyzeClipping returns 0 0 pair when total pixels is 0`() {
        val bitmap = mockk<Bitmap>()
        val mockMat = mockk<Mat>(relaxed = true)

        every { analyzer.createMat() } returns mockMat
        every { mockMat.total() } returns 0L
        every { mockMat.channels() } returns 4

        every { Utils.bitmapToMat(bitmap, mockMat) } returns Unit
        every { Imgproc.cvtColor(mockMat, mockMat, Imgproc.COLOR_RGBA2GRAY) } returns Unit

        val (highlight, shadow) = analyzer.analyzeClipping(bitmap)

        assertEquals(0.0, highlight, 1e-4)
        assertEquals(0.0, shadow, 1e-4)
    }
}
