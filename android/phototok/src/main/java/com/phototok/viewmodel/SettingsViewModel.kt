package com.phototok.viewmodel

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phototok.data.repository.SettingsRepository
import com.phototok.domain.CollectionAction
import com.phototok.domain.FileTypeFilter
import com.phototok.domain.SwipeAction
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                settingsRepository.selectionFolderName,
                settingsRepository.sortingEnabled,
                settingsRepository.phoneCollectionAction,
                settingsRepository.phoneTrashConfirmEnabled,
                settingsRepository.phoneDirectDeleteConfirmEnabled,
                settingsRepository.phoneSortByOrientation,
                settingsRepository.phoneRandomizeOrder,
                settingsRepository.phoneFileTypeFilter,
                settingsRepository.phoneShowExifOverlay,
                settingsRepository.phoneMoveRelatedFiles,
                settingsRepository.phoneRecentPathsEnabled,
                settingsRepository.phoneRecentPathsCount,
                settingsRepository.phoneLeftSwipeAction,
            ) { arrays ->
                SettingsUiState(
                    selectionFolderName = arrays[0] as String,
                    sortingEnabled = arrays[1] as Boolean,
                    collectionAction = arrays[2] as CollectionAction,
                    trashConfirmEnabled = arrays[3] as Boolean,
                    directDeleteConfirmEnabled = arrays[4] as Boolean,
                    sortByOrientation = arrays[5] as Boolean,
                    randomizeOrder = arrays[6] as Boolean,
                    fileTypeFilter = arrays[7] as FileTypeFilter,
                    showExifOverlay = arrays[8] as Boolean,
                    moveRelatedFiles = arrays[9] as Boolean,
                    recentPathsEnabled = arrays[10] as Boolean,
                    recentPathsCount = arrays[11] as Int,
                    leftSwipeAction = arrays[12] as SwipeAction,
                    collectionUri = _uiState.value.collectionUri,
                    leftSwipeUri = _uiState.value.leftSwipeUri,
                    sourceFolderUri = _uiState.value.sourceFolderUri,
                    sourceFolderName = _uiState.value.sourceFolderName,
                )
            }.collect { newState ->
                _uiState.update { newState }
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
                    try {
                        DocumentFile.fromTreeUri(context, Uri.parse(uri))?.name ?: "Photos"
                    } catch (_: Exception) { "Photos" }
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
