package com.photoselector.core.preview

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.nio.channels.FileChannel
import java.util.zip.Inflater

/** Decoded pixels as packed ARGB, row-major. */
class DecodedPixels(val width: Int, val height: Int, val argb: IntArray) {
    fun toBitmap(): Bitmap = Bitmap.createBitmap(argb, width, height, Bitmap.Config.ARGB_8888)
}

/**
 * Baseline TIFF decoder, because Android has no platform TIFF codec at all.
 *
 * Supports what scanners, Lightroom/Photoshop exports and `sips` write: 8-bit
 * chunky (interleaved) strips, grey / RGB with optional extra (alpha) samples,
 * uncompressed, LZW, Deflate or PackBits, with or without horizontal
 * predictor. Anything else (tiles, 16-bit, planar, CMYK, JPEG-in-TIFF) returns
 * null so the caller can try something else.
 *
 * Subsamples while decoding: only every n-th row and column is kept, so the
 * result is at most about [decode]'s `maxLongEdge` and memory stays bounded by
 * one strip plus the output.
 */
object TiffBitmapDecoder {

    private const val MAX_STRIP_BYTES = 256 * 1024 * 1024

    fun decode(channel: FileChannel, maxLongEdge: Int = Int.MAX_VALUE): DecodedPixels? = try {
        decodeInternal(ChannelReader(channel), maxLongEdge)
    } catch (e: Exception) {
        null
    } catch (e: OutOfMemoryError) {
        null
    }

    fun decode(file: File, maxLongEdge: Int = Int.MAX_VALUE): DecodedPixels? = try {
        RandomAccessFile(file, "r").use { decode(it.channel, maxLongEdge) }
    } catch (e: Exception) {
        null
    }

