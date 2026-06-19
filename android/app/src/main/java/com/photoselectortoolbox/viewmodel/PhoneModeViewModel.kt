package com.photoselectortoolbox.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photoselectortoolbox.data.model.ExifData
import com.photoselectortoolbox.data.model.ImageItem
import com.photoselectortoolbox.data.repository.ImageRepository
import com.photoselectortoolbox.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class PhoneModeUiState(
    val images: List<ImageItem> = emptyList(),
    val currentIndex: Int = 0,
    val isLoading: Boolean = false,
    val sourceFolderUri: String? = null,
    val sourceFolderName: String = "",
    val collectionFolderUri: String? = null,
    val collectionFolderName: String = "",
    val error: String? = null,
    val showDeleteConfirmation: Boolean = false,
    val showGestureTutorial: Boolean = false,
    val lastActionFeedback: ActionFeedback? = null,
    // Settings (observed)
    val collectionAction: String = "copy", // "copy" or "move"
    val deleteConfirmEnabled: Boolean = true,
    val sortByOrientation: Boolean = true,
    /** Index where portrait section starts (-1 = no split). */
    val portraitSectionStart: Int = -1,
)

data class ActionFeedback(
    val message: String,
    val isError: Boolean = false,
    val id: Long = System.nanoTime(),
)

