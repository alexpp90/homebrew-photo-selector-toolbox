package com.photoselector.core.preview

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/**
 * Positional, bounds-checked reads over a [FileChannel].
 *
 * Every read is a `pread` at an absolute position, so a channel shared with
 * another reader (ExifInterface on the same descriptor, say) is never moved.
 * Reads past the end return short arrays instead of throwing; callers treat a
 * short read as "not this structure".
 */
internal class ChannelReader(private val channel: FileChannel) {
    val size: Long = channel.size()

    fun bytes(position: Long, length: Int): ByteArray {
        if (position < 0 || length <= 0 || position >= size) return ByteArray(0)
        val wanted = minOf(length.toLong(), size - position).toInt()
        val buffer = ByteBuffer.allocate(wanted)
        var at = position
        while (buffer.hasRemaining()) {
            val n = channel.read(buffer, at)
            if (n <= 0) break
            at += n
        }
        return if (buffer.position() == wanted) buffer.array() else buffer.array().copyOf(buffer.position())
    }
}

/** One IFD entry. [valueBytes] holds the value whether it was inline or out of line. */
internal class TiffEntry(
    val tag: Int,
    val type: Int,
    val count: Long,
    /** Absolute file position of the value (inline values point into the entry itself). */
    val valuePosition: Long,
    val valueBytes: ByteArray,
    private val order: ByteOrder,
) {
    private fun buf(): ByteBuffer = ByteBuffer.wrap(valueBytes).order(order)

    /** The raw 32-bit "value or offset" field interpreted as an unsigned offset. */
    val rawOffset: Long get() = valuePosition

    fun longs(): LongArray {
        val b = buf()
        val n = minOf(count, (valueBytes.size / typeSize(type).coerceAtLeast(1)).toLong()).toInt()
        return LongArray(n) {
            when (type) {
                TYPE_BYTE, TYPE_UNDEFINED -> (b.get(it).toLong() and 0xFF)
                TYPE_SBYTE -> b.get(it).toLong()
                TYPE_SHORT -> (b.getShort(it * 2).toLong() and 0xFFFF)
                TYPE_SSHORT -> b.getShort(it * 2).toLong()
                TYPE_LONG, TYPE_IFD -> (b.getInt(it * 4).toLong() and 0xFFFFFFFFL)
                TYPE_SLONG -> b.getInt(it * 4).toLong()
                else -> 0L
            }
        }
    }

    fun long(): Long? = longs().firstOrNull()

    fun doubles(): DoubleArray {
        val b = buf()
        val n = minOf(count, (valueBytes.size / typeSize(type).coerceAtLeast(1)).toLong()).toInt()
        return when (type) {
            TYPE_RATIONAL -> DoubleArray(n) {
                val num = b.getInt(it * 8).toLong() and 0xFFFFFFFFL
                val den = b.getInt(it * 8 + 4).toLong() and 0xFFFFFFFFL
                if (den == 0L) Double.NaN else num.toDouble() / den
            }
            TYPE_SRATIONAL -> DoubleArray(n) {
                val num = b.getInt(it * 8)
                val den = b.getInt(it * 8 + 4)
                if (den == 0) Double.NaN else num.toDouble() / den
            }
            TYPE_FLOAT -> DoubleArray(n) { b.getFloat(it * 4).toDouble() }
            TYPE_DOUBLE -> DoubleArray(n) { b.getDouble(it * 8) }
            else -> longs().map { it.toDouble() }.toDoubleArray()
        }
    }

    fun double(): Double? = doubles().firstOrNull()?.takeIf { it.isFinite() }

    fun string(): String? {
        if (type != TYPE_ASCII && type != TYPE_UNDEFINED && type != TYPE_BYTE) return null
        val end = valueBytes.indexOf(0).let { if (it < 0) valueBytes.size else it }
        return String(valueBytes, 0, end, Charsets.ISO_8859_1).trim().ifEmpty { null }
    }

    companion object {
        const val TYPE_BYTE = 1
        const val TYPE_ASCII = 2
        const val TYPE_SHORT = 3
        const val TYPE_LONG = 4
        const val TYPE_RATIONAL = 5
        const val TYPE_SBYTE = 6
        const val TYPE_UNDEFINED = 7
        const val TYPE_SSHORT = 8
        const val TYPE_SLONG = 9
        const val TYPE_SRATIONAL = 10
        const val TYPE_FLOAT = 11
        const val TYPE_DOUBLE = 12
        const val TYPE_IFD = 13

        fun typeSize(type: Int): Int = when (type) {
            TYPE_BYTE, TYPE_ASCII, TYPE_SBYTE, TYPE_UNDEFINED -> 1
            TYPE_SHORT, TYPE_SSHORT -> 2
            TYPE_LONG, TYPE_SLONG, TYPE_FLOAT, TYPE_IFD -> 4
            TYPE_RATIONAL, TYPE_SRATIONAL, TYPE_DOUBLE -> 8
            else -> 0
        }
    }
}

