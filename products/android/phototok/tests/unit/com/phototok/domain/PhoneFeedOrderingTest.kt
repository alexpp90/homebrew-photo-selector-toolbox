package com.phototok.domain

import com.phototok.data.model.ImageItem
import org.junit.Assert.assertEquals
import org.junit.Test

class PhoneFeedOrderingTest {

    private fun img(
        name: String,
        date: Long,
        width: Int = 0,
        height: Int = 0,
    ) = ImageItem(
        uri = "content://photos/$name",
        fileName = name,
        fileSize = 1L,
        lastModified = date,
        mimeType = "image/jpeg",
        imageWidth = width,
        imageHeight = height,
    )

    @Test
    fun `default order is by date newest first`() {
        val input = listOf(
            img("old.jpg", date = 100),
            img("new.jpg", date = 300),
            img("mid.jpg", date = 200),
        )

        val result = PhoneFeedOrdering.order(input, randomize = false, sortByOrientation = false)

        assertEquals(listOf("new.jpg", "mid.jpg", "old.jpg"), result.images.map { it.fileName })
        assertEquals(-1, result.portraitSectionStart)
    }

    @Test
    fun `orientation on groups landscape first then portrait each by date desc`() {
        val input = listOf(
            img("p_old.jpg", date = 100, width = 3000, height = 4000), // portrait
            img("l_new.jpg", date = 400, width = 4000, height = 3000), // landscape
            img("p_new.jpg", date = 300, width = 3000, height = 4000), // portrait
            img("l_old.jpg", date = 200, width = 4000, height = 3000), // landscape
        )

        val result = PhoneFeedOrdering.order(input, randomize = false, sortByOrientation = true)

        // Landscape group (date desc), then portrait group (date desc)
        assertEquals(
            listOf("l_new.jpg", "l_old.jpg", "p_new.jpg", "p_old.jpg"),
            result.images.map { it.fileName },
        )
        // Portrait section starts after the 2 landscape images
        assertEquals(2, result.portraitSectionStart)
    }

    @Test
    fun `orientation on with no portraits yields no split`() {
        val input = listOf(
            img("l1.jpg", date = 100, width = 4000, height = 3000),
            img("l2.jpg", date = 200, width = 4000, height = 3000),
        )

        val result = PhoneFeedOrdering.order(input, randomize = false, sortByOrientation = true)

        assertEquals(listOf("l2.jpg", "l1.jpg"), result.images.map { it.fileName })
        assertEquals(-1, result.portraitSectionStart)
    }

    @Test
    fun `randomize uses injected shuffler and ignores date and orientation`() {
        val input = listOf(
            img("a.jpg", date = 100),
            img("b.jpg", date = 200),
            img("c.jpg", date = 300),
        )

        // Deterministic "shuffle" = reverse, so we can assert exactly.
        val result = PhoneFeedOrdering.order(
            input,
            randomize = true,
            sortByOrientation = true,
            shuffler = { it.reversed() },
        )

        assertEquals(listOf("c.jpg", "b.jpg", "a.jpg"), result.images.map { it.fileName })
        assertEquals(-1, result.portraitSectionStart)
    }

    @Test
    fun `empty input is handled`() {
        val result = PhoneFeedOrdering.order(emptyList(), randomize = false, sortByOrientation = true)
        assertEquals(emptyList<String>(), result.images.map { it.fileName })
        assertEquals(-1, result.portraitSectionStart)
    }

    // ── Progressive discovery ────────────────────────────────────────────

    @Test
    fun `newItems returns everything when nothing is known yet`() {
        val incoming = listOf(img("a.jpg", 1), img("b.jpg", 2))

        val fresh = PhoneFeedOrdering.newItems(incoming, emptySet())

        assertEquals(listOf("a.jpg", "b.jpg"), fresh.map { it.fileName })
    }

