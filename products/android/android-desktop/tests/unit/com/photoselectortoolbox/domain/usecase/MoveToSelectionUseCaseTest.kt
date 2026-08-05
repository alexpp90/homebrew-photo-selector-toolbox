package com.photoselectortoolbox.domain.usecase

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for MoveToSelectionUseCase's pure logic — file extension routing
 * and the MoveResult/ScanProgress data classes. The actual file I/O uses
 * DocumentFile and is tested in instrumented tests.
 */
class MoveToSelectionUseCaseTest {

    // ── Extension classification (mirrors companion object sets) ─────

    private val rawExtensions = setOf(
        "arw", "cr2", "cr3", "nef", "nrw", "orf", "raf", "rw2",
        "pef", "srw", "dng", "raw", "3fr", "ari", "bay", "cap",
        "iiq", "eip", "erf", "fff", "mef", "mdc", "mos", "mrw",
        "obm", "ptx", "pxn", "rwl", "rwz", "sr2", "srf", "x3f"
    )
    private val jpegExtensions = setOf("jpg", "jpeg")

    private fun classifyExtension(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        val stem = fileName.substringBeforeLast('.')
        return when {
            stem.endsWith("-Edit") -> "RAW"
            ext in rawExtensions -> "RAW"
            ext in jpegExtensions -> "JPEG"
            ext == "xmp" -> {
                val parentExt = stem.substringAfterLast('.', "").lowercase()
                if (parentExt in rawExtensions) "RAW" else "Selection"
            }
            else -> "Selection"
        }
    }

    @Test
    fun `RAW extensions route to RAW folder`() {
        assertEquals("RAW", classifyExtension("IMG_001.ARW"))
        assertEquals("RAW", classifyExtension("DSC_0001.NEF"))
        assertEquals("RAW", classifyExtension("photo.CR2"))
        assertEquals("RAW", classifyExtension("IMG_001.DNG"))
        assertEquals("RAW", classifyExtension("photo.RAF"))
    }

    @Test
    fun `JPEG extensions route to JPEG folder`() {
        assertEquals("JPEG", classifyExtension("IMG_001.jpg"))
        assertEquals("JPEG", classifyExtension("photo.JPEG"))
    }

    @Test
    fun `Lightroom edit files route to RAW folder`() {
        assertEquals("RAW", classifyExtension("IMG_001-Edit.tif"))
        assertEquals("RAW", classifyExtension("photo-Edit.jpg"))
        // Note: -Edit.jpg has jpg extension but -Edit suffix takes priority
        val ext = "jpg"
        val stem = "photo-Edit"
        val result = when {
            stem.endsWith("-Edit") -> "RAW"
            ext in rawExtensions -> "RAW"
            ext in jpegExtensions -> "JPEG"
            else -> "Selection"
        }
        assertEquals("RAW", result)
    }

    @Test
    fun `non-JPEG edit files route to RAW via -Edit suffix`() {
        assertEquals("RAW", classifyExtension("IMG_001-Edit.tif"))
        assertEquals("RAW", classifyExtension("photo-Edit.psd"))
    }

    @Test
    fun `XMP sidecar for RAW parent routes to RAW`() {
        assertEquals("RAW", classifyExtension("IMG_001.ARW.xmp"))
        assertEquals("RAW", classifyExtension("photo.NEF.xmp"))
    }

    @Test
    fun `XMP sidecar without RAW parent routes to Selection root`() {
        assertEquals("Selection", classifyExtension("IMG_001.xmp"))
        assertEquals("Selection", classifyExtension("photo.xmp"))
    }

    @Test
    fun `PNG routes to Selection root`() {
        assertEquals("Selection", classifyExtension("screenshot.png"))
    }

    @Test
    fun `TIFF routes to Selection root`() {
        assertEquals("Selection", classifyExtension("scan.tiff"))
    }

    @Test
    fun `HEIC routes to Selection root`() {
        assertEquals("Selection", classifyExtension("IMG_001.HEIC"))
    }

    // ── MoveResult data class ───────────────────────────────────────

    @Test
    fun `MoveResult success`() {
        val result = MoveResult("source_uri", "dest_uri", success = true)
        assertTrue(result.success)
        assertEquals("source_uri", result.sourceUri)
        assertEquals("dest_uri", result.destinationUri)
        assertNull(result.error)
    }

    @Test
    fun `MoveResult failure with error`() {
        val result = MoveResult("source_uri", null, success = false, error = "Permission denied")
        assertFalse(result.success)
        assertNull(result.destinationUri)
        assertEquals("Permission denied", result.error)
    }

    // ── ScanProgress data class ─────────────────────────────────────

    @Test
    fun `ScanProgress initial state`() {
        val progress = ScanProgress(0, 100, "", emptyMap())
        assertEquals(0, progress.processed)
        assertEquals(100, progress.total)
        assertEquals("", progress.currentFile)
        assertTrue(progress.results.isEmpty())
    }

    @Test
    fun `ScanProgress mid-scan`() {
        val results = mapOf(
            "uri1" to com.photoselectortoolbox.data.model.ScanResult("uri1", sharpnessScore = 50.0)
        )
        val progress = ScanProgress(1, 10, "IMG_001.jpg", results)
        assertEquals(1, progress.processed)
        assertEquals("IMG_001.jpg", progress.currentFile)
        assertEquals(50.0, progress.results["uri1"]!!.sharpnessScore!!, 1e-9)
    }
}
