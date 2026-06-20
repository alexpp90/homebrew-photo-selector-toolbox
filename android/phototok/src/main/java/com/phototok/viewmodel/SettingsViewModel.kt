package com.phototok.viewmodel

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phototok.data.repository.SettingsRepository
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
    val collectionAction: String = "copy",
    val deleteConfirmEnabled: Boolean = true,
    val sortByOrientation: Boolean = true,
    val randomizeOrder: Boolean = false,
    val collectionUri: String? = null,
    val fileTypeFilter: String = "all",
    val showExifOverlay: Boolean = true,
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
                settingsRepository.phoneDeleteConfirmEnabled,
                settingsRepository.phoneSortByOrientation,
                settingsRepository.phoneRandomizeOrder,
                settingsRepository.phoneFileTypeFilter,
                settingsRepository.phoneShowExifOverlay
            ) { arrays ->
                SettingsUiState(
                    selectionFolderName = arrays[0] as String,
                    sortingEnabled = arrays[1] as Boolean,
                    collectionAction = arrays[2] as String,
                    deleteConfirmEnabled = arrays[3] as Boolean,
                    sortByOrientation = arrays[4] as Boolean,
                    randomizeOrder = arrays[5] as Boolean,
                    fileTypeFilter = arrays[6] as String,
                    showExifOverlay = arrays[7] as Boolean,
                    collectionUri = _uiState.value.collectionUri,
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

    fun updateCollectionAction(action: String) {
        viewModelScope.launch { settingsRepository.setPhoneCollectionAction(action) }
    }

    fun updateDeleteConfirm(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setPhoneDeleteConfirmEnabled(enabled) }
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

    fun updateFileTypeFilter(filter: String) {
        viewModelScope.launch { settingsRepository.setPhoneFileTypeFilter(filter) }
    }

    fun updateShowExifOverlay(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setPhoneShowExifOverlay(enabled) }
    }
}
