package com.photoselectortoolbox.data.metadata

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.photoselectortoolbox.domain.format.ImageFormats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Non-destructive metadata write-back engine for ratings.
 *
 * - JPEG Files: In-place embedded XMP update via [ExifInterface] setting [ExifInterface.TAG_XMP],
 *   leaving image pixel bytes and EXIF capture timestamps ([ExifInterface.TAG_DATETIME_ORIGINAL]) untouched.
 * - RAW & DNG Files (CR2, CR3, NEF, ARW, ORF, RAF, DNG, etc.): Creates or updates companion `<filename>.xmp`
 *   sidecar files formatted with ISO 16684-1 standard `<xmp:Rating>`.
 * - Non-destructive fallback: If in-place write fails (read-only or unsupported header), falls back gracefully
 *   to companion `.xmp` sidecar.
 */
interface MetadataWriter {
    suspend fun writeRating(imageFile: File, rating: Int): Result<Unit>

    suspend fun writeRating(context: Context, imageUri: Uri, rating: Int): Result<Unit> {
        val file = when {
            imageUri.scheme == "file" -> imageUri.path?.let { File(it) } ?: File(imageUri.toString())
            imageUri.scheme == null -> File(imageUri.toString())
            imageUri.path != null && File(imageUri.path!!).exists() -> File(imageUri.path!!)
            else -> File(imageUri.toString())
        }
        return writeRating(file, rating)
    }
}

@Singleton
class MetadataWriterImpl @Inject constructor() : MetadataWriter {

    companion object {
        private const val TAG = "MetadataWriter"
    }

    override suspend fun writeRating(context: Context, imageUri: Uri, rating: Int): Result<Unit> =
        withContext(Dispatchers.IO) {
            val directFile = when {
                imageUri.scheme == "file" -> imageUri.path?.let { File(it) }
                imageUri.scheme == null && imageUri.path != null -> File(imageUri.path!!)
                else -> imageUri.path?.let { p -> val f = File(p); if (f.exists()) f else null }
            }
            if (directFile != null && directFile.exists()) {
                return@withContext writeRating(directFile, rating)
            }

            try {
                context.contentResolver.openFileDescriptor(imageUri, "rw")?.use { pfd ->
                    val exif = ExifInterface(pfd.fileDescriptor)
                    val currentXmp = exif.getAttribute(ExifInterface.TAG_XMP)
                    val updatedXmp = XmpSidecarWriter.injectOrUpdateRating(currentXmp, rating)
                    exif.setAttribute(ExifInterface.TAG_XMP, updatedXmp)
                    exif.saveAttributes()
                    return@withContext Result.success(Unit)
                }
                Result.failure(IOException("Unable to open read-write descriptor for $imageUri"))
            } catch (e: Exception) {
                Log.w(TAG, "SAF descriptor rating write skipped/failed for $imageUri", e)
                Result.failure(e)
            }
        }

    override suspend fun writeRating(imageFile: File, rating: Int): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                if (!imageFile.exists()) {
                    return@withContext Result.failure(
                        IOException("Image file does not exist: ${imageFile.absolutePath}")
                    )
                }

                // RAW (and TIFF, via the final branch) get a sidecar; only JPEG is
                // edited in place. Classification comes from ImageFormats.
                if (ImageFormats.isRaw(imageFile.name)) {
                    writeSidecarRating(imageFile, rating)
                } else if (ImageFormats.isJpeg(imageFile.name)) {
                    try {
                        writeJpegRating(imageFile, rating)
                    } catch (e: Exception) {
                        Log.w(TAG, "In-place JPEG write failed for ${imageFile.name}; falling back to sidecar", e)
                        writeSidecarRating(imageFile, rating)
                    }
                } else {
                    // For any other format, use sidecar non-destructively
                    writeSidecarRating(imageFile, rating)
                }
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to write rating to ${imageFile.absolutePath}", e)
                Result.failure(e)
            }
        }

    private fun writeJpegRating(imageFile: File, rating: Int) {
        val exif = ExifInterface(imageFile.absolutePath)
        val currentXmp = exif.getAttribute(ExifInterface.TAG_XMP)
        val updatedXmp = XmpSidecarWriter.injectOrUpdateRating(currentXmp, rating)

        exif.setAttribute(ExifInterface.TAG_XMP, updatedXmp)
        exif.saveAttributes()
    }

    private fun writeSidecarRating(imageFile: File, rating: Int) {
        val parent = imageFile.parentFile ?: File(".")
        // Standard ISO 16684-1 companion: <filename>.xmp (e.g. IMG_0001.CR3.xmp) takes priority over <stem>.xmp
        val filenameSidecar = File(parent, "${imageFile.name}.xmp")
        val stemSidecar = File(parent, "${imageFile.nameWithoutExtension}.xmp")
        val targetSidecar = if (filenameSidecar.exists() && filenameSidecar.isFile) {
            filenameSidecar
        } else if (stemSidecar.exists() && stemSidecar.isFile) {
            stemSidecar
        } else {
            filenameSidecar
        }

        val existingContent = if (targetSidecar.exists() && targetSidecar.isFile) {
            targetSidecar.readText()
        } else {
            null
        }

        val updatedContent = XmpSidecarWriter.injectOrUpdateRating(existingContent, rating)
        targetSidecar.writeText(updatedContent)
    }
}

