package com.photoselectortoolbox.domain.analysis

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfDouble
import org.opencv.core.Rect
import org.opencv.imgproc.Imgproc
import javax.inject.Inject

/**
 * Computes a sharpness score for an image using Laplacian variance on a grid
 * of blocks over the center crop. Returns the maximum block variance, which
 * represents the sharpest region of the image.
 *
 * Ported from the desktop Python implementation.
 */
class SharpnessAnalyzer @Inject constructor() {

    companion object {
        private const val DEFAULT_GRID_SIZE = 8
        private const val CENTER_CROP_RATIO = 0.5
    }

    /**
     * Analyze the sharpness of the given bitmap.
     *
     * @param bitmap The image to analyze.
     * @param gridSize Number of blocks in each dimension (gridSize x gridSize).
     * @return The maximum Laplacian variance across all grid blocks (higher = sharper).
     */
    fun analyze(bitmap: Bitmap, gridSize: Int = DEFAULT_GRID_SIZE): Double {
        val srcMat = Mat()
        try {
            Utils.bitmapToMat(bitmap, srcMat)
            return analyzeFromMat(srcMat, gridSize)
        } finally {
            srcMat.release()
        }
    }

    private fun analyzeFromMat(srcMat: Mat, gridSize: Int): Double {
        // Crop center 50% of the image
        val cropX = (srcMat.cols() * (1.0 - CENTER_CROP_RATIO) / 2.0).toInt()
        val cropY = (srcMat.rows() * (1.0 - CENTER_CROP_RATIO) / 2.0).toInt()
        val cropWidth = (srcMat.cols() * CENTER_CROP_RATIO).toInt()
        val cropHeight = (srcMat.rows() * CENTER_CROP_RATIO).toInt()

        if (cropWidth <= 0 || cropHeight <= 0) return 0.0

        val croppedMat = Mat(srcMat, Rect(cropX, cropY, cropWidth, cropHeight))
        try {
            return computeGridSharpness(croppedMat, gridSize)
        } finally {
            croppedMat.release()
        }
    }

    private fun computeGridSharpness(croppedMat: Mat, gridSize: Int): Double {
        val blockWidth = croppedMat.cols() / gridSize
        val blockHeight = croppedMat.rows() / gridSize

        if (blockWidth <= 0 || blockHeight <= 0) return 0.0

        var maxVariance = 0.0

        for (row in 0 until gridSize) {
            for (col in 0 until gridSize) {
                val x = col * blockWidth
                val y = row * blockHeight
                val w = if (col == gridSize - 1) croppedMat.cols() - x else blockWidth
                val h = if (row == gridSize - 1) croppedMat.rows() - y else blockHeight

                val variance = computeBlockVariance(croppedMat, x, y, w, h)
                if (variance > maxVariance) {
                    maxVariance = variance
                }
            }
        }

        return maxVariance
    }

    private fun computeBlockVariance(src: Mat, x: Int, y: Int, w: Int, h: Int): Double {
        val block = Mat(src, Rect(x, y, w, h))
        val grayBlock = Mat()
        val laplacian = Mat()
        val mean = MatOfDouble()
        val stddev = MatOfDouble()
        try {
            // Convert to grayscale
            if (block.channels() > 1) {
                Imgproc.cvtColor(block, grayBlock, Imgproc.COLOR_RGBA2GRAY)
            } else {
                block.copyTo(grayBlock)
            }

            // Compute Laplacian
            Imgproc.Laplacian(grayBlock, laplacian, CvType.CV_64F)

            // Calculate variance (stddev^2)
            Core.meanStdDev(laplacian, mean, stddev)
            val stddevValue = stddev.get(0, 0)[0]
            return stddevValue * stddevValue
        } finally {
            block.release()
            grayBlock.release()
            laplacian.release()
            mean.release()
            stddev.release()
        }
    }
}
