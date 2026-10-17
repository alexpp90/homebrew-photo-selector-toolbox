package com.phototok.domain

import com.phototok.data.model.ImageItem
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SeenStoreTest {

    private fun img(
        name: String,
        size: Long = 1024L,
        modified: Long = 1000L,
        uriPrefix: String = "content://com.android.externalstorage.documents/tree/primary%3ADCIM/document/primary%3ADCIM%2F",
    ) = ImageItem(
        uri = "$uriPrefix$name",
        fileName = name,
        fileSize = size,
        lastModified = modified,
        mimeType = "image/jpeg",
    )

    @Test
    fun `fingerprint is deterministic and sensitive to documentKey, fileSize, and lastModified`() {
        val f1 = SeenStore.fingerprint("primary:DCIM/IMG_001.JPG", 1024L, 5000L)
        val f2 = SeenStore.fingerprint("primary:DCIM/IMG_001.JPG", 1024L, 5000L)
        val diffSize = SeenStore.fingerprint("primary:DCIM/IMG_001.JPG", 2048L, 5000L)
        val diffTime = SeenStore.fingerprint("primary:DCIM/IMG_001.JPG", 1024L, 5001L)
        val diffKey = SeenStore.fingerprint("primary:DCIM/IMG_002.JPG", 1024L, 5000L)

        assertEquals(f1, f2)
        assertNotEquals(f1, diffSize)
        assertNotEquals(f1, diffTime)
        assertNotEquals(f1, diffKey)
    }

    @Test
    fun `markSeen persists to log file, survives reload, and compacts when exceeding threshold`() {
        val tempDir = Files.createTempDirectory("seen_store_test").toFile()
        try {
            val logFile = java.io.File(tempDir, "seen_log.txt")
            val store = SeenStore(logFile = logFile, maxEntriesBeforeCompact = 3)
            val sourceA = "content://tree/sourceA"
            val sourceB = "content://tree/sourceB"

            val p1 = img("p1.jpg")
            val p2 = img("p2.jpg")
            val p3 = img("p3.jpg")
            val p4 = img("p4.jpg")

            assertTrue(store.markSeen(p1, sourceA))
            assertFalse(store.markSeen(p1, sourceA)) // duplicate returns false
            assertTrue(store.markSeen(p2, sourceA))
            assertTrue(store.markSeen(p3, sourceB))
            assertTrue(store.markSeen(p4, sourceB)) // triggers compaction

            assertEquals(2, store.countSeen(listOf(p1, p2), sourceA))
            assertEquals(setOf(p1.uri, p2.uri), store.filterSeenUris(listOf(p1, p2), sourceA))

            // Reload from disk
            val reloaded = SeenStore(logFile = logFile)
            assertTrue(reloaded.isSeen(p1, sourceA))
            assertTrue(reloaded.isSeen(p2, sourceA))
            assertTrue(reloaded.isSeen(p3, sourceB))
            assertTrue(reloaded.isSeen(p4, sourceB))

            // Reset sourceA only
            reloaded.resetSource(sourceA)
            assertFalse(reloaded.isSeen(p1, sourceA))
            assertTrue(reloaded.isSeen(p3, sourceB))

            // Reset all
            reloaded.resetAll()
            assertFalse(reloaded.isSeen(p3, sourceB))
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
