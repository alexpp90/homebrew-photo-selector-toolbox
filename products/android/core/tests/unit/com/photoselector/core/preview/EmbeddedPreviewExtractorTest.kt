package com.photoselector.core.preview

import android.graphics.BitmapFactory
import com.photoselector.core.conformance.FormatManifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

/**
 * Corpus-independent tests: synthetic containers built around the committed
 * sample.jpg (800x533), so these run in every CI job.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EmbeddedPreviewExtractorTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val jpeg: ByteArray by lazy { File(FormatManifest.repoRoot, "testdata/formats/bitmaps/sample.jpg").readBytes() }

    /** Little-endian TIFF: IFD0 with [entries] (tag, type, value) and the JPEG appended. */
    private fun tiffWith(magic: Int = 42, entries: (jpegOffset: Int) -> List<Triple<Int, Int, Int>>): File {
        val header = 8
        val probe = entries(0)
        val ifdSize = 2 + probe.size * 12 + 4
        val jpegOffset = header + ifdSize
        val list = entries(jpegOffset).sortedBy { it.first }
        val b = ByteBuffer.allocate(jpegOffset).order(ByteOrder.LITTLE_ENDIAN)
        b.put('I'.code.toByte()).put('I'.code.toByte()).putShort(magic.toShort()).putInt(8)
        b.putShort(list.size.toShort())
        for ((tag, type, value) in list) {
            b.putShort(tag.toShort()).putShort(type.toShort()).putInt(1)
            if (type == 3) b.putShort(value.toShort()).putShort(0) else b.putInt(value)
        }
        b.putInt(0)
        val f = tmp.newFile()
        f.writeBytes(b.array() + jpeg)
        return f
    }

    private fun findAll(f: File) = RandomAccessFile(f, "r").use { EmbeddedPreviewExtractor.findAll(it.channel) }

    @Test
    fun `TIFF JPEGInterchangeFormat preview is found with its dimensions`() {
        val f = tiffWith { off -> listOf(Triple(0x201, 4, off), Triple(0x202, 4, jpeg.size)) }
        val all = findAll(f)
        assertEquals(1, all.size)
        assertEquals(800, all[0].width)
        assertEquals(533, all[0].height)
        assertEquals(1, all[0].orientation)
    }

    @Test
    fun `single JPEG strip is a preview and IFD0 orientation is applied on decode`() {
        val f = tiffWith { off ->
            listOf(Triple(0x103, 3, 6), Triple(0x111, 4, off), Triple(0x117, 4, jpeg.size), Triple(0x112, 3, 6))
        }
        val extracted = assertNotNull(EmbeddedPreviewExtractor.extract(f)).let { EmbeddedPreviewExtractor.extract(f)!! }
        assertEquals(6, extracted.preview.orientation)
        val bmp = assertNotNull(extracted.decode()).let { extracted.decode()!! }
        assertEquals(533, bmp.width)
        assertEquals(800, bmp.height)
    }

    @Test
    fun `Panasonic JpgFromRaw tag is a preview`() {
        val f = tiffWith(magic = 0x55) { off -> listOf(Triple(0x2E, 7, off)) }
        // count must be the JPEG length for tag 0x2E — rewrite count field.
        val bytes = f.readBytes()
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putInt(8 + 2 + 4, jpeg.size)
        f.writeBytes(bytes)
        assertEquals(listOf(800), findAll(f).map { it.width })
    }

    @Test
    fun `RAF header points at the JPEG`() {
        val head = ByteArray(100)
        "FUJIFILMCCD-RAW 0201FF383501".toByteArray().copyInto(head)
        ByteBuffer.wrap(head).order(ByteOrder.BIG_ENDIAN).putInt(84, 100).putInt(88, jpeg.size)
        val f = tmp.newFile("x.raf").apply { writeBytes(head + jpeg) }
        assertEquals(listOf(800 to 533), findAll(f).map { it.width to it.height })
    }

    @Test
    fun `lossless JPEG (SOF3) payload is rejected`() {
        val sof3 = jpeg.copyOf()
        // Rewrite the first SOF0 marker to SOF3.
        for (i in 2 until sof3.size - 1) {
            if (sof3[i] == 0xFF.toByte() && sof3[i + 1] == 0xC0.toByte()) { sof3[i + 1] = 0xC3.toByte(); break }
        }
        val f = tmp.newFile()
        val prefix = ByteBuffer.allocate(8 + 2 + 24 + 4).order(ByteOrder.LITTLE_ENDIAN)
        prefix.put('I'.code.toByte()).put('I'.code.toByte()).putShort(42).putInt(8).putShort(2)
        prefix.putShort(0x201).putShort(4).putInt(1).putInt(38)
        prefix.putShort(0x202).putShort(4).putInt(1).putInt(sof3.size)
        prefix.putInt(0)
        f.writeBytes(prefix.array() + sof3)
        assertTrue(findAll(f).isEmpty())
    }

    @Test
    fun `garbage, empty and truncated files yield nothing and never throw`() {
        val empty = tmp.newFile()
        assertTrue(findAll(empty).isEmpty())
        val garbage = tmp.newFile().apply { writeBytes(ByteArray(4096) { (it * 31).toByte() }) }
        assertTrue(findAll(garbage).isEmpty())
        val full = tiffWith { off -> listOf(Triple(0x201, 4, off), Triple(0x202, 4, jpeg.size)) }
        val truncated = tmp.newFile().apply { writeBytes(full.readBytes().copyOf(60)) }
        assertTrue(findAll(truncated).isEmpty())
        assertNull(EmbeddedPreviewExtractor.extract(File(tmp.root, "missing.arw")))
    }

    @Test
    fun `choose prefers the smallest preview that reaches the minimum, else the largest`() {
        val small = EmbeddedPreview(0, 1, 160, 120)
        val mid = EmbeddedPreview(1, 1, 1616, 1080)
        val big = EmbeddedPreview(2, 1, 6000, 4000)
        val all = listOf(big, small, mid)
        assertEquals(mid, EmbeddedPreviewExtractor.choose(all, 1024))
        assertEquals(big, EmbeddedPreviewExtractor.choose(all, 2048))
        assertEquals(big, EmbeddedPreviewExtractor.choose(all, 9000))
        assertEquals(small, EmbeddedPreviewExtractor.choose(all, 0))
        assertNull(EmbeddedPreviewExtractor.choose(emptyList(), 0))
    }

    @Test
    fun `sampleSizeFor never drops below the requested long edge`() {
        assertEquals(1, ExtractedPreview.sampleSizeFor(1616, 1024))
        assertEquals(4, ExtractedPreview.sampleSizeFor(6000, 1024))
        assertEquals(2, ExtractedPreview.sampleSizeFor(6000, 2048))
        assertEquals(1, ExtractedPreview.sampleSizeFor(800, Int.MAX_VALUE))
    }

    // ── TIFF bitmap decoder ────────────────────────────────────────────────

    @Test
    fun `LZW predictor TIFF decodes to the same pixels as the PNG of the same photo`() {
        val tif = File(FormatManifest.repoRoot, "testdata/formats/bitmaps/sample.tif")
        val png = File(FormatManifest.repoRoot, "testdata/formats/bitmaps/sample.png")
        val decoded = assertNotNull(TiffBitmapDecoder.decode(tif)).let { TiffBitmapDecoder.decode(tif)!! }
        assertEquals(400, decoded.width)
        assertEquals(266, decoded.height)
        val ref = BitmapFactory.decodeFile(png.path)
        assertEquals(decoded.width, ref.width)
        var diff = 0L
        for (y in 0 until ref.height) for (x in 0 until ref.width) {
            val a = decoded.argb[y * decoded.width + x]
            val b = ref.getPixel(x, y)
            diff += abs(((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)) +
                abs(((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)) + abs((a and 0xFF) - (b and 0xFF))
        }
        val mean = diff.toDouble() / (ref.width * ref.height * 3)
        assertTrue("mean channel difference $mean", mean < 3.0)
        val bmp = decoded.toBitmap()
        assertEquals(400, bmp.width)
    }

    @Test
    fun `TIFF decoder subsamples to the requested long edge`() {
        val tif = File(FormatManifest.repoRoot, "testdata/formats/bitmaps/sample.tif")
        val decoded = TiffBitmapDecoder.decode(tif, maxLongEdge = 200)!!
        assertEquals(200, decoded.width)
        assertEquals(133, decoded.height)
    }

    @Test
    fun `uncompressed and PackBits grey TIFFs decode`() {
        val w = 4
        val h = 2
        val pixels = byteArrayOf(0, 64, -128, -1, 10, 20, 30, 40)
        fun grey(compression: Int, data: ByteArray): File {
            val entries = listOf(
                0x100 to w, 0x101 to h, 0x102 to 8, 0x103 to compression, 0x106 to 1,
                0x111 to 0, 0x115 to 1, 0x116 to h, 0x117 to data.size,
            )
            val dataOffset = 8 + 2 + entries.size * 12 + 4
            val b = ByteBuffer.allocate(dataOffset).order(ByteOrder.LITTLE_ENDIAN)
            b.put('I'.code.toByte()).put('I'.code.toByte()).putShort(42).putInt(8).putShort(entries.size.toShort())
            for ((tag, v) in entries) {
                b.putShort(tag.toShort()).putShort(4).putInt(1).putInt(if (tag == 0x111) dataOffset else v)
            }
            b.putInt(0)
            return tmp.newFile().apply { writeBytes(b.array() + data) }
        }
        val packed = ByteArrayOutputStream().apply { write(7); write(pixels) }.toByteArray()
        for (f in listOf(grey(1, pixels), grey(32773, packed))) {
            val d = TiffBitmapDecoder.decode(f)!!
            assertEquals(w, d.width)
            assertEquals(pixels.map { (it.toInt() and 0xFF) }, d.argb.map { it and 0xFF })
        }
    }

    @Test
    fun `non-TIFF input returns null`() {
        val jpg = File(FormatManifest.repoRoot, "testdata/formats/bitmaps/sample.jpg")
        assertNull(TiffBitmapDecoder.decode(jpg))
    }
}
