package com.phototok.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phototok.data.model.ImageItem
import com.phototok.data.repository.ImageRepository
import com.phototok.data.source.SelectionListing
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SelectionViewerUiState(
    /** Whether the read-only selection viewer is currently shown. */
    val isOpen: Boolean = false,
    val images: List<ImageItem> = emptyList(),
    val folderName: String = "",
    val currentIndex: Int = 0,
    val feedback: ActionFeedback? = null,
)

/**
 * Read-only viewer for the PhotoTok_Selection folder (view + back only).
 * Extracted from PhoneModeViewModel so the feed ViewModel stays focused on
 * the swipe feed; all folder traversal happens in the data layer.
 */
@HiltViewModel
class SelectionViewerViewModel @Inject constructor(
    private val imageRepository: ImageRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SelectionViewerUiState())
    val uiState: StateFlow<SelectionViewerUiState> = _uiState.asStateFlow()

    /**
     * Open the selection folder inside [targetFolderUri] (the collection target,
     * falling back to the source folder). Local folders only.
     */
    fun open(targetFolderUri: String?) {
        if (targetFolderUri == null) {
            _uiState.update {
                it.copy(feedback = ActionFeedback("No selection folder yet", isError = true))
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isOpen = true, currentIndex = 0) }
            try {
                when (val listing = imageRepository.listSelectionImages(Uri.parse(targetFolderUri))) {
                    is SelectionListing.Available -> _uiState.update {
                        it.copy(images = listing.images, folderName = listing.folderName)
                    }
                    SelectionListing.NotSupported -> _uiState.update {
                        it.copy(
                            isOpen = false,
                            feedback = ActionFeedback("Selection view is local-only", isError = true),
                        )
                    }
                    SelectionListing.Missing -> _uiState.update {
                        it.copy(
                            isOpen = false,
                            feedback = ActionFeedback("Selection folder is empty", isError = true),
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isOpen = false,
                        feedback = ActionFeedback(
                            "Failed to open selection: ${e.message}",
                            isError = true,
                        ),
                    )
                }
            }
        }
    }

    fun close() {
        _uiState.update {
            it.copy(isOpen = false, images = emptyList(), currentIndex = 0)
        }
    }

    fun navigateTo(index: Int) {
        if (index in _uiState.value.images.indices) {
            _uiState.update { it.copy(currentIndex = index) }
        }
    }

    fun clearFeedback() {
        _uiState.update { it.copy(feedback = null) }
    }
}
