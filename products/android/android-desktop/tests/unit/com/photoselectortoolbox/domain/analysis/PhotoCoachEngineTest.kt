package com.photoselectortoolbox.domain.analysis

import com.photoselector.core.Requirement
import com.photoselector.core.model.ExifData
import com.photoselectortoolbox.data.model.ScanResult
import com.photoselectortoolbox.domain.model.TipSeverity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@Requirement("REQ-AND-COACH.01")
class PhotoCoachEngineTest {

    private val engine = PhotoCoachEngineImpl()

    @Test
    fun fastShutterSpeed_noCameraShakeRisk() {
        // 50mm focal length -> safe shutter speed = 1/50s (0.02s).
        // Actual shutter speed = 1/500s (0.002s), which is much faster than safe speed.
        val exif = ExifData(
            shutterSpeed = 0.002,
            focalLength = 50.0,
            aperture = 2.8,
            iso = 100
        )
        val scan = ScanResult(
            filePath = "test.jpg",
            sharpnessScore = 75.0,
            noiseLevel = 1.0,
            highlightClipping = 0.0,
            shadowClipping = 0.0
        )

        val report = engine.evaluate(exif, scan)

        assertEquals(0.02, report.safeShutterSpeed!!, 1e-4)
        assertEquals(0.0f, report.cameraShakeRisk, 0.001f)
        assertTrue(report.tips.none { it.category == "Camera Shake" })
    }

    @Test
    fun slowShutterSpeed_lowSharpness_cameraShakeWarningOrCritical() {
        // 100mm focal length -> safe shutter speed = 1/100s (0.01s).
        // Actual shutter speed = 1/10s (0.1s) -> 10x slower than safe shutter speed.
        // Sharpness is low (18.0 < 20.0 critical threshold).
        val exif = ExifData(
            shutterSpeed = 0.1,
            focalLength35mm = 100.0,
            aperture = 4.0,
            iso = 400
        )
        val scan = ScanResult(
            filePath = "test.jpg",
            sharpnessScore = 18.0
        )

        val report = engine.evaluate(exif, scan)

        assertEquals(0.01, report.safeShutterSpeed!!, 1e-4)
        assertTrue(report.cameraShakeRisk >= 0.7f)
        val shakeTip = report.tips.find { it.category == "Camera Shake" }
        assertNotNull("Expected camera shake tip", shakeTip)
        assertEquals(TipSeverity.CRITICAL, shakeTip!!.severity)
        assertTrue(shakeTip.recommendation.contains("shutter speed", ignoreCase = true))
    }

    @Test
    fun slowShutterSpeed_moderateBlur_cameraShakeWarning() {
        // 50mm focal length -> safe shutter speed = 0.02s (1/50s).
        // Actual shutter speed = 0.04s (1/25s, 2x slower).
        // Sharpness is 32.0 (below 40.0, but above 20.0 critical).
        val exif = ExifData(
            shutterSpeed = 0.04,
            focalLength = 50.0
        )
        val scan = ScanResult(
            filePath = "test.jpg",
            sharpnessScore = 32.0
        )

        val report = engine.evaluate(exif, scan)

        assertEquals(0.02, report.safeShutterSpeed!!, 1e-4)
        assertEquals(0.6f, report.cameraShakeRisk, 0.01f)
        val shakeTip = report.tips.find { it.category == "Camera Shake" }
        assertNotNull("Expected camera shake tip", shakeTip)
        assertEquals(TipSeverity.WARNING, shakeTip!!.severity)
    }

    @Test
    fun slowShutterSpeed_highSharpness_stabilizationInfoTip() {
        // 200mm focal length -> safe shutter speed = 1/200s (0.005s).
        // Actual shutter speed = 1/30s (0.0333s) -> much slower than safe speed.
        // But sharpness is high (78.0 >= 40.0) -> effective OIS/IBIS/tripod!
        val exif = ExifData(
            shutterSpeed = 0.0333,
            focalLength35mm = 200.0
        )
        val scan = ScanResult(
            filePath = "test.jpg",
            sharpnessScore = 78.0
        )

        val report = engine.evaluate(exif, scan)

        assertEquals(0.005, report.safeShutterSpeed!!, 1e-4)
        assertEquals(0.1f, report.cameraShakeRisk, 0.01f)
        val stabTip = report.tips.find { it.category == "Stabilization" }
        assertNotNull("Expected stabilization tip", stabTip)
        assertEquals(TipSeverity.INFO, stabTip!!.severity)
        assertTrue(stabTip.recommendation.contains("stabilization", ignoreCase = true))
    }

