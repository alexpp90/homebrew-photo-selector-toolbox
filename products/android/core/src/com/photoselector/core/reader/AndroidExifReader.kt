package com.photoselector.core.reader

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.photoselector.core.model.ExifData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidExifReader @Inject constructor() : ExifReaderStrategy {

    companion object {
        private const val TAG = "AndroidExifReader"

        private val SUPPORTED_MIME_TYPES = setOf(
            "image/jpeg",
            "image/x-adobe-dng",
            "image/x-canon-cr2",
            "image/x-canon-cr3",
            "image/x-nikon-nef",
            "image/x-sony-arw",
            "image/x-fuji-raf",
            "image/x-olympus-orf",
            "image/x-panasonic-rw2",
            "image/x-pentax-pef",
            "image/x-samsung-srw",
            "image/webp",
            "image/heif",
            "image/heic"
        )
    }

    override suspend fun readExif(context: Context, uri: Uri): ExifData? =
        withContext(Dispatchers.IO) {
            try {
                // If direct file or file URI, resolve File object
                val directFile = when {
                    uri.scheme == "file" -> uri.path?.let { File(it) }
                    uri.scheme == null && uri.path != null -> File(uri.path!!)
                    else -> uri.path?.let { p -> val f = File(p); if (f.exists()) f else null }
                }

                if (directFile != null && directFile.exists()) {
                    return@withContext readExif(directFile)
                }

                // Try ParcelFileDescriptor first because it's seekable (important for TIFF/RAW/DNG formats and many JPEGs)
                val pfd = try {
                    context.contentResolver.openFileDescriptor(uri, "r")
                } catch (e: Exception) {
                    null
                }

                if (pfd != null) {
                    pfd.use {
                        val exif = ExifInterface(it.fileDescriptor)
                        parseExifData(exif, file = directFile, pfd = it)
                    }
                } else {
                    // Fall back to InputStream if PFD fails
                    val inputStream = context.contentResolver.openInputStream(uri)
                        ?: return@withContext null
                    inputStream.use { stream ->
                        val exif = ExifInterface(stream)
                        parseExifData(exif, file = directFile)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to read EXIF from $uri", e)
                null
            }
        }

    /**
     * Direct File reader overload.
     */
    suspend fun readExif(file: File): ExifData? =
        withContext(Dispatchers.IO) {
            try {
                if (!file.exists()) return@withContext null
                val exif = ExifInterface(file.absolutePath)
                parseExifData(exif, file = file)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to read EXIF from file ${file.path}", e)
                null
            }
        }

    override fun supportedMimeTypes(): Set<String> = SUPPORTED_MIME_TYPES

    private fun parseExifData(
        exif: ExifInterface,
        file: File? = null,
        pfd: ParcelFileDescriptor? = null
    ): ExifData {
        val shutterSpeed = parseExposureTime(
            exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME)
        )

        val aperture = exif.getAttributeDouble(ExifInterface.TAG_F_NUMBER, 0.0)
            .takeIf { it > 0.0 }

        val focalLength = exif.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH, 0.0)
            .takeIf { it > 0.0 } ?: parseRationalValue(
            exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH)
        )

        val focalLength35mm = exif.getAttributeInt(
            ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, 0
        ).takeIf { it > 0 }?.toDouble()

        val iso = parseIso(exif)

        val lens = parseLensInfo(exif)

        val rating = extractRating(exif, file, pfd)

        return ExifData(
            shutterSpeed = shutterSpeed,
            aperture = aperture,
            focalLength = focalLength,
            focalLength35mm = focalLength35mm,
            iso = iso,
            lens = lens,
            isFallback = false,
            rating = rating
        )
    }

    private fun extractRating(
        exif: ExifInterface,
        file: File?,
        pfd: ParcelFileDescriptor?
    ): Int {
        // 1. Companion sidecar (takes absolute precedence as user intent over embedded camera metadata)
        if (file != null) {
            val sidecar = readSidecarRating(file)
            if (sidecar != null) return sidecar
        }

        // 2. Embedded XMP packet (TAG_XMP)
        val xmp = exif.getAttribute(ExifInterface.TAG_XMP)
        if (!xmp.isNullOrBlank()) {
            val xmpRating = XmpRatingParser.parseRating(xmp)
            if (xmpRating != null && xmpRating != 0) return xmpRating
        }

        // 3. Binary EXIF attributes or tags
        val attrRating = exif.getAttribute("Rating")?.toIntOrNull()
        if (attrRating != null && attrRating != 0) {
            return XmpRatingParser.normalizeRating(attrRating)
        }
        val attrPercent = exif.getAttribute("RatingPercent")?.toIntOrNull()
        if (attrPercent != null && attrPercent != 0) {
            return XmpRatingParser.percentToRating(attrPercent)
        }

        // 4. Binary EXIF parser (0x4746 / 0x4749)
        val binaryRating = BinaryExifRatingReader.readRating(file)
            ?: BinaryExifRatingReader.readRating(pfd)
        if (binaryRating != null && binaryRating != 0) {
            return binaryRating
        }

        // 5. If XMP or binary EXIF explicitly declared 0 (unrated) or reject (-1)
        if (!xmp.isNullOrBlank()) {
            val xmpRating = XmpRatingParser.parseRating(xmp)
            if (xmpRating != null) return xmpRating
        }
        if (binaryRating != null) return binaryRating

        return 0
    }

    private fun readSidecarRating(imageFile: File): Int? {
        try {
            // 1. Check <filename>.xmp (e.g. IMG_0001.CR3.xmp)
            val sidecar1 = File(imageFile.parentFile, "${imageFile.name}.xmp")
            if (sidecar1.exists() && sidecar1.isFile) {
                val rating = XmpRatingParser.parseRating(sidecar1.readText())
                if (rating != null) return rating
            }
            // 2. Check <stem>.xmp (e.g. IMG_0001.xmp)
            val sidecar2 = File(imageFile.parentFile, "${imageFile.nameWithoutExtension}.xmp")
            if (sidecar2.exists() && sidecar2.isFile) {
                val rating = XmpRatingParser.parseRating(sidecar2.readText())
                if (rating != null) return rating
            }
        } catch (e: Exception) {
            // Ignore read errors
        }
        return null
    }

    private fun parseExposureTime(value: String?): Double? {
        if (value.isNullOrBlank()) return null
        return try {
            val doubleValue = value.toDouble()
            if (doubleValue > 0.0) doubleValue else null
        } catch (e: NumberFormatException) {
            // Try parsing as rational (e.g., "1/250")
            try {
                val parts = value.split("/")
                if (parts.size == 2) {
                    val numerator = parts[0].trim().toDouble()
                    val denominator = parts[1].trim().toDouble()
                    if (denominator != 0.0) numerator / denominator else null
                } else {
                    null
                }
            } catch (e2: NumberFormatException) {
                null
            }
        }
    }

    private fun parseRationalValue(value: String?): Double? {
        if (value.isNullOrBlank()) return null
        return try {
            val parts = value.split("/")
            if (parts.size == 2) {
                val numerator = parts[0].trim().toDouble()
                val denominator = parts[1].trim().toDouble()
                if (denominator != 0.0) numerator / denominator else null
            } else {
                value.toDoubleOrNull()
            }
        } catch (e: NumberFormatException) {
            null
        }
    }

    @Suppress("DEPRECATION")
    private fun parseIso(exif: ExifInterface): Int? {
        // Try TAG_PHOTOGRAPHIC_SENSITIVITY first (newer standard)
        val isoValue = exif.getAttributeInt(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, 0)
        if (isoValue > 0) return isoValue

        // Fall back to TAG_ISO_SPEED_RATINGS (deprecated but widely used)
        val isoLegacy = exif.getAttributeInt(ExifInterface.TAG_ISO_SPEED_RATINGS, 0)
        if (isoLegacy > 0) return isoLegacy

        return null
    }

    private fun parseLensInfo(exif: ExifInterface): String {
        val lensModel = exif.getAttribute(ExifInterface.TAG_LENS_MODEL)
        if (!lensModel.isNullOrBlank()) return lensModel

        val lensMake = exif.getAttribute(ExifInterface.TAG_LENS_MAKE)
        if (!lensMake.isNullOrBlank()) return lensMake

        return "Unknown"
    }
}

