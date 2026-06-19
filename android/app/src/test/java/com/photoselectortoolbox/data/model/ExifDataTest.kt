package com.photoselectortoolbox.data.model

import org.junit.Assert.*
import org.junit.Test

class ExifDataTest {

    @Test
    fun `defaults produce Unknown lens and null optionals`() {
        val exif = ExifData()
        assertNull(exif.shutterSpeed)
        assertNull(exif.aperture)
        assertNull(exif.focalLength)
        assertNull(exif.focalLength35mm)
        assertNull(exif.iso)
        assertEquals("Unknown", exif.lens)
        assertFalse(exif.isFallback)
    }

    @Test
    fun `custom values are stored correctly`() {
        val exif = ExifData(
            shutterSpeed = 0.004,
            aperture = 2.8,
            focalLength = 50.0,
            focalLength35mm = 75.0,
            iso = 400,
            lens = "Nikkor 50mm f/1.8",
            isFallback = true
        )
        assertEquals(0.004, exif.shutterSpeed!!, 1e-9)
        assertEquals(2.8, exif.aperture!!, 1e-9)
        assertEquals(50.0, exif.focalLength!!, 1e-9)
        assertEquals(75.0, exif.focalLength35mm!!, 1e-9)
        assertEquals(400, exif.iso)
        assertEquals("Nikkor 50mm f/1.8", exif.lens)
        assertTrue(exif.isFallback)
    }

    @Test
    fun `data class equality works`() {
        val a = ExifData(aperture = 4.0, iso = 100)
        val b = ExifData(aperture = 4.0, iso = 100)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `copy changes only specified field`() {
        val original = ExifData(iso = 100, lens = "Lens A")
        val copy = original.copy(iso = 800)
        assertEquals(800, copy.iso)
        assertEquals("Lens A", copy.lens)
    }
}
