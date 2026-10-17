package com.photoselectortoolbox.viewmodel

import android.net.Uri
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.photoselector.core.model.ExifData
import com.photoselectortoolbox.data.cache.ScoreDao
import com.photoselectortoolbox.data.cache.ScoreEntity
import com.photoselectortoolbox.data.metadata.MetadataWriter
import com.photoselectortoolbox.data.model.ImageDimensions
import com.photoselectortoolbox.data.model.ImageItem
import com.photoselectortoolbox.data.repository.CacheRepository
import com.photoselectortoolbox.data.repository.ImageRepository
import com.photoselectortoolbox.data.repository.SettingsRepository
import com.photoselectortoolbox.domain.curation.UndoableOperation
import com.photoselectortoolbox.domain.grouping.GroupingLevel
import com.photoselectortoolbox.domain.interaction.FilingAction
import com.photoselectortoolbox.domain.model.RatingFilter
import com.photoselectortoolbox.domain.usecase.ScanImagesUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SelectorViewModelRatingTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val appScope = CoroutineScope(testDispatcher)

    private val groupingEnabledFlow = MutableStateFlow(false)
    private val groupingLevelFlow = MutableStateFlow(GroupingLevel.TIME_FILENAME)
    private val sortingEnabledFlow = MutableStateFlow(true)
    private val lastFolderUriFlow = MutableStateFlow<String?>(null)
    private val filingActionFlow = MutableStateFlow(FilingAction.COPY)
    private val fullscreenButtonsFlow = MutableStateFlow(true)
    private val overlayValuesFlow = MutableStateFlow(true)
    private val filmstripVisibleFlow = MutableStateFlow(true)
    private val detailsVisibleFlow = MutableStateFlow(true)
    private val seenFullscreenHintFlow = MutableStateFlow(true)
    private val seenNavHintFlow = MutableStateFlow(true)
    private val selectionFolderNameFlow = MutableStateFlow(FilingAction.DEFAULT_SELECTION_FOLDER)

    private val discovery = MutableSharedFlow<List<ImageItem>>(replay = 1)

    private companion object {
        const val FOLDER_URI = "content://test/folder"
    }

    private val settingsRepository: SettingsRepository = mockk(relaxed = true) {
        every { groupingEnabled } returns groupingEnabledFlow
        every { groupingLevel } returns groupingLevelFlow
        every { sortingEnabled } returns sortingEnabledFlow
        every { lastFolderUri } returns lastFolderUriFlow
        every { filingAction } returns filingActionFlow
        every { fullscreenButtonsEnabled } returns fullscreenButtonsFlow
        every { overlayValuesVisible } returns overlayValuesFlow
        every { filmstripVisible } returns filmstripVisibleFlow
        every { detailsVisible } returns detailsVisibleFlow
        every { hasSeenFullscreenGestureHint } returns seenFullscreenHintFlow
        every { hasSeenNavHint } returns seenNavHintFlow
        every { selectionFolderName } returns selectionFolderNameFlow
    }

    private val imageRepository: ImageRepository = mockk(relaxed = true) {
        coEvery { openFolder(any(), any()) } returns "Test Shoot"
        every { discoverImages(any()) } returns discovery
        every { canTrash(any()) } returns false
        coEvery { getExifData(any(), any()) } returns null
        coEvery { resolveDimensions(any(), any()) } returns emptyMap<String, ImageDimensions>()
    }

    private val scanImagesUseCase: ScanImagesUseCase = mockk(relaxed = true)
    private val cacheRepository: CacheRepository = mockk(relaxed = true)
    private val scoreDao: ScoreDao = mockk(relaxed = true) {
        coEvery { getScore(any()) } returns null
    }
    private val metadataWriter: MetadataWriter = mockk(relaxed = true) {
        coEvery { writeRating(any(), any()) } returns Result.success(Unit)
    }

    private fun image(name: String, rating: Int = 0) = ImageItem(
        uri = "content://test/folder/$name",
        fileName = name,
        fileSize = 1,
        lastModified = 1,
        mimeType = "image/jpeg",
        rating = rating,
    )

    private val viewModels = mutableListOf<SelectorViewModel>()

    private fun buildViewModel(): SelectorViewModel {
        val viewModel = SelectorViewModel(
            imageRepository = imageRepository,
            scanImagesUseCase = scanImagesUseCase,
            cacheRepository = cacheRepository,
            settingsRepository = settingsRepository,
            scoreDao = scoreDao,
            appScope = appScope,
            context = ApplicationProvider.getApplicationContext(),
            metadataWriter = metadataWriter,
        )
        viewModel.ioDispatcher = testDispatcher
        viewModels.add(viewModel)
        return viewModel
    }

    private suspend fun loadFolder(vararg images: ImageItem): SelectorViewModel {
        val viewModel = buildViewModel()
        viewModel.selectFolder(Uri.parse(FOLDER_URI))
        discovery.emit(images.toList())
        return viewModel
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        appScope.cancel()
        viewModels.forEach { it.viewModelScope.cancel() }
        viewModels.clear()
        Dispatchers.resetMain()
        discovery.resetReplayCache()
    }

    @Test
    fun `setRating updates in-memory image and triggers Room and metadata write-back`() = runTest {
        val viewModel = loadFolder(image("a.jpg"), image("b.jpg"))

        assertEquals(0, viewModel.uiState.value.currentImage?.rating)

        viewModel.setRatingForCurrent(4)

        // Verify in-memory state updated
        assertEquals(4, viewModel.uiState.value.currentImage?.rating)
        assertEquals(4, viewModel.uiState.value.images[0].rating)

        // Verify Room cache update called
        coVerify { cacheRepository.updateRating("content://test/folder/a.jpg", 4) }

        // Verify async metadata write-back called
        coVerify { metadataWriter.writeRating(any(), 4) }
    }

    @Test
    fun `setRatingForMaximised updates rating on maximised frame`() = runTest {
        val viewModel = loadFolder(image("a.jpg"), image("b.jpg"), image("c.jpg"))

        // Maximise the NEXT frame (b.jpg)
        viewModel.toggleMaximised(SelectorFrame.NEXT)
        assertEquals(SelectorFrame.NEXT, viewModel.uiState.value.maximisedFrame)

        viewModel.setRatingForMaximised(5)

        assertEquals(5, viewModel.uiState.value.nextImage?.rating)
        assertEquals(5, viewModel.uiState.value.images[1].rating)
        coVerify { cacheRepository.updateRating("content://test/folder/b.jpg", 5) }
        coVerify { metadataWriter.writeRating(any(), 5) }
    }

    @Test
    fun `ratingFilter filters images and maintains correct filtered position`() = runTest {
        val viewModel = loadFolder(
            image("a.jpg", rating = 0),
            image("b.jpg", rating = 4),
            image("c.jpg", rating = 5),
            image("d.jpg", rating = 2),
            image("e.jpg", rating = 4),
        )

        assertEquals(5, viewModel.uiState.value.filteredImages.size)

        // Filter to 4+ stars - snaps immediately to closest matching (b.jpg)
        viewModel.setRatingFilter(RatingFilter.MinRating(4))

        val state = viewModel.uiState.value
        assertEquals(RatingFilter.MinRating(4), state.ratingFilter)
        assertEquals(listOf("b.jpg", "c.jpg", "e.jpg"), state.filteredImages.map { it.fileName })
        assertEquals("b.jpg", state.currentImage?.fileName)
        // filteredPosition is 1-based photographic position index
        assertEquals(1, state.filteredPosition)

        // Then navigateNext steps to next matching (c.jpg)
        viewModel.navigateNext()
        assertEquals("c.jpg", viewModel.uiState.value.currentImage?.fileName)
        assertEquals(2, viewModel.uiState.value.filteredPosition)
    }

    @Test
    fun `navigateNext and navigatePrevious step strictly through filtered images`() = runTest {
        val viewModel = loadFolder(
            image("img0.jpg", rating = 5),
            image("img1.jpg", rating = 1),
            image("img2.jpg", rating = 5),
            image("img3.jpg", rating = 2),
            image("img4.jpg", rating = 5),
        )

        viewModel.setRatingFilter(RatingFilter.MinRating(4))
        assertEquals(listOf("img0.jpg", "img2.jpg", "img4.jpg"), viewModel.uiState.value.filteredImages.map { it.fileName })

        // Initial image is img0.jpg
        assertEquals("img0.jpg", viewModel.uiState.value.currentImage?.fileName)

        // Navigate next -> should skip img1.jpg and go directly to img2.jpg
        viewModel.navigateNext()
        assertEquals("img2.jpg", viewModel.uiState.value.currentImage?.fileName)

        // Navigate next -> should skip img3.jpg and go to img4.jpg
        viewModel.navigateNext()
        assertEquals("img4.jpg", viewModel.uiState.value.currentImage?.fileName)

        // End of list: navigate next should remain at img4.jpg
        viewModel.navigateNext()
        assertEquals("img4.jpg", viewModel.uiState.value.currentImage?.fileName)

        // Navigate previous -> should go back to img2.jpg
        viewModel.navigatePrevious()
        assertEquals("img2.jpg", viewModel.uiState.value.currentImage?.fileName)

        // Navigate previous -> should go back to img0.jpg
        viewModel.navigatePrevious()
        assertEquals("img0.jpg", viewModel.uiState.value.currentImage?.fileName)
    }

    @Test
    fun `restoreCachedScores applies cached rating to loaded images`() = runTest {
        val entity = ScoreEntity(
            filePath = "content://test/folder/cached.jpg",
            fileSize = 1L,
            lastModified = 1L,
            sharpnessScore = 85.0,
            rating = 4,
        )
        coEvery { scoreDao.getScore("content://test/folder/cached.jpg") } returns entity

        val viewModel = loadFolder(image("cached.jpg", rating = 0))

        advanceUntilIdle()

        // Give coroutine chance to run cache restore
        val cachedImage = viewModel.uiState.value.images.firstOrNull { it.fileName == "cached.jpg" }
        assertNotNull(cachedImage)
        assertEquals(4, cachedImage?.rating)
    }
}
