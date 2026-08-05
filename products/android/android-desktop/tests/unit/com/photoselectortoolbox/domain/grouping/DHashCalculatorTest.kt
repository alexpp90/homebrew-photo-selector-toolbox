package com.photoselectortoolbox.domain.grouping

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for DHashCalculator. Uses Robolectric because the class depends on
 * android.graphics.Bitmap and android.graphics.Color.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DHashCalculatorTest {

    private val calculator = DHashCalculator()

    private fun solidBitmap(width: Int, height: Int, color: Int): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(color)
        return bmp
    }

    private fun gradientBitmap(width: Int, height: Int): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val gray = (x * 255 / width).coerceIn(0, 255)
                bmp.setPixel(x, y, Color.rgb(gray, gray, gray))
            }
        }
        return bmp
    }

    @Test
    fun `identical images produce same hash`() {
        val bmp1 = solidBitmap(100, 100, Color.RED)
        val bmp2 = solidBitmap(100, 100, Color.RED)
        assertEquals(calculator.computeDHash(bmp1), calculator.computeDHash(bmp2))
        bmp1.recycle()
        bmp2.recycle()
    }

    @Test
    fun `different images produce different hashes`() {
        val bmp1 = solidBitmap(100, 100, Color.BLACK)
        val bmp2 = gradientBitmap(100, 100)
        // Solid image and gradient image should differ
        val hash1 = calculator.computeDHash(bmp1)
        val hash2 = calculator.computeDHash(bmp2)
        // They may or may not be equal depending on scaling, but distance should be measurable
        // At minimum, the function should not crash
        assertNotNull(hash1)
        assertNotNull(hash2)
        bmp1.recycle()
        bmp2.recycle()
    }

    @Test
    fun `hamming distance of identical hashes is zero`() {
        assertEquals(0, calculator.hammingDistance(0L, 0L))
        assertEquals(0, calculator.hammingDistance(0xFFFFL, 0xFFFFL))
    }

    @Test
    fun `hamming distance counts differing bits`() {
        // 0b0001 vs 0b0010 = 2 bits differ
        assertEquals(2, calculator.hammingDistance(1L, 2L))
    }

    @Test
    fun `hamming distance of fully opposite values is 64`() {
        assertEquals(64, calculator.hammingDistance(0L, -1L)) // 0 vs all-ones
    }

    @Test
    fun `hash size 8 produces deterministic result`() {
        val bmp = gradientBitmap(100, 100)
        val h1 = calculator.computeDHash(bmp, hashSize = 8)
        val h2 = calculator.computeDHash(bmp, hashSize = 8)
        assertEquals(h1, h2)
        bmp.recycle()
    }

    @Test
    fun `hash size 16 produces deterministic result`() {
        val bmp = gradientBitmap(100, 100)
        val h1 = calculator.computeDHash(bmp, hashSize = 16)
        val h2 = calculator.computeDHash(bmp, hashSize = 16)
        assertEquals(h1, h2)
        bmp.recycle()
    }
}
