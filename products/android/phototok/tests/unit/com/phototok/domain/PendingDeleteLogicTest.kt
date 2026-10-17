package com.phototok.domain

import com.photoselector.core.Requirement
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
    @Requirement("REQ-TOK-ARCH.08", "REQ-TOK-GESTURE.03")
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

    @Test
    fun `remove drops related siblings from filtered list when filter is ALL and adjusts currentIndex`() {
        val rawBefore = img("b.arw")
        val rawAfter = img("b.dng")
        // Feed under ALL filter: [a, rawBefore, b, rawAfter, c], deleting b at currentIndex = 2
        val feed = listOf(a, rawBefore, b, rawAfter, c)
        val (lists, pending) = PendingDeleteLogic.remove(
            images = feed,
            allImages = feed,
            currentIndex = 2,
            related = listOf(rawBefore, rawAfter),
            sortByOrientation = false,
        )!!

        assertEquals(listOf(a, c), lists.images)
        assertEquals(listOf(a, c), lists.allImages)
        // rawBefore sat before currentIndex (at index 1), so c shifts into index 1
        assertEquals(1, lists.currentIndex)
        assertEquals(c.uri, pending.revertAllowedUri)
        assertEquals(3, pending.slots.size)
    }

    @Test
    fun `restore reinserts related siblings into filtered list at their original positions when filter is ALL`() {
        val rawBefore = img("b.arw")
        val rawAfter = img("b.dng")
        val feed = listOf(a, rawBefore, b, rawAfter, c)
        val (lists, pending) = PendingDeleteLogic.remove(
            images = feed,
            allImages = feed,
            currentIndex = 2,
            related = listOf(rawBefore, rawAfter),
            sortByOrientation = false,
        )!!

        val restored = PendingDeleteLogic.restore(
            images = lists.images,
            allImages = lists.allImages,
            pending = pending,
            sortByOrientation = false,
        )

        assertEquals(feed, restored.images)
        assertEquals(feed, restored.allImages)
        assertEquals(2, restored.currentIndex)
    }

    @Test
    fun `pushRemove and popRestore maintain multi-step LIFO undo stack`() {
        var images = listOf(a, b, c)
        var allImages = listOf(a, b, c)
        var stack = emptyList<PendingDeleteLogic.Pending>()

        val push1 = PendingDeleteLogic.pushRemove(
            images = images,
            allImages = allImages,
            currentIndex = 0,
            related = emptyList(),
            sortByOrientation = false,
            currentStack = stack,
        )!!
        images = push1.state.images
        allImages = push1.state.allImages
        stack = push1.updatedStack
        assertEquals(listOf(b, c), images)
        assertEquals(1, stack.size)
        assertNull(push1.evictedOldest)

        val push2 = PendingDeleteLogic.pushRemove(
            images = images,
            allImages = allImages,
            currentIndex = 0,
            related = emptyList(),
            sortByOrientation = false,
            currentStack = stack,
        )!!
        images = push2.state.images
        allImages = push2.state.allImages
        stack = push2.updatedStack
        assertEquals(listOf(c), images)
        assertEquals(2, stack.size)

        val pop2 = PendingDeleteLogic.popRestore(images, allImages, stack, false)!!
        images = pop2.state.images
        allImages = pop2.state.allImages
        stack = pop2.remainingStack
        assertEquals(listOf(b, c), images)
        assertEquals(1, stack.size)

        val pop1 = PendingDeleteLogic.popRestore(images, allImages, stack, false)!!
        assertEquals(listOf(a, b, c), pop1.state.images)
        assertEquals(0, pop1.remainingStack.size)
    }

    @Test
    fun `pushRemove evicts oldest item when stack exceeds MAX_STACK_SIZE`() {
        var images = (0..PendingDeleteLogic.MAX_STACK_SIZE).map { img("p$it.jpg") }
        var allImages = images
        var stack = emptyList<PendingDeleteLogic.Pending>()

        repeat(PendingDeleteLogic.MAX_STACK_SIZE) {
            val res = PendingDeleteLogic.pushRemove(
                images = images,
                allImages = allImages,
                currentIndex = 0,
                related = emptyList(),
                sortByOrientation = false,
                currentStack = stack,
            )!!
            images = res.state.images
            allImages = res.state.allImages
            stack = res.updatedStack
            assertNull(res.evictedOldest)
        }
        assertEquals(PendingDeleteLogic.MAX_STACK_SIZE, stack.size)

        val overflow = PendingDeleteLogic.pushRemove(
            images = images,
            allImages = allImages,
            currentIndex = 0,
            related = emptyList(),
            sortByOrientation = false,
            currentStack = stack,
        )!!
        assertEquals(PendingDeleteLogic.MAX_STACK_SIZE, overflow.updatedStack.size)
        assertEquals("p0.jpg", overflow.evictedOldest?.image?.fileName)
    }
}