internal class TiffIfd(val position: Long, val entries: Map<Int, TiffEntry>, val nextIfdOffset: Long) {
    operator fun get(tag: Int): TiffEntry? = entries[tag]
}

/**
 * A minimal TIFF structure reader: header, IFD chains and entries.
 *
 * Offsets inside a TIFF stream are relative to [base] (the position of the
 * `II`/`MM` header) — 0 for a plain TIFF-family RAW, the box payload for a CR3
 * `CMT1`, the APP1 payload for a JPEG. Accepts the TIFF magic 42 and the
 * vendor variants used by RAW files (Panasonic 0x55, Olympus `RO`/`RS`).
 *
 * Large out-of-line values (strip data, maker notes, embedded JPEGs) are
 * **not** loaded: only values up to [MAX_VALUE_BYTES] are read, everything else
 * is addressed by [TiffEntry.valuePosition].
 */
internal class TiffReader private constructor(
    val reader: ChannelReader,
    val base: Long,
    val order: ByteOrder,
    val firstIfdOffset: Long,
) {
    fun readIfd(offset: Long, valueBase: Long = base): TiffIfd? {
        val pos = base + offset
        if (offset <= 0 || pos + 2 > reader.size) return null
        return readIfdAt(pos, valueBase)
    }

    /** Read an IFD at absolute [pos]; out-of-line value offsets are relative to [valueBase]. */
    fun readIfdAt(pos: Long, valueBase: Long = base): TiffIfd? {
        val head = reader.bytes(pos, 2)
        if (head.size < 2) return null
        val n = ByteBuffer.wrap(head).order(order).short.toInt() and 0xFFFF
        if (n == 0 || n > MAX_ENTRIES) return null
        val block = reader.bytes(pos + 2, n * 12 + 4)
        if (block.size < n * 12) return null
        val b = ByteBuffer.wrap(block).order(order)
        val entries = LinkedHashMap<Int, TiffEntry>(n)
        for (i in 0 until n) {
            val e = i * 12
            val tag = b.getShort(e).toInt() and 0xFFFF
            val type = b.getShort(e + 2).toInt() and 0xFFFF
            val count = b.getInt(e + 4).toLong() and 0xFFFFFFFFL
            val unit = TiffEntry.typeSize(type)
            if (unit == 0) continue
            val total = count * unit
            val (valuePos, bytes) = if (total <= 4) {
                val inline = pos + 2 + e + 8
                inline to block.copyOfRange(e + 8, e + 8 + total.toInt())
            } else {
                val off = b.getInt(e + 8).toLong() and 0xFFFFFFFFL
                val at = valueBase + off
                at to (if (total <= MAX_VALUE_BYTES) reader.bytes(at, total.toInt()) else ByteArray(0))
            }
            entries.putIfAbsent(tag, TiffEntry(tag, type, count, valuePos, bytes, order))
        }
        val next = if (block.size >= n * 12 + 4) b.getInt(n * 12).toLong() and 0xFFFFFFFFL else 0L
        return TiffIfd(pos, entries, next)
    }

    /** IFD0 and every IFD chained after it, guarding against loops. */
    fun ifdChain(maxIfds: Int = 16): List<TiffIfd> {
        val out = mutableListOf<TiffIfd>()
        val seen = mutableSetOf<Long>()
        var offset = firstIfdOffset
        while (offset > 0 && out.size < maxIfds && seen.add(offset)) {
            val ifd = readIfd(offset) ?: break
            out += ifd
            offset = ifd.nextIfdOffset
        }
        return out
    }

    companion object {
        const val MAX_ENTRIES = 1000
        const val MAX_VALUE_BYTES = 64 * 1024

        val TIFF_MAGICS = setOf(42, 0x55, 0x4F52, 0x5352)

        /** A reader for an IFD structure without a TIFF header (maker notes). */
        fun headerless(reader: ChannelReader, base: Long, order: ByteOrder): TiffReader =
            TiffReader(reader, base, order, 0)

        /** Parse a TIFF header at absolute position [base], or null if there is none. */
        fun at(reader: ChannelReader, base: Long): TiffReader? {
            val h = reader.bytes(base, 8)
            if (h.size < 8) return null
            val order = when {
                h[0] == 'I'.code.toByte() && h[1] == 'I'.code.toByte() -> ByteOrder.LITTLE_ENDIAN
                h[0] == 'M'.code.toByte() && h[1] == 'M'.code.toByte() -> ByteOrder.BIG_ENDIAN
                else -> return null
            }
            val b = ByteBuffer.wrap(h).order(order)
            val magic = b.getShort(2).toInt() and 0xFFFF
            if (magic !in TIFF_MAGICS) return null
            val first = b.getInt(4).toLong() and 0xFFFFFFFFL
            return TiffReader(reader, base, order, first)
        }
    }
}

