package com.phototok.data.repository

import com.phototok.data.model.RecentPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentPathCodecTest {

    @Test
    fun `encode then decode round-trips entries and order`() {
        val paths = listOf(
            RecentPath("content://tree/a", "Alpha"),
            RecentPath("content://tree/b", "Beta"),
        )

        val decoded = RecentPathCodec.decode(RecentPathCodec.encode(paths))

        assertEquals(paths, decoded)
    }

    @Test
    fun `decode of null or empty yields empty list`() {
        assertTrue(RecentPathCodec.decode(null).isEmpty())
        assertTrue(RecentPathCodec.decode("").isEmpty())
    }

    @Test
    fun `add moves existing uri to front without duplicating`() {
        val start = listOf(
            RecentPath("uri-a", "A"),
            RecentPath("uri-b", "B"),
            RecentPath("uri-c", "C"),
        )

        val result = RecentPathCodec.add(start, "uri-c", "C-renamed")

        assertEquals(
            listOf(
                RecentPath("uri-c", "C-renamed"),
                RecentPath("uri-a", "A"),
                RecentPath("uri-b", "B"),
            ),
            result,
        )
    }

    @Test
    fun `add prepends new uri`() {
        val result = RecentPathCodec.add(listOf(RecentPath("uri-a", "A")), "uri-b", "B")
        assertEquals(listOf(RecentPath("uri-b", "B"), RecentPath("uri-a", "A")), result)
    }

    @Test
    fun `add caps at the maximum, dropping the oldest`() {
        val start = (1..RecentPathCodec.MAX_STORED).map { RecentPath("uri-$it", "N$it") }

        val result = RecentPathCodec.add(start, "uri-new", "New")

        assertEquals(RecentPathCodec.MAX_STORED, result.size)
        assertEquals("uri-new", result.first().uri)
        // The previously-oldest entry ("uri-10") should have been dropped.
        assertTrue(result.none { it.uri == "uri-${RecentPathCodec.MAX_STORED}" })
    }

    @Test
    fun `names containing spaces survive round-trip`() {
        val paths = listOf(RecentPath("content://tree/x", "Holiday 2026 Photos"))
        assertEquals(paths, RecentPathCodec.decode(RecentPathCodec.encode(paths)))
    }

    @Test
    fun `evictedUris returns uris dropped by the cap`() {
        val before = (1..RecentPathCodec.MAX_STORED).map { RecentPath("uri-$it", "N$it") }
        val after = RecentPathCodec.add(before, "uri-new", "New")

        assertEquals(
            listOf("uri-${RecentPathCodec.MAX_STORED}"),
            RecentPathCodec.evictedUris(before, after),
        )
    }

    @Test
    fun `evictedUris is empty when nothing was dropped`() {
        val before = listOf(RecentPath("uri-a", "A"))
        val after = RecentPathCodec.add(before, "uri-b", "B")

        assertTrue(RecentPathCodec.evictedUris(before, after).isEmpty())
    }

    @Test
    fun `evictedUris ignores re-added uri`() {
        val before = listOf(RecentPath("uri-a", "A"), RecentPath("uri-b", "B"))
        val after = RecentPathCodec.add(before, "uri-a", "A2")

        assertTrue(RecentPathCodec.evictedUris(before, after).isEmpty())
    }
}
