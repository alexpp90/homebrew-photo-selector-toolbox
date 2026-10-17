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
    val gpsLatitude: Double? = null,
    val gpsLongitude: Double? = null,
    /** GPS UTC time as the EXIF date stamp and time, `YYYY:MM:DD HH:MM:SS`. Location data: never log, persist or transmit. */
    val gpsDateTimeUtc: String? = null,
    val dateTimeOriginal: String? = null,
    val offsetTimeOriginal: String? = null,
    val focalPlaneXResolution: Double? = null,
    val focalPlaneResolutionUnit: Int? = null,
)
