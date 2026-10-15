package com.phototok.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.phototok.domain.CollectionAction
import com.phototok.domain.FileTypeFilter
import com.phototok.domain.FirstRunHint
import com.phototok.domain.FolderScanInfo
import com.phototok.domain.SwipeAction
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsRepositoryTest {

    private lateinit var context: Context
    private lateinit var repository: SettingsRepository

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        repository = SettingsRepository(context)
    }

    @Test
    fun `default values match initial state`() = runTest {
        assertEquals("Selection", repository.selectionFolderName.first())
        assertTrue(repository.sortingEnabled.first())
        assertNull(repository.lastFolderUri.first())
        assertEquals(CollectionAction.DEFAULT, repository.phoneCollectionAction.first())
        assertNull(repository.phoneCollectionUri.first())
        assertEquals(SwipeAction.DEFAULT, repository.phoneLeftSwipeAction.first())
        assertNull(repository.phoneLeftSwipeUri.first())
        assertTrue(repository.phoneDirectDeleteConfirmEnabled.first())
        assertFalse(repository.phoneSortByOrientation.first())
        assertFalse(repository.phoneRandomizeOrder.first())
        assertEquals(0L, repository.phoneGestureTutorialTs.first())
        assertEquals(FileTypeFilter.DEFAULT, repository.phoneFileTypeFilter.first())
        assertFalse(repository.phoneShowExifOverlay.first())
        assertFalse(repository.phoneMoveRelatedFiles.first())
        assertTrue(repository.phoneRecentPathsEnabled.first())
        assertEquals(3, repository.phoneRecentPathsCount.first())
        assertTrue(repository.phoneRecentPaths.first().isEmpty())
        assertTrue(repository.phoneSeenFirstRunHints.first().isEmpty())
        assertEquals(0L, repository.lastAppUsedTs.first())
        assertFalse(repository.selectionUseSourceRoot.first())
    }

    @Test
    fun `setters update respective flows`() = runTest {
        repository.setSelectionFolderName("CustomSelection")
        assertEquals("CustomSelection", repository.selectionFolderName.first())

        repository.setSortingEnabled(false)
        assertFalse(repository.sortingEnabled.first())

        repository.setLastFolderUri("content://tree/last")
        assertEquals("content://tree/last", repository.lastFolderUri.first())
        repository.setLastFolderUri(null)
        assertNull(repository.lastFolderUri.first())

        repository.setPhoneCollectionAction(CollectionAction.MOVE)
        assertEquals(CollectionAction.MOVE, repository.phoneCollectionAction.first())

        repository.setPhoneCollectionUri("content://tree/coll")
        assertEquals("content://tree/coll", repository.phoneCollectionUri.first())
        repository.setPhoneCollectionUri(null)
        assertNull(repository.phoneCollectionUri.first())

        repository.setPhoneLeftSwipeAction(SwipeAction.COPY)
        assertEquals(SwipeAction.COPY, repository.phoneLeftSwipeAction.first())

        repository.setPhoneLeftSwipeUri("content://tree/left")
        assertEquals("content://tree/left", repository.phoneLeftSwipeUri.first())
        repository.setPhoneLeftSwipeUri(null)
        assertNull(repository.phoneLeftSwipeUri.first())

        repository.setPhoneDirectDeleteConfirmEnabled(false)
        assertFalse(repository.phoneDirectDeleteConfirmEnabled.first())

        repository.setPhoneSortByOrientation(true)
        assertTrue(repository.phoneSortByOrientation.first())

        repository.setPhoneRandomizeOrder(true)
        assertTrue(repository.phoneRandomizeOrder.first())

        repository.setPhoneGestureTutorialTs(123456789L)
        assertEquals(123456789L, repository.phoneGestureTutorialTs.first())

        repository.setPhoneFileTypeFilter(FileTypeFilter.RAW)
        assertEquals(FileTypeFilter.RAW, repository.phoneFileTypeFilter.first())

        repository.setPhoneShowExifOverlay(true)
        assertTrue(repository.phoneShowExifOverlay.first())

        repository.setPhoneMoveRelatedFiles(true)
        assertTrue(repository.phoneMoveRelatedFiles.first())

        repository.setPhoneRecentPathsEnabled(false)
        assertFalse(repository.phoneRecentPathsEnabled.first())

        repository.setPhoneRecentPathsCount(7)
        assertEquals(7, repository.phoneRecentPathsCount.first())

        repository.recordAppUsed(555555L)
        assertEquals(555555L, repository.lastAppUsedTs.first())

        repository.setSelectionUseSourceRoot(true)
        assertTrue(repository.selectionUseSourceRoot.first())
    }

    @Test
    fun `first run hints mark seen and reset`() = runTest {
        repository.markFirstRunHintSeen(FirstRunHint.SWIPE_RIGHT)
        repository.markFirstRunHintSeen(FirstRunHint.TAP_HUD)

        val seen = repository.phoneSeenFirstRunHints.first()
        assertTrue(seen.contains(FirstRunHint.SWIPE_RIGHT.key))
        assertTrue(seen.contains(FirstRunHint.TAP_HUD.key))

        repository.setPhoneGestureTutorialTs(99999L)

        repository.resetFirstRunHints()

        assertTrue(repository.phoneSeenFirstRunHints.first().isEmpty())
        assertEquals(0L, repository.phoneGestureTutorialTs.first())
    }

    // Positions are no longer stored (the feed always opens on the newest photo), but older
    // installs still hold the keys. They must be removed, not left to grow.
    private val retiredPositionKeys = { uri: String ->
        listOf(
            androidx.datastore.preferences.core.intPreferencesKey("folder_pos_v2_$uri"),
            androidx.datastore.preferences.core.intPreferencesKey("folder_pos_${uri.hashCode()}"),
        )
    }

    private suspend fun writeRetiredPosition(uri: String, index: Int) {
        context.dataStore.edit { prefs -> retiredPositionKeys(uri).forEach { prefs[it] = index } }
    }

    private suspend fun hasRetiredPosition(uri: String): Boolean {
        val prefs = context.dataStore.data.first()
        return retiredPositionKeys(uri).any { prefs[it] != null }
    }

    @Test
    fun `clearFolderPositions deletes retired folder position keys`() = runTest {
        writeRetiredPosition("content://tree/a", 5)
        writeRetiredPosition("content://tree/b", 10)
        assertTrue(hasRetiredPosition("content://tree/a"))

        repository.clearFolderPositions()

        assertFalse(hasRetiredPosition("content://tree/a"))
        assertFalse(hasRetiredPosition("content://tree/b"))
    }

    @Test
    fun `addRecentPath moves to front and evicts oldest with position cleanup`() = runTest {
        val baseUri = "content://tree/folder"

        // Add 10 folders (the MAX_STORED limit) and record positions
        for (i in 1..10) {
            val uri = "${baseUri}$i"
            repository.addRecentPath(uri, "Folder $i")
            writeRetiredPosition(uri, i * 10)
        }

        val recents = repository.phoneRecentPaths.first()
        assertEquals(10, recents.size)
        assertEquals("${baseUri}10", recents.first().uri)
        assertTrue(hasRetiredPosition("${baseUri}10"))
        assertTrue(hasRetiredPosition("${baseUri}1"))

        // Adding an 11th folder must evict the 1st folder and prune its position
        repository.addRecentPath("${baseUri}11", "Folder 11")

        val updatedRecents = repository.phoneRecentPaths.first()
        assertEquals(10, updatedRecents.size)
        assertEquals("${baseUri}11", updatedRecents.first().uri)
        assertTrue(updatedRecents.none { it.uri == "${baseUri}1" })

        // The evicted folder's retired position keys are cleaned up; kept folders are untouched.
        assertFalse(hasRetiredPosition("${baseUri}1"))
        assertTrue(hasRetiredPosition("${baseUri}10"))
    }

    @Test
    fun `folder scan info stores and retrieves by URI`() = runTest {
        val uri = "content://com.android.externalstorage.documents/tree/SD%3ADCIM"
        assertEquals(FolderScanInfo(0L, 0), repository.getFolderScanInfo(uri))

        repository.setFolderScanInfo(uri, FolderScanInfo(maxLastModified = 1700000000L, fileCount = 42))
        val retrieved = repository.getFolderScanInfo(uri)
        assertEquals(1700000000L, retrieved.maxLastModified)
        assertEquals(42, retrieved.fileCount)
    }

    @Test
    fun `clearFolderPositions deletes scan info alongside positions`() = runTest {
        val uri = "content://com.android.externalstorage.documents/tree/SD%3ADCIM"
        writeRetiredPosition(uri, 5)
        repository.setFolderScanInfo(uri, FolderScanInfo(12345L, 10))

        repository.clearFolderPositions()

        assertFalse(hasRetiredPosition(uri))
        assertEquals(FolderScanInfo(0L, 0), repository.getFolderScanInfo(uri))
    }
}
