package com.photoselector.core.model

/**
 * Capture metadata of one photograph.
 *
 * [make], [model] and [lens] are trimmed — some cameras (Pentax, Olympus) pad
 * them with spaces, which must never reach the screen. [make] and [model] are
 * null when the file does not carry them.
 */
data class ExifData(
    val shutterSpeed: Double? = null,
    val aperture: Double? = null,
    val focalLength: Double? = null,
    val focalLength35mm: Double? = null,
    val iso: Int? = null,
    val lens: String = "Unknown",
    val isFallback: Boolean = false,
    val rating: Int = 0,
    val make: String? = null,
    val model: String? = null,
)