@HiltViewModel
class PhoneModeViewModel @Inject constructor(
    private val imageRepository: ImageRepository,
    private val settingsRepository: SettingsRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PhoneModeUiState())
    val uiState: StateFlow<PhoneModeUiState> = _uiState.asStateFlow()

    private val loadedExifCache = object : LinkedHashMap<String, ExifData>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ExifData>?): Boolean =
            size > MAX_EXIF_CACHE_SIZE
    }

    companion object {
        private const val TAG = "PhoneModeVM"
        private const val MAX_EXIF_CACHE_SIZE = 30
        private const val ONE_WEEK_MS = 7L * 24 * 60 * 60 * 1000
    }

    init {
        observeSettings()
        restoreLastFolders()
    }

    // ── Settings observation ──────────────────────────────────────────────

    private fun observeSettings() {
        viewModelScope.launch {
            settingsRepository.phoneCollectionAction.collect { action ->
                _uiState.update { it.copy(collectionAction = action) }
            }
        }
        viewModelScope.launch {
            settingsRepository.phoneDeleteConfirmEnabled.collect { enabled ->
                _uiState.update { it.copy(deleteConfirmEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            settingsRepository.phoneSortByOrientation.collect { enabled ->
                _uiState.update { it.copy(sortByOrientation = enabled) }
                if (_uiState.value.images.isNotEmpty()) {
                    resortImages()
                }
            }
        }
        viewModelScope.launch {
            settingsRepository.phoneCollectionUri.collect { uri ->
                val name = if (uri != null) {
                    try {
                        DocumentFile.fromTreeUri(context, Uri.parse(uri))?.name ?: "Collection"
                    } catch (_: Exception) { "Collection" }
                } else ""
                _uiState.update {
                    it.copy(collectionFolderUri = uri, collectionFolderName = name)
                }
            }
        }
    }

    private fun restoreLastFolders() {
        viewModelScope.launch {
            val lastUri = settingsRepository.lastFolderUri.first()
            if (lastUri != null && _uiState.value.sourceFolderUri == null) {
                selectSourceFolder(Uri.parse(lastUri))
            }
        }
    }

    // ── Folder selection ──────────────────────────────────────────────────

    fun selectSourceFolder(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, sourceFolderUri = uri.toString()) }

            try {
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, takeFlags)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to persist URI permission for $uri", e)
            }

            val folderDoc = try {
                DocumentFile.fromTreeUri(context, uri)
            } catch (e: SecurityException) {
                null
            }

            if (folderDoc == null || !folderDoc.exists()) {
                _uiState.update {
                    it.copy(isLoading = false, error = "Cannot access folder. Permission may have been revoked.")
                }
                return@launch
            }

            _uiState.update { it.copy(sourceFolderName = folderDoc.name ?: "Photos") }
            settingsRepository.setLastFolderUri(uri.toString())

            try {
                imageRepository.discoverImages(uri).collect { images ->
                    val sorted = sortImages(images)
                    _uiState.update {
                        it.copy(
                            images = sorted.first,
                            portraitSectionStart = sorted.second,
                            currentIndex = 0,
                            isLoading = false,
                        )
                    }
                    checkGestureTutorial()
                    loadExifForCurrent()
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Failed to load images: ${e.message}") }
            }
        }
    }

    fun selectCollectionFolder(uri: Uri) {
        viewModelScope.launch {
            try {
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, takeFlags)
            } catch (_: Exception) {}

            val name = DocumentFile.fromTreeUri(context, uri)?.name ?: "Collection"
            settingsRepository.setPhoneCollectionUri(uri.toString())
            _uiState.update { it.copy(collectionFolderUri = uri.toString(), collectionFolderName = name) }
        }
    }

    // ── Sorting ───────────────────────────────────────────────────────────

    private suspend fun sortImages(images: List<ImageItem>): Pair<List<ImageItem>, Int> {
        val sortByOrientation = settingsRepository.phoneSortByOrientation.first()
        if (!sortByOrientation) return Pair(images, -1)

        val landscape = images.filter { it.isLandscape }
        val portrait = images.filter { !it.isLandscape }
        val result = landscape + portrait
        val splitIndex = if (portrait.isEmpty()) -1 else landscape.size
        return Pair(result, splitIndex)
    }

    private fun resortImages() {
        viewModelScope.launch {
            val current = _uiState.value
            if (current.images.isEmpty()) return@launch
            val currentUri = current.images.getOrNull(current.currentIndex)?.uri
            val sorted = sortImages(current.images)
            val newIndex = if (currentUri != null) {
                sorted.first.indexOfFirst { it.uri == currentUri }.coerceAtLeast(0)
            } else 0
            _uiState.update {
                it.copy(images = sorted.first, portraitSectionStart = sorted.second, currentIndex = newIndex)
            }
        }
    }

    // ── Navigation ────────────────────────────────────────────────────────

    fun navigateToImage(index: Int) {
        if (index in _uiState.value.images.indices) {
            _uiState.update { it.copy(currentIndex = index) }
            loadExifForCurrent()
        }
    }

    // ── Actions ───────────────────────────────────────────────────────────

    /** Double-tap: copy or move to collection. */
    fun addToCollection() {
        val state = _uiState.value
        if (state.images.isEmpty()) return

        val targetUri = state.collectionFolderUri ?: state.sourceFolderUri ?: return
        val currentImage = state.images[state.currentIndex]
        val isCopy = state.collectionAction == "copy"

        viewModelScope.launch {
            try {
                val sortingEnabled = settingsRepository.sortingEnabled.first()
                val folderUri = Uri.parse(targetUri)

                if (isCopy) {
                    imageRepository.copyImage(context, Uri.parse(currentImage.uri), folderUri, sortingEnabled)
                } else {
                    imageRepository.moveImage(context, Uri.parse(currentImage.uri), folderUri, sortingEnabled)
                }

                if (!isCopy) {
                    removeCurrentImageFromList()
                }

                val verb = if (isCopy) "Copied" else "Moved"
                _uiState.update { it.copy(lastActionFeedback = ActionFeedback("$verb to collection")) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(lastActionFeedback = ActionFeedback("Failed: ${e.message}", isError = true))
                }
            }
        }
    }

    /** Swipe left: request delete. */
    fun requestDelete() {
        val state = _uiState.value
        if (state.images.isEmpty()) return

        if (state.deleteConfirmEnabled) {
            _uiState.update { it.copy(showDeleteConfirmation = true) }
        } else {
            confirmDelete()
        }
    }

    fun dismissDeleteConfirmation() {
        _uiState.update { it.copy(showDeleteConfirmation = false) }
    }

    fun confirmDelete() {
        val state = _uiState.value
        if (state.images.isEmpty()) return

        val imageToDelete = state.images[state.currentIndex]
        viewModelScope.launch {
            _uiState.update { it.copy(showDeleteConfirmation = false) }
            try {
                val deleted = imageRepository.deleteImage(context, Uri.parse(imageToDelete.uri))
                if (deleted) {
                    removeCurrentImageFromList()
                    _uiState.update { it.copy(lastActionFeedback = ActionFeedback("Deleted")) }
                } else {
                    _uiState.update {
                        it.copy(lastActionFeedback = ActionFeedback("Delete failed", isError = true))
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(lastActionFeedback = ActionFeedback("Delete failed: ${e.message}", isError = true))
                }
            }
        }
    }

    fun clearFeedback() {
        _uiState.update { it.copy(lastActionFeedback = null) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    // ── Gesture tutorial ──────────────────────────────────────────────────

    private fun checkGestureTutorial() {
        viewModelScope.launch {
            val lastTs = settingsRepository.phoneGestureTutorialTs.first()
            val now = System.currentTimeMillis()
            if (lastTs == 0L || (now - lastTs) > ONE_WEEK_MS) {
                _uiState.update { it.copy(showGestureTutorial = true) }
            }
        }
    }

    fun dismissGestureTutorial() {
        viewModelScope.launch {
            settingsRepository.setPhoneGestureTutorialTs(System.currentTimeMillis())
            _uiState.update { it.copy(showGestureTutorial = false) }
        }
    }

    // ── Internal helpers ──────────────────────────────────────────────────

    private fun removeCurrentImageFromList() {
        val state = _uiState.value
        val updatedImages = state.images.toMutableList().apply {
            removeAt(state.currentIndex)
        }
        val newIndex = state.currentIndex.coerceAtMost(updatedImages.size - 1).coerceAtLeast(0)
        // Recompute portrait section start
        val newSplit = if (state.sortByOrientation && updatedImages.isNotEmpty()) {
            val firstPortrait = updatedImages.indexOfFirst { !it.isLandscape }
            if (firstPortrait >= 0) firstPortrait else -1
        } else -1

        _uiState.update {
            it.copy(images = updatedImages, currentIndex = newIndex, portraitSectionStart = newSplit)
        }
        loadExifForCurrent()
    }

    private fun loadExifForCurrent() {
        val state = _uiState.value
        if (state.images.isEmpty()) return

        val indices = listOf(state.currentIndex, state.currentIndex - 1, state.currentIndex + 1)
            .filter { it in state.images.indices }

        viewModelScope.launch {
            indices.forEach { idx ->
                val image = state.images[idx]
                if (image.exifData == null) {
                    val cached = loadedExifCache[image.uri]
                    if (cached != null) {
                        updateImageExif(image.uri, cached)
                    } else {
                        val exif = withContext(Dispatchers.IO) {
                            imageRepository.getExifData(context, Uri.parse(image.uri))
                        }
                        if (exif != null) {
                            loadedExifCache[image.uri] = exif
                            updateImageExif(image.uri, exif)
                        }
                    }
                }
            }
        }
    }

    private fun updateImageExif(uri: String, exif: ExifData) {
        _uiState.update { state ->
            val updatedImages = state.images.map { img ->
                if (img.uri == uri) img.copy(exifData = exif) else img
            }
            state.copy(images = updatedImages)
        }
    }
}
