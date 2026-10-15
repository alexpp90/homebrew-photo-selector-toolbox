package com.photoselector.core.preview

import java.nio.ByteBuffer

/** One ISO base-media box: [start] is the box header, [payload] the first content byte. */
internal class Box(val type: String, val start: Long, val payload: Long, val end: Long) {
    val payloadSize: Long get() = end - payload
}

/**
 * Minimal ISO base-media (MP4 / HEIF / Canon CR3) box walker.
 */
internal object IsoBmff {

    /** Canon's metadata container inside `moov` (CMT1..CMT4, THMB). */
    const val CANON_METADATA_UUID = "85c0b687820f11e08111f4ce462b6a48"

    /** Canon's top-level preview container (PRVW). */
    const val CANON_PREVIEW_UUID = "eaf42b5e1c984b88b9fbb7dc406e4d16"

    /** Adobe XMP packet box used by CR3. */
    const val XMP_UUID = "be7acfcb97a942e89c71999491e3afac"

    fun fourcc(bytes: ByteArray, at: Int): String =
        String(bytes, at, 4, Charsets.ISO_8859_1)

    /** True when the file starts with an `ftyp` box whose major brand is in [brands]. */
    fun hasBrand(reader: ChannelReader, brands: Set<String>): Boolean {
        val h = reader.bytes(0, 12)
        if (h.size < 12 || fourcc(h, 4) != "ftyp") return false
        return fourcc(h, 8) in brands
    }

    /** Children of the region [from, to). Stops at the first malformed header. */
    fun children(reader: ChannelReader, from: Long, to: Long): List<Box> {
        val out = mutableListOf<Box>()
        var pos = from
        while (pos + 8 <= to && out.size < 4096) {
            val h = reader.bytes(pos, 16)
            if (h.size < 8) break
            val b = ByteBuffer.wrap(h)
            var size = b.getInt(0).toLong() and 0xFFFFFFFFL
            val type = fourcc(h, 4)
            var header = 8L
            if (size == 1L) {
                if (h.size < 16) break
                size = b.getLong(8)
                header = 16L
            } else if (size == 0L) {
                size = to - pos
            }
            if (size < header || pos + size > to) break
            out += Box(type, pos, pos + header, pos + size)
            pos += size
        }
        return out
    }

    /** The 16-byte extended type of a `uuid` box as lowercase hex. */
    fun uuidOf(reader: ChannelReader, box: Box): String? {
        if (box.type != "uuid" || box.payloadSize < 16) return null
        return reader.bytes(box.payload, 16).joinToString("") { "%02x".format(it) }
    }

    /** Top-level boxes of the file. */
    fun topLevel(reader: ChannelReader): List<Box> = children(reader, 0, reader.size)
}

/**
 * The parts of a Canon CR3 this module uses: the TIFF metadata blocks, the
 * XMP packet, and the embedded JPEGs (PRVW, THMB and the full-size track 1).
 */
internal class Cr3Structure(
    val cmt1: Box?,
    val cmt2: Box?,
    val xmp: Box?,
    /** Candidate JPEG streams as (absolute offset, length). */
    val jpegs: List<Pair<Long, Long>>,
) {
    companion object {
        private val BRANDS = setOf("crx ")

        fun isCr3(reader: ChannelReader): Boolean = IsoBmff.hasBrand(reader, BRANDS)

        fun parse(reader: ChannelReader): Cr3Structure? {
            if (!isCr3(reader)) return null
            var cmt1: Box? = null
            var cmt2: Box? = null
            var xmp: Box? = null
            val jpegs = mutableListOf<Pair<Long, Long>>()

            for (top in IsoBmff.topLevel(reader)) {
                when (top.type) {
                    "moov" -> for (child in IsoBmff.children(reader, top.payload, top.end)) {
                        when {
                            child.type == "uuid" &&
                                IsoBmff.uuidOf(reader, child) == IsoBmff.CANON_METADATA_UUID -> {
                                for (m in IsoBmff.children(reader, child.payload + 16, child.end)) {
                                    when (m.type) {
                                        "CMT1" -> cmt1 = m
                                        "CMT2" -> cmt2 = m
                                        "THMB" -> findJpegIn(reader, m)?.let(jpegs::add)
                                    }
                                }
                            }
                            child.type == "trak" -> firstChunkOf(reader, child)?.let(jpegs::add)
                        }
                    }
                    "uuid" -> when (IsoBmff.uuidOf(reader, top)) {
                        IsoBmff.CANON_PREVIEW_UUID -> {
                            // 16-byte uuid, then 8 bytes Canon header, then PRVW.
                            for (p in IsoBmff.children(reader, top.payload + 24, top.end)) {
                                if (p.type == "PRVW") findJpegIn(reader, p)?.let(jpegs::add)
                            }
                        }
                        IsoBmff.XMP_UUID -> xmp = top
                    }
                }
            }
            return Cr3Structure(cmt1, cmt2, xmp, jpegs)
        }

        /** The first JPEG SOI inside the first 64 payload bytes of [box], to the box end. */
        private fun findJpegIn(reader: ChannelReader, box: Box): Pair<Long, Long>? {
            val head = reader.bytes(box.payload, 64)
            for (i in 0 until head.size - 2) {
                if (head[i] == 0xFF.toByte() && head[i + 1] == 0xD8.toByte() &&
                    head[i + 2] == 0xFF.toByte()
                ) {
                    val start = box.payload + i
                    return start to (box.end - start)
                }
            }
            return null
        }

        /** The first sample of a track (`stco`/`co64` + `stsz`). */
        private fun firstChunkOf(reader: ChannelReader, trak: Box): Pair<Long, Long>? {
            val mdia = IsoBmff.children(reader, trak.payload, trak.end).firstOrNull { it.type == "mdia" }
                ?: return null
            val minf = IsoBmff.children(reader, mdia.payload, mdia.end).firstOrNull { it.type == "minf" }
                ?: return null
            val stbl = IsoBmff.children(reader, minf.payload, minf.end).firstOrNull { it.type == "stbl" }
                ?: return null
            val boxes = IsoBmff.children(reader, stbl.payload, stbl.end)
            val stsz = boxes.firstOrNull { it.type == "stsz" } ?: return null
            val sz = ByteBuffer.wrap(reader.bytes(stsz.payload, 16))
            if (sz.capacity() < 12) return null
            var size = sz.getInt(4).toLong() and 0xFFFFFFFFL
            if (size == 0L && sz.capacity() >= 16) size = sz.getInt(12).toLong() and 0xFFFFFFFFL
            val offset = boxes.firstOrNull { it.type == "co64" }?.let {
                val b = ByteBuffer.wrap(reader.bytes(it.payload, 16))
                if (b.capacity() < 16) null else b.getLong(8)
            } ?: boxes.firstOrNull { it.type == "stco" }?.let {
                val b = ByteBuffer.wrap(reader.bytes(it.payload, 12))
                if (b.capacity() < 12) null else b.getInt(8).toLong() and 0xFFFFFFFFL
            } ?: return null
            if (size <= 0) return null
            return offset to size
        }
    }
}
