package com.phototok.viewmodel

import android.content.Context
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
    private val context: Context = mockk(relaxed = true)

    // Flow mocks
    private val selectionFolderNameFlow = MutableStateFlow("Selection")
    private val sortingEnabledFlow = MutableStateFlow(true)
    private val phoneCollectionActionFlow = MutableStateFlow(CollectionAction.COPY)
    private val phoneTrashConfirmEnabledFlow = MutableStateFlow(true)
    private val phoneDirectDeleteConfirmEnabledFlow = MutableStateFlow(true)
    private val phoneSortByOrientationFlow = MutableStateFlow(false)
    private val phoneRandomizeOrderFlow = MutableStateFlow(false)
    private val phoneFileTypeFilterFlow = MutableStateFlow(FileTypeFilter.ALL)
    private val phoneShowExifOverlayFlow = MutableStateFlow(false)
    private val phoneMoveRelatedFilesFlow = MutableStateFlow(false)
    private val phoneRecentPathsEnabledFlow = MutableStateFlow(true)
    private val phoneRecentPathsCountFlow = MutableStateFlow(3)
    private val phoneCollectionUriFlow = MutableStateFlow<String?>(null)
    private val phoneLeftSwipeActionFlow = MutableStateFlow(SwipeAction.DELETE)
    private val phoneLeftSwipeUriFlow = MutableStateFlow<String?>(null)
    private val lastFolderUriFlow = MutableStateFlow<String?>(null)

    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        // Mock settings flows
        every { settingsRepository.selectionFolderName } returns selectionFolderNameFlow
        every { settingsRepository.sortingEnabled } returns sortingEnabledFlow
        every { settingsRepository.phoneCollectionAction } returns phoneCollectionActionFlow
        every { settingsRepository.phoneTrashConfirmEnabled } returns phoneTrashConfirmEnabledFlow
        every { settingsRepository.phoneDirectDeleteConfirmEnabled } returns phoneDirectDeleteConfirmEnabledFlow
        every { settingsRepository.phoneSortByOrientation } returns phoneSortByOrientationFlow
        every { settingsRepository.phoneRandomizeOrder } returns phoneRandomizeOrderFlow
        every { settingsRepository.phoneFileTypeFilter } returns phoneFileTypeFilterFlow
        every { settingsRepository.phoneShowExifOverlay } returns phoneShowExifOverlayFlow
        every { settingsRepository.phoneMoveRelatedFiles } returns phoneMoveRelatedFilesFlow
        every { settingsRepository.phoneRecentPathsEnabled } returns phoneRecentPathsEnabledFlow
        every { settingsRepository.phoneRecentPathsCount } returns phoneRecentPathsCountFlow
        every { settingsRepository.phoneRecentPaths } returns MutableStateFlow(emptyList())
        every { settingsRepository.phoneCollectionUri } returns phoneCollectionUriFlow
        every { settingsRepository.phoneLeftSwipeAction } returns phoneLeftSwipeActionFlow
        every { settingsRepository.phoneLeftSwipeUri } returns phoneLeftSwipeUriFlow
        every { settingsRepository.lastFolderUri } returns lastFolderUriFlow

        viewModel = SettingsViewModel(settingsRepository, context)
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
        assertTrue(state.trashConfirmEnabled)
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
        coEvery { settingsRepository.setPhoneTrashConfirmEnabled(any()) } just Runs
        coEvery { settingsRepository.setPhoneDirectDeleteConfirmEnabled(any()) } just Runs

        viewModel.updateMoveRelatedFiles(true)
        coVerify { settingsRepository.setPhoneMoveRelatedFiles(true) }

        viewModel.updateRecentPathsEnabled(false)
        coVerify { settingsRepository.setPhoneRecentPathsEnabled(false) }

        viewModel.updateRecentPathsCount(5)
        coVerify { settingsRepository.setPhoneRecentPathsCount(5) }

        viewModel.updateTrashConfirm(false)
        coVerify { settingsRepository.setPhoneTrashConfirmEnabled(false) }

        viewModel.updateDirectDeleteConfirm(false)
        coVerify { settingsRepository.setPhoneDirectDeleteConfirmEnabled(false) }

        viewModel.updateLeftSwipeAction(SwipeAction.COPY)
        coVerify { settingsRepository.setPhoneLeftSwipeAction(SwipeAction.COPY) }

        viewModel.updateLeftSwipeUri("content://left")
        coVerify { settingsRepository.setPhoneLeftSwipeUri("content://left") }
    }

    @Test
    fun `state updates when repository flows emit new values`() = runTest {
        assertFalse(viewModel.uiState.value.moveRelatedFiles)

        phoneMoveRelatedFilesFlow.value = true

        assertTrue(viewModel.uiState.value.moveRelatedFiles)
    }

    @Test
    fun `state updates when trash and direct delete confirmation flows emit new values`() = runTest {
        assertTrue(viewModel.uiState.value.trashConfirmEnabled)
        assertTrue(viewModel.uiState.value.directDeleteConfirmEnabled)

        phoneTrashConfirmEnabledFlow.value = false
        phoneDirectDeleteConfirmEnabledFlow.value = false

        assertFalse(viewModel.uiState.value.trashConfirmEnabled)
        assertFalse(viewModel.uiState.value.directDeleteConfirmEnabled)
    }

    @Test
    fun `state updates when left swipe flows emit new values`() = runTest {
        assertEquals(SwipeAction.DELETE, viewModel.uiState.value.leftSwipeAction)
        assertEquals(null, viewModel.uiState.value.leftSwipeUri)

        phoneLeftSwipeActionFlow.value = SwipeAction.MOVE
        phoneLeftSwipeUriFlow.value = "content://left"

        assertEquals(SwipeAction.MOVE, viewModel.uiState.value.leftSwipeAction)
        assertEquals("content://left", viewModel.uiState.value.leftSwipeUri)
    }
}
