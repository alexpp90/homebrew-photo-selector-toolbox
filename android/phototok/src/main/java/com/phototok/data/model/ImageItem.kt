package com.phototok.data.model

data class ImageItem(
    val uri: String,
    val fileName: String,
    val fileSize: Long,
    val lastModified: Long,
    val mimeType: String?,
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
    val exifData: ExifData? = null,
    val groupId: Int? = null,
) {
    /** True when the image is wider than it is tall (or dimensions unknown). */
    val isLandscape: Boolean
        get() = imageWidth == 0 && imageHeight == 0 || imageWidth > imageHeight

    /**
     * True when the image lives on a remote backend (Google Drive) rather than
     * local storage. Remote deletions go to a trash; local ones are permanent.
     */
    val isRemote: Boolean
        get() = com.phototok.domain.SourceUris.isRemote(uri)
}
