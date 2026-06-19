package com.photoselectortoolbox.domain.grouping

import android.graphics.Bitmap
import android.graphics.Color

/**
 * Utility class for computing difference hashes (dHash) and comparing them.
 * dHash is a perceptual hash that captures relative gradient information,
 * making it robust to scaling and minor modifications.
 *
 * - Fast mode (hashSize=8): 9x8 resize -> 64-bit hash
 * - Detailed mode (hashSize=16): 17x16 resize -> uses lower 64 bits of a wider hash
 */
class DHashCalculator {

    /**
     * Compute a difference hash for the given bitmap.
     *
     * The image is resized to (hashSize+1) x hashSize, converted to grayscale,
     * and a horizontal gradient is computed. Each bit represents whether a pixel
     * is brighter than its right neighbor.
     *
     * @param bitmap The image to hash.
     * @param hashSize 8 for fast comparison, 16 for detailed comparison.
     * @return A Long hash value encoding the gradient pattern.
     */
    fun computeDHash(bitmap: Bitmap, hashSize: Int = 8): Long {
        val width = hashSize + 1
        val height = hashSize

        // Resize to (hashSize+1) x hashSize
        val resized = Bitmap.createScaledBitmap(bitmap, width, height, true)

        try {
            var hash = 0L
            var bitPosition = 0

            for (y in 0 until height) {
                for (x in 0 until hashSize) {
                    // Compare each pixel with its right neighbor
                    val leftLuminance = luminance(resized.getPixel(x, y))
                    val rightLuminance = luminance(resized.getPixel(x + 1, y))

                    if (leftLuminance > rightLuminance) {
                        hash = hash or (1L shl bitPosition)
                    }

                    bitPosition++

                    // Long only holds 64 bits; for hashSize=16 (256 bits needed),
                    // we use the first 64 bits which still provides good discrimination
                    if (bitPosition >= 64) {
                        return hash
                    }
                }
            }

            return hash
        } finally {
            if (resized !== bitmap) {
                resized.recycle()
            }
        }
    }

    /**
     * Compute the Hamming distance between two hashes.
     * This is the number of bit positions where the hashes differ.
     *
     * @param hash1 First hash value.
     * @param hash2 Second hash value.
     * @return Number of differing bits (0 = identical, 64 = maximally different).
     */
    fun hammingDistance(hash1: Long, hash2: Long): Int {
        return (hash1 xor hash2).countOneBits()
    }

    /**
     * Convert an ARGB pixel to grayscale luminance using the standard
     * ITU-R BT.601 coefficients.
     */
    private fun luminance(pixel: Int): Double {
        val r = Color.red(pixel)
        val g = Color.green(pixel)
        val b = Color.blue(pixel)
        return 0.299 * r + 0.587 * g + 0.114 * b
    }
}
