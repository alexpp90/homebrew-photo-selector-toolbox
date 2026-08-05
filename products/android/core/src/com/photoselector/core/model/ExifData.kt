package com.photoselector.core.model

data class ExifData(
    val shutterSpeed: Double? = null,
    val aperture: Double? = null,
    val focalLength: Double? = null,
    val focalLength35mm: Double? = null,
    val iso: Int? = null,
    val lens: String = "Unknown",
    val isFallback: Boolean = false
)