    @Test
    fun fastShutterSpeed_lowSharpness_reportsMissedFocusRatherThanShake() {
        // 50mm -> safe shutter speed 0.02s. Actual 0.001s (1/1000s, fast).
        // Low sharpness (25.0) -> not camera shake, but missed focus.
        val exif = ExifData(
            shutterSpeed = 0.001,
            focalLength = 50.0
        )
        val scan = ScanResult(
            filePath = "test.jpg",
            sharpnessScore = 25.0
        )

        val report = engine.evaluate(exif, scan)

        assertEquals(0.0f, report.cameraShakeRisk, 0.001f)
        val focusTip = report.tips.find { it.category == "Focus" }
        assertNotNull("Expected focus tip", focusTip)
        assertEquals(TipSeverity.INFO, focusTip!!.severity)
        assertTrue(focusTip.description.contains("missed focus", ignoreCase = true))
    }

    @Test
    fun highIso_elevatedNoise_isoNoiseWarning() {
        val exif = ExifData(
            iso = 3200,
            aperture = 4.0,
            shutterSpeed = 0.005
        )
        val scan = ScanResult(
            filePath = "test.jpg",
            noiseLevel = 18.0
        )

        val report = engine.evaluate(exif, scan)

        val noiseTip = report.tips.find { it.category == "Sensor Noise" }
        assertNotNull("Expected sensor noise tip", noiseTip)
        assertEquals(TipSeverity.WARNING, noiseTip!!.severity)
        assertTrue(noiseTip.recommendation.contains("aperture", ignoreCase = true))
        assertTrue(noiseTip.recommendation.contains("ISO", ignoreCase = true))
    }

    @Test
    fun highIso_criticalNoise_isoNoiseCritical() {
        val exif = ExifData(
            iso = 12800,
            aperture = 2.8
        )
        val scan = ScanResult(
            filePath = "test.jpg",
            noiseLevel = 22.0
        )

        val report = engine.evaluate(exif, scan)

        val noiseTip = report.tips.find { it.category == "Sensor Noise" }
        assertNotNull("Expected sensor noise tip", noiseTip)
        assertEquals(TipSeverity.CRITICAL, noiseTip!!.severity)
    }

    @Test
    fun cleanNoise_highIso_noNoiseWarning() {
        val exif = ExifData(
            iso = 3200,
            aperture = 2.8
        )
        val scan = ScanResult(
            filePath = "test.jpg",
            noiseLevel = 1.5 // clean sensor noise
        )

        val report = engine.evaluate(exif, scan)

        assertTrue(report.tips.none { it.category == "Sensor Noise" })
    }

    @Test
    fun blownHighlights_negativeEvCompensationTip() {
        val scan = ScanResult(
            filePath = "test.jpg",
            highlightClipping = 3.5, // 3.5% blown highlights
            shadowClipping = 0.2
        )

        val report = engine.evaluate(null, scan)

        assertEquals(-0.7, report.recommendedEvCompensation!!, 0.01)
        val expTip = report.tips.find { it.category == "Exposure" && it.title.contains("Blown Highlights") }
        assertNotNull("Expected blown highlights tip", expTip)
        assertEquals(TipSeverity.WARNING, expTip!!.severity)
        assertTrue(expTip.recommendation.contains("negative EV", ignoreCase = true))
    }

    @Test
    fun severeBlownHighlights_largerNegativeEvCompensation() {
        val scan = ScanResult(
            filePath = "test.jpg",
            highlightClipping = 8.5, // > 5.0% severe
            shadowClipping = 0.5
        )

        val report = engine.evaluate(null, scan)

        assertEquals(-1.0, report.recommendedEvCompensation!!, 0.01)
    }

    @Test
    fun crushedShadows_positiveEvCompensationTip() {
        val scan = ScanResult(
            filePath = "test.jpg",
            highlightClipping = 0.3,
            shadowClipping = 7.2 // 7.2% crushed shadows
        )

        val report = engine.evaluate(null, scan)

        assertEquals(0.7, report.recommendedEvCompensation!!, 0.01)
        val expTip = report.tips.find { it.category == "Exposure" && it.title.contains("Crushed Shadows") }
        assertNotNull("Expected crushed shadows tip", expTip)
        assertEquals(TipSeverity.WARNING, expTip!!.severity)
        assertTrue(expTip.recommendation.contains("positive EV", ignoreCase = true))
    }

