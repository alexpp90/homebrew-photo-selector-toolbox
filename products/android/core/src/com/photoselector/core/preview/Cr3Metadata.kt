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
    val gpsLatitude: Double? = null,
    val gpsLongitude: Double? = null,
    val gpsDateTimeUtc: String? = null,
    val dateTimeOriginal: String? = null,
    val offsetTimeOriginal: String? = null,
    val focalPlaneXResolution: Double? = null,
    val focalPlaneResolutionUnit: Int? = null,
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
                val gps = cr3.cmt4?.let { box ->
                    TiffReader.at(reader, box.payload)?.let { it.readIfd(it.firstIfdOffset) }
                }
                val xmp = cr3.xmp?.let { box ->
                    val len = (box.payloadSize - 16).coerceIn(0, 1L shl 20).toInt()
                    String(reader.bytes(box.payload + 16, len), Charsets.UTF_8)
                }
                fun d(tag: Int) = exif?.get(tag)?.double()?.takeIf { it > 0.0 }
                fun i(tag: Int) = exif?.get(tag)?.long()?.toInt()?.takeIf { it > 0 }
                val lat = parseGpsCoord(
                    gps?.get(Tags.GPS_LATITUDE)?.doubles(),
                    gps?.get(Tags.GPS_LATITUDE_REF)?.string()
                )
                val lon = parseGpsCoord(
                    gps?.get(Tags.GPS_LONGITUDE)?.doubles(),
                    gps?.get(Tags.GPS_LONGITUDE_REF)?.string()
                )
                val gpsUtc = parseGpsUtc(
                    gps?.get(Tags.GPS_DATE_STAMP)?.string(),
                    gps?.get(Tags.GPS_TIME_STAMP)?.doubles()
                )
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
                    gpsLatitude = lat,
                    gpsLongitude = lon,
                    gpsDateTimeUtc = gpsUtc,
                    dateTimeOriginal = exif?.get(Tags.DATE_TIME_ORIGINAL)?.string(),
                    offsetTimeOriginal = exif?.get(Tags.OFFSET_TIME_ORIGINAL)?.string(),
                    focalPlaneXResolution = d(Tags.FOCAL_PLANE_X_RESOLUTION),
                    focalPlaneResolutionUnit = i(Tags.FOCAL_PLANE_RESOLUTION_UNIT),
                )
            }
        } catch (e: Exception) {
            null
        }

        private fun parseGpsCoord(dms: DoubleArray?, ref: String?): Double? {
            if (dms == null || dms.size < 3) return null
            val deg = dms[0]
            val min = dms[1]
            val sec = dms[2]
            if (!deg.isFinite() || !min.isFinite() || !sec.isFinite()) return null
            val abs = deg + (min / 60.0) + (sec / 3600.0)
            val sign = if (ref?.trim()?.uppercase() in setOf("S", "W")) -1.0 else 1.0
            return abs * sign
        }

        private fun parseGpsUtc(dateStamp: String?, timeRationals: DoubleArray?): String? {
            val cleanDate = dateStamp?.trim()?.takeIf { it.length >= 10 } ?: return null
            if (timeRationals == null || timeRationals.size < 3) return null
            val h = timeRationals[0].toInt().coerceIn(0, 23)
            val m = timeRationals[1].toInt().coerceIn(0, 59)
            val s = timeRationals[2].toInt().coerceIn(0, 59)
            return "%s %02d:%02d:%02d".format(cleanDate, h, m, s)
        }
    }
}

