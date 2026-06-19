package com.photoselectortoolbox.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photoselectortoolbox.data.cache.ScoreDao
import com.photoselectortoolbox.data.model.ExifData
import com.photoselectortoolbox.data.model.ImageItem
import com.photoselectortoolbox.data.model.ScanResult
import com.photoselectortoolbox.data.repository.CacheRepository
import com.photoselectortoolbox.domain.grouping.GroupingLevel
import com.photoselectortoolbox.data.repository.ImageRepository
import com.photoselectortoolbox.data.repository.SettingsRepository
import com.photoselectortoolbox.domain.grouping.ImageGrouper
import com.photoselectortoolbox.domain.usecase.MoveToSelectionUseCase
import com.photoselectortoolbox.domain.usecase.ScanImagesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class SelectorUiState(
    val images: List<ImageItem> = emptyList(),
    val currentIndex: Int = 0,
    val isLoading: Boolean = false,
    val isScanRunning: Boolean = false,
    val scanProgress: Float = 0f,
    val scanStatusText: String = "",
    val folderUri: String? = null,
    val folderName: String = "",
    val error: String? = null,
    val showDeleteConfirmation: Boolean = false,
    val groupingEnabled: Boolean = false,
    val groups: List<List<Int>> = emptyList()
)

@HiltViewModel
class SelectorViewModel @Inject constructor(
    private val imageRepository: ImageRepository,
    private val scanImagesUseCase: ScanImagesUseCase,
    private val moveToSelectionUseCase: MoveToSelectionUseCase,
    private val cacheRepository: CacheRepository,
    private val settingsRepository: SettingsRepository,
    private val scoreDao: ScoreDao,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(SelectorUiState())
    val uiState: StateFlow<SelectorUiState> = _uiState.asStateFlow()

    private var scanJob: Job? = null
    private var lastDeletedImage: ImageItem? = null
    private var lastDeletedIndex: Int? = null
    private val imageGrouper = ImageGrouper(context)

    init {
        viewModelScope.launch {
            combine(
                settingsRepository.groupingEnabled,
                settingsRepository.groupingLevel
            ) { enabled, level ->
                Pair(enabled, level)
            }.collect { (enabled, level) ->
                _uiState.update { it.copy(groupingEnabled = enabled) }
                if (_uiState.value.images.isNotEmpty()) {
                    if (enabled) {
                        recomputeGroups(level)
                    } else {
                        _uiState.update { it.copy(groups = emptyList()) }
                    }
                }
            }
        }

        viewModelScope.launch {
            settingsRepository.lastFolderUri.collect { uri ->
                if (uri != null && _uiState.value.folderUri == null) {
                    selectFolder(Uri.parse(uri))
                }
            }
        }
    }

    fun selectFolder(uri: Uri) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    error = null,
                    folderUri = uri.toString()
                )
            }

            try {
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, takeFlags)
            } catch (e: Exception) {
                Log.e("SelectorViewModel", "Failed to persist URI permission for $uri", e)
            }

            val folderDoc = try {
                DocumentFile.fromTreeUri(context, uri)
            } catch (e: SecurityException) {
                Log.e("SelectorViewModel", "SecurityException loading folder $uri", e)
                null
            }

            if (folderDoc == null || !folderDoc.exists()) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Failed to load folder: permission revoked or directory deleted."
                    )
                }
                settingsRepository.setLastFolderUri(null)
                return@launch
            }

            val folderName = folderDoc.name ?: "Unknown"
            _uiState.update { it.copy(folderName = folderName) }

            settingsRepository.setLastFolderUri(uri.toString())

            try {
                imageRepository.discoverImages(uri).collect { images ->
                    _uiState.update {
                        it.copy(
                            images = images,
                            currentIndex = 0,
                            isLoading = false
                        )
                    }

                    // Restore cached scan scores immediately (#11)
                    restoreCachedScores()

                    loadMetadataForActiveRange()
                    val groupingEnabled = settingsRepository.groupingEnabled.first()
                    val groupingLevel = settingsRepository.groupingLevel.first()
                    if (groupingEnabled) {
                        recomputeGroups(groupingLevel)
                    } else {
                        _uiState.update { it.copy(groups = emptyList()) }
                    }
                }
            } catch (e: Exception) {
                Log.e("SelectorViewModel", "Failed to discover images in $uri", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Failed to load images: ${e.message}"
                    )
                }
                if (e is SecurityException) {
                    settingsRepository.setLastFolderUri(null)
                }
            }
        }
    }

    fun navigateToImage(index: Int) {
        val images = _uiState.value.images
        if (index in images.indices) {
            _uiState.update { it.copy(currentIndex = index) }
            loadMetadataForActiveRange()
        }
    }

    fun navigateNext() {
        val state = _uiState.value
        if (state.currentIndex < state.images.size - 1) {
            _uiState.update { it.copy(currentIndex = state.currentIndex + 1) }
            loadMetadataForActiveRange()
        }
    }

    fun navigatePrevious() {
        val state = _uiState.value
        if (state.currentIndex > 0) {
            _uiState.update { it.copy(currentIndex = state.currentIndex - 1) }
            loadMetadataForActiveRange()
        }
    }

    fun startScan() {
        val images = _uiState.value.images
        if (images.isEmpty()) return

        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isScanRunning = true,
                    scanProgress = 0f,
                    scanStatusText = "Starting scan...",
                    error = null
                )
            }

            try {
                scanImagesUseCase(images).collect { progress ->
                    val fraction = if (progress.total > 0) {
                        progress.processed.toFloat() / progress.total.toFloat()
                    } else {
                        0f
                    }

                    val statusText = if (progress.currentFile.isNotEmpty()) {
                        "Analyzing ${progress.currentFile} (${progress.processed}/${progress.total})"
                    } else {
                        "Preparing scan..."
                    }

                    // Efficient update: use URI→index map instead of O(n) list scan
                    val currentImages = _uiState.value.images
                    val uriToIndex = currentImages.withIndex().associate { (i, img) -> img.uri to i }
                    val mutableImages = currentImages.toMutableList()
                    var changed = false

                    for ((uri, result) in progress.results) {
                        val idx = uriToIndex[uri] ?: continue
                        val existing = mutableImages[idx]
                        if (existing.scanResult == null) {
                            mutableImages[idx] = existing.copy(scanResult = result)
                            changed = true
                        }
                    }

                    _uiState.update {
                        it.copy(
                            scanProgress = fraction,
                            scanStatusText = statusText,
                            images = if (changed) mutableImages.toList() else it.images
                        )
                    }
                }

                _uiState.update {
                    it.copy(
                        isScanRunning = false,
                        scanProgress = 1f,
                        scanStatusText = "Scan complete"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isScanRunning = false,
                        scanStatusText = "",
                        error = "Scan failed: ${e.message}"
                    )
                }
            }
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
        scanJob = null
        _uiState.update {
            it.copy(
                isScanRunning = false,
                scanStatusText = "Scan cancelled"
            )
        }
    }

    fun showDeleteConfirmation() {
        _uiState.update { it.copy(showDeleteConfirmation = true) }
    }

    fun dismissDeleteConfirmation() {
        _uiState.update { it.copy(showDeleteConfirmation = false) }
    }

    fun deleteCurrentImage() {
        val state = _uiState.value
        if (state.images.isEmpty()) return

        val imageToDelete = state.images[state.currentIndex]

        viewModelScope.launch {
            _uiState.update { it.copy(showDeleteConfirmation = false) }

            try {
                val uri = Uri.parse(imageToDelete.uri)
                val deleted = imageRepository.deleteImage(context, uri)

                if (deleted) {
                    lastDeletedImage = imageToDelete
                    lastDeletedIndex = state.currentIndex

                    val updatedImages = state.images.toMutableList().apply {
                        removeAt(state.currentIndex)
                    }
                    val newIndex = state.currentIndex.coerceAtMost(updatedImages.size - 1)
                        .coerceAtLeast(0)

                    _uiState.update {
                        it.copy(
                            images = updatedImages,
                            currentIndex = newIndex
                        )
                    }
                    loadMetadataForActiveRange()
                } else {
                    _uiState.update {
                        it.copy(error = "Failed to delete ${imageToDelete.fileName}")
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = "Delete failed: ${e.message}")
                }
            }
        }
    }

    fun moveToSelection() {
        performSelectionOperation(copy = false)
    }

    fun copyToSelection() {
        performSelectionOperation(copy = true)
    }

    private fun performSelectionOperation(copy: Boolean) {
        val state = _uiState.value
        if (state.images.isEmpty() || state.folderUri == null) return

        val currentImage = state.images[state.currentIndex]

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            try {
                val sortingEnabled = settingsRepository.sortingEnabled.first()
                val folderUri = Uri.parse(state.folderUri)

                if (copy) {
                    imageRepository.copyImage(context, Uri.parse(currentImage.uri), folderUri, sortingEnabled)
                } else {
                    imageRepository.moveImage(context, Uri.parse(currentImage.uri), folderUri, sortingEnabled)
                }

                if (!copy) {
                    val updatedImages = state.images.toMutableList().apply {
                        removeAt(state.currentIndex)
                    }
                    val newIndex = state.currentIndex.coerceAtMost(updatedImages.size - 1)
                        .coerceAtLeast(0)

                    _uiState.update {
                        it.copy(
                            images = updatedImages,
                            currentIndex = newIndex,
                            isLoading = false
                        )
                    }
                    loadMetadataForActiveRange()
                } else {
                    _uiState.update { it.copy(isLoading = false) }
                }
            } catch (e: Exception) {
                val operation = if (copy) "Copy" else "Move"
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "$operation failed: ${e.message}"
                    )
                }
            }
        }
    }

    fun clearScores() {
        viewModelScope.launch {
            try {
                cacheRepository.clearAll()

                val clearedImages = _uiState.value.images.map { image ->
                    image.copy(scanResult = null)
                }
                _uiState.update { it.copy(images = clearedImages) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = "Failed to clear cache: ${e.message}")
                }
            }
        }
    }

    fun toggleGrouping() {
        viewModelScope.launch {
            val current = settingsRepository.groupingEnabled.first()
            settingsRepository.setGroupingEnabled(!current)
        }
    }

    fun setGroupingLevel(level: GroupingLevel) {
        viewModelScope.launch {
            settingsRepository.setGroupingLevel(level)
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private suspend fun recomputeGroups(level: GroupingLevel) {
        val images = _uiState.value.images
        if (images.isEmpty()) {
            _uiState.update { it.copy(groups = emptyList(), groupingEnabled = true) }
            return
        }

        try {
            val groupedImages = imageGrouper.groupImages(images, level)

            val indexGroups = groupedImages.map { group ->
                group.mapNotNull { groupedImage ->
                    images.indexOfFirst { it.uri == groupedImage.uri }.takeIf { it >= 0 }
                }
            }.filter { it.isNotEmpty() }

            _uiState.update {
                it.copy(
                    groups = indexGroups,
                    groupingEnabled = true
                )
            }
        } catch (e: Exception) {
            _uiState.update {
                it.copy(error = "Grouping failed: ${e.message}")
            }
        }
    }

    private val loadedExifCache = object : LinkedHashMap<String, ExifData>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ExifData>?): Boolean {
            return size > MAX_EXIF_CACHE_SIZE
        }
    }

    private fun loadMetadataForActiveRange() {
        val state = _uiState.value
        val images = state.images
        if (images.isEmpty()) return

        val indicesToLoad = listOf(state.currentIndex, state.currentIndex - 1, state.currentIndex + 1)
            .filter { it in images.indices }

        viewModelScope.launch {
            indicesToLoad.forEach { index ->
                val image = images[index]
                if (image.exifData == null) {
                    val cachedExif = loadedExifCache[image.uri]
                    if (cachedExif != null) {
                        updateImageExif(image.uri, cachedExif)
                    } else {
                        val exif = imageRepository.getExifData(context, Uri.parse(image.uri))
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

    /**
     * Restore cached scan scores from Room on folder open (#11).
     * Checks each discovered image against the score cache and pre-populates
     * scanResult for any images with valid cached scores.
     */
    private suspend fun restoreCachedScores() {
        val images = _uiState.value.images
        if (images.isEmpty()) return

        val updatedImages = withContext(Dispatchers.IO) {
            images.map { image ->
                if (image.scanResult != null) return@map image

                val cached = try {
                    scoreDao.getScore(image.uri)
                } catch (e: Exception) {
                    null
                } ?: return@map image

                // Validate cache entry matches current file
                if (cached.fileSize != image.fileSize || cached.lastModified != image.lastModified) {
                    return@map image
                }

                image.copy(
                    scanResult = ScanResult(
                        filePath = image.uri,
                        sharpnessScore = cached.sharpnessScore,
                        noiseLevel = cached.noiseLevel,
                        highlightClipping = cached.highlightClipping,
                        shadowClipping = cached.shadowClipping
                    )
                )
            }
        }

        _uiState.update { it.copy(images = updatedImages) }
    }

    companion object {
        /** Maximum number of EXIF data entries to keep in memory. */
        private const val MAX_EXIF_CACHE_SIZE = 50
    }
}
