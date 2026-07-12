package com.phototok.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phototok.data.repository.ImageRepository
import com.phototok.data.repository.SettingsRepository
import com.phototok.domain.CollectionAction
import com.phototok.domain.FileTypeFilter
import com.phototok.domain.SwipeAction
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val selectionFolderName: String = "Selection",
    val sortingEnabled: Boolean = true,
    val collectionAction: CollectionAction = CollectionAction.DEFAULT,
    val trashConfirmEnabled: Boolean = true,
    val directDeleteConfirmEnabled: Boolean = true,
    val sortByOrientation: Boolean = false,
    val randomizeOrder: Boolean = false,
    val collectionUri: String? = null,
    val leftSwipeAction: SwipeAction = SwipeAction.DEFAULT,
    val leftSwipeUri: String? = null,
    val fileTypeFilter: FileTypeFilter = FileTypeFilter.DEFAULT,
    val showExifOverlay: Boolean = false,
    val moveRelatedFiles: Boolean = false,
    val recentPathsEnabled: Boolean = true,
    val recentPathsCount: Int = 3,
    val sourceFolderUri: String? = null,
    val sourceFolderName: String = "",
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val imageRepository: ImageRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            // Typed flows only — no positional array casts.
            combine(
                settingsRepository.phoneSettings,
                settingsRepository.selectionFolderName,
                settingsRepository.sortingEnabled,
            ) { phone, selectionFolderName, sortingEnabled ->
                Triple(phone, selectionFolderName, sortingEnabled)
            }.collect { (phone, selectionFolderName, sortingEnabled) ->
                _uiState.update {
                    it.copy(
                        selectionFolderName = selectionFolderName,
                        sortingEnabled = sortingEnabled,
                        collectionAction = phone.collectionAction,
                        trashConfirmEnabled = phone.trashConfirmEnabled,
                        directDeleteConfirmEnabled = phone.directDeleteConfirmEnabled,
                        sortByOrientation = phone.sortByOrientation,
                        randomizeOrder = phone.randomizeOrder,
                        fileTypeFilter = phone.fileTypeFilter,
                        showExifOverlay = phone.showExifOverlay,
                        moveRelatedFiles = phone.moveRelatedFiles,
                        recentPathsEnabled = phone.recentPathsEnabled,
                        recentPathsCount = phone.recentPathsCount,
                        leftSwipeAction = phone.leftSwipeAction,
                    )
                }
            }
        }

        viewModelScope.launch {
            settingsRepository.phoneCollectionUri.collect { uri ->
                _uiState.update { it.copy(collectionUri = uri) }
            }
        }

        viewModelScope.launch {
            settingsRepository.phoneLeftSwipeUri.collect { uri ->
                _uiState.update { it.copy(leftSwipeUri = uri) }
            }
        }

        viewModelScope.launch {
            settingsRepository.lastFolderUri.collect { uri ->
                val name = if (uri != null) {
                    imageRepository.resolveFolderName(Uri.parse(uri)) ?: "Photos"
                } else ""
                _uiState.update { it.copy(sourceFolderUri = uri, sourceFolderName = name) }
            }
        }
    }

    fun updateSelectionFolder(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { settingsRepository.setSelectionFolderName(trimmed) }
    }

    fun updateSortingEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setSortingEnabled(enabled) }
    }

    fun updateCollectionAction(action: CollectionAction) {
        viewModelScope.launch { settingsRepository.setPhoneCollectionAction(action) }
    }

    fun updateTrashConfirm(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setPhoneTrashConfirmEnabled(enabled) }
    }

    fun updateDirectDeleteConfirm(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setPhoneDirectDeleteConfirmEnabled(enabled) }
    }

    fun updateSortByOrientation(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setPhoneSortByOrientation(enabled) }
    }

    fun updateRandomizeOrder(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setPhoneRandomizeOrder(enabled) }
    }

    fun updateCollectionUri(uri: String?) {
        viewModelScope.launch { settingsRepository.setPhoneCollectionUri(uri) }
    }

    fun updateFileTypeFilter(filter: FileTypeFilter) {
        viewModelScope.launch { settingsRepository.setPhoneFileTypeFilter(filter) }
    }

    fun updateShowExifOverlay(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setPhoneShowExifOverlay(enabled) }
    }

    fun updateMoveRelatedFiles(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setPhoneMoveRelatedFiles(enabled) }
    }

    fun updateRecentPathsEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setPhoneRecentPathsEnabled(enabled) }
    }

    fun updateRecentPathsCount(count: Int) {
        viewModelScope.launch { settingsRepository.setPhoneRecentPathsCount(count) }
    }

    fun updateLeftSwipeAction(action: SwipeAction) {
        viewModelScope.launch { settingsRepository.setPhoneLeftSwipeAction(action) }
    }

    fun updateLeftSwipeUri(uri: String?) {
        viewModelScope.launch { settingsRepository.setPhoneLeftSwipeUri(uri) }
    }
}