object XmpRatingParser {
    // Matches <xmp:Rating>3</xmp:Rating>, <xmp:Rating>3.0</xmp:Rating>, or <Rating>4.000000</Rating>
    private val TAG_RATING_REGEX = Regex(
        "<(?:[a-zA-Z0-9_-]+:)?Rating>\\s*(-?\\d+(?:\\.\\d+)?)\\s*</(?:[a-zA-Z0-9_-]+:)?Rating>",
        RegexOption.IGNORE_CASE
    )
    // Matches xmp:Rating="3" or Rating="3.0"
    private val ATTR_RATING_REGEX = Regex(
        "(?:[a-zA-Z0-9_-]+:)?Rating\\s*=\\s*[\"'](-?\\d+(?:\\.\\d+)?)[\"']",
        RegexOption.IGNORE_CASE
    )
    // Matches <MicrosoftPhoto:RatingPercent>75</MicrosoftPhoto:RatingPercent> or <RatingPercent>75.0</RatingPercent>
    private val TAG_PERCENT_REGEX = Regex(
        "<(?:[a-zA-Z0-9_-]+:)?RatingPercent>\\s*(-?\\d+(?:\\.\\d+)?)\\s*</(?:[a-zA-Z0-9_-]+:)?RatingPercent>",
        RegexOption.IGNORE_CASE
    )
    // Matches RatingPercent="75"
    private val ATTR_PERCENT_REGEX = Regex(
        "(?:[a-zA-Z0-9_-]+:)?RatingPercent\\s*=\\s*[\"'](-?\\d+(?:\\.\\d+)?)[\"']",
        RegexOption.IGNORE_CASE
    )

