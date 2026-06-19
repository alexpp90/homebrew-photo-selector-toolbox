package com.photoselectortoolbox.domain.analysis

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc
import javax.inject.Inject

/**
 * Analyzes highlight and shadow clipping in images.
 * - Highlight clipping: percentage of pixels at or near pure white (>= 254)
 * - Shadow clipping: percentage of pixels at or near pure black (<= 2)
 *
 * Ported from the desktop Python implementation.
 */
class ClippingAnalyzer @Inject constructor() {

    companion object {
        private const val HIGHLIGHT_THRESHOLD = 254.0
        private const val SHADOW_THRESHOLD = 2.0
    }

    /**
     * Analyze the percentage of highlight-clipped pixels.
     *
     * @param bitmap The image to analyze.
     * @return Percentage of pixels with grayscale value >= 254 (0.0 to 100.0).
     */
    fun analyzeHighlightClipping(bitmap: Bitmap): Double {
        val srcMat = Mat()
        try {
            Utils.bitmapToMat(bitmap, srcMat)
            return computeHighlightClipping(srcMat)
        } finally {
            srcMat.release()
        }
    }

    /**
     * Analyze the percentage of shadow-clipped pixels.
     *
     * @param bitmap The image to analyze.
     * @return Percentage of pixels with grayscale value <= 2 (0.0 to 100.0).
     */
    fun analyzeShadowClipping(bitmap: Bitmap): Double {
        val srcMat = Mat()
        try {
            Utils.bitmapToMat(bitmap, srcMat)
            return computeShadowClipping(srcMat)
        } finally {
            srcMat.release()
        }
    }

    private fun computeHighlightClipping(srcMat: Mat): Double {
        val grayMat = Mat()
        val thresholdMat = Mat()
        try {
            toGrayscale(srcMat, grayMat)

            val totalPixels = grayMat.total()
            if (totalPixels == 0L) return 0.0

            // Threshold: pixels >= 254 become 255 (non-zero), rest become 0
            Imgproc.threshold(
                grayMat,
                thresholdMat,
                HIGHLIGHT_THRESHOLD - 1,
                255.0,
                Imgproc.THRESH_BINARY
            )

            val clippedPixels = Core.countNonZero(thresholdMat)
            return (clippedPixels.toDouble() / totalPixels.toDouble()) * 100.0
        } finally {
            grayMat.release()
            thresholdMat.release()
        }
    }

    private fun computeShadowClipping(srcMat: Mat): Double {
        val grayMat = Mat()
        val thresholdMat = Mat()
        try {
            toGrayscale(srcMat, grayMat)

            val totalPixels = grayMat.total()
            if (totalPixels == 0L) return 0.0

            // Threshold: pixels <= 2 become 255 (non-zero), rest become 0
            // THRESH_BINARY_INV: values <= threshold -> 255, above -> 0
            Imgproc.threshold(
                grayMat,
                thresholdMat,
                SHADOW_THRESHOLD,
                255.0,
                Imgproc.THRESH_BINARY_INV
            )

            val clippedPixels = Core.countNonZero(thresholdMat)
            return (clippedPixels.toDouble() / totalPixels.toDouble()) * 100.0
        } finally {
            grayMat.release()
            thresholdMat.release()
        }
    }

    /**
     * Analyze both highlight and shadow clipping in a single pass,
     * avoiding duplicate bitmap→Mat and grayscale conversions.
     *
     * @param bitmap The image to analyze.
     * @return Pair of (highlightClipping%, shadowClipping%).
     */
    fun analyzeClipping(bitmap: Bitmap): Pair<Double, Double> {
        val srcMat = Mat()
        val grayMat = Mat()
        val highlightThresholdMat = Mat()
        val shadowThresholdMat = Mat()
        try {
            Utils.bitmapToMat(bitmap, srcMat)
            toGrayscale(srcMat, grayMat)

            val totalPixels = grayMat.total()
            if (totalPixels == 0L) return Pair(0.0, 0.0)

            // Highlight: pixels >= 254
            Imgproc.threshold(
                grayMat, highlightThresholdMat,
                HIGHLIGHT_THRESHOLD - 1, 255.0,
                Imgproc.THRESH_BINARY
            )
            val highlightClipped = Core.countNonZero(highlightThresholdMat)

            // Shadow: pixels <= 2
            Imgproc.threshold(
                grayMat, shadowThresholdMat,
                SHADOW_THRESHOLD, 255.0,
                Imgproc.THRESH_BINARY_INV
            )
            val shadowClipped = Core.countNonZero(shadowThresholdMat)

            val highlight = (highlightClipped.toDouble() / totalPixels.toDouble()) * 100.0
            val shadow = (shadowClipped.toDouble() / totalPixels.toDouble()) * 100.0

            return Pair(highlight, shadow)
        } finally {
            srcMat.release()
            grayMat.release()
            highlightThresholdMat.release()
            shadowThresholdMat.release()
        }
    }

    private fun toGrayscale(src: Mat, dst: Mat) {
        if (src.channels() > 1) {
            Imgproc.cvtColor(src, dst, Imgproc.COLOR_RGBA2GRAY)
        } else {
            src.copyTo(dst)
        }
    }
}
