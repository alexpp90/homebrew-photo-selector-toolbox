package com.photoselectortoolbox.domain.format

import com.photoselector.core.model.ExifData
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExifFormatterTest {

    @Test
    fun `fractional exposures render as a fraction`() {
        assertEquals("1/250s", ExifFormatter.shutterSpeed(1.0 / 250.0))
        assertEquals("1/8000s", ExifFormatter.shutterSpeed(1.0 / 8000.0))
    }

    @Test
    fun `long exposures render as seconds`() {
        assertEquals("2.0s", ExifFormatter.shutterSpeed(2.0))
        assertEquals("1.0s", ExifFormatter.shutterSpeed(1.0))
    }

    @Test
    fun `a nonsensical exposure does not crash or divide by zero`() {
        assertEquals("—", ExifFormatter.shutterSpeed(0.0))
        assertEquals("—", ExifFormatter.shutterSpeed(-1.0))
    }

    @Test
    fun `aperture and focal length use the expected notation`() {
        assertEquals("f/2.8", ExifFormatter.aperture(2.8))
        assertEquals("35mm", ExifFormatter.focalLength(35.0))
        assertEquals("ISO 400", ExifFormatter.iso(400))
    }

    @Test
    fun `decimal separator is always a dot regardless of device locale`() {
        // A German device would otherwise render "f/2,8", which does not match
        // what the desktop app writes and forces the reader to re-parse.
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            assertEquals("f/2.8", ExifFormatter.aperture(2.8))
            assertEquals("2.5s", ExifFormatter.shutterSpeed(2.5))
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun `summary line joins the values in shooting order`() {
        val exif = ExifData(
            shutterSpeed = 1.0 / 250.0,
            aperture = 2.8,
            focalLength = 35.0,
            iso = 400,
        )
        assertEquals("1/250s  ·  f/2.8  ·  35mm  ·  ISO 400", ExifFormatter.summaryLine(exif))
    }

    @Test
    fun `summary line drops missing values rather than padding with dashes`() {
        val exif = ExifData(aperture = 1.4, iso = 100)
        assertEquals("f/1.4  ·  ISO 100", ExifFormatter.summaryLine(exif))
    }

    @Test
    fun `summary line is null when nothing at all is known`() {
        assertNull(ExifFormatter.summaryLine(null))
        assertNull(ExifFormatter.summaryLine(ExifData()))
    }

    @Test
    fun `detail rows keep every field and label the unknowns`() {
        val rows = ExifFormatter.detailRows(ExifData(iso = 200))
        val map = rows.toMap()
        assertEquals(6, rows.size)
        assertEquals("200", map["ISO"])
        assertEquals(ExifFormatter.UNKNOWN, map["Aperture"])
        // "Unknown" is a legitimate value in the panel: not knowing the lens is
        // information the photographer wants, not a field to hide.
        assertEquals(ExifFormatter.UNKNOWN, map["Lens"])
    }

    @Test
    fun `detail rows are empty when there is no exif at all`() {
        assertTrue(ExifFormatter.detailRows(null).isEmpty())
    }
}