    @Test
    fun bothBlownHighlightsAndCrushedShadows_hdrBracketingTip() {
        val scan = ScanResult(
            filePath = "test.jpg",
            highlightClipping = 4.2,
            shadowClipping = 8.0
        )

        val report = engine.evaluate(null, scan)

        // Cannot fix both with a single exposure offset
        assertNull(report.recommendedEvCompensation)
        val drTip = report.tips.find { it.category == "Dynamic Range" }
        assertNotNull("Expected dynamic range bracketing tip", drTip)
        assertEquals(TipSeverity.WARNING, drTip!!.severity)
        assertTrue(drTip.recommendation.contains("HDR", ignoreCase = true))
    }

    @Test
    fun diffractionWarning_f22() {
        val exif = ExifData(aperture = 22.0)
        val scan = ScanResult(filePath = "test.jpg")

        val report = engine.evaluate(exif, scan)

        val diffTip = report.tips.find { it.category == "Optics" && it.title.contains("Diffraction") }
        assertNotNull("Expected diffraction tip", diffTip)
        assertEquals(TipSeverity.WARNING, diffTip!!.severity)
        assertTrue(diffTip.recommendation.contains("sweet spot", ignoreCase = true))
    }

    @Test
    fun diffractionInfo_f16() {
        val exif = ExifData(aperture = 16.0)

        val report = engine.evaluate(exif, null)

        val diffTip = report.tips.find { it.category == "Optics" && it.title.contains("Diffraction") }
        assertNotNull("Expected diffraction tip at f/16", diffTip)
        assertEquals(TipSeverity.INFO, diffTip!!.severity)
    }

    @Test
    fun wideOpenSoftnessTip_f14_lowSharpness() {
        val exif = ExifData(aperture = 1.4)
        val scan = ScanResult(
            filePath = "test.jpg",
            sharpnessScore = 28.0 // soft image
        )

        val report = engine.evaluate(exif, scan)

        val wideOpenTip = report.tips.find { it.category == "Optics" && it.title.contains("Wide-Open") }
        assertNotNull("Expected wide-open softness tip", wideOpenTip)
        assertEquals(TipSeverity.INFO, wideOpenTip!!.severity)
        assertTrue(wideOpenTip.recommendation.contains("Stop down", ignoreCase = true))
    }

    @Test
    fun wideOpenSharp_noWideOpenTip() {
        val exif = ExifData(aperture = 1.4)
        val scan = ScanResult(
            filePath = "test.jpg",
            sharpnessScore = 80.0 // crisp sharpness despite f/1.4
        )

        val report = engine.evaluate(exif, scan)

        assertTrue(report.tips.none { it.title.contains("Wide-Open") })
    }

    @Test
    fun nullOrMissingMetadata_safeDefaultReportWithoutCrashing() {
        val reportFromNulls = engine.evaluate(null, null)
        assertTrue(reportFromNulls.tips.isEmpty())
        assertEquals(0.0f, reportFromNulls.cameraShakeRisk, 0.001f)
        assertNull(reportFromNulls.safeShutterSpeed)
        assertNull(reportFromNulls.recommendedEvCompensation)

        val emptyExif = ExifData()
        val emptyScan = ScanResult(filePath = "test.jpg")
        val reportFromEmpty = engine.evaluate(emptyExif, emptyScan)
        assertTrue(reportFromEmpty.tips.isEmpty())
        assertEquals(0.0f, reportFromEmpty.cameraShakeRisk, 0.001f)
        assertNull(reportFromEmpty.safeShutterSpeed)
        assertNull(reportFromEmpty.recommendedEvCompensation)
    }

    @Test
    fun scanResultExifFallback_worksWhenExifParamIsNull() {
        val scan = ScanResult(
            filePath = "test.jpg",
            exifData = ExifData(
                iso = 6400,
                aperture = 4.0
            ),
            noiseLevel = 20.0
        )

        val report = engine.evaluate(null, scan)

        val noiseTip = report.tips.find { it.category == "Sensor Noise" }
        assertNotNull("Expected noise tip from scanResult.exifData", noiseTip)
    }

    @Test
    fun fractionalClipping_handlesFractionsCorrectly() {
        // Fractional scale: 0.035 = 3.5%, 0.001 = 0.1%
        val scan = ScanResult(
            filePath = "test.jpg",
            highlightClipping = 0.035,
            shadowClipping = 0.001
        )
        val report = engine.evaluate(null, scan)
        assertEquals(-0.7, report.recommendedEvCompensation!!, 0.01)

        // Fractional scale: shadow clipping 0.08 = 8%
        val scanShadow = ScanResult(
            filePath = "test.jpg",
            highlightClipping = 0.001,
            shadowClipping = 0.08
        )
        val reportShadow = engine.evaluate(null, scanShadow)
        assertEquals(0.7, reportShadow.recommendedEvCompensation!!, 0.01)
    }
}
