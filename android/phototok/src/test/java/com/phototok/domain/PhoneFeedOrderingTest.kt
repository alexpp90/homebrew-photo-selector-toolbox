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
}
