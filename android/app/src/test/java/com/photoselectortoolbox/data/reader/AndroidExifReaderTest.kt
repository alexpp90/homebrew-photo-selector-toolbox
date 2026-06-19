package com.photoselectortoolbox.data.reader

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for AndroidExifReader — verifies the supported MIME types set
 * and that the reader can be instantiated. Parsing tests require a real
 * ExifInterface which is covered in instrumented tests.
 */
class AndroidExifReaderTest {

    private val reader = AndroidExifReader()

    @Test
    fun `supportedMimeTypes includes JPEG`() {
        assertTrue(reader.supportedMimeTypes().contains("image/jpeg"))
    }

    @Test
    fun `supportedMimeTypes includes common RAW formats`() {
        val types = reader.supportedMimeTypes()
        assertTrue(types.contains("image/x-adobe-dng"))
        assertTrue(types.contains("image/x-canon-cr2"))
        assertTrue(types.contains("image/x-nikon-nef"))
        assertTrue(types.contains("image/x-sony-arw"))
        assertTrue(types.contains("image/x-fuji-raf"))
    }

    @Test
    fun `supportedMimeTypes includes modern formats`() {
        val types = reader.supportedMimeTypes()
        assertTrue(types.contains("image/webp"))
        assertTrue(types.contains("image/heif"))
        assertTrue(types.contains("image/heic"))
    }

    @Test
    fun `supportedMimeTypes does not include PNG`() {
        assertFalse(reader.supportedMimeTypes().contains("image/png"))
    }

    @Test
    fun `supportedMimeTypes does not include video`() {
        assertFalse(reader.supportedMimeTypes().contains("video/mp4"))
    }
}
