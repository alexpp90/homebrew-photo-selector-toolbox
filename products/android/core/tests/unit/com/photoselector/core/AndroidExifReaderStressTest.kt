package com.photoselector.core

import com.photoselector.core.reader.BinaryExifRatingReader
import com.photoselector.core.reader.XmpRatingParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(RobolectricTestRunner::class)
class AndroidExifReaderStressTest {

    // -------------------------------------------------------------
    // 1. TIFF Circular SubIFD Pointer (StackOverflow DoS)
    // -------------------------------------------------------------

    @Test
    fun `BinaryExifRatingReader circular SubIFD pointer triggers StackOverflowError`() {
        // Construct TIFF buffer with circular SubIFD tag 0x8769 pointing back to offset 8 (itself)
        val buffer = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put('I'.code.toByte())
        buffer.put('I'.code.toByte())
        buffer.putShort(42)
        buffer.putInt(8) // IFD0 at offset 8

        // IFD0 at offset 8:
        buffer.putShort(1) // 1 entry
        buffer.putShort(0x8769.toShort()) // tag ExifSubIFD (34665)
        buffer.putShort(4) // type LONG
        buffer.putInt(1) // count 1
        buffer.putInt(8) // offset pointing back to 8! (Circular reference to IFD0)

        buffer.flip()

        try {
            val result = BinaryExifRatingReader.parseFromBuffer(buffer)
            assertNull("Circular SubIFD should safely return null", result)
        } catch (e: StackOverflowError) {
            fail("EMPIRICALLY CONFIRMED BUG: BinaryExifRatingReader enters infinite recursion and crashes with StackOverflowError on circular SubIFD!")
        }
    }

    // -------------------------------------------------------------
    // 2. TIFF Negative SubIFD Offset
    // -------------------------------------------------------------

    @Test
    fun `BinaryExifRatingReader negative SubIFD offset does not crash`() {
        val buffer = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put('I'.code.toByte())
        buffer.put('I'.code.toByte())
        buffer.putShort(42)
        buffer.putInt(8)

        // IFD0 at offset 8:
        buffer.putShort(1)
        buffer.putShort(0x8769.toShort())
        buffer.putShort(4)
        buffer.putInt(1)
        buffer.putInt(-10) // Negative offset!

        buffer.flip()

        try {
            val result = BinaryExifRatingReader.parseFromBuffer(buffer)
            assertNull("Negative SubIFD should return null", result)
        } catch (e: IndexOutOfBoundsException) {
            fail("EMPIRICALLY CONFIRMED BUG: BinaryExifRatingReader crashes with IndexOutOfBoundsException on negative SubIFD offset: ${e.message}")
        }
    }

    // -------------------------------------------------------------
    // 3. Fuzzed and Truncated Buffers
    // -------------------------------------------------------------

    @Test
    fun `BinaryExifRatingReader handles truncated or corrupted buffers safely`() {
        // Zero-length
        assertNull(BinaryExifRatingReader.parseFromBuffer(ByteBuffer.allocate(0)))

        // Truncated header (3 bytes)
        val shortBuf = ByteBuffer.wrap(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()))
        assertNull(BinaryExifRatingReader.parseFromBuffer(shortBuf))

        // Corrupted JPEG with huge claimed APP1 length but truncated buffer
        val fakeJpeg = ByteBuffer.allocate(16)
        fakeJpeg.put(0xFF.toByte())
        fakeJpeg.put(0xD8.toByte())
        fakeJpeg.put(0xFF.toByte())
        fakeJpeg.put(0xE1.toByte())
        fakeJpeg.putShort(0x7FFF.toShort()) // 32767 bytes
        fakeJpeg.flip()
        assertNull(BinaryExifRatingReader.parseFromBuffer(fakeJpeg))
    }

    // -------------------------------------------------------------
    // 4. XMP Real / Floating-Point Rating Parsing
    // -------------------------------------------------------------

    @Test
    fun `XmpRatingParser fails to parse Adobe standard Real or floating-point ratings`() {
        val xmpFloat = "<xmp:Rating>3.0</xmp:Rating>"
        val parsed = XmpRatingParser.parseRating(xmpFloat)
        assertEquals("Adobe standard Real rating 3.0 should parse as 3", 3, parsed)
    }

    // -------------------------------------------------------------
    // 5. Case-Insensitive Tag Names and Whitespace
    // -------------------------------------------------------------

    @Test
    fun `XmpRatingParser parses ratings with whitespace around values`() {
        val xmpWhitespace = "<xmp:Rating>   5   </xmp:Rating>"
        assertEquals(5, XmpRatingParser.parseRating(xmpWhitespace))
    }
}
