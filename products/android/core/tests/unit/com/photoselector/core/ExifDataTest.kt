package com.photoselector.core

import com.photoselector.core.model.ExifData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ExifDataTest {
    @Test
    fun defaultExifData_hasDefaults() {
        val exifData = ExifData()
        assertEquals("Unknown", exifData.lens)
        assertFalse(exifData.isFallback)
        assertEquals(0, exifData.rating)
    }

    @Test
    fun exifData_withCustomRating_preservesValue() {
        for (stars in 1..5) {
            val exif = ExifData(rating = stars)
            assertEquals(stars, exif.rating)
        }
    }

    @Test
    fun exifData_withRejectRating_preservesValue() {
        val exif = ExifData(rating = -1)
        assertEquals(-1, exif.rating)
    }

    @Test
    fun exifData_copy_preservesRating() {
        val original = ExifData(rating = 4, iso = 800)
        val copied = original.copy(shutterSpeed = 0.004)
        assertEquals(4, copied.rating)
        assertEquals(800, copied.iso)
        assertEquals(0.004, copied.shutterSpeed!!, 1e-6)

        val updatedRating = copied.copy(rating = 5)
        assertEquals(5, updatedRating.rating)
    }

    @Test
    fun exifData_backwardsCompatibility_constructWithoutRating() {
        // Simulates PhotoTok constructor call without passing rating
        val exif = ExifData(
            shutterSpeed = 0.005,
            aperture = 2.8,
            focalLength = 50.0,
            focalLength35mm = 75.0,
            iso = 400,
            lens = "FE 50mm F1.8",
            isFallback = false
        )
        assertEquals(0, exif.rating)
        assertEquals(400, exif.iso)
        assertEquals("FE 50mm F1.8", exif.lens)
    }
}
