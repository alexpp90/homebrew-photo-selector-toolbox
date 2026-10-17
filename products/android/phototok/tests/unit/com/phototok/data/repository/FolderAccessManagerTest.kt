package com.phototok.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Unit tests for PhotoTok [FolderAccessManager] and its integration with
 * [SettingsRepository] (`WP-07` / `F-09`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FolderAccessManagerTest {

    private class FakeSafGrantBackend(
        initialPersisted: Set<String> = emptySet(),
    ) : SafGrantBackend {
        val persistedUris = LinkedHashSet(initialPersisted)
        val takenCalls = mutableListOf<Pair<String, Int>>()
        val releasedCalls = mutableListOf<Pair<String, Int>>()

        override fun takePersistableGrant(uri: Uri, modeFlags: Int) {
            val s = uri.toString()
            takenCalls += s to modeFlags
            persistedUris += s
        }

        override fun releasePersistableGrant(uri: Uri, modeFlags: Int) {
            val s = uri.toString()
            releasedCalls += s to modeFlags
            persistedUris -= s
        }

        override fun persistedGrantUris(): Set<String> = persistedUris.toSet()
    }

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
    }

    @Test
    fun `takeGrant records purpose and requests read-write persistable grant`() {
        val backend = FakeSafGrantBackend()
        val manager = FolderAccessManager(backend)
        val uri = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3ADCIM")

        val granted = manager.takeGrant(uri, FolderAccessPurpose.SOURCE)

        assertTrue(granted)
        assertEquals(setOf(uri.toString()), manager.referencedUrisFor(FolderAccessPurpose.SOURCE))
        val expectedFlags =
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        assertEquals(uri.toString() to expectedFlags, backend.takenCalls.single())
    }

    @Test
    fun `reconcilePersistedGrants releases orphaned OS grants across all four purposes`() {
        val sourceUri = "content://tree/source"
        val selectionUri = "content://tree/selection"
        val leftSwipeUri = "content://tree/left_swipe"
        val recentUri = "content://tree/recent_1"
        val orphanUri = "content://tree/orphan_stale"

        val backend = FakeSafGrantBackend(
            initialPersisted = setOf(sourceUri, selectionUri, leftSwipeUri, recentUri, orphanUri),
        )
        val manager = FolderAccessManager(backend)

        val released = manager.reconcilePersistedGrants(
            mapOf(
                FolderAccessPurpose.SOURCE to setOf(sourceUri),
                FolderAccessPurpose.SELECTION_TARGET to setOf(selectionUri),
                FolderAccessPurpose.LEFT_SWIPE_TARGET to setOf(leftSwipeUri),
                FolderAccessPurpose.RECENT_FOLDER to setOf(recentUri),
            )
        )

        assertEquals(setOf(orphanUri), released)
        assertEquals(
            setOf(sourceUri, selectionUri, leftSwipeUri, recentUri),
            backend.persistedUris,
        )
    }

    @Test
    fun `evicting a recent folder releases its grant unless another purpose still references it`() = runTest {
        val backend = FakeSafGrantBackend()
        val manager = FolderAccessManager(backend)
        val settings = SettingsRepository(context, manager)

        settings.setLastFolderUri(null)
        settings.setPhoneCollectionUri(null)
        settings.setPhoneLeftSwipeUri(null)

        // Populate 10 recent folders (MAX_STORED_RECENT_PATHS = 10)
        for (i in 1..10) {
            val uri = "content://tree/recent_$i"
            manager.takeGrant(Uri.parse(uri), FolderAccessPurpose.SOURCE)
            settings.addRecentPath(uri, "Folder $i")
        }
        backend.releasedCalls.clear()

        // Add an 11th folder -> recent_1 is evicted and not referenced anywhere else -> released
        val uri11 = "content://tree/recent_11"
        manager.takeGrant(Uri.parse(uri11), FolderAccessPurpose.SOURCE)
        settings.addRecentPath(uri11, "Folder 11")

        assertTrue(
            "Evicted recent_1 grant must be released",
            backend.releasedCalls.any { it.first == "content://tree/recent_1" },
        )

        // Pin recent_2 as SELECTION_TARGET, then evict it by adding recent_12
        settings.setPhoneCollectionUri("content://tree/recent_2")
        backend.releasedCalls.clear()

        val uri12 = "content://tree/recent_12"
        manager.takeGrant(Uri.parse(uri12), FolderAccessPurpose.SOURCE)
        settings.addRecentPath(uri12, "Folder 12")

        assertFalse(
            "recent_2 must NOT be released on eviction while SELECTION_TARGET still references it",
            backend.releasedCalls.any { it.first == "content://tree/recent_2" },
        )

        // Once SELECTION_TARGET changes away from recent_2, recent_2 is released
        settings.setPhoneCollectionUri("content://tree/custom_collection")
        assertTrue(
            "recent_2 must be released once SELECTION_TARGET no longer references it",
            backend.releasedCalls.any { it.first == "content://tree/recent_2" },
        )
    }
}
