package com.photoselectortoolbox.data.reader

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.photoselectortoolbox.data.model.ExifData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
                // Try ParcelFileDescriptor first because it's seekable (important for TIFF/RAW/DNG formats and many JPEGs)
                val pfd = try {
                    context.contentResolver.openFileDescriptor(uri, "r")
                } catch (e: Exception) {
                    null
                }

                if (pfd != null) {
                    pfd.use {
                        val exif = ExifInterface(it.fileDescriptor)
                        parseExifData(exif)
                    }
                } else {
                    // Fall back to InputStream if PFD fails
                    val inputStream = context.contentResolver.openInputStream(uri)
                        ?: return@withContext null
                    inputStream.use { stream ->
                        val exif = ExifInterface(stream)
                        parseExifData(exif)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to read EXIF from $uri", e)
                null
            }
        }

    override fun supportedMimeTypes(): Set<String> = SUPPORTED_MIME_TYPES

    private fun parseExifData(exif: ExifInterface): ExifData {
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

        return ExifData(
            shutterSpeed = shutterSpeed,
            aperture = aperture,
            focalLength = focalLength,
            focalLength35mm = focalLength35mm,
            iso = iso,
            lens = lens,
            isFallback = false
        )
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
