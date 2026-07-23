package com.photoselectortoolbox.data.model

data class ScanResult(
    val filePath: String,
    val sharpnessScore: Double? = null,
    val noiseLevel: Double? = null,
    val highlightClipping: Double? = null,
    val shadowClipping: Double? = null,
    /** On-device AI aesthetic score on a 1.0–10.0 scale (null when not computed). */
    val aestheticScore: Double? = null,
    val exifData: ExifData? = null
)
