package com.phototok.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phototok.data.source.googledrive.DriveFile
import com.phototok.data.source.googledrive.GoogleDriveClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One level of the Drive folder navigation stack. */
data class DriveBreadcrumb(val id: String, val name: String)

data class DriveFolderPickerUiState(
    val isLoading: Boolean = true,
    val folders: List<DriveFile> = emptyList(),
    val error: String? = null,
    val breadcrumb: List<DriveBreadcrumb> = listOf(ROOT),
) {
    val currentFolder: DriveBreadcrumb get() = breadcrumb.last()
    val canGoBack: Boolean get() = breadcrumb.size > 1

    companion object {
        val ROOT = DriveBreadcrumb("root", "My Drive")
    }
}

/**
 * Backs the Google Drive folder picker dialog, so the Compose layer never
 * holds a reference to the Drive client.
 */
@HiltViewModel
class DriveFolderPickerViewModel @Inject constructor(
    private val driveClient: GoogleDriveClient,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DriveFolderPickerUiState())
    val uiState: StateFlow<DriveFolderPickerUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        loadCurrentFolder()
    }

    /** Reset to the Drive root (called when the dialog is re-opened). */
    fun reset() {
        _uiState.update { DriveFolderPickerUiState() }
        loadCurrentFolder()
    }

    fun enterFolder(folder: DriveFile) {
        _uiState.update {
            it.copy(breadcrumb = it.breadcrumb + DriveBreadcrumb(folder.id, folder.name))
        }
        loadCurrentFolder()
    }

    fun navigateBack() {
        val state = _uiState.value
        if (!state.canGoBack) return
        _uiState.update { it.copy(breadcrumb = it.breadcrumb.dropLast(1)) }
        loadCurrentFolder()
    }

    private fun loadCurrentFolder() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val folders = driveClient.listFolders(_uiState.value.currentFolder.id)
                _uiState.update { it.copy(isLoading = false, folders = folders) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        folders = emptyList(),
                        error = e.message ?: "Failed to load folders",
                    )
                }
            }
        }
    }
}
