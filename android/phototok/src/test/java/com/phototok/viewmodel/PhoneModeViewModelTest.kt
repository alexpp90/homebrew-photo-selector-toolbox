package com.phototok.viewmodel

import android.net.Uri
import com.phototok.data.model.ImageItem
import com.phototok.data.model.PhoneSettings
import com.phototok.data.repository.ImageRepository
import com.phototok.data.repository.SettingsRepository
import com.phototok.data.source.ExternalStorageDetector
import com.phototok.domain.CollectionAction
import com.phototok.domain.SwipeAction
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
}
