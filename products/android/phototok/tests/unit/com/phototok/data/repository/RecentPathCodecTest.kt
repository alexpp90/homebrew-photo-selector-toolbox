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

    @Test
    fun `encode and decode preserves SourceKind and volumeUuid`() {
        val paths = listOf(
            RecentPath(
                uri = "content://com.android.externalstorage.documents/tree/1A2B-3C4D%3ADCIM",
                name = "SD Card",
                kind = com.photoselector.core.storage.SourceKind.REMOVABLE,
                volumeUuid = "1A2B-3C4D",
            ),
            RecentPath(
                uri = "content://com.google.android.apps.docs.storage/tree/drive",
                name = "Drive",
                kind = com.photoselector.core.storage.SourceKind.CLOUD_OTHER,
                volumeUuid = null,
            ),
        )

        val decoded = RecentPathCodec.decode(RecentPathCodec.encode(paths))
        assertEquals(paths, decoded)
    }

    @Test
    fun `legacy 2-field entry decodes with default PHONE_LIBRARY and null volumeUuid`() {
        val legacyEncoded = "content://tree/dcim\u001FCamera"
        val decoded = RecentPathCodec.decode(legacyEncoded)

        assertEquals(1, decoded.size)
        assertEquals("content://tree/dcim", decoded[0].uri)
        assertEquals("Camera", decoded[0].name)
        assertEquals(com.photoselector.core.storage.SourceKind.PHONE_LIBRARY, decoded[0].kind)
        assertEquals(null, decoded[0].volumeUuid)
    }
}