    fun decode(context: Context, uri: Uri, maxLongEdge: Int = Int.MAX_VALUE): DecodedPixels? = try {
        if (uri.scheme == null || uri.scheme == "file") {
            uri.path?.let { decode(File(it), maxLongEdge) }
        } else {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                // The stream is not closed on purpose: the descriptor belongs to `pfd`.
                decode(FileInputStream(pfd.fileDescriptor).channel, maxLongEdge)
            }
        }
    } catch (e: Exception) {
        null
    }

    private fun decodeInternal(reader: ChannelReader, maxLongEdge: Int): DecodedPixels? {
        val tiff = TiffReader.at(reader, 0) ?: return null
        if (Cr3Structure.isCr3(reader)) return null
        val ifd = tiff.readIfd(tiff.firstIfdOffset) ?: return null
        val width = ifd[Tags.IMAGE_WIDTH]?.long()?.toInt() ?: return null
        val height = ifd[Tags.IMAGE_LENGTH]?.long()?.toInt() ?: return null
        if (width <= 0 || height <= 0) return null
        if (ifd[Tags.TILE_WIDTH] != null) return null
        val compression = ifd[Tags.COMPRESSION]?.long()?.toInt() ?: 1
        val photometric = ifd[Tags.PHOTOMETRIC]?.long()?.toInt() ?: return null
        val spp = ifd[Tags.SAMPLES_PER_PIXEL]?.long()?.toInt() ?: 1
        val bits = ifd[Tags.BITS_PER_SAMPLE]?.longs() ?: longArrayOf(1)
        if (bits.any { it != 8L }) return null
        if ((ifd[Tags.PLANAR_CONFIGURATION]?.long() ?: 1L) != 1L) return null
        if ((ifd[Tags.SAMPLE_FORMAT]?.long() ?: 1L) != 1L) return null
        val colour = when (photometric) {
            0, 1 -> 1
            2 -> 3
            else -> return null
        }
        if (spp < colour) return null
        val hasAlpha = spp > colour && ifd[Tags.EXTRA_SAMPLES]?.long()?.let { it == 1L || it == 2L } == true
        val predictor = ifd[Tags.PREDICTOR]?.long()?.toInt() ?: 1
        if (predictor != 1 && predictor != 2) return null
        if (compression !in setOf(1, 5, 8, 32946, 32773)) return null
        val rowsPerStrip = (ifd[Tags.ROWS_PER_STRIP]?.long() ?: height.toLong()).coerceIn(1, height.toLong()).toInt()
        val offsets = ifd[Tags.STRIP_OFFSETS]?.longs() ?: return null
        val counts = ifd[Tags.STRIP_BYTE_COUNTS]?.longs() ?: return null
        if (offsets.size != counts.size || offsets.isEmpty()) return null

        var step = 1
        while (maxOf(width, height) / (step * 2) >= maxLongEdge) step *= 2
        val outW = (width + step - 1) / step
        val outH = (height + step - 1) / step
        val out = IntArray(outW * outH)
        val rowBytes = width * spp

        for ((strip, offset) in offsets.withIndex()) {
            val firstRow = strip * rowsPerStrip
            if (firstRow >= height) break
            val rows = minOf(rowsPerStrip, height - firstRow)
            // Skip strips that contain no sampled row.
            if ((firstRow until firstRow + rows).none { it % step == 0 }) continue
            if (counts[strip] > MAX_STRIP_BYTES) return null
            val raw = reader.bytes(offset, counts[strip].toInt())
            val data = when (compression) {
                1 -> raw
                5 -> lzw(raw, rowBytes * rows)
                8, 32946 -> inflate(raw, rowBytes * rows)
                32773 -> packBits(raw, rowBytes * rows)
                else -> return null
            }
            for (r in 0 until rows) {
                val y = firstRow + r
                val rowStart = r * rowBytes
                if (rowStart + rowBytes > data.size) break
                if (predictor == 2) {
                    for (i in rowStart + spp until rowStart + rowBytes) {
                        data[i] = (data[i] + data[i - spp]).toByte()
                    }
                }
                if (y % step != 0) continue
                val outRow = (y / step) * outW
                var x = 0
                while (x < width) {
                    val p = rowStart + x * spp
                    val argb = if (colour == 1) {
                        var v = data[p].toInt() and 0xFF
                        if (photometric == 0) v = 255 - v
                        val a = if (hasAlpha) data[p + 1].toInt() and 0xFF else 255
                        (a shl 24) or (v shl 16) or (v shl 8) or v
                    } else {
                        val a = if (hasAlpha) data[p + 3].toInt() and 0xFF else 255
                        (a shl 24) or ((data[p].toInt() and 0xFF) shl 16) or
                            ((data[p + 1].toInt() and 0xFF) shl 8) or (data[p + 2].toInt() and 0xFF)
                    }
                    out[outRow + x / step] = argb
                    x += step
                }
            }
        }
        return DecodedPixels(outW, outH, out)
    }

    private fun inflate(src: ByteArray, expected: Int): ByteArray {
        val inflater = Inflater()
        try {
            inflater.setInput(src)
            val out = ByteArrayOutputStream(expected)
            val buf = ByteArray(64 * 1024)
            while (!inflater.finished()) {
                val n = inflater.inflate(buf)
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
                out.write(buf, 0, n)
            }
            return out.toByteArray()
        } finally {
            inflater.end()
        }
    }

    private fun packBits(src: ByteArray, expected: Int): ByteArray {
        val out = ByteArrayOutputStream(expected)
        var i = 0
        while (i < src.size && out.size() < expected) {
            val n = src[i++].toInt()
            when {
                n >= 0 -> {
                    val len = minOf(n + 1, src.size - i)
                    out.write(src, i, len); i += len
                }
                n != -128 -> {
                    if (i >= src.size) break
                    val b = src[i++].toInt()
                    repeat(1 - n) { out.write(b) }
                }
            }
        }
        return out.toByteArray()
    }

    /** TIFF LZW (MSB-first codes, early change). */
    private fun lzw(src: ByteArray, expected: Int): ByteArray {
        val out = ByteArrayOutputStream(maxOf(expected, 16))
        val prefix = IntArray(4096)
        val suffix = ByteArray(4096)
        val length = IntArray(4096)
        for (i in 0 until 256) { suffix[i] = i.toByte(); length[i] = 1; prefix[i] = -1 }
        val stack = ByteArray(4096)
        var next = 258
        var width = 9
        var bitBuf = 0L
        var bitCount = 0
        var pos = 0
        var old = -1

        fun emit(code: Int): Byte {
            var c = code
            var n = length[c]
            val first: Byte
            var k = n
            while (c >= 0 && k > 0) { stack[--k] = suffix[c]; c = prefix[c] }
            first = stack[0]
            out.write(stack, 0, n)
            return first
        }

        while (true) {
            while (bitCount < width && pos < src.size) {
                bitBuf = (bitBuf shl 8) or (src[pos++].toLong() and 0xFF)
                bitCount += 8
            }
            if (bitCount < width) break
            val code = ((bitBuf shr (bitCount - width)) and ((1L shl width) - 1)).toInt()
            bitCount -= width
            when {
                code == 257 -> break
                code == 256 -> { next = 258; width = 9; old = -1 }
                old == -1 -> { if (code > 255) break; emit(code); old = code }
                else -> {
                    if (code < next) {
                        val first = emit(code)
                        if (next < 4096) {
                            prefix[next] = old; suffix[next] = first; length[next] = length[old] + 1; next++
                        }
                    } else if (code == next && next < 4096) {
                        // KwKwK: the new entry is old + first(old).
                        var c = old
                        while (prefix[c] >= 0) c = prefix[c]
                        prefix[next] = old; suffix[next] = suffix[c]; length[next] = length[old] + 1; next++
                        emit(code)
                    } else {
                        break
                    }
                    old = code
                }
            }
            width = when {
                next + 1 >= 2048 -> 12
                next + 1 >= 1024 -> 11
                next + 1 >= 512 -> 10
                else -> 9
            }
            if (out.size() >= expected) break
        }
        return out.toByteArray()
    }
}
