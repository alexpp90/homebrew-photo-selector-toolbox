package com.photoselectortoolbox.data.repository

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
 * Unit tests for [FolderAccessManager] and its integration with [SettingsRepository]
 * (`WP-07` / `F-09`).
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
        assertEquals(1, backend.takenCalls.size)
        val expectedFlags =
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        assertEquals(uri.toString() to expectedFlags, backend.takenCalls.single())
    }

    @Test
    fun `releaseIfUnreferenced keeps grant while another purpose still references URI`() {
        val sharedUri = "content://tree/shared"
        val backend = FakeSafGrantBackend(initialPersisted = setOf(sharedUri))
        val manager = FolderAccessManager(backend)

        manager.syncReferences(
            mapOf(
                FolderAccessPurpose.SOURCE to setOf("content://tree/new_source"),
                FolderAccessPurpose.SELECTION_TARGET to setOf(sharedUri),
            )
        )

        val released = manager.releaseIfUnreferenced(sharedUri)

        assertFalse("Grant must be retained while SELECTION_TARGET still references it", released)
        assertTrue(backend.releasedCalls.isEmpty())
        assertTrue(sharedUri in backend.persistedUris)
    }

    @Test
    fun `releaseIfUnreferenced releases grant once last purpose drops URI`() {
        val oldUri = "content://tree/old_source"
        val backend = FakeSafGrantBackend(initialPersisted = setOf(oldUri))
        val manager = FolderAccessManager(backend)

        manager.syncReferences(
            mapOf(
                FolderAccessPurpose.SOURCE to setOf("content://tree/new_source"),
                FolderAccessPurpose.SELECTION_TARGET to emptySet(),
            )
        )

        val released = manager.releaseIfUnreferenced(oldUri)

        assertTrue("Grant must be released once no purpose references it", released)
        assertEquals(1, backend.releasedCalls.size)
        assertEquals(oldUri, backend.releasedCalls.single().first)
        assertFalse(oldUri in backend.persistedUris)
    }

    @Test
    fun `reconcilePersistedGrants releases orphaned OS grants and retains referenced ones`() {
        val activeSource = "content://tree/source"
        val activeSelection = "content://tree/selection"
        val orphan1 = "content://tree/orphan_1"
        val orphan2 = "content://tree/orphan_2"

        val backend = FakeSafGrantBackend(
            initialPersisted = setOf(activeSource, activeSelection, orphan1, orphan2),
        )
        val manager = FolderAccessManager(backend)

        val released = manager.reconcilePersistedGrants(
            mapOf(
                FolderAccessPurpose.SOURCE to setOf(activeSource),
                FolderAccessPurpose.SELECTION_TARGET to setOf(activeSelection),
            )
        )

        assertEquals(setOf(orphan1, orphan2), released)
        assertEquals(setOf(activeSource, activeSelection), backend.persistedUris)
    }

    @Test
    fun `SettingsRepository releases previous grant on folder change and reconciles on startup`() = runTest {
        val orphan = "content://tree/orphan"
        val folderA = "content://tree/folder_a"
        val folderB = "content://tree/folder_b"
        val backend = FakeSafGrantBackend(initialPersisted = setOf(orphan))
        val manager = FolderAccessManager(backend)
        val settings = SettingsRepository(context, manager)

        // Reset any previous DataStore state
        settings.setLastFolderUri(null)
        settings.setPhoneCollectionUri(null)
        backend.releasedCalls.clear()
        backend.takenCalls.clear()
        backend.persistedUris.clear()
        backend.persistedUris += orphan

        // Set folderA as both SOURCE and SELECTION_TARGET
        settings.setLastFolderUri(folderA)
        settings.setPhoneCollectionUri(folderA)
        assertTrue(folderA in backend.persistedUris)
        assertTrue(backend.releasedCalls.isEmpty())

        // Switch SOURCE to folderB — folderA must NOT be released yet because SELECTION_TARGET still uses it
        settings.setLastFolderUri(folderB)
        assertFalse(backend.releasedCalls.any { it.first == folderA })
        assertTrue(folderA in backend.persistedUris)
        assertTrue(folderB in backend.persistedUris)

        // Clear SELECTION_TARGET — now folderA is unreferenced and must be released
        settings.setPhoneCollectionUri(null)
        assertTrue(backend.releasedCalls.any { it.first == folderA })
        assertFalse(folderA in backend.persistedUris)

        // Startup reconciliation releases orphan while keeping folderB
        val releasedOnStartup = settings.reconcilePersistedGrants()
        assertEquals(setOf(orphan), releasedOnStartup)
        assertEquals(setOf(folderB), backend.persistedUris)
    }
}
