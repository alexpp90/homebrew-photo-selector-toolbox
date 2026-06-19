package com.photoselectortoolbox.data.model

import org.junit.Assert.*
import org.junit.Test

class ScanResultTest {

    @Test
    fun `defaults leave optional scores null`() {
        val result = ScanResult(filePath = "/path/to/image.jpg")
        assertEquals("/path/to/image.jpg", result.filePath)
        assertNull(result.sharpnessScore)
        assertNull(result.noiseLevel)
        assertNull(result.highlightClipping)
        assertNull(result.shadowClipping)
        assertNull(result.exifData)
    }

    @Test
    fun `all fields populated`() {
        val exif = ExifData(aperture = 5.6)
        val result = ScanResult(
            filePath = "test.jpg",
            sharpnessScore = 120.5,
            noiseLevel = 3.2,
            highlightClipping = 0.5,
            shadowClipping = 1.8,
            exifData = exif
        )
        assertEquals(120.5, result.sharpnessScore!!, 1e-9)
        assertEquals(3.2, result.noiseLevel!!, 1e-9)
        assertEquals(0.5, result.highlightClipping!!, 1e-9)
        assertEquals(1.8, result.shadowClipping!!, 1e-9)
        assertEquals(5.6, result.exifData!!.aperture!!, 1e-9)
    }

    @Test
    fun `data class equality`() {
        val a = ScanResult("a.jpg", sharpnessScore = 10.0)
        val b = ScanResult("a.jpg", sharpnessScore = 10.0)
        assertEquals(a, b)
    }
}
