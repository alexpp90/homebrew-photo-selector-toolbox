package com.photoselectortoolbox.data.model

import org.junit.Assert.*
import org.junit.Test

class DuplicateGroupTest {

    @Test
    fun `stores hash and file list`() {
        val group = DuplicateGroup(
            hash = "abc123",
            files = listOf("file1.jpg", "file2.jpg", "file3.jpg")
        )
        assertEquals("abc123", group.hash)
        assertEquals(3, group.files.size)
        assertEquals("file1.jpg", group.files[0])
    }

    @Test
    fun `data class equality`() {
        val a = DuplicateGroup("hash1", listOf("a", "b"))
        val b = DuplicateGroup("hash1", listOf("a", "b"))
        assertEquals(a, b)
    }

    @Test
    fun `empty file list is valid`() {
        val group = DuplicateGroup(hash = "empty", files = emptyList())
        assertTrue(group.files.isEmpty())
    }
}
