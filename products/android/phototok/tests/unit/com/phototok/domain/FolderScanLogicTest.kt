package com.phototok.domain

import com.phototok.data.model.ImageItem
import org.junit.Assert.assertEquals
import org.junit.Test

class FolderScanLogicTest {

    private fun sampleImage(name: String, lastModified: Long): ImageItem = ImageItem(
        uri = "content://media/$name",
        fileName = name,
        fileSize = 1000L,
        lastModified = lastModified,
        mimeType = "image/jpeg",
    )

    @Test
    fun `findFirstNewImageIndex returns -1 when previous timestamp is zero or negative`() {
        val list = listOf(
            sampleImage("a.jpg", 100L),
            sampleImage("b.jpg", 200L),
        )
        assertEquals(-1, FolderScanLogic.findFirstNewImageIndex(list, 0L))
        assertEquals(-1, FolderScanLogic.findFirstNewImageIndex(list, -10L))
    }

    @Test
    fun `findFirstNewImageIndex returns -1 when list is empty`() {
        assertEquals(-1, FolderScanLogic.findFirstNewImageIndex(emptyList(), 100L))
    }

    @Test
    fun `findFirstNewImageIndex returns -1 when no items are newer than previous timestamp`() {
        val list = listOf(
            sampleImage("a.jpg", 100L),
            sampleImage("b.jpg", 200L),
            sampleImage("c.jpg", 300L),
        )
        assertEquals(-1, FolderScanLogic.findFirstNewImageIndex(list, 300L))
        assertEquals(-1, FolderScanLogic.findFirstNewImageIndex(list, 400L))
    }

    @Test
    fun `findFirstNewImageIndex returns index of first item strictly newer than previous timestamp`() {
        val list = listOf(
            sampleImage("a.jpg", 100L),
            sampleImage("b.jpg", 200L),
            sampleImage("c.jpg", 300L),
            sampleImage("d.jpg", 400L),
        )
        // Item at index 2 (300L) is the first newer than 250L
        assertEquals(2, FolderScanLogic.findFirstNewImageIndex(list, 250L))
        // Item at index 0 (100L) is newer than 50L
        assertEquals(0, FolderScanLogic.findFirstNewImageIndex(list, 50L))
        // Item at index 3 (400L) is newer than 300L
        assertEquals(3, FolderScanLogic.findFirstNewImageIndex(list, 300L))
    }

    @Test
    fun `countNewImages counts items strictly newer than previous timestamp`() {
        val list = listOf(
            sampleImage("a.jpg", 100L),
            sampleImage("b.jpg", 200L),
            sampleImage("c.jpg", 300L),
            sampleImage("d.jpg", 400L),
        )
        assertEquals(0, FolderScanLogic.countNewImages(list, 0L))
        assertEquals(0, FolderScanLogic.countNewImages(list, 400L))
        assertEquals(2, FolderScanLogic.countNewImages(list, 200L))
        assertEquals(4, FolderScanLogic.countNewImages(list, 50L))
    }

    @Test
    fun `computeUpdatedScanInfo extracts max lastModified and size`() {
        val emptyInfo = FolderScanLogic.computeUpdatedScanInfo(emptyList())
        assertEquals(0L, emptyInfo.maxLastModified)
        assertEquals(0, emptyInfo.fileCount)

        val list = listOf(
            sampleImage("a.jpg", 100L),
            sampleImage("b.jpg", 500L),
            sampleImage("c.jpg", 300L),
        )
        val info = FolderScanLogic.computeUpdatedScanInfo(list)
        assertEquals(500L, info.maxLastModified)
        assertEquals(3, info.fileCount)
    }
}
