package com.photoselectortoolbox.data.model

data class ImageItem(
    val uri: String,
    val fileName: String,
    val fileSize: Long,
    val lastModified: Long,
    val mimeType: String?,
    val exifData: ExifData? = null,
    val scanResult: ScanResult? = null,
    val groupId: Int? = null
)
