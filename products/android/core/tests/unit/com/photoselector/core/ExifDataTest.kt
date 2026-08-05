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
    }
}
