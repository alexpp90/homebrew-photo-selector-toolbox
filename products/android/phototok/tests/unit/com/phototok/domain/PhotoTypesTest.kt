package com.phototok.domain

import com.phototok.data.model.ImageItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoTypesTest {

    // ── Enum key round-trips (DataStore wire-format compatibility) ────────

    @Test
    fun `swipe action keys round-trip`() {
        SwipeAction.entries.forEach { action ->
            assertEquals(action, SwipeAction.fromKey(action.key))
        }
    }

    @Test
    fun `collection action keys round-trip`() {
        CollectionAction.entries.forEach { action ->
            assertEquals(action, CollectionAction.fromKey(action.key))
        }
    }

    @Test
    fun `file type filter keys round-trip`() {
        FileTypeFilter.entries.forEach { filter ->
            assertEquals(filter, FileTypeFilter.fromKey(filter.key))
        }
    }

    @Test
    fun `unknown or null keys fall back to defaults`() {
        assertEquals(SwipeAction.DELETE, SwipeAction.fromKey(null))
        assertEquals(SwipeAction.DELETE, SwipeAction.fromKey("bogus"))
        assertEquals(CollectionAction.COPY, CollectionAction.fromKey(null))
        assertEquals(FileTypeFilter.ALL, FileTypeFilter.fromKey("nope"))
    }

    // ── Extension helpers ──────────────────────────────────────────────────

    @Test
    fun `extension detection is case-insensitive`() {
        assertTrue(PhotoExtensions.isRaw("IMG_001.ARW"))
        assertTrue(PhotoExtensions.isRaw("img_001.dng"))
        assertTrue(PhotoExtensions.isJpeg("IMG_001.JPG"))
        assertTrue(PhotoExtensions.isJpeg("img_001.jpeg"))
        assertFalse(PhotoExtensions.isRaw("img_001.jpg"))
        assertFalse(PhotoExtensions.isJpeg("img_001.arw"))
        assertFalse(PhotoExtensions.isRaw("no_extension"))
    }

    // ── Feed filtering ─────────────────────────────────────────────────────

    private fun img(name: String) = ImageItem(
        uri = "content://photos/$name",
        fileName = name,
        fileSize = 1L,
        lastModified = 0L,
        mimeType = null,
    )

    @Test
    fun `filterByType returns matching subsets`() {
        val images = listOf(img("a.jpg"), img("b.ARW"), img("c.png"), img("d.jpeg"))

        assertEquals(images, PhoneFeedOrdering.filterByType(images, FileTypeFilter.ALL))
        assertEquals(
            listOf("b.ARW"),
            PhoneFeedOrdering.filterByType(images, FileTypeFilter.RAW).map { it.fileName },
        )
        assertEquals(
            listOf("a.jpg", "d.jpeg"),
            PhoneFeedOrdering.filterByType(images, FileTypeFilter.JPG).map { it.fileName },
        )
    }

    // ── Portrait split helper ──────────────────────────────────────────────

    private fun sized(name: String, w: Int, h: Int) = ImageItem(
        uri = "content://photos/$name",
        fileName = name,
        fileSize = 1L,
        lastModified = 0L,
        mimeType = null,
        imageWidth = w,
        imageHeight = h,
    )

    @Test
    fun `portraitSplit finds first portrait index`() {
        val landscape = sized("l.jpg", 4000, 3000)
        val portrait = sized("p.jpg", 3000, 4000)

        assertEquals(1, PhoneFeedOrdering.portraitSplit(listOf(landscape, portrait), true))
        assertEquals(0, PhoneFeedOrdering.portraitSplit(listOf(portrait, landscape), true))
        assertEquals(-1, PhoneFeedOrdering.portraitSplit(listOf(landscape), true))
        assertEquals(-1, PhoneFeedOrdering.portraitSplit(emptyList(), true))
        assertEquals(-1, PhoneFeedOrdering.portraitSplit(listOf(portrait), false))
    }
}