    @Test
    fun `newItems filters out URIs already published`() {
        val a = img("a.jpg", 1)
        val b = img("b.jpg", 2)
        val c = img("c.jpg", 3)

        val fresh = PhoneFeedOrdering.newItems(listOf(a, b, c), setOf(a.uri, b.uri))

        assertEquals(listOf("c.jpg"), fresh.map { it.fileName })
    }

    @Test
    fun `newItems treats removed-but-published items as known`() {
        // The user moved a.jpg out of the feed while discovery was still running;
        // the next cumulative batch must not resurrect it.
        val a = img("a.jpg", 1)
        val b = img("b.jpg", 2)

        val fresh = PhoneFeedOrdering.newItems(listOf(a, b), knownUris = setOf(a.uri, b.uri))

        assertEquals(emptyList<String>(), fresh.map { it.fileName })
    }

    @Test
    fun `appendBatch on an empty feed behaves exactly like order`() {
        val fresh = listOf(img("old.jpg", 100), img("new.jpg", 300))

        val appended = PhoneFeedOrdering.appendBatch(
            current = emptyList(),
            fresh = fresh,
            randomize = false,
            sortByOrientation = false,
        )
        val ordered = PhoneFeedOrdering.order(fresh, randomize = false, sortByOrientation = false)

        assertEquals(ordered.images.map { it.fileName }, appended.images.map { it.fileName })
    }

    @Test
    fun `appendBatch never reorders photos the user has already seen`() {
        // First batch is old photos; the second batch contains newer ones. A plain
        // re-sort would jump them in front of the user mid-swipe — append must not.
        val current = PhoneFeedOrdering.order(
            listOf(img("b1_old.jpg", 100), img("b1_mid.jpg", 200)),
            randomize = false,
            sortByOrientation = false,
        ).images
        val fresh = listOf(img("b2_newest.jpg", 900), img("b2_newer.jpg", 800))

        val result = PhoneFeedOrdering.appendBatch(
            current = current,
            fresh = fresh,
            randomize = false,
            sortByOrientation = false,
        )

        assertEquals(
            listOf("b1_mid.jpg", "b1_old.jpg", "b2_newest.jpg", "b2_newer.jpg"),
            result.images.map { it.fileName },
        )
    }

    @Test
    fun `appendBatch shuffles only the incoming batch when randomizing`() {
        val current = listOf(img("seen1.jpg", 100), img("seen2.jpg", 200))
        val fresh = listOf(img("f1.jpg", 300), img("f2.jpg", 400), img("f3.jpg", 500))

        val result = PhoneFeedOrdering.appendBatch(
            current = current,
            fresh = fresh,
            randomize = true,
            sortByOrientation = false,
            shuffler = { it.reversed() },
        )

        assertEquals(
            listOf("seen1.jpg", "seen2.jpg", "f3.jpg", "f2.jpg", "f1.jpg"),
            result.images.map { it.fileName },
        )
    }

    @Test
    fun `appendBatch with nothing new leaves the feed untouched`() {
        val current = listOf(img("a.jpg", 100), img("b.jpg", 200))

        val result = PhoneFeedOrdering.appendBatch(
            current = current,
            fresh = emptyList(),
            randomize = false,
            sortByOrientation = false,
        )

        assertEquals(current.map { it.fileName }, result.images.map { it.fileName })
    }

    @Test
    fun `appendBatch recomputes the portrait split over the merged feed`() {
        val current = listOf(
            img("l1.jpg", 400, width = 4000, height = 3000),
            img("l2.jpg", 300, width = 4000, height = 3000),
        )
        val fresh = listOf(img("p1.jpg", 200, width = 3000, height = 4000))

        val result = PhoneFeedOrdering.appendBatch(
            current = current,
            fresh = fresh,
            randomize = false,
            sortByOrientation = true,
        )

        assertEquals(3, result.images.size)
        assertEquals(2, result.portraitSectionStart)
    }
}
