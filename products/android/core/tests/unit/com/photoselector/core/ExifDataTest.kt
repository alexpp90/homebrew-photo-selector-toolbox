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
        org.junit.Assert.assertNull(exif.gpsLatitude)
        org.junit.Assert.assertNull(exif.gpsLongitude)
        org.junit.Assert.assertNull(exif.gpsDateTimeUtc)
        org.junit.Assert.assertNull(exif.dateTimeOriginal)
        org.junit.Assert.assertNull(exif.offsetTimeOriginal)
        org.junit.Assert.assertNull(exif.focalPlaneXResolution)
        org.junit.Assert.assertNull(exif.focalPlaneResolutionUnit)
    }

    @Test
    fun exifData_withGpsTimeAndFocalPlaneFields_preservesValues() {
        val exif = ExifData(
            gpsLatitude = 48.137154,
            gpsLongitude = 11.576124,
            gpsDateTimeUtc = "2026:06:21 18:30:00",
            dateTimeOriginal = "2026:06:21 20:30:00",
            offsetTimeOriginal = "+02:00",
            focalPlaneXResolution = 3840.0,
            focalPlaneResolutionUnit = 3
        )
        assertEquals(48.137154, exif.gpsLatitude!!, 1e-6)
        assertEquals(11.576124, exif.gpsLongitude!!, 1e-6)
        assertEquals("2026:06:21 18:30:00", exif.gpsDateTimeUtc)
        assertEquals("2026:06:21 20:30:00", exif.dateTimeOriginal)
        assertEquals("+02:00", exif.offsetTimeOriginal)
        assertEquals(3840.0, exif.focalPlaneXResolution!!, 1e-4)
        assertEquals(3, exif.focalPlaneResolutionUnit)
    }
}
