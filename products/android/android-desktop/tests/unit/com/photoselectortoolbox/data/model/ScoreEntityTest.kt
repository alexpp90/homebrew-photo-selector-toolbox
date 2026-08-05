package com.photoselectortoolbox.data.model

import com.photoselectortoolbox.data.cache.ScoreEntity
import org.junit.Assert.*
import org.junit.Test

class ScoreEntityTest {

    @Test
    fun `required fields stored correctly`() {
        val entity = ScoreEntity(
            filePath = "content://media/1",
            fileSize = 2048L,
            lastModified = 1700000000000L
        )
        assertEquals("content://media/1", entity.filePath)
        assertEquals(2048L, entity.fileSize)
        assertEquals(1700000000000L, entity.lastModified)
    }

    @Test
    fun `optional scores default to null`() {
        val entity = ScoreEntity("path", 100, 200)
        assertNull(entity.sharpnessScore)
        assertNull(entity.noiseLevel)
        assertNull(entity.highlightClipping)
        assertNull(entity.shadowClipping)
    }

    @Test
    fun `all scores populated`() {
        val entity = ScoreEntity(
            filePath = "path",
            fileSize = 100,
            lastModified = 200,
            sharpnessScore = 55.5,
            noiseLevel = 3.2,
            highlightClipping = 0.1,
            shadowClipping = 2.3
        )
        assertEquals(55.5, entity.sharpnessScore!!, 1e-9)
        assertEquals(3.2, entity.noiseLevel!!, 1e-9)
        assertEquals(0.1, entity.highlightClipping!!, 1e-9)
        assertEquals(2.3, entity.shadowClipping!!, 1e-9)
    }

    @Test
    fun `lastAccessTime has default`() {
        val before = System.currentTimeMillis()
        val entity = ScoreEntity("path", 100, 200)
        val after = System.currentTimeMillis()
        assertTrue(entity.lastAccessTime in before..after)
    }
}