    fun parseRating(xmpContent: String?): Int? {
        if (xmpContent.isNullOrBlank()) return null

        TAG_RATING_REGEX.find(xmpContent)?.groupValues?.get(1)?.let { raw ->
            parseNumericRating(raw)?.let { return normalizeRating(it) }
        }
        ATTR_RATING_REGEX.find(xmpContent)?.groupValues?.get(1)?.let { raw ->
            parseNumericRating(raw)?.let { return normalizeRating(it) }
        }
        TAG_PERCENT_REGEX.find(xmpContent)?.groupValues?.get(1)?.let { raw ->
            parseNumericRating(raw)?.let { return percentToRating(it) }
        }
        ATTR_PERCENT_REGEX.find(xmpContent)?.groupValues?.get(1)?.let { raw ->
            parseNumericRating(raw)?.let { return percentToRating(it) }
        }
        return null
    }

    private fun parseNumericRating(value: String): Int? {
        return value.toDoubleOrNull()?.roundToInt() ?: value.toIntOrNull()
    }

    fun normalizeRating(rating: Int): Int = when {
        rating in 1..5 -> rating
        rating == -1 -> -1
        else -> 0
    }

    fun percentToRating(percent: Int): Int = when {
        percent <= 0 -> 0
        percent < 25 -> 1
        percent < 50 -> 2
        percent < 75 -> 3
        percent < 99 -> 4
        else -> 5
    }
}

internal object BinaryExifRatingReader {
    private const val TAG_RATING = 0x4746
    private const val TAG_RATING_PERCENT = 0x4749

    fun readRating(file: File?): Int? {
        if (file == null || !file.exists() || !file.canRead()) return null
        return try {
            RandomAccessFile(file, "r").use { raf ->
                readFromRaf(raf)
            }
        } catch (e: Throwable) {
            null
        }
    }

    fun readRating(pfd: ParcelFileDescriptor?): Int? {
        if (pfd == null) return null
        return try {
            val fd = pfd.fileDescriptor
            FileInputStream(fd).channel.use { channel ->
                val bufferSize = minOf(channel.size(), 131072L).toInt()
                if (bufferSize <= 0) return null
                val buffer = ByteBuffer.allocate(bufferSize)
                channel.position(0)
                channel.read(buffer)
                buffer.flip()
                parseFromBuffer(buffer)
            }
        } catch (e: Throwable) {
            null
        }
    }

    private fun readFromRaf(raf: RandomAccessFile): Int? {
        val length = minOf(raf.length(), 131072L).toInt()
        if (length < 8) return null
        val bytes = ByteArray(length)
        raf.seek(0)
        raf.readFully(bytes)
        val buffer = ByteBuffer.wrap(bytes)
        return parseFromBuffer(buffer)
    }

    fun parseFromBuffer(buffer: ByteBuffer): Int? {
        return try {
            parseFromBufferInternal(buffer)
        } catch (e: Throwable) {
            null
        }
    }

