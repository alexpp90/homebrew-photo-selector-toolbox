package com.photoselector.core.preview

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/**
 * A JPEG stream embedded in a RAW file.
 *
 * @property offset absolute file position of the JPEG SOI marker.
 * @property length bytes from [offset] to the end of the stream.
 * @property width pixel width from the JPEG frame header (before rotation).
 * @property height pixel height from the JPEG frame header (before rotation).
 * @property orientation EXIF orientation (1..8) the preview must be displayed
 *   with — the RAW container's orientation, else the JPEG's own, else 1.
 */
data class EmbeddedPreview(
    val offset: Long,
    val length: Long,
    val width: Int,
    val height: Int,
    val orientation: Int = 1,
) {
    val longEdge: Int get() = maxOf(width, height)
}

/** An [EmbeddedPreview] with its bytes read into memory. */
class ExtractedPreview(val preview: EmbeddedPreview, val jpeg: ByteArray) {

    /**
     * Decode to a software `ARGB_8888` bitmap whose long edge is at most about
     * [maxLongEdge] (power-of-two subsampling, never upscaled), rotated by
     * [EmbeddedPreview.orientation]. Null if the platform cannot decode it.
     */
    fun decode(maxLongEdge: Int = Int.MAX_VALUE): Bitmap? {
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(preview.longEdge, maxLongEdge)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, opts) ?: return null
        return applyOrientation(decoded, preview.orientation)
    }

    companion object {
        /** Largest power of two that keeps the long edge at or above [maxLongEdge]. */
        fun sampleSizeFor(longEdge: Int, maxLongEdge: Int): Int {
            var sample = 1
            if (longEdge <= 0 || maxLongEdge <= 0) return 1
            while (longEdge / (sample * 2) >= maxLongEdge) sample *= 2
            return sample
        }

        /** Rotate/flip [bitmap] for EXIF [orientation]; returns [bitmap] itself for 1. */
        fun applyOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
            val m = Matrix()
            when (orientation) {
                2 -> m.setScale(-1f, 1f)
                3 -> m.setRotate(180f)
                4 -> m.setScale(1f, -1f)
                5 -> { m.setRotate(90f); m.postScale(-1f, 1f) }
                6 -> m.setRotate(90f)
                7 -> { m.setRotate(-90f); m.postScale(-1f, 1f) }
                8 -> m.setRotate(-90f)
                else -> return bitmap
            }
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
            if (rotated !== bitmap) bitmap.recycle()
            return rotated
        }
    }
}

/**
 * Finds the JPEG previews camera RAW files carry, without decoding any RAW data.
 *
 * Pure Kotlin over positional reads, so it works on any seekable descriptor
 * (a `File`, a SAF `ParcelFileDescriptor`) and in JVM tests. Supported
 * containers:
 *
 * - **TIFF family** — ARW, CR2, NEF, NRW, PEF, DNG, SRW, ORF, RW2, 3FR, ERF…:
 *   every IFD in the IFD0 chain and its SubIFDs, using `JPEGInterchangeFormat`
 *   and single-strip JPEG-compressed images; Panasonic `JpgFromRaw` (0x2E);
 *   the Olympus maker-note `PreviewImage`.
 * - **Fujifilm RAF** — the JPEG addressed by the RAF header.
 * - **Canon CR3** — `PRVW`, `THMB` and the full-size JPEG of track 1.
 *
 * Every candidate is validated by walking its JPEG markers to a baseline or
 * progressive frame header (SOF0–SOF2), which also yields its dimensions;
 * lossless-JPEG RAW payloads (DNG, CR2 sensor data, SOF3) are rejected.
 *
 * Never throws: a file that is not a recognised container yields no previews.
 */
object EmbeddedPreviewExtractor {

    /** Refuse to read previews larger than this into memory. */
    private const val MAX_PREVIEW_BYTES = 64L * 1024 * 1024
    private const val MAX_SUB_IFD_DEPTH = 3

    /** All valid embedded JPEG previews, in discovery order. */
    fun findAll(channel: FileChannel): List<EmbeddedPreview> = try {
        val reader = ChannelReader(channel)
        val raw = candidates(reader)
        val seen = mutableSetOf<Long>()
        raw.mapNotNull { c ->
            if (!seen.add(c.offset)) return@mapNotNull null
            validate(reader, c.offset, c.length, c.orientation)
        }
    } catch (e: Exception) {
        emptyList()
    }

    /**
     * The preview to use for a display or analysis of at least [minLongEdge]
     * pixels: the smallest preview that reaches it, else the largest there is.
     * Null if the file embeds no usable preview.
     */
    fun find(channel: FileChannel, minLongEdge: Int = 0): EmbeddedPreview? =
        choose(findAll(channel), minLongEdge)

