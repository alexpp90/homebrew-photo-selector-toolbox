package com.phototok.viewmodel

import android.net.Uri
import com.phototok.data.model.ImageItem
import com.phototok.data.model.PhoneSettings
import com.phototok.data.repository.ImageRepository
import com.phototok.data.repository.SettingsRepository
import com.phototok.data.source.ExternalStorageDetector
import com.phototok.domain.CollectionAction
import com.phototok.domain.FirstRunHint
import com.phototok.domain.SwipeAction
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PhoneModeViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val phoneSettingsFlow = MutableStateFlow(PhoneSettings())
    private val collectionUriFlow = MutableStateFlow<String?>(null)
    private val leftSwipeUriFlow = MutableStateFlow<String?>(null)
    private val lastFolderUriFlow = MutableStateFlow<String?>(null)
    private val sortingEnabledFlow = MutableStateFlow(true)
    // A recent timestamp so the gesture tutorial stays hidden during tests.
    private val gestureTutorialTsFlow = MutableStateFlow(System.currentTimeMillis())

    private val settingsRepository: SettingsRepository = mockk(relaxed = true) {
        every { phoneSettings } returns phoneSettingsFlow
        every { phoneCollectionUri } returns collectionUriFlow
        every { phoneLeftSwipeUri } returns leftSwipeUriFlow
        every { lastFolderUri } returns lastFolderUriFlow
        every { sortingEnabled } returns sortingEnabledFlow
        every { phoneGestureTutorialTs } returns gestureTutorialTsFlow
    }

    private val imageRepository: ImageRepository = mockk(relaxed = true) {
        coEvery { prepareSourceFolder(any()) } returns "Photos"
        coEvery { resolveFolderName(any()) } returns null
        coEvery { getExifData(any()) } returns null
        coEvery { getImageDimensions(any()) } returns Pair(0, 0)
    }

    private val externalStorageDetector: ExternalStorageDetector = mockk {
        every { detectRemovableVolumes() } returns emptyList()
    }

    private fun image(name: String, modified: Long) = ImageItem(
        uri = "content://photos/$name",
        fileName = name,
        fileSize = 1,
        lastModified = modified,
        mimeType = "image/jpeg",
        imageWidth = 100,
        imageHeight = 50,
    )

    private fun buildViewModel(): PhoneModeViewModel = PhoneModeViewModel(
        imageRepository = imageRepository,
        settingsRepository = settingsRepository,
        externalStorageDetector = externalStorageDetector,
        appScope = CoroutineScope(testDispatcher),
    )

    private fun loadFolder(vararg images: ImageItem): PhoneModeViewModel {
        every { imageRepository.discoverImages(any()) } returns flowOf(images.toList())
        coEvery { settingsRepository.getFolderLastPosition(any()) } returns 0
        val viewModel = buildViewModel()
        viewModel.selectSourceFolder(Uri.parse("content://tree/photos"))
        return viewModel
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `typed settings flow propagates into ui state`() = runTest {
        val viewModel = buildViewModel()

        phoneSettingsFlow.value = PhoneSettings(
            collectionAction = CollectionAction.MOVE,
            leftSwipeAction = SwipeAction.COPY,
            moveRelatedFiles = true,
            recentPathsCount = 5,
        )

        val state = viewModel.uiState.value
        assertEquals(CollectionAction.MOVE, state.collectionAction)
        assertEquals(SwipeAction.COPY, state.leftSwipeAction)
        assertTrue(state.moveRelatedFiles)
        assertEquals(5, state.recentPathsCount)
    }

    @Test
    fun `selectSourceFolder loads images and records the folder`() = runTest {
        val viewModel = loadFolder(image("a.jpg", 1), image("b.jpg", 2))

        val state = viewModel.uiState.value
        assertEquals(2, state.images.size)
        assertEquals("Photos", state.sourceFolderName)
        assertFalse(state.isLoading)
        assertNull(state.error)
        coVerify { settingsRepository.addRecentPath("content://tree/photos", "Photos") }
    }

    @Test
    fun `selectRecentPath re-opens the folder like a fresh selection`() = runTest {
        every { imageRepository.discoverImages(any()) } returns
            flowOf(listOf(image("IMG_001.JPG", 1)))
        coEvery { settingsRepository.getFolderLastPosition(any()) } returns 0
        val viewModel = buildViewModel()

        viewModel.selectRecentPath(
            com.phototok.data.model.RecentPath("content://tree/recent", "Recent")
        )

        val state = viewModel.uiState.value
        assertEquals("content://tree/recent", state.sourceFolderUri)
        assertEquals(1, state.images.size)
        coVerify { settingsRepository.addRecentPath("content://tree/recent", "Photos") }
    }

    @Test
    fun `selectSourceFolder reports inaccessible folders`() = runTest {
        coEvery { imageRepository.prepareSourceFolder(any()) } returns null
        val viewModel = buildViewModel()

        viewModel.selectSourceFolder(Uri.parse("content://tree/gone"))

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNotNull(state.error)
    }

    @Test
    fun `move to collection with partial failure keeps failed file and reports counts`() = runTest {
        phoneSettingsFlow.value = PhoneSettings(
            collectionAction = CollectionAction.MOVE,
            moveRelatedFiles = true,
        )
        // Sibling pair (same stem) plus one unrelated file.
        val jpg = image("IMG_001.jpg", 1)
        val arw = image("IMG_001.arw", 2)
        val other = image("IMG_002.jpg", 3)
        coEvery {
            imageRepository.moveImage(match { it.toString().endsWith(".jpg") }, any(), any(), any())
        } returns true
        coEvery {
            imageRepository.moveImage(match { it.toString().endsWith(".arw") }, any(), any(), any())
        } returns false
        val viewModel = loadFolder(jpg, arw, other)
        val current = viewModel.uiState.value.images[viewModel.uiState.value.currentIndex]
        // Only exercise the sibling-pair case (current must be part of the pair).
        if (!current.fileName.startsWith("IMG_001")) {
            viewModel.navigateToImage(
                viewModel.uiState.value.images.indexOfFirst { it.fileName.startsWith("IMG_001") }
            )
        }

        viewModel.addToCollection()

        val state = viewModel.uiState.value
        val feedback = state.lastActionFeedback
        assertNotNull(feedback)
        assertTrue(feedback!!.isError)
        assertEquals("Moved 1 of 2 to collection, 1 failed", feedback.message)
        // Only the successfully moved file left the feed.
        assertTrue(state.images.any { it.fileName == "IMG_001.arw" })
        assertFalse(state.images.any { it.fileName == "IMG_001.jpg" })
        assertTrue(state.images.any { it.fileName == "IMG_002.jpg" })
    }

    @Test
    fun `copy to collection reports success without removing files`() = runTest {
        coEvery { imageRepository.copyImage(any(), any(), any(), any()) } returns true
        val viewModel = loadFolder(image("a.jpg", 1), image("b.jpg", 2))

        viewModel.addToCollection()

        val state = viewModel.uiState.value
        assertEquals("Copied to collection", state.lastActionFeedback?.message)
        assertFalse(state.lastActionFeedback?.isError == true)
        assertEquals(2, state.images.size)
    }

    @Test
    fun `requestDelete is revertable and revert restores the feed`() = runTest {
        val viewModel = loadFolder(image("a.jpg", 1), image("b.jpg", 2))

        viewModel.requestDelete()
        assertNotNull(viewModel.uiState.value.pendingDelete)
        assertEquals(1, viewModel.uiState.value.images.size)

        viewModel.revertDelete()
        assertNull(viewModel.uiState.value.pendingDelete)
        assertEquals(2, viewModel.uiState.value.images.size)
        coVerify(exactly = 0) { imageRepository.deleteImage(any()) }
    }

    @Test
    fun `finalizePendingDelete deletes via the repository`() = runTest {
        coEvery { imageRepository.deleteImage(any()) } returns true
        val viewModel = loadFolder(image("a.jpg", 1), image("b.jpg", 2))

        viewModel.requestDelete()
        viewModel.finalizePendingDelete()

        assertNull(viewModel.uiState.value.pendingDelete)
        coVerify(exactly = 1) { imageRepository.deleteImage(any()) }
    }

    @Test
    fun `navigateToImage persists the position for the folder`() = runTest {
        val viewModel = loadFolder(image("a.jpg", 1), image("b.jpg", 2))

        viewModel.navigateToImage(1)

        assertEquals(1, viewModel.uiState.value.currentIndex)
        coVerify { settingsRepository.setFolderLastPosition("content://tree/photos", 1) }
    }

    // ── One-time action explanations ─────────────────────────────────────

    @Test
    fun `first swipe right explains the action and marks it seen`() = runTest {
        phoneSettingsFlow.value = PhoneSettings(collectionAction = CollectionAction.COPY)
        val viewModel = loadFolder(image("a.jpg", 1), image("b.jpg", 2))

        viewModel.addToCollection()

        val hint = viewModel.uiState.value.firstRunHint
        assertNotNull(hint)
        assertEquals(FirstRunHint.SWIPE_RIGHT, hint!!.hint)
        assertTrue(hint.message.contains("copied"))
        coVerify { settingsRepository.markFirstRunHintSeen(FirstRunHint.SWIPE_RIGHT) }
    }

    @Test
    fun `a hint fires only once per action`() = runTest {
        val viewModel = loadFolder(image("a.jpg", 1), image("b.jpg", 2))

        viewModel.addToCollection()
        assertNotNull(viewModel.uiState.value.firstRunHint)

        viewModel.dismissFirstRunHint()
        viewModel.addToCollection()

        assertNull(viewModel.uiState.value.firstRunHint)
    }

    @Test
    fun `hints already persisted as seen never fire`() = runTest {
        phoneSettingsFlow.value = PhoneSettings(
            seenFirstRunHints = setOf(FirstRunHint.SWIPE_RIGHT.key),
        )
        val viewModel = loadFolder(image("a.jpg", 1), image("b.jpg", 2))

        viewModel.addToCollection()

        assertNull(viewModel.uiState.value.firstRunHint)
        coVerify(exactly = 0) { settingsRepository.markFirstRunHintSeen(any()) }
    }

    @Test
    fun `delete uses the delete hint and copy-move uses the folder hint`() = runTest {
        phoneSettingsFlow.value = PhoneSettings(leftSwipeAction = SwipeAction.DELETE)
        val deleting = loadFolder(image("a.jpg", 1), image("b.jpg", 2))
        deleting.requestDelete()
        assertEquals(
            FirstRunHint.SWIPE_LEFT_DELETE,
            deleting.uiState.value.firstRunHint?.hint,
        )

        phoneSettingsFlow.value = PhoneSettings(leftSwipeAction = SwipeAction.MOVE)
        val moving = loadFolder(image("c.jpg", 1), image("d.jpg", 2))
        moving.performLeftSwipeCopyOrMove()
        assertEquals(
            FirstRunHint.SWIPE_LEFT_FOLDER,
            moving.uiState.value.firstRunHint?.hint,
        )
    }

    @Test
    fun `no hint is shown while the full tutorial is up`() = runTest {
        gestureTutorialTsFlow.value = 0L // forces the tutorial to show
        val viewModel = loadFolder(image("a.jpg", 1), image("b.jpg", 2))
        assertTrue(viewModel.uiState.value.showGestureTutorial)

        viewModel.addToCollection()

        assertNull(viewModel.uiState.value.firstRunHint)
    }

    @Test
    fun `controls guide can be opened and closed`() = runTest {
        val viewModel = buildViewModel()

        assertFalse(viewModel.uiState.value.showControlsGuide)
        viewModel.showControlsGuide()
        assertTrue(viewModel.uiState.value.showControlsGuide)
        viewModel.hideControlsGuide()
        assertFalse(viewModel.uiState.value.showControlsGuide)
    }

    // ── Optimistic copy / move (the swipe must not wait for I/O) ─────────

    @Test
    fun `copy advances to the next photo before the copy finishes`() = runTest {
        val gate = CompletableDeferred<Boolean>()
        coEvery { imageRepository.copyImage(any(), any(), any(), any()) } coAnswers { gate.await() }
        val viewModel = loadFolder(image("a.jpg", 2), image("b.jpg", 1))

        viewModel.addToCollection()

        // The next photo is already on screen while the copy is still in flight.
        assertEquals(1, viewModel.uiState.value.currentIndex)
        assertNull(viewModel.uiState.value.lastActionFeedback)

        gate.complete(true)
        advanceUntilIdle()
        assertEquals("Copied to collection", viewModel.uiState.value.lastActionFeedback?.message)
        assertEquals(2, viewModel.uiState.value.images.size)
    }

    @Test
    fun `move removes the photo from the feed before the move finishes`() = runTest {
        phoneSettingsFlow.value = PhoneSettings(collectionAction = CollectionAction.MOVE)
        val gate = CompletableDeferred<Boolean>()
        coEvery { imageRepository.moveImage(any(), any(), any(), any()) } coAnswers { gate.await() }
        val viewModel = loadFolder(image("a.jpg", 2), image("b.jpg", 1))
        val moved = viewModel.uiState.value.images[0].fileName

        viewModel.addToCollection()

        // Gone from the feed immediately; the transfer is still running.
        assertEquals(1, viewModel.uiState.value.images.size)
        assertFalse(viewModel.uiState.value.images.any { it.fileName == moved })
        assertNull(viewModel.uiState.value.lastActionFeedback)

        gate.complete(true)
        advanceUntilIdle()
        assertEquals("Moved to collection", viewModel.uiState.value.lastActionFeedback?.message)
        assertEquals(1, viewModel.uiState.value.images.size)
    }

    @Test
    fun `a failed move puts the photo back into the feed`() = runTest {
        phoneSettingsFlow.value = PhoneSettings(collectionAction = CollectionAction.MOVE)
        coEvery { imageRepository.moveImage(any(), any(), any(), any()) } returns false
        val viewModel = loadFolder(image("a.jpg", 3), image("b.jpg", 2), image("c.jpg", 1))
        val target = viewModel.uiState.value.images[0].fileName

        viewModel.addToCollection()

        val state = viewModel.uiState.value
        assertEquals(3, state.images.size)
        assertTrue("a failed move must not lose the photo", state.images.any { it.fileName == target })
        assertEquals(target, state.images[0].fileName)
        assertTrue(state.lastActionFeedback?.isError == true)
    }

    @Test
    fun `a move that throws puts the photo back into the feed`() = runTest {
        phoneSettingsFlow.value = PhoneSettings(collectionAction = CollectionAction.MOVE)
        coEvery {
            imageRepository.moveImage(any(), any(), any(), any())
        } throws IllegalStateException("card removed")
        val viewModel = loadFolder(image("a.jpg", 2), image("b.jpg", 1))
        val target = viewModel.uiState.value.images[0].fileName

        viewModel.addToCollection()

        val state = viewModel.uiState.value
        assertEquals(2, state.images.size)
        assertTrue(state.images.any { it.fileName == target })
        assertTrue(state.lastActionFeedback?.isError == true)
    }

    @Test
    fun `a failed copy does not rewind the feed`() = runTest {
        // A copy leaves the source in place, so there is nothing to restore — the
        // user should stay on the photo they advanced to.
        coEvery { imageRepository.copyImage(any(), any(), any(), any()) } returns false
        val viewModel = loadFolder(image("a.jpg", 2), image("b.jpg", 1))

        viewModel.addToCollection()

        val state = viewModel.uiState.value
        assertEquals(2, state.images.size)
        assertEquals(1, state.currentIndex)
        assertTrue(state.lastActionFeedback?.isError == true)
    }

    @Test
    fun `copy on the last photo stays put instead of running off the end`() = runTest {
        coEvery { imageRepository.copyImage(any(), any(), any(), any()) } returns true
        val viewModel = loadFolder(image("a.jpg", 2), image("b.jpg", 1))
        viewModel.navigateToImage(1)

        viewModel.addToCollection()

        assertEquals(1, viewModel.uiState.value.currentIndex)
    }

    // ── Progressive discovery of large folders ───────────────────────────

    @Test
    fun `the viewer opens on the first batch while discovery continues`() = runTest {
        val discovery = MutableSharedFlow<List<ImageItem>>(extraBufferCapacity = 8)
        every { imageRepository.discoverImages(any()) } returns discovery
        coEvery { settingsRepository.getFolderLastPosition(any()) } returns 0
        val viewModel = buildViewModel()

        viewModel.selectSourceFolder(Uri.parse("content://tree/photos"))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isLoading)

        discovery.emit(listOf(image("a.jpg", 3), image("b.jpg", 2)))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.images.size)
        assertFalse("the user must be able to swipe already", state.isLoading)
        assertTrue("the count is still growing", state.isDiscovering)
    }

    @Test
    fun `later batches are appended without reordering the visible feed`() = runTest {
        val discovery = MutableSharedFlow<List<ImageItem>>(extraBufferCapacity = 8)
        every { imageRepository.discoverImages(any()) } returns discovery
        coEvery { settingsRepository.getFolderLastPosition(any()) } returns 0
        val viewModel = buildViewModel()
        viewModel.selectSourceFolder(Uri.parse("content://tree/photos"))

        val first = listOf(image("old_a.jpg", 10), image("old_b.jpg", 20))
        discovery.emit(first)
        advanceUntilIdle()
        val orderAfterFirstBatch = viewModel.uiState.value.images.map { it.fileName }

        // Cumulative emission: the same two files plus two *newer* ones. A global
        // re-sort would put the newer files first and yank the feed under the user.
        discovery.emit(first + listOf(image("new_a.jpg", 900), image("new_b.jpg", 800)))
        advanceUntilIdle()

        val names = viewModel.uiState.value.images.map { it.fileName }
        assertEquals(4, names.size)
        assertEquals(orderAfterFirstBatch, names.take(2))
        assertEquals(listOf("new_a.jpg", "new_b.jpg"), names.drop(2))
    }

    @Test
    fun `a photo removed during discovery is not resurrected by the next batch`() = runTest {
        phoneSettingsFlow.value = PhoneSettings(collectionAction = CollectionAction.MOVE)
        coEvery { imageRepository.moveImage(any(), any(), any(), any()) } returns true
        val discovery = MutableSharedFlow<List<ImageItem>>(extraBufferCapacity = 8)
        every { imageRepository.discoverImages(any()) } returns discovery
        coEvery { settingsRepository.getFolderLastPosition(any()) } returns 0
        val viewModel = buildViewModel()
        viewModel.selectSourceFolder(Uri.parse("content://tree/photos"))

        val first = listOf(image("a.jpg", 20), image("b.jpg", 10))
        discovery.emit(first)
        advanceUntilIdle()
        val moved = viewModel.uiState.value.images[0].fileName
        viewModel.addToCollection()
        assertFalse(viewModel.uiState.value.images.any { it.fileName == moved })

        discovery.emit(first + listOf(image("c.jpg", 5)))
        advanceUntilIdle()

        val names = viewModel.uiState.value.images.map { it.fileName }
        assertFalse("the moved photo must stay gone", names.contains(moved))
        assertTrue(names.contains("c.jpg"))
    }

    @Test
    fun `a saved position beyond the first batch is restored as photos stream in`() = runTest {
        val discovery = MutableSharedFlow<List<ImageItem>>(extraBufferCapacity = 8)
        every { imageRepository.discoverImages(any()) } returns discovery
        coEvery { settingsRepository.getFolderLastPosition(any()) } returns 4
        val viewModel = buildViewModel()
        viewModel.selectSourceFolder(Uri.parse("content://tree/photos"))

        val first = (1..3).map { image("b1_$it.jpg", it.toLong()) }
        discovery.emit(first)
        advanceUntilIdle()
        // Clamped to what is loaded so far.
        assertEquals(2, viewModel.uiState.value.currentIndex)

        discovery.emit(first + (4..8).map { image("b2_$it.jpg", it.toLong()) })
        advanceUntilIdle()
        assertEquals(4, viewModel.uiState.value.currentIndex)
    }

    @Test
    fun `swiping during discovery cancels the pending position restore`() = runTest {
        val discovery = MutableSharedFlow<List<ImageItem>>(extraBufferCapacity = 8)
        every { imageRepository.discoverImages(any()) } returns discovery
        coEvery { settingsRepository.getFolderLastPosition(any()) } returns 7
        val viewModel = buildViewModel()
        viewModel.selectSourceFolder(Uri.parse("content://tree/photos"))

        val first = (1..3).map { image("b1_$it.jpg", it.toLong()) }
        discovery.emit(first)
        advanceUntilIdle()
        viewModel.navigateToImage(0) // the user takes over

        discovery.emit(first + (4..9).map { image("b2_$it.jpg", it.toLong()) })
        advanceUntilIdle()

        assertEquals(0, viewModel.uiState.value.currentIndex)
    }

    @Test
    fun `selecting another folder does not prepend the previous folder's photos`() = runTest {
        val viewModel = loadFolder(image("first_a.jpg", 2), image("first_b.jpg", 1))
        assertEquals(2, viewModel.uiState.value.images.size)

        every { imageRepository.discoverImages(any()) } returns
            flowOf(listOf(image("second_a.jpg", 5)))
        viewModel.selectSourceFolder(Uri.parse("content://tree/other"))

        val names = viewModel.uiState.value.images.map { it.fileName }
        assertEquals(listOf("second_a.jpg"), names)
    }

    @Test
    fun `isDiscovering clears when enumeration completes`() = runTest {
        val viewModel = loadFolder(image("a.jpg", 1))

        assertFalse(viewModel.uiState.value.isDiscovering)
        assertFalse(viewModel.uiState.value.isLoading)
    }
}
