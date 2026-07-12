package com.phototok.viewmodel

import com.phototok.data.model.PhoneSettings
import com.phototok.data.repository.ImageRepository
import com.phototok.data.repository.SettingsRepository
import com.phototok.domain.CollectionAction
import com.phototok.domain.FileTypeFilter
import com.phototok.domain.SwipeAction
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val settingsRepository: SettingsRepository = mockk(relaxed = true)
    private val imageRepository: ImageRepository = mockk(relaxed = true)

    // Flow mocks
    private val phoneSettingsFlow = MutableStateFlow(
        PhoneSettings(
            collectionAction = CollectionAction.COPY,
            directDeleteConfirmEnabled = true,
            sortByOrientation = false,
            randomizeOrder = false,
            fileTypeFilter = FileTypeFilter.ALL,
            leftSwipeAction = SwipeAction.DELETE,
            showExifOverlay = false,
            moveRelatedFiles = false,
            recentPathsEnabled = true,
            recentPathsCount = 3,
        )
    )
    private val selectionFolderNameFlow = MutableStateFlow("Selection")
    private val sortingEnabledFlow = MutableStateFlow(true)
    private val phoneCollectionUriFlow = MutableStateFlow<String?>(null)
    private val phoneLeftSwipeUriFlow = MutableStateFlow<String?>(null)
    private val lastFolderUriFlow = MutableStateFlow<String?>(null)

    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        // Mock settings flows (one typed flow for the simple settings)
        every { settingsRepository.phoneSettings } returns phoneSettingsFlow
        every { settingsRepository.selectionFolderName } returns selectionFolderNameFlow
        every { settingsRepository.sortingEnabled } returns sortingEnabledFlow
        every { settingsRepository.phoneCollectionUri } returns phoneCollectionUriFlow
        every { settingsRepository.phoneLeftSwipeUri } returns phoneLeftSwipeUriFlow
        every { settingsRepository.lastFolderUri } returns lastFolderUriFlow

        viewModel = SettingsViewModel(settingsRepository, imageRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state reflects settings repository defaults`() = runTest {
        val state = viewModel.uiState.value

        assertEquals("Selection", state.selectionFolderName)
        assertTrue(state.sortingEnabled)
        assertEquals(CollectionAction.COPY, state.collectionAction)
        assertTrue(state.directDeleteConfirmEnabled)
        assertFalse(state.sortByOrientation)
        assertFalse(state.randomizeOrder)
        assertEquals(FileTypeFilter.ALL, state.fileTypeFilter)
        assertFalse(state.showExifOverlay)
        assertFalse(state.moveRelatedFiles)
        assertTrue(state.recentPathsEnabled)
        assertEquals(3, state.recentPathsCount)
        assertEquals(SwipeAction.DELETE, state.leftSwipeAction)
        assertEquals(null, state.leftSwipeUri)
    }

    @Test
    fun `updating settings calls repository`() = runTest {
        coEvery { settingsRepository.setPhoneMoveRelatedFiles(any()) } just Runs
        coEvery { settingsRepository.setPhoneRecentPathsEnabled(any()) } just Runs
        coEvery { settingsRepository.setPhoneRecentPathsCount(any()) } just Runs
        coEvery { settingsRepository.setPhoneDirectDeleteConfirmEnabled(any()) } just Runs

        viewModel.updateMoveRelatedFiles(true)
        coVerify { settingsRepository.setPhoneMoveRelatedFiles(true) }

        viewModel.updateRecentPathsEnabled(false)
        coVerify { settingsRepository.setPhoneRecentPathsEnabled(false) }

        viewModel.updateRecentPathsCount(5)
        coVerify { settingsRepository.setPhoneRecentPathsCount(5) }

        viewModel.updateDirectDeleteConfirm(false)
        coVerify { settingsRepository.setPhoneDirectDeleteConfirmEnabled(false) }

        viewModel.updateLeftSwipeAction(SwipeAction.COPY)
        coVerify { settingsRepository.setPhoneLeftSwipeAction(SwipeAction.COPY) }

        viewModel.updateLeftSwipeUri("content://left")
        coVerify { settingsRepository.setPhoneLeftSwipeUri("content://left") }
    }

    @Test
    fun `state updates when the typed settings flow emits new values`() = runTest {
        assertFalse(viewModel.uiState.value.moveRelatedFiles)

        phoneSettingsFlow.value = phoneSettingsFlow.value.copy(moveRelatedFiles = true)

        assertTrue(viewModel.uiState.value.moveRelatedFiles)
    }

    @Test
    fun `state updates when the direct delete confirmation changes`() = runTest {
        assertTrue(viewModel.uiState.value.directDeleteConfirmEnabled)

        phoneSettingsFlow.value = phoneSettingsFlow.value.copy(
            directDeleteConfirmEnabled = false,
        )

        assertFalse(viewModel.uiState.value.directDeleteConfirmEnabled)
    }

    @Test
    fun `state updates when left swipe settings change`() = runTest {
        assertEquals(SwipeAction.DELETE, viewModel.uiState.value.leftSwipeAction)
        assertEquals(null, viewModel.uiState.value.leftSwipeUri)

        phoneSettingsFlow.value = phoneSettingsFlow.value.copy(leftSwipeAction = SwipeAction.MOVE)
        phoneLeftSwipeUriFlow.value = "content://left"

        assertEquals(SwipeAction.MOVE, viewModel.uiState.value.leftSwipeAction)
        assertEquals("content://left", viewModel.uiState.value.leftSwipeUri)
    }
}