    /** Read [preview]'s bytes. Empty if it is implausibly large or unreadable. */
    fun read(channel: FileChannel, preview: EmbeddedPreview): ByteArray {
        if (preview.length <= 0 || preview.length > MAX_PREVIEW_BYTES) return ByteArray(0)
        return try {
            ChannelReader(channel).bytes(preview.offset, preview.length.toInt())
        } catch (e: Exception) {
            ByteArray(0)
        }
    }

    fun extract(channel: FileChannel, minLongEdge: Int = 0): ExtractedPreview? {
        val preview = find(channel, minLongEdge) ?: return null
        val bytes = read(channel, preview)
        return if (bytes.isEmpty()) null else ExtractedPreview(preview, bytes)
    }

    fun extract(file: File, minLongEdge: Int = 0): ExtractedPreview? = try {
        RandomAccessFile(file, "r").use { extract(it.channel, minLongEdge) }
    } catch (e: Exception) {
        null
    }

    /** Opens [uri] read-only (file or content scheme) and extracts its preview. */
    fun extract(context: Context, uri: Uri, minLongEdge: Int = 0): ExtractedPreview? = try {
        if (uri.scheme == null || uri.scheme == "file") {
            uri.path?.let { extract(File(it), minLongEdge) }
        } else {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                // The stream is not closed on purpose: the descriptor belongs to `pfd`.
                extract(FileInputStream(pfd.fileDescriptor).channel, minLongEdge)
            }
        }
    } catch (e: Exception) {
        null
    }

    internal fun choose(previews: List<EmbeddedPreview>, minLongEdge: Int): EmbeddedPreview? {
        if (previews.isEmpty()) return null
        val sufficient = previews.filter { it.longEdge >= minLongEdge }
        return if (sufficient.isNotEmpty()) {
            sufficient.minBy { it.width.toLong() * it.height }
        } else {
            previews.maxBy { it.width.toLong() * it.height }
        }
    }

    // ── Candidates ──────────────────────────────────────────────────────────

    private class Candidate(val offset: Long, val length: Long, val orientation: Int?)

    private fun candidates(reader: ChannelReader): List<Candidate> {
        val head = reader.bytes(0, 16)
        if (head.size >= 16 && String(head, Charsets.ISO_8859_1).startsWith("FUJIFILMCCD-RAW")) {
            return rafCandidates(reader)
        }
        Cr3Structure.parse(reader)?.let { cr3 ->
            val orientation = cr3.cmt1?.let { box ->
                TiffReader.at(reader, box.payload)?.let { t ->
                    t.readIfd(t.firstIfdOffset)?.get(Tags.ORIENTATION)?.long()?.toInt()
                }
            }
            return cr3.jpegs.map { (o, l) -> Candidate(o, l, orientation) }
        }
        val tiff = TiffReader.at(reader, 0) ?: return emptyList()
        return tiffCandidates(tiff)
    }

    private fun rafCandidates(reader: ChannelReader): List<Candidate> {
        val h = reader.bytes(84, 8)
        if (h.size < 8) return emptyList()
        val b = ByteBuffer.wrap(h).order(ByteOrder.BIG_ENDIAN)
        val offset = b.getInt(0).toLong() and 0xFFFFFFFFL
        val length = b.getInt(4).toLong() and 0xFFFFFFFFL
        return listOf(Candidate(offset, length, null))
    }

    private fun tiffCandidates(tiff: TiffReader): List<Candidate> {
        val out = mutableListOf<Candidate>()
        val chain = tiff.ifdChain()
        val orientation = chain.firstOrNull()?.get(Tags.ORIENTATION)?.long()?.toInt()
        val visited = mutableSetOf<Long>()

        fun visit(ifd: TiffIfd, depth: Int) {
            if (!visited.add(ifd.position)) return
            collectFrom(tiff, ifd, orientation, out)
            if (depth < MAX_SUB_IFD_DEPTH) {
                ifd[Tags.SUB_IFDS]?.longs()?.forEach { off ->
                    tiff.readIfd(off)?.let { visit(it, depth + 1) }
                }
            }
            ifd[Tags.EXIF_IFD]?.long()?.let { off ->
                tiff.readIfd(off)?.let { exif ->
                    exif[Tags.MAKER_NOTE]?.let { olympusMakerNote(tiff, it, orientation, out) }
                }
            }
        }
        chain.forEach { visit(it, 0) }
        return out
    }

    private fun collectFrom(tiff: TiffReader, ifd: TiffIfd, orientation: Int?, out: MutableList<Candidate>) {
        val jif = ifd[Tags.JPEG_INTERCHANGE_FORMAT]?.long()
        val jifLen = ifd[Tags.JPEG_INTERCHANGE_FORMAT_LENGTH]?.long()
        if (jif != null && jifLen != null && jif > 0 && jifLen > 0) {
            out += Candidate(tiff.base + jif, jifLen, orientation)
        }
        val compression = ifd[Tags.COMPRESSION]?.long()
        val strips = ifd[Tags.STRIP_OFFSETS]?.longs()
        val counts = ifd[Tags.STRIP_BYTE_COUNTS]?.longs()
        if ((compression == 6L || compression == 7L || compression == 99L) &&
            strips != null && counts != null && strips.size == 1 && counts.size == 1
        ) {
            out += Candidate(tiff.base + strips[0], counts[0], orientation)
        }
        ifd[Tags.PANASONIC_JPG_FROM_RAW]?.let { e ->
            if (e.count > 0) out += Candidate(e.valuePosition, e.count, orientation)
        }
    }

    /** Olympus/OM System maker note → CameraSettings (0x2020) → PreviewImage. */
    private fun olympusMakerNote(tiff: TiffReader, note: TiffEntry, orientation: Int?, out: MutableList<Candidate>) {
        val m = note.valuePosition
        val h = tiff.reader.bytes(m, 12)
        if (h.size < 12) return
        val text = String(h, Charsets.ISO_8859_1)
        val (ifdPos, valueBase, order) = when {
            text.startsWith("OLYMPUS\u0000") || text.startsWith("OM SYSTEM") -> {
                val headerLen = if (text.startsWith("OM SYSTEM")) 16L else 12L
                val bo = tiff.reader.bytes(m + headerLen - 4, 2)
                val o = if (bo.size == 2 && bo[0] == 'M'.code.toByte()) ByteOrder.BIG_ENDIAN else ByteOrder.LITTLE_ENDIAN
                Triple(m + headerLen, m, o)
            }
            text.startsWith("OLYMP\u0000") -> Triple(m + 8, tiff.base, tiff.order)
            else -> return
        }
        val noteReader = TiffReader.headerless(tiff.reader, valueBase, order)
        val ifd = noteReader.readIfdAt(ifdPos, valueBase) ?: return
        val cs = ifd[0x2020] ?: return
        val csPos = when (cs.type) {
            TiffEntry.TYPE_LONG, TiffEntry.TYPE_IFD -> valueBase + (cs.long() ?: return)
            else -> cs.valuePosition
        }
        val settings = noteReader.readIfdAt(csPos, valueBase) ?: return
        val start = settings[0x0101]?.long() ?: return
        val length = settings[0x0102]?.long() ?: return
        if (start > 0 && length > 0) out += Candidate(valueBase + start, length, orientation)
    }

    // ── Validation ──────────────────────────────────────────────────────────

    /** Walk JPEG markers from [offset] to a SOF0–SOF2 header; null if it is not a usable JPEG. */
    private fun validate(reader: ChannelReader, offset: Long, length: Long, orientation: Int?): EmbeddedPreview? {
        if (offset < 0 || length < 4 || offset + length > reader.size + 1) return null
        val end = minOf(offset + length, reader.size)
        val soi = reader.bytes(offset, 2)
        if (soi.size < 2 || soi[0] != 0xFF.toByte() || soi[1] != 0xD8.toByte()) return null
        var pos = offset + 2
        var ownOrientation: Int? = null
        var markers = 0
        while (pos + 4 <= end && markers++ < 256) {
            val h = reader.bytes(pos, 4)
            if (h.size < 4 || h[0] != 0xFF.toByte()) return null
            val marker = h[1].toInt() and 0xFF
            if (marker == 0xFF) { pos += 1; continue }
            if (marker == 0xD8 || marker in 0xD0..0xD7 || marker == 0x01) { pos += 2; continue }
            val segLen = ((h[2].toInt() and 0xFF) shl 8) or (h[3].toInt() and 0xFF)
            if (segLen < 2) return null
            when (marker) {
                0xC0, 0xC1, 0xC2 -> {
                    val sof = reader.bytes(pos + 4, 5)
                    if (sof.size < 5) return null
                    val height = ((sof[1].toInt() and 0xFF) shl 8) or (sof[2].toInt() and 0xFF)
                    val width = ((sof[3].toInt() and 0xFF) shl 8) or (sof[4].toInt() and 0xFF)
                    if (width <= 0 || height <= 0) return null
                    return EmbeddedPreview(
                        offset = offset,
                        length = end - offset,
                        width = width,
                        height = height,
                        orientation = (orientation ?: ownOrientation ?: 1).takeIf { it in 1..8 } ?: 1,
                    )
                }
                0xC3, in 0xC5..0xC7, in 0xC9..0xCB, in 0xCD..0xCF, 0xDA, 0xD9 -> return null
                0xE1 -> if (ownOrientation == null) ownOrientation = app1Orientation(reader, pos + 4, segLen - 2)
            }
            pos += 2 + segLen
        }
        return null
    }

    private fun app1Orientation(reader: ChannelReader, payload: Long, length: Int): Int? {
        if (length < 14) return null
        val sig = reader.bytes(payload, 6)
        if (sig.size < 6 || String(sig, 0, 4, Charsets.ISO_8859_1) != "Exif") return null
        val tiff = TiffReader.at(reader, payload + 6) ?: return null
        return tiff.readIfd(tiff.firstIfdOffset)?.get(Tags.ORIENTATION)?.long()?.toInt()
    }
}
