package com.phototok.data.repository

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FavouritesRepositoryTest {

    @Test
    fun `toggleFavourite marks primary and companions, queues system sync, and persists to disk`() {
        val tempDir = Files.createTempDirectory("favs_test").toFile()
        try {
            val storeFile = java.io.File(tempDir, "favourites_store.json")
            val repo = FavouritesRepository(FavouritesStore(storeFile = storeFile))

            val jpg = "content://photos/IMG_001.JPG"
            val raw = "content://photos/IMG_001.ARW"

            val nowFav = repo.toggleFavourite(
                uri = jpg,
                companionUris = listOf(raw),
                queueForSystemSync = true,
            )
            assertTrue(nowFav)
            assertTrue(repo.isFavourite(jpg))
            assertTrue(repo.isFavourite(raw))
            assertEquals(setOf(jpg, raw), repo.unsyncedSystemUris.value)

            // Reload from disk
            val reloaded = FavouritesRepository(FavouritesStore(storeFile = storeFile))
            assertEquals(setOf(jpg, raw), reloaded.favouritedUris.value)
            assertEquals(setOf(jpg, raw), reloaded.unsyncedSystemUris.value)

            reloaded.markSystemSynced(listOf(jpg, raw))
            assertTrue(reloaded.unsyncedSystemUris.value.isEmpty())

            // Toggle off
            val afterSecondToggle = reloaded.toggleFavourite(uri = jpg, companionUris = listOf(raw))
            assertFalse(afterSecondToggle)
            assertFalse(reloaded.isFavourite(jpg))
            assertFalse(reloaded.isFavourite(raw))
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
