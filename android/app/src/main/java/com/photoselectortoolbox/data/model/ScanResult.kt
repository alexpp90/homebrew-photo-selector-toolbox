package com.photoselectortoolbox.data.model

data class ScanResult(
    val filePath: String,
    val sharpnessScore: Double? = null,
    val noiseLevel: Double? = null,
    val highlightClipping: Double? = null,
    val shadowClipping: Double? = null,
    val exifData: ExifData? = null
)
