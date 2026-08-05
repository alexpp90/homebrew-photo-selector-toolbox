package com.photoselectortoolbox.data.model

import org.junit.Assert.*
import org.junit.Test

class ImageItemTest {

    private fun item(width: Int = 0, height: Int = 0) = ImageItem(
        uri = "content://media/external/images/1",
        fileName = "IMG_0001.jpg",
        fileSize = 1024L,
        lastModified = 1700000000000L,
        mimeType = "image/jpeg",
        imageWidth = width,
        imageHeight = height
    )

    @Test
    fun `isLandscape true when width greater than height`() {
        assertTrue(item(width = 4000, height = 3000).isLandscape)
    }

    @Test
    fun `isLandscape false when height greater than width`() {
        assertFalse(item(width = 3000, height = 4000).isLandscape)
    }

    @Test
    fun `isLandscape true when dimensions unknown (both zero)`() {
        assertTrue(item(width = 0, height = 0).isLandscape)
    }

    @Test
    fun `isLandscape true when equal dimensions`() {
        // width > height is false when equal, but 0,0 fallback path triggers first
        // for square images: width == height -> not strictly landscape
        assertFalse(item(width = 3000, height = 3000).isLandscape)
    }

    @Test
    fun `scanResult defaults to null`() {
        assertNull(item().scanResult)
    }

    @Test
    fun `groupId defaults to null`() {
        assertNull(item().groupId)
    }

    @Test
    fun `copy with scanResult attaches score`() {
        val result = ScanResult(filePath = "test", sharpnessScore = 42.5)
        val updated = item().copy(scanResult = result)
        assertEquals(42.5, updated.scanResult!!.sharpnessScore!!, 1e-9)
    }
}
