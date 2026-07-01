package com.phototok.domain

import com.phototok.data.model.ImageItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PendingDeleteLogicTest {

    private fun img(name: String, width: Int = 4000, height: Int = 3000) = ImageItem(
        uri = "content://photos/$name",
        fileName = name,
        fileSize = 1L,
        lastModified = 0L,
        mimeType = "image/jpeg",
        imageWidth = width,
        imageHeight = height,
    )

    private val a = img("a.jpg")
    private val b = img("b.jpg")
    private val c = img("c.jpg")

    @Test
    fun `remove deletes current image from both lists and keeps index`() {
        val (lists, pending) = PendingDeleteLogic.remove(
            images = listOf(a, b, c),
            allImages = listOf(a, b, c),
            currentIndex = 1,
            related = emptyList(),
            sortByOrientation = false,
        )!!

        assertEquals(listOf(a, c), lists.images)
        assertEquals(listOf(a, c), lists.allImages)
        assertEquals(1, lists.currentIndex)
        assertEquals(b, pending.image)
        assertEquals(1, pending.index)
        assertEquals(1, pending.allImagesIndex)
        assertEquals(c.uri, pending.revertAllowedUri)
    }

    @Test
    fun `remove at last index clamps current index`() {
        val (lists, pending) = PendingDeleteLogic.remove(
            images = listOf(a, b, c),
            allImages = listOf(a, b, c),
            currentIndex = 2,
            related = emptyList(),
            sortByOrientation = false,
        )!!

        assertEquals(1, lists.currentIndex)
        assertEquals(b.uri, pending.revertAllowedUri)
    }

    @Test
    fun `remove out of bounds returns null`() {
        assertNull(
            PendingDeleteLogic.remove(
                images = listOf(a),
                allImages = listOf(a),
                currentIndex = 5,
                related = emptyList(),
                sortByOrientation = false,
            )
        )
    }

    @Test
    fun `remove also drops related siblings from unfiltered list`() {
        val raw = img("b.arw")
        val (lists, pending) = PendingDeleteLogic.remove(
            images = listOf(a, b, c),          // filtered: JPG only
            allImages = listOf(a, b, raw, c),  // unfiltered contains RAW sibling
            currentIndex = 1,
            related = listOf(raw),
            sortByOrientation = false,
        )!!

        assertEquals(listOf(a, c), lists.images)
        assertEquals(listOf(a, c), lists.allImages)
        assertEquals(listOf(raw), pending.related)
    }

    @Test
    fun `restore reinserts image and siblings at original positions`() {
        val raw = img("b.arw")
        val (lists, pending) = PendingDeleteLogic.remove(
            images = listOf(a, b, c),
            allImages = listOf(a, b, raw, c),
            currentIndex = 1,
            related = listOf(raw),
            sortByOrientation = false,
        )!!

        val restored = PendingDeleteLogic.restore(
            images = lists.images,
            allImages = lists.allImages,
            pending = pending,
            sortByOrientation = false,
        )

        assertEquals(listOf(a, b, c), restored.images)
        assertEquals(1, restored.currentIndex)
        // Primary back at its original slot; sibling re-appended.
        assertEquals(b, restored.allImages[1])
        assertEquals(true, restored.allImages.any { it.uri == raw.uri })
    }

    @Test
    fun `restore does not duplicate siblings already present`() {
        val raw = img("b.arw")
        val pending = PendingDeleteLogic.Pending(
            image = b,
            index = 1,
            allImagesIndex = 1,
            related = listOf(raw),
            revertAllowedUri = null,
        )

        val restored = PendingDeleteLogic.restore(
            images = listOf(a, c),
            allImages = listOf(a, raw, c), // sibling already present
            pending = pending,
            sortByOrientation = false,
        )

        assertEquals(1, restored.allImages.count { it.uri == raw.uri })
    }

    @Test
    fun `remove recomputes portrait split when orientation sorting is on`() {
        val l1 = img("l1.jpg", width = 4000, height = 3000)
        val l2 = img("l2.jpg", width = 4000, height = 3000)
        val p1 = img("p1.jpg", width = 3000, height = 4000)

        // Delete l2 → remaining [l1, p1] → portrait section starts at 1.
        val (lists, _) = PendingDeleteLogic.remove(
            images = listOf(l1, l2, p1),
            allImages = listOf(l1, l2, p1),
            currentIndex = 1,
            related = emptyList(),
            sortByOrientation = true,
        )!!

        assertEquals(1, lists.portraitSectionStart)
    }

    @Test
    fun `remove last remaining image yields empty lists and no split`() {
        val (lists, pending) = PendingDeleteLogic.remove(
            images = listOf(a),
            allImages = listOf(a),
            currentIndex = 0,
            related = emptyList(),
            sortByOrientation = true,
        )!!

        assertEquals(0, lists.images.size)
        assertEquals(0, lists.currentIndex)
        assertEquals(-1, lists.portraitSectionStart)
        assertNull(pending.revertAllowedUri)
    }
}
