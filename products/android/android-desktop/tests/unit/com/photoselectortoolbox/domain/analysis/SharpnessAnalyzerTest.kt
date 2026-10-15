package com.photoselectortoolbox.domain.analysis

import android.app.Application
import android.graphics.Bitmap
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfDouble
import org.opencv.core.Rect
import org.opencv.imgproc.Imgproc
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.Config

/**
 * Robolectric shadow for OpenCV's [Mat] class to bypass loading native C++
 * JNI binaries (`libopencv_java4.so`) during host JVM unit testing.
 */
@Implements(Mat::class)
class ShadowMat {
    @Implementation
    fun __constructor__() {
    }

    @Implementation
    fun __constructor__(m: Mat, r: Rect) {
    }

    companion object {
        @Implementation
        @JvmStatic
        fun n_Mat(): Long = 1L

        @Implementation
        @JvmStatic
        fun n_submat(addr: Long, rowStart: Int, rowEnd: Int, colStart: Int, colEnd: Int): Long = 1L

        @Implementation
        @JvmStatic
        fun n_release(addr: Long) {
        }

        @Implementation
        @JvmStatic
        fun n_delete(addr: Long) {
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, shadows = [ShadowMat::class])
class SharpnessAnalyzerTest {

    @Before
    fun setUp() {
        mockkStatic(OpenCVLoader::class)
        every { OpenCVLoader.initLocal() } returns true

        mockkStatic(Utils::class)
        mockkStatic(Imgproc::class)
        mockkStatic(Core::class)
        mockkConstructor(Mat::class)
        mockkConstructor(MatOfDouble::class)

        // Default mock behaviors for OpenCV image processing
        every { Utils.bitmapToMat(any(), any()) } returns Unit
        every { Imgproc.cvtColor(any(), any(), any()) } returns Unit
        every { Imgproc.Laplacian(any(), any(), any()) } returns Unit
        every { Core.meanStdDev(any(), any(), any()) } returns Unit
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `analyze computes max variance across grid blocks for multi-channel bitmap`() {
        val bitmap = mockk<Bitmap>()
        val analyzer = SharpnessAnalyzer()

        every { anyConstructed<Mat>().cols() } returns 100
        every { anyConstructed<Mat>().rows() } returns 100
        every { anyConstructed<Mat>().release() } returns Unit
        every { anyConstructed<Mat>().channels() } returns 4

        // Standard deviation = 4.0 -> variance = 16.0
        every { anyConstructed<MatOfDouble>().get(0, 0) } returns doubleArrayOf(4.0)

        val score = analyzer.analyze(bitmap, gridSize = 2)

        assertEquals(16.0, score, 1e-6)
        verify { Imgproc.cvtColor(any(), any(), Imgproc.COLOR_RGBA2GRAY) }
        verify(atLeast = 1) { anyConstructed<Mat>().release() }
    }

    @Test
    fun `analyze selects maximum variance among blocks with varying sharpness`() {
        val bitmap = mockk<Bitmap>()
        val analyzer = SharpnessAnalyzer()

        every { anyConstructed<Mat>().cols() } returns 100
        every { anyConstructed<Mat>().rows() } returns 100
        every { anyConstructed<Mat>().release() } returns Unit
        every { anyConstructed<Mat>().channels() } returns 4

        // Return different stddevs for 2x2 grid (4 blocks): 1.0, 3.0, 2.0, 0.5
        val stddevs = listOf(1.0, 3.0, 2.0, 0.5)
        var callCount = 0
        every { anyConstructed<MatOfDouble>().get(0, 0) } answers {
            val stddev = stddevs[callCount % stddevs.size]
            callCount++
            doubleArrayOf(stddev)
        }

        val score = analyzer.analyze(bitmap, gridSize = 2)

        // Max stddev is 3.0, variance = 3.0^2 = 9.0
        assertEquals(9.0, score, 1e-6)
    }

    @Test
    fun `analyze processes single-channel bitmap using copyTo instead of cvtColor`() {
        val bitmap = mockk<Bitmap>()
        val analyzer = SharpnessAnalyzer()

        every { anyConstructed<Mat>().cols() } returns 100
        every { anyConstructed<Mat>().rows() } returns 100
        every { anyConstructed<Mat>().release() } returns Unit
        every { anyConstructed<Mat>().channels() } returns 1
        every { anyConstructed<Mat>().copyTo(any()) } returns Unit

        every { anyConstructed<MatOfDouble>().get(0, 0) } returns doubleArrayOf(3.0)

        val score = analyzer.analyze(bitmap, gridSize = 2)

        assertEquals(9.0, score, 1e-6)
        verify { anyConstructed<Mat>().copyTo(any()) }
        verify(exactly = 0) { Imgproc.cvtColor(any(), any(), any()) }
    }

    @Test
    fun `analyze returns zero when crop dimensions are zero`() {
        val bitmap = mockk<Bitmap>()
        val analyzer = SharpnessAnalyzer()

        // 1x1 image -> center crop (50%) -> cropWidth = 0, cropHeight = 0
        every { anyConstructed<Mat>().cols() } returns 1
        every { anyConstructed<Mat>().rows() } returns 1
        every { anyConstructed<Mat>().release() } returns Unit

        val score = analyzer.analyze(bitmap)

        assertEquals(0.0, score, 1e-6)
    }

    @Test
    fun `analyze returns zero when block dimensions are zero`() {
        val bitmap = mockk<Bitmap>()
        val analyzer = SharpnessAnalyzer()

        // 4x4 image -> crop width = 2, with default gridSize = 8 -> blockWidth = 0
        every { anyConstructed<Mat>().cols() } returns 4
        every { anyConstructed<Mat>().rows() } returns 4
        every { anyConstructed<Mat>().release() } returns Unit

        val score = analyzer.analyze(bitmap, gridSize = 8)

        assertEquals(0.0, score, 1e-6)
    }

    @Test
    fun `analyze with default grid size uses 8x8 grid`() {
        val bitmap = mockk<Bitmap>()
        val analyzer = SharpnessAnalyzer()

        every { anyConstructed<Mat>().cols() } returns 160
        every { anyConstructed<Mat>().rows() } returns 160
        every { anyConstructed<Mat>().release() } returns Unit
        every { anyConstructed<Mat>().channels() } returns 4

        every { anyConstructed<MatOfDouble>().get(0, 0) } returns doubleArrayOf(2.0)

        val score = analyzer.analyze(bitmap) // default gridSize = 8

        assertEquals(4.0, score, 1e-6)
        // 8x8 grid = 64 blocks, so Laplacian should be called 64 times
        verify(exactly = 64) { Imgproc.Laplacian(any(), any(), any()) }
    }
}
