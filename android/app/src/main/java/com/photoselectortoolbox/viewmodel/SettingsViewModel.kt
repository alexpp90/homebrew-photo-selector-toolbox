package com.photoselectortoolbox.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photoselectortoolbox.data.repository.CacheRepository
import com.photoselectortoolbox.domain.grouping.GroupingLevel
import com.photoselectortoolbox.data.repository.SettingsRepository
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
    val groupingEnabled: Boolean = false,
    val groupingLevel: GroupingLevel = GroupingLevel.TIME_FILENAME,
    val analysisThreadCount: Int = 4,
    val cachedScoreCount: Int = 0,
    // Phone mode
    val phoneCollectionAction: String = "copy",
    val phoneDeleteConfirmEnabled: Boolean = true,
    val phoneSortByOrientation: Boolean = true,
    val phoneCollectionUri: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val cacheRepository: CacheRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                settingsRepository.selectionFolderName,
                settingsRepository.sortingEnabled,
                settingsRepository.groupingEnabled,
                settingsRepository.groupingLevel,
                settingsRepository.analysisThreadCount
            ) { folderName, sorting, enabled, grouping, threadCount ->
                SettingsUiState(
                    selectionFolderName = folderName,
                    sortingEnabled = sorting,
                    groupingEnabled = enabled,
                    groupingLevel = grouping,
                    analysisThreadCount = threadCount,
                    cachedScoreCount = _uiState.value.cachedScoreCount,
                    phoneCollectionAction = _uiState.value.phoneCollectionAction,
                    phoneDeleteConfirmEnabled = _uiState.value.phoneDeleteConfirmEnabled,
                    phoneSortByOrientation = _uiState.value.phoneSortByOrientation,
                    phoneCollectionUri = _uiState.value.phoneCollectionUri,
                )
            }.collect { newState ->
                _uiState.update { newState }
            }
        }

        // Observe phone-mode settings separately to keep combine arity manageable
        viewModelScope.launch {
            combine(
                settingsRepository.phoneCollectionAction,
                settingsRepository.phoneDeleteConfirmEnabled,
                settingsRepository.phoneSortByOrientation,
                settingsRepository.phoneCollectionUri,
            ) { action, confirm, sortOrientation, collectionUri ->
                PhoneSettings(action, confirm, sortOrientation, collectionUri)
            }.collect { ps ->
                _uiState.update {
                    it.copy(
                        phoneCollectionAction = ps.action,
                        phoneDeleteConfirmEnabled = ps.confirm,
                        phoneSortByOrientation = ps.sortOrientation,
                        phoneCollectionUri = ps.collectionUri,
                    )
                }
            }
        }

        refreshCachedScoreCount()
    }

    private data class PhoneSettings(
        val action: String,
        val confirm: Boolean,
        val sortOrientation: Boolean,
        val collectionUri: String?,
    )

    fun updateSelectionFolder(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return

        viewModelScope.launch {
            settingsRepository.setSelectionFolderName(trimmed)
        }
    }

    fun updateSortingEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setSortingEnabled(enabled)
        }
    }

    fun updateGroupingEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setGroupingEnabled(enabled)
        }
    }

    fun updateGroupingLevel(level: GroupingLevel) {
        viewModelScope.launch {
            settingsRepository.setGroupingLevel(level)
        }
    }

    fun updateThreadCount(count: Int) {
        viewModelScope.launch {
            settingsRepository.setAnalysisThreadCount(count)
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            try {
                cacheRepository.clearAll()
                _uiState.update { it.copy(cachedScoreCount = 0) }
            } catch (e: Exception) {
                // Cache clear failure is non-critical
            }
        }
    }

    fun updatePhoneCollectionAction(action: String) {
        viewModelScope.launch { settingsRepository.setPhoneCollectionAction(action) }
    }

    fun updatePhoneDeleteConfirm(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setPhoneDeleteConfirmEnabled(enabled) }
    }

    fun updatePhoneSortByOrientation(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setPhoneSortByOrientation(enabled) }
    }

    fun updatePhoneCollectionUri(uri: String?) {
        viewModelScope.launch { settingsRepository.setPhoneCollectionUri(uri) }
    }

    private fun refreshCachedScoreCount() {
        viewModelScope.launch {
            try {
                cacheRepository.pruneToLimit()
                // CacheRepository does not expose a count method directly,
                // so we note a zero count initially. The UI can refresh this
                // by calling refreshCachedScoreCount() when the settings screen
                // becomes visible.
                _uiState.update { it.copy(cachedScoreCount = 0) }
            } catch (e: Exception) {
                // Non-critical
            }
        }
    }
}