    private fun parseFromBufferInternal(buffer: ByteBuffer): Int? {
        if (buffer.remaining() < 8) return null

        val b0 = buffer.get(0).toInt() and 0xFF
        val b1 = buffer.get(1).toInt() and 0xFF
        var tiffOffset = -1

        if (b0 == 0xFF && b1 == 0xD8) {
            var pos = 2
            while (pos + 4 < buffer.limit()) {
                val marker0 = buffer.get(pos).toInt() and 0xFF
                val marker1 = buffer.get(pos + 1).toInt() and 0xFF
                if (marker0 == 0xFF && marker1 == 0xE1) {
                    val app1Len = ((buffer.get(pos + 2).toInt() and 0xFF) shl 8) or
                            (buffer.get(pos + 3).toInt() and 0xFF)
                    if (pos + 10 <= buffer.limit()) {
                        val isExif = buffer.get(pos + 4) == 'E'.code.toByte() &&
                                buffer.get(pos + 5) == 'x'.code.toByte() &&
                                buffer.get(pos + 6) == 'i'.code.toByte() &&
                                buffer.get(pos + 7) == 'f'.code.toByte() &&
                                buffer.get(pos + 8) == 0.toByte() &&
                                buffer.get(pos + 9) == 0.toByte()
                        if (isExif) {
                            tiffOffset = pos + 10
                            break
                        }
                    }
                    pos += 2 + app1Len
                } else if (marker0 == 0xFF && (marker1 in 0xE0..0xEF || marker1 == 0xFE)) {
                    val segLen = ((buffer.get(pos + 2).toInt() and 0xFF) shl 8) or
                            (buffer.get(pos + 3).toInt() and 0xFF)
                    pos += 2 + segLen
                } else {
                    pos++
                }
            }
        } else {
            tiffOffset = 0
        }

        if (tiffOffset < 0 || tiffOffset + 8 > buffer.limit()) return null

        val byteOrderByte1 = buffer.get(tiffOffset)
        val byteOrderByte2 = buffer.get(tiffOffset + 1)
        val isLittleEndian = when {
            byteOrderByte1 == 'I'.code.toByte() && byteOrderByte2 == 'I'.code.toByte() -> true
            byteOrderByte1 == 'M'.code.toByte() && byteOrderByte2 == 'M'.code.toByte() -> false
            else -> return null
        }

        buffer.order(if (isLittleEndian) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN)

        val magic = buffer.getShort(tiffOffset + 2).toInt() and 0xFFFF
        if (magic != 42 && magic != 0x55) {
            return null
        }

        val ifd0Offset = buffer.getInt(tiffOffset + 4)
        if (ifd0Offset < 0 || tiffOffset + ifd0Offset + 2 > buffer.limit()) return null

        return parseIfd(buffer, tiffOffset, tiffOffset + ifd0Offset)
    }

    private fun parseIfd(
        buffer: ByteBuffer,
        tiffBase: Int,
        ifdPos: Int,
        depth: Int = 0,
        visitedOffsets: MutableSet<Int> = mutableSetOf()
    ): Int? {
        if (depth > 2) return null
        if (ifdPos < 0 || ifdPos + 2 > buffer.limit()) return null
        if (!visitedOffsets.add(ifdPos)) return null

        val numEntries = buffer.getShort(ifdPos).toInt() and 0xFFFF
        var entryPos = ifdPos + 2

        var exifSubIfdOffset: Int? = null

        for (i in 0 until numEntries) {
            if (entryPos + 12 > buffer.limit()) break
            val tag = buffer.getShort(entryPos).toInt() and 0xFFFF
            val type = buffer.getShort(entryPos + 2).toInt() and 0xFFFF
            val valueOrOffset = buffer.getInt(entryPos + 8)

            if (tag == TAG_RATING) {
                val rating = when (type) {
                    3 -> buffer.getShort(entryPos + 8).toInt()
                    4 -> valueOrOffset
                    else -> valueOrOffset
                }
                return XmpRatingParser.normalizeRating(rating)
            } else if (tag == TAG_RATING_PERCENT) {
                val percent = when (type) {
                    3 -> buffer.getShort(entryPos + 8).toInt() and 0xFFFF
                    4 -> valueOrOffset
                    else -> valueOrOffset
                }
                return XmpRatingParser.percentToRating(percent)
            } else if (tag == 0x8769) {
                exifSubIfdOffset = valueOrOffset
            }

            entryPos += 12
        }

        if (exifSubIfdOffset != null &&
            exifSubIfdOffset > 0 &&
            tiffBase + exifSubIfdOffset >= 0 &&
            tiffBase + exifSubIfdOffset + 2 <= buffer.limit()
        ) {
            return parseIfd(buffer, tiffBase, tiffBase + exifSubIfdOffset, depth + 1, visitedOffsets)
        }

        return null
    }
}

