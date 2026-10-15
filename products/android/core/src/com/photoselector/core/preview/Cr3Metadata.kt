package com.photoselector.core.preview

import java.nio.channels.FileChannel

/**
 * EXIF values read straight from a Canon CR3's `CMT1` (IFD0) and `CMT2`
 * (Exif IFD) TIFF blocks — androidx ExifInterface does not parse CR3.
 *
 * Fields are raw camera values; interpretation (ISO fallbacks, APEX, trimming)
 * stays in the reader that builds `ExifData`.
 */
internal class Cr3Metadata(
    val make: String?,
    val model: String?,
    val orientation: Int?,
    val exposureTime: Double?,
    val fNumber: Double?,
    val shutterSpeedValue: Double?,
    val apertureValue: Double?,
    val focalLength: Double?,
    val focalLength35mm: Double?,
    val photographicSensitivity: Int?,
    val sensitivityType: Int?,
    val recommendedExposureIndex: Int?,
    val isoSpeed: Int?,
    val standardOutputSensitivity: Int?,
    val lensModel: String?,
    val lensMake: String?,
    val xmp: String?,
) {
    companion object {
        /** Null when [channel] is not a CR3. */
        fun read(channel: FileChannel): Cr3Metadata? = try {
            val reader = ChannelReader(channel)
            val cr3 = Cr3Structure.parse(reader)
            if (cr3 == null) {
                null
            } else {
                val ifd0 = cr3.cmt1?.let { box ->
                    TiffReader.at(reader, box.payload)?.let { it.readIfd(it.firstIfdOffset) }
                }
                val exif = cr3.cmt2?.let { box ->
                    TiffReader.at(reader, box.payload)?.let { it.readIfd(it.firstIfdOffset) }
                }
                val xmp = cr3.xmp?.let { box ->
                    val len = (box.payloadSize - 16).coerceIn(0, 1L shl 20).toInt()
                    String(reader.bytes(box.payload + 16, len), Charsets.UTF_8)
                }
                fun d(tag: Int) = exif?.get(tag)?.double()?.takeIf { it > 0.0 }
                fun i(tag: Int) = exif?.get(tag)?.long()?.toInt()?.takeIf { it > 0 }
                Cr3Metadata(
                    make = ifd0?.get(Tags.MAKE)?.string(),
                    model = ifd0?.get(Tags.MODEL)?.string(),
                    orientation = ifd0?.get(Tags.ORIENTATION)?.long()?.toInt(),
                    exposureTime = d(Tags.EXPOSURE_TIME),
                    fNumber = d(Tags.F_NUMBER),
                    shutterSpeedValue = exif?.get(Tags.SHUTTER_SPEED_VALUE)?.double(),
                    apertureValue = exif?.get(Tags.APERTURE_VALUE)?.double(),
                    focalLength = d(Tags.FOCAL_LENGTH),
                    focalLength35mm = d(Tags.FOCAL_LENGTH_35MM),
                    photographicSensitivity = i(Tags.PHOTOGRAPHIC_SENSITIVITY),
                    sensitivityType = i(Tags.SENSITIVITY_TYPE),
                    recommendedExposureIndex = i(Tags.RECOMMENDED_EXPOSURE_INDEX),
                    isoSpeed = i(Tags.ISO_SPEED),
                    standardOutputSensitivity = i(Tags.STANDARD_OUTPUT_SENSITIVITY),
                    lensModel = exif?.get(Tags.LENS_MODEL)?.string(),
                    lensMake = exif?.get(Tags.LENS_MAKE)?.string(),
                    xmp = xmp,
                )
            }
        } catch (e: Exception) {
            null
        }
    }
}
