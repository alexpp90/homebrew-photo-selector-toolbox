package com.photoselectortoolbox.domain.analysis

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import javax.inject.Inject

/**
 * Estimates image noise level using Median Absolute Deviation (MAD) of the
 * Laplacian. Based on the robust median estimator:
 *   sigma = median(|Laplacian(I) - median(Laplacian(I))|) / 0.6745
 *
 * Ported from the desktop Python implementation.
 */
class NoiseAnalyzer @Inject constructor() {

    companion object {
        /**
         * Consistency constant for MAD as an estimator of standard deviation
         * under a normal distribution.
         */
        private const val MAD_SCALE_FACTOR = 0.6745
    }

    /**
     * Analyze the noise level of the given bitmap.
     *
     * @param bitmap The image to analyze.
     * @return The estimated noise level (higher = noisier).
     */
    fun analyze(bitmap: Bitmap): Double {
        val srcMat = Mat()
        try {
            Utils.bitmapToMat(bitmap, srcMat)
            return analyzeFromMat(srcMat)
        } finally {
            srcMat.release()
        }
    }

    private fun analyzeFromMat(srcMat: Mat): Double {
        val grayMat = Mat()
        val laplacian = Mat()
        try {
            // Convert to grayscale
            if (srcMat.channels() > 1) {
                Imgproc.cvtColor(srcMat, grayMat, Imgproc.COLOR_RGBA2GRAY)
            } else {
                srcMat.copyTo(grayMat)
            }

            // Downsample if the image is too large to prevent JVM OutOfMemoryError.
            // Nearest neighbor preserves original pixel-level noise characteristics.
            val maxDimension = 1000
            val width = grayMat.cols()
            val height = grayMat.rows()
            if (width > maxDimension || height > maxDimension) {
                val scale = Math.max(width.toDouble() / maxDimension.toDouble(), height.toDouble() / maxDimension.toDouble())
                val newWidth = (width / scale).toInt()
                val newHeight = (height / scale).toInt()
                val resized = Mat()
                try {
                    Imgproc.resize(
                        grayMat,
                        resized,
                        Size(newWidth.toDouble(), newHeight.toDouble()),
                        0.0,
                        0.0,
                        Imgproc.INTER_NEAREST
                    )
                    resized.copyTo(grayMat)
                } finally {
                    resized.release()
                }
            }

            // Apply Laplacian filter
            Imgproc.Laplacian(grayMat, laplacian, CvType.CV_64F)

            // Flatten laplacian to a 1D array and compute MAD
            val totalElements = (laplacian.total() * laplacian.channels()).toInt()
            if (totalElements == 0) return 0.0

            val values = DoubleArray(totalElements)
            laplacian.get(0, 0, values)

            return computeMAD(values)
        } finally {
            grayMat.release()
            laplacian.release()
        }
    }

    /**
     * Compute the Median Absolute Deviation (MAD) based noise estimate.
     * sigma = median(|values - median(values)|) / 0.6745
     */
    private fun computeMAD(values: DoubleArray): Double {
        if (values.isEmpty()) return 0.0

        values.sort()
        val median = medianOfSorted(values)

        // Compute absolute deviations from median
        val absDeviations = DoubleArray(values.size) { i ->
            kotlin.math.abs(values[i] - median)
        }
        absDeviations.sort()

        val medianAbsDeviation = medianOfSorted(absDeviations)

        return if (MAD_SCALE_FACTOR != 0.0) {
            medianAbsDeviation / MAD_SCALE_FACTOR
        } else {
            medianAbsDeviation
        }
    }

    /**
     * Returns the median of a pre-sorted array.
     */
    private fun medianOfSorted(sorted: DoubleArray): Double {
        val n = sorted.size
        return if (n % 2 == 0) {
            (sorted[n / 2 - 1] + sorted[n / 2]) / 2.0
        } else {
            sorted[n / 2]
        }
    }
}
