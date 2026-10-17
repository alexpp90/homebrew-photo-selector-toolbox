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

    // ── B-8 / D-10: undo history and the pending-delete journal keep a grant alive ──

    private val tree = "content://com.android.externalstorage.documents/tree/primary%3ADCIM"
    private val document = "$tree/document/primary%3ADCIM%2FIMG_0001.JPG"

    @Test
    fun `a pending-delete journal entry inside the tree keeps the tree grant`() {
        val backend = FakeSafGrantBackend(initialPersisted = setOf(tree))
        val manager = FolderAccessManager(backend)
        manager.syncReferences(
            mapOf(
                FolderAccessPurpose.SOURCE to setOf("content://other/tree/new"),
                FolderAccessPurpose.PENDING_DELETE_JOURNAL to setOf(document),
            )
        )

        assertFalse(manager.releaseIfUnreferenced(tree))

        assertTrue(backend.releasedCalls.isEmpty())
    }

    @Test
    fun `the grant is released once the journal no longer references the tree`() {
        val backend = FakeSafGrantBackend(initialPersisted = setOf(tree))
        val manager = FolderAccessManager(backend)
        manager.syncReferences(mapOf(FolderAccessPurpose.PENDING_DELETE_JOURNAL to setOf(document)))
        assertFalse(manager.releaseIfUnreferenced(tree))

        manager.syncReferences(emptyMap())

        assertTrue(manager.releaseIfUnreferenced(tree))
        assertEquals(listOf(tree), backend.releasedCalls.map { it.first })
    }

    @Test
    fun `a folder pinned by the undo history keeps its grant across reference syncs`() {
        val backend = FakeSafGrantBackend(initialPersisted = setOf(tree))
        val manager = FolderAccessManager(backend)
        manager.pin(FolderAccessPurpose.UNDO_HISTORY, setOf(tree))

        manager.syncReferences(mapOf(FolderAccessPurpose.SOURCE to setOf("content://other/tree/new")))

        assertFalse(manager.releaseIfUnreferenced(tree))
        manager.pin(FolderAccessPurpose.UNDO_HISTORY, emptySet())
        assertTrue(manager.releaseIfUnreferenced(tree))
    }

    @Test
    fun `uris are compared normalized, not as raw strings`() {
        val backend = FakeSafGrantBackend(initialPersisted = setOf(tree))
        val manager = FolderAccessManager(backend)
        // Same folder, different spelling: lower-case percent escape and a trailing slash.
        manager.syncReferences(
            mapOf(FolderAccessPurpose.SELECTION_TARGET to setOf("content://com.android.externalstorage.documents/tree/primary%3adcim/".replace("dcim", "DCIM")))
        )

        assertFalse(manager.releaseIfUnreferenced(tree))
    }

    @Test
    fun `reconcile keeps a grant that only the journal references`() {
        val backend = FakeSafGrantBackend(initialPersisted = setOf(tree, "content://orphan/tree/x"))
        val manager = FolderAccessManager(backend)

        val released = manager.reconcilePersistedGrants(
            mapOf(FolderAccessPurpose.PENDING_DELETE_JOURNAL to setOf(document))
        )

        assertEquals(setOf("content://orphan/tree/x"), released)
    }

    @Test
    fun `switching folders with a pending delete keeps the old grant until it is committed`() = runTest {
        val folderA = tree
        val folderB = "content://com.android.externalstorage.documents/tree/primary%3APictures"
        val backend = FakeSafGrantBackend()
        val manager = FolderAccessManager(backend)
        val settings = SettingsRepository(context, manager)
        settings.setLastFolderUri(null)
        settings.setPendingDeleteJournal(emptySet())
        backend.releasedCalls.clear()
        backend.persistedUris.clear()

        settings.setLastFolderUri(folderA)
        settings.addPendingDeletes(listOf(document))
        settings.setLastFolderUri(folderB)

        assertTrue("the journal still needs folder A", folderA in backend.persistedUris)
        assertFalse(backend.releasedCalls.any { it.first == folderA })

        settings.removePendingDeletes(listOf(document))
        settings.releaseFolderGrantIfUnreferenced(folderA)

        assertTrue(backend.releasedCalls.any { it.first == folderA })
        settings.setLastFolderUri(null)
    }
}
