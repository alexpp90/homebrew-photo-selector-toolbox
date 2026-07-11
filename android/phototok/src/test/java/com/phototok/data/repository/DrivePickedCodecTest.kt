package com.phototok.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DrivePickedCodecTest {

    private fun selection(key: String, name: String = "Drive photos", vararg ids: String) =
        DrivePickedSelection(key = key, name = name, fileIds = ids.toList())

    @Test
    fun `encode-decode round trip preserves selections and id order`() {
        val original = listOf(
            selection("100", "Drive photos (2)", "idA", "idB"),
            selection("200", "Drive photos (1)", "idC"),
        )
        val decoded = DrivePickedCodec.decode(DrivePickedCodec.encode(original))
        assertEquals(original, decoded)
    }

    @Test
    fun `decode of null or empty yields empty list`() {
        assertTrue(DrivePickedCodec.decode(null).isEmpty())
        assertTrue(DrivePickedCodec.decode("").isEmpty())
    }

    @Test
    fun `decode skips malformed entries`() {
        val valid = selection("100", "ok", "idA")
        val raw = DrivePickedCodec.encode(listOf(valid)) + "\u001E" + "garbage-without-fields"
        assertEquals(listOf(valid), DrivePickedCodec.decode(raw))
    }

    @Test
    fun `selection with empty id list survives round trip`() {
        val original = listOf(DrivePickedSelection("100", "empty", emptyList()))
        assertEquals(original, DrivePickedCodec.decode(DrivePickedCodec.encode(original)))
    }

    @Test
    fun `add puts newest first, replaces same key and caps the list`() {
        var current = emptyList<DrivePickedSelection>()
        for (i in 1..12) {
            current = DrivePickedCodec.add(current, selection("$i", "s$i", "id$i"))
        }
        assertEquals(DrivePickedCodec.MAX_STORED, current.size)
        assertEquals("12", current.first().key)
        assertNull(current.find { it.key == "1" }) // evicted by cap

        // Re-adding an existing key replaces it and moves it to the front.
        current = DrivePickedCodec.add(current, selection("5", "updated", "newId"))
        assertEquals(DrivePickedCodec.MAX_STORED, current.size)
        assertEquals("5", current.first().key)
        assertEquals(listOf("newId"), current.first().fileIds)
    }

    @Test
    fun `find returns matching selection or null`() {
        val list = listOf(selection("100", "a", "idA"), selection("200", "b", "idB"))
        assertEquals("b", DrivePickedCodec.find(list, "200")?.name)
        assertNull(DrivePickedCodec.find(list, "300"))
    }
}
