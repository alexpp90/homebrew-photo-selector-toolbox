package com.phototok.domain

import com.phototok.data.model.ImageItem
import org.junit.Assert.assertEquals
import org.junit.Test

class OptimisticFeedTest {

    private fun img(name: String, date: Long, width: Int = 0, height: Int = 0) = ImageItem(
        uri = "content://photos/$name",
        fileName = name,
        fileSize = 1L,
        lastModified = date,
        mimeType = "image/jpeg",
        imageWidth = width,
        imageHeight = height,
    )

    private val a = img("a.jpg", 1)
    private val b = img("b.jpg", 2)
    private val c = img("c.jpg", 3)

    @Test
    fun `slots record the position in both lists`() {
        val images = listOf(a, c)
        val allImages = listOf(a, b, c)

        val slots = OptimisticFeed.slotsOf(images, allImages, listOf(c))

        assertEquals(1, slots.single().index)
        assertEquals(2, slots.single().allImagesIndex)
    }

    @Test
    fun `a target hidden by the file filter records index -1 for the feed`() {
        val images = listOf(a)            // b filtered out of the feed
        val allImages = listOf(a, b)

        val slots = OptimisticFeed.slotsOf(images, allImages, listOf(b))

        assertEquals(-1, slots.single().index)
        assertEquals(1, slots.single().allImagesIndex)
    }

    @Test
    fun `restore puts a failed move back at its original position`() {
        val images = listOf(a, b, c)
        val allImages = listOf(a, b, c)
        val slots = OptimisticFeed.slotsOf(images, allImages, listOf(b))
        // Optimistic removal of b.
        val afterRemoval = images.filter { it.uri != b.uri }

        val restored = OptimisticFeed.restore(
            images = afterRemoval,
            allImages = allImages.filter { it.uri != b.uri },
            slots = slots,
            currentIndex = 1,
            sortByOrientation = false,
        )

        assertEquals(listOf("a.jpg", "b.jpg", "c.jpg"), restored.images.map { it.fileName })
        assertEquals(listOf("a.jpg", "b.jpg", "c.jpg"), restored.allImages.map { it.fileName })
    }

    @Test
    fun `restore keeps the user on the photo they are actually looking at`() {
        // b was removed, so index 1 is now c — the photo on screen. Re-inserting b
        // at index 1 must move the user to index 2 rather than silently swap them
        // onto b.
        val slots = listOf(OptimisticFeed.Slot(image = b, index = 1, allImagesIndex = 1))

        val restored = OptimisticFeed.restore(
            images = listOf(a, c),
            allImages = listOf(a, c),
            slots = slots,
            currentIndex = 1,
            sortByOrientation = false,
        )

        assertEquals(listOf("a.jpg", "b.jpg", "c.jpg"), restored.images.map { it.fileName })
        assertEquals(2, restored.currentIndex)
        assertEquals("c.jpg", restored.images[restored.currentIndex].fileName)
    }

    @Test
    fun `restore re-inserts several siblings in ascending index order`() {
        val d = img("d.jpg", 4)
        val slots = listOf(
            OptimisticFeed.Slot(image = b, index = 1, allImagesIndex = 1),
            OptimisticFeed.Slot(image = d, index = 3, allImagesIndex = 3),
        )

        val restored = OptimisticFeed.restore(
            images = listOf(a, c),
            allImages = listOf(a, c),
            slots = slots,
            currentIndex = 0,
            sortByOrientation = false,
        )

        assertEquals(
            listOf("a.jpg", "b.jpg", "c.jpg", "d.jpg"),
            restored.images.map { it.fileName },
        )
    }

    @Test
    fun `restore skips a sibling the filter had hidden from the feed`() {
        val slots = listOf(OptimisticFeed.Slot(image = b, index = -1, allImagesIndex = 1))

        val restored = OptimisticFeed.restore(
            images = listOf(a, c),
            allImages = listOf(a, c),
            slots = slots,
            currentIndex = 0,
            sortByOrientation = false,
        )

        // Back in the unfiltered list, still absent from the filtered feed.
        assertEquals(listOf("a.jpg", "c.jpg"), restored.images.map { it.fileName })
        assertEquals(listOf("a.jpg", "b.jpg", "c.jpg"), restored.allImages.map { it.fileName })
    }

    @Test
    fun `restore is idempotent when the item is already back`() {
        val slots = listOf(OptimisticFeed.Slot(image = b, index = 1, allImagesIndex = 1))

        val restored = OptimisticFeed.restore(
            images = listOf(a, b, c),
            allImages = listOf(a, b, c),
            slots = slots,
            currentIndex = 0,
            sortByOrientation = false,
        )

        assertEquals(listOf("a.jpg", "b.jpg", "c.jpg"), restored.images.map { it.fileName })
    }

    @Test
    fun `restore recomputes the portrait split`() {
        val landscape = img("l.jpg", 400, width = 4000, height = 3000)
        val portrait = img("p.jpg", 300, width = 3000, height = 4000)
        val slots = listOf(OptimisticFeed.Slot(image = portrait, index = 1, allImagesIndex = 1))

        val restored = OptimisticFeed.restore(
            images = listOf(landscape),
            allImages = listOf(landscape),
            slots = slots,
            currentIndex = 0,
            sortByOrientation = true,
        )

        assertEquals(1, restored.portraitSectionStart)
    }

    @Test
    fun `restoring into an empty feed lands at index 0`() {
        val slots = listOf(OptimisticFeed.Slot(image = a, index = 0, allImagesIndex = 0))

        val restored = OptimisticFeed.restore(
            images = emptyList(),
            allImages = emptyList(),
            slots = slots,
            currentIndex = 0,
            sortByOrientation = false,
        )

        assertEquals(listOf("a.jpg"), restored.images.map { it.fileName })
        assertEquals(0, restored.currentIndex)
    }
}
