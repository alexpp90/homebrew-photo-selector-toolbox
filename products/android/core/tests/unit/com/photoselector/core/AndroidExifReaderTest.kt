package com.photoselector.core

import com.photoselector.core.reader.AndroidExifReader
import com.photoselector.core.reader.BinaryExifRatingReader
import com.photoselector.core.reader.XmpRatingParser
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(RobolectricTestRunner::class)
class AndroidExifReaderTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val reader = AndroidExifReader()

    @Test
    fun `supportedMimeTypes includes JPEG and RAWs including CR3`() {
        val types = reader.supportedMimeTypes()
        assertTrue(types.contains("image/jpeg"))
        assertTrue(types.contains("image/x-adobe-dng"))
        assertTrue(types.contains("image/x-sony-arw"))
        assertTrue(types.contains("image/x-canon-cr2"))
        assertTrue(types.contains("image/x-canon-cr3"))
        assertFalse(types.contains("image/png"))
    }

    @Test
    fun `readExif with context and file Uri reads companion xmp rating`() = runTest {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        val imageFile = tempFolder.newFile("URI_TEST.CR3")
        imageFile.writeBytes(ByteArray(100))
        val sidecar = File(tempFolder.root, "URI_TEST.CR3.xmp")
        sidecar.writeText("<xmp:Rating>4</xmp:Rating>")

        val uri = android.net.Uri.fromFile(imageFile)
        val exif = reader.readExif(context, uri)
        assertNotNull(exif)
        assertEquals(4, exif?.rating)
    }

    @Test
    fun `readExif with context and content Uri via ContentResolver stream returns ExifData`() = runTest {
        val imageFile = tempFolder.newFile("STREAM_TEST.JPG")
        imageFile.writeBytes(ByteArray(64))

        val resolver = io.mockk.mockk<android.content.ContentResolver>()
        val context = io.mockk.mockk<android.content.Context>()
        val uri = android.net.Uri.parse("content://com.photoselector.test/images/1")

        io.mockk.every { context.contentResolver } returns resolver
        io.mockk.every { resolver.openFileDescriptor(uri, "r") } returns null
        io.mockk.every { resolver.openInputStream(uri) } answers { imageFile.inputStream() }

        val exif = reader.readExif(context, uri)
        assertNotNull(exif)
        assertEquals("Unknown", exif?.lens)
        assertFalse(exif!!.isFallback)
    }

    @Test
    fun `XmpRatingParser extracts element tag rating`() {
        val xmp = """
            <x:xmpmeta xmlns:x="adobe:ns:meta/">
              <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
                <rdf:Description rdf:about="" xmlns:xmp="http://ns.adobe.com/xap/1.0/">
                  <xmp:Rating>4</xmp:Rating>
                </rdf:Description>
              </rdf:RDF>
            </x:xmpmeta>
        """.trimIndent()
        assertEquals(4, XmpRatingParser.parseRating(xmp))
    }

    @Test
    fun `XmpRatingParser extracts attribute rating`() {
        val xmp = """
            <rdf:Description rdf:about="" xmlns:xmp="http://ns.adobe.com/xap/1.0/" xmp:Rating="5"/>
        """.trimIndent()
        assertEquals(5, XmpRatingParser.parseRating(xmp))
    }

    @Test
    fun `XmpRatingParser extracts reject rating`() {
        val xmp = "<xmp:Rating>-1</xmp:Rating>"
        assertEquals(-1, XmpRatingParser.parseRating(xmp))
    }

    @Test
    fun `XmpRatingParser extracts percent rating`() {
        val xmp75 = "<MicrosoftPhoto:RatingPercent>75</MicrosoftPhoto:RatingPercent>"
        assertEquals(4, XmpRatingParser.parseRating(xmp75))

        val xmp99 = """<xmpmeta RatingPercent="99" />"""
        assertEquals(5, XmpRatingParser.parseRating(xmp99))

        val xmp25 = "<RatingPercent>25</RatingPercent>"
        assertEquals(2, XmpRatingParser.parseRating(xmp25))

        val xmp0 = "<RatingPercent>0</RatingPercent>"
        assertEquals(0, XmpRatingParser.parseRating(xmp0))
    }

    @Test
    fun `XmpRatingParser returns null on missing or empty`() {
        assertNull(XmpRatingParser.parseRating(null))
        assertNull(XmpRatingParser.parseRating(""))
        assertNull(XmpRatingParser.parseRating("<x:xmpmeta></x:xmpmeta>"))
    }

    @Test
    fun `readExif reads rating from companion xmp sidecar`() = runTest {
        val imageFile = tempFolder.newFile("DSC0001.ARW")
        imageFile.writeBytes(ByteArray(100)) // Raw stub

        val sidecarFile = File(tempFolder.root, "DSC0001.ARW.xmp")
        sidecarFile.writeText(
            """
            <x:xmpmeta xmlns:x="adobe:ns:meta/">
              <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
                <rdf:Description xmlns:xmp="http://ns.adobe.com/xap/1.0/">
                  <xmp:Rating>5</xmp:Rating>
                </rdf:Description>
              </rdf:RDF>
            </x:xmpmeta>
            """.trimIndent()
        )

        val exif = reader.readExif(imageFile)
        assertNotNull(exif)
        assertEquals(5, exif?.rating)
    }

    @Test
    fun `readExif reads rating from stem companion xmp sidecar`() = runTest {
        val imageFile = tempFolder.newFile("IMG_9999.CR3")
        imageFile.writeBytes(ByteArray(100))

        val stemSidecar = File(tempFolder.root, "IMG_9999.xmp")
        stemSidecar.writeText("<xmp:Rating>3</xmp:Rating>")

        val exif = reader.readExif(imageFile)
        assertNotNull(exif)
        assertEquals(3, exif?.rating)
    }

    @Test
    fun `BinaryExifRatingReader parses tag 0x4746 from little-endian TIFF buffer`() {
        // Construct a synthetic minimal TIFF buffer in memory
        val buffer = ByteBuffer.allocate(128).order(ByteOrder.LITTLE_ENDIAN)
        // TIFF header: "II" (0x4949), 42 (0x002A), offset to IFD0 = 8
        buffer.put('I'.code.toByte())
        buffer.put('I'.code.toByte())
        buffer.putShort(42)
        buffer.putInt(8)

        // IFD0 at offset 8:
        // Number of entries: 1
        buffer.putShort(1)
        // Entry 1: tag = 0x4746 (18246), type = 3 (SHORT), count = 1, value = 4
        buffer.putShort(0x4746.toShort())
        buffer.putShort(3)
        buffer.putInt(1)
        buffer.putShort(4) // Short value
        buffer.putShort(0) // Padding in 4-byte value field

        buffer.flip()
        val rating = BinaryExifRatingReader.parseFromBuffer(buffer)
        assertEquals(4, rating)
    }

    @Test
    fun `BinaryExifRatingReader parses tag 0x4749 RatingPercent from big-endian TIFF buffer`() {
        val buffer = ByteBuffer.allocate(128).order(ByteOrder.BIG_ENDIAN)
        // TIFF header: "MM" (0x4D4D), 42, offset = 8
        buffer.put('M'.code.toByte())
        buffer.put('M'.code.toByte())
        buffer.putShort(42)
        buffer.putInt(8)

        // IFD0 at offset 8:
        buffer.putShort(1)
        // Entry 1: tag = 0x4749, type = 3 (SHORT), count = 1, value = 75% -> 4 stars
        buffer.putShort(0x4749.toShort())
        buffer.putShort(3)
        buffer.putInt(1)
        buffer.putShort(75) // 75%
        buffer.putShort(0)

        buffer.flip()
        val rating = BinaryExifRatingReader.parseFromBuffer(buffer)
        assertEquals(4, rating)
    }

    @Test
    fun `readExif sidecar with rating 0 takes absolute precedence`() = runTest {
        val imageFile = tempFolder.newFile("unrate_test.CR3")
        imageFile.writeBytes(ByteArray(100))

        val sidecarFile = File(tempFolder.root, "unrate_test.CR3.xmp")
        sidecarFile.writeText(
            """
            <x:xmpmeta xmlns:x="adobe:ns:meta/">
              <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
                <rdf:Description xmlns:xmp="http://ns.adobe.com/xap/1.0/">
                  <xmp:Rating>0</xmp:Rating>
                </rdf:Description>
              </rdf:RDF>
            </x:xmpmeta>
            """.trimIndent()
        )

        val exif = reader.readExif(imageFile)
        assertNotNull(exif)
        assertEquals(0, exif?.rating)
    }

    @Test
    fun `XmpRatingParser parses Adobe standard real decimal ratings and attributes`() {
        val xmpTag = "<xmp:Rating>4.0</xmp:Rating>"
        assertEquals(4, XmpRatingParser.parseRating(xmpTag))

        val xmpAttr = "<rdf:Description xmp:Rating=\"5.000000\"/>"
        assertEquals(5, XmpRatingParser.parseRating(xmpAttr))

        val xmpReject = "<xmp:Rating>-1.0</xmp:Rating>"
        assertEquals(-1, XmpRatingParser.parseRating(xmpReject))
    }

    @Test
    fun `BinaryExifRatingReader handles cyclic SubIFD and negative offset safely`() {
        // Cyclic SubIFD
        val cyclicBuffer = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN)
        cyclicBuffer.put('I'.code.toByte())
        cyclicBuffer.put('I'.code.toByte())
        cyclicBuffer.putShort(42)
        cyclicBuffer.putInt(8)
        cyclicBuffer.putShort(1)
        cyclicBuffer.putShort(0x8769.toShort())
        cyclicBuffer.putShort(4)
        cyclicBuffer.putInt(1)
        cyclicBuffer.putInt(8)
        cyclicBuffer.flip()
        assertNull(BinaryExifRatingReader.parseFromBuffer(cyclicBuffer))

        // Negative SubIFD offset
        val negBuffer = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN)
        negBuffer.put('I'.code.toByte())
        negBuffer.put('I'.code.toByte())
        negBuffer.putShort(42)
        negBuffer.putInt(8)
        negBuffer.putShort(1)
        negBuffer.putShort(0x8769.toShort())
        negBuffer.putShort(4)
        negBuffer.putInt(1)
        negBuffer.putInt(-100)
        negBuffer.flip()
        assertNull(BinaryExifRatingReader.parseFromBuffer(negBuffer))
    }
}