object XmpSidecarWriter {

    private val RATING_TAG_REGEX = Regex(
        "<(?:[a-zA-Z0-9_-]+:)?Rating>\\s*(-?\\d+(?:\\.\\d+)?)\\s*</(?:[a-zA-Z0-9_-]+:)?Rating>",
        RegexOption.IGNORE_CASE
    )
    private val RATING_ATTR_REGEX = Regex(
        "((?:[a-zA-Z0-9_-]+:)?Rating\\s*=\\s*[\"'])(-?\\d+(?:\\.\\d+)?)([\"'])",
        RegexOption.IGNORE_CASE
    )

    fun createXmpPacket(rating: Int): String {
        return buildString {
            append("<?xpacket begin=\"\" id=\"W5M0MpCehiHzreSzNTczkc9d\"?>\n")
            append("<x:xmpmeta xmlns:x=\"adobe:ns:meta/\">\n")
            append("  <rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">\n")
            append("    <rdf:Description rdf:about=\"\" xmlns:xmp=\"http://ns.adobe.com/xap/1.0/\">\n")
            append("      <xmp:Rating>$rating</xmp:Rating>\n")
            append("    </rdf:Description>\n")
            append("  </rdf:RDF>\n")
            append("</x:xmpmeta>\n")
            append("<?xpacket end=\"w\"?>")
        }
    }

    fun injectOrUpdateRating(existingXmp: String?, rating: Int): String {
        if (existingXmp.isNullOrBlank()) {
            return createXmpPacket(rating)
        }

        // 1. If <xmp:Rating>...</xmp:Rating> exists, replace it
        if (RATING_TAG_REGEX.containsMatchIn(existingXmp)) {
            return existingXmp.replace(RATING_TAG_REGEX, "<xmp:Rating>$rating</xmp:Rating>")
        }

        // 2. If xmp:Rating="..." attribute exists, replace it
        if (RATING_ATTR_REGEX.containsMatchIn(existingXmp)) {
            return existingXmp.replace(RATING_ATTR_REGEX, "$1$rating$3")
        }

        // 3. If <rdf:Description exists, inject inside it
        val descIndex = existingXmp.indexOf("<rdf:Description")
        if (descIndex != -1) {
            val closeIndex = existingXmp.indexOf("</rdf:Description>", descIndex)
            if (closeIndex != -1) {
                val descTagEnd = existingXmp.indexOf(">", descIndex)
                val hasXmpNs = existingXmp.substring(descIndex, descTagEnd).contains("xmlns:xmp")
                val ratingTag = if (hasXmpNs) {
                    "      <xmp:Rating>$rating</xmp:Rating>\n"
                } else {
                    "      <xmp:Rating xmlns:xmp=\"http://ns.adobe.com/xap/1.0/\">$rating</xmp:Rating>\n"
                }
                return StringBuilder(existingXmp)
                    .insert(closeIndex, ratingTag)
                    .toString()
            } else {
                // Self-closing <rdf:Description ... />
                val selfCloseIndex = existingXmp.indexOf("/>", descIndex)
                if (selfCloseIndex != -1) {
                    val before = existingXmp.substring(0, selfCloseIndex)
                    val after = existingXmp.substring(selfCloseIndex + 2)
                    return "$before>\n      <xmp:Rating xmlns:xmp=\"http://ns.adobe.com/xap/1.0/\">$rating</xmp:Rating>\n    </rdf:Description>$after"
                }
            }
        }

        // Fallback: create fresh packet
        return createXmpPacket(rating)
    }
}
