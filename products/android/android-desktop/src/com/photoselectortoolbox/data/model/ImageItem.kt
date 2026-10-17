package com.photoselectortoolbox.data.model

import com.photoselector.core.model.ExifData

typealias PhotoItem = ImageItem

/**
 * A companion file grouped with a primary [ImageItem] at discovery time
 * (e.g. a camera RAW twin when a processed JPEG is primary, an `.xmp` sidecar,
 * or a Lightroom `-Edit` derivative).
 */
data class CompanionFile(
    val uri: String,
    val fileName: String,
    val fileSize: Long = 0L,
    val lastModified: Long = 0L,
    val mimeType: String? = null,
)

data class ImageItem(
    val uri: String,
    val fileName: String,
    val fileSize: Long,
    val lastModified: Long,
    val mimeType: String?,
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
    val exifData: ExifData? = null,
    val scanResult: ScanResult? = null,
    val groupId: Int? = null,
    val rating: Int = exifData?.rating ?: 0,
    val companions: List<CompanionFile> = emptyList(),
    val parentDocumentId: String? = null,
) {
    /** True when the image is wider than it is tall (or dimensions unknown). */
    val isLandscape: Boolean
        get() = imageWidth == 0 && imageHeight == 0 || imageWidth > imageHeight

    /** Aspect ratio (width / height), or null if dimensions are unknown. */
    val aspectRatio: Float?
        get() = if (imageWidth > 0 && imageHeight > 0) {
            imageWidth.toFloat() / imageHeight.toFloat()
        } else null

    /** The primary file followed by all grouped companion files. */
    val allFiles: List<CompanionFile>
        get() = listOf(
            CompanionFile(
                uri = uri,
                fileName = fileName,
                fileSize = fileSize,
                lastModified = lastModified,
                mimeType = mimeType,
            )
        ) + companions

    /** True when any companion file is a camera RAW container. */
    val hasRawCompanion: Boolean
        get() = companions.any { com.photoselectortoolbox.domain.format.ImageFormats.isRaw(it.fileName) }
}