/** Tag numbers used by the readers in this package. */
internal object Tags {
    const val NEW_SUBFILE_TYPE = 0xFE
    const val IMAGE_WIDTH = 0x100
    const val IMAGE_LENGTH = 0x101
    const val BITS_PER_SAMPLE = 0x102
    const val COMPRESSION = 0x103
    const val PHOTOMETRIC = 0x106
    const val MAKE = 0x10F
    const val MODEL = 0x110
    const val STRIP_OFFSETS = 0x111
    const val ORIENTATION = 0x112
    const val SAMPLES_PER_PIXEL = 0x115
    const val ROWS_PER_STRIP = 0x116
    const val STRIP_BYTE_COUNTS = 0x117
    const val PLANAR_CONFIGURATION = 0x11C
    const val PREDICTOR = 0x13D
    const val TILE_WIDTH = 0x142
    const val SUB_IFDS = 0x14A
    const val EXTRA_SAMPLES = 0x152
    const val SAMPLE_FORMAT = 0x153
    const val JPEG_INTERCHANGE_FORMAT = 0x201
    const val JPEG_INTERCHANGE_FORMAT_LENGTH = 0x202
    const val PANASONIC_JPG_FROM_RAW = 0x2E
    const val EXIF_IFD = 0x8769
    const val XMP = 0x2BC

    const val EXPOSURE_TIME = 0x829A
    const val F_NUMBER = 0x829D
    const val PHOTOGRAPHIC_SENSITIVITY = 0x8827
    const val SENSITIVITY_TYPE = 0x8830
    const val STANDARD_OUTPUT_SENSITIVITY = 0x8831
    const val RECOMMENDED_EXPOSURE_INDEX = 0x8832
    const val ISO_SPEED = 0x8833
    const val DATE_TIME_ORIGINAL = 0x9003
    const val OFFSET_TIME_ORIGINAL = 0x9011
    const val SHUTTER_SPEED_VALUE = 0x9201
    const val APERTURE_VALUE = 0x9202
    const val FOCAL_LENGTH = 0x920A
    const val MAKER_NOTE = 0x927C
    const val FOCAL_PLANE_X_RESOLUTION = 0xA20E
    const val FOCAL_PLANE_RESOLUTION_UNIT = 0xA210
    const val FOCAL_LENGTH_35MM = 0xA405
    const val LENS_MAKE = 0xA433
    const val LENS_MODEL = 0xA434

    const val GPS_LATITUDE_REF = 0x0001
    const val GPS_LATITUDE = 0x0002
    const val GPS_LONGITUDE_REF = 0x0003
    const val GPS_LONGITUDE = 0x0004
    const val GPS_TIME_STAMP = 0x0007
    const val GPS_DATE_STAMP = 0x001D
}
