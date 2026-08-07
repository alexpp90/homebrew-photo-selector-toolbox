package com.photoselectortoolbox.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photoselector.core.model.ExifData
import com.photoselectortoolbox.data.cache.ScoreDao
import com.photoselectortoolbox.data.model.ImageItem
import com.photoselectortoolbox.data.model.ScanResult
import com.photoselectortoolbox.data.repository.CacheRepository
import com.photoselectortoolbox.data.repository.ImageRepository
import com.photoselectortoolbox.data.repository.SettingsRepository
import com.photoselectortoolbox.data.source.googledrive.GoogleDriveAuth
import com.photoselectortoolbox.data.source.googledrive.GoogleDriveClient
import com.photoselectortoolbox.data.source.googledrive.GoogleDriveImageSource
import com.photoselectortoolbox.domain.grouping.GroupingLevel
import com.photoselectortoolbox.domain.grouping.ImageGrouper
import com.photoselectortoolbox.domain.interaction.FilingAction
import com.photoselectortoolbox.domain.usecase.MoveToSelectionUseCase
import com.photoselectortoolbox.domain.usecase.ScanImagesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
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

/**
 * Which of the three frames on screen a control refers to.
 *
 * Named rather than passed as an index because "1" and "the previous frame" are
 * different things: the index shifts every time the user advances, and every
 * bug in this area has come from an index that outlived the mutation it was
 * read before.
 */
enum class SelectorFrame { PREVIOUS, CURRENT, NEXT }

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
    val groups: List<List<Int>> = emptyList(),
    val fullscreenButtonsEnabled: Boolean = true,
    /** Which of Copy and Move is the emphasised filing control (persisted). */
    val filingAction: FilingAction = FilingAction.DEFAULT,
    /** Whether the one-time on-image nav arrows have already been shown. */
    val hasSeenNavHint: Boolean = false,
    /** Whether the filmstrip along the bottom edge is shown (persisted). */
    val filmstripVisible: Boolean = true,
    /** Whether the readout block beside the current frame is shown (persisted). */
    val detailsVisible: Boolean = true,
    /** Whether Previous and Next carry their value overlay (persisted). */
    val overlayValuesVisible: Boolean = true,
    /**
     * Which frame, if any, is currently filling the image region.
     *
     * Null is the normal three-up state. Maximise is transient view state, not
     * a preference: it is a thing you do to look closer at one frame, and it
     * should not survive a relaunch the way a layout choice would.
     */
    val maximisedFrame: SelectorFrame? = null,
    /** Whether the one-time fullscreen gesture hint has already been dismissed. */
    val hasSeenFullscreenHint: Boolean = false,
) {
    /** The frame being judged, or null when no folder is loaded. */
    val currentImage: ImageItem?
        get() = images.getOrNull(currentIndex)

    /** The frame before the current one, or null at the start of the folder. */
    val previousImage: ImageItem?
        get() = images.getOrNull(currentIndex - 1)

    /** The frame after the current one, or null at the end of the folder. */
    val nextImage: ImageItem?
        get() = images.getOrNull(currentIndex + 1)

    /** Human-readable position, 1-based, as shown in the app bar and rails. */
    val position: Int
        get() = if (images.isEmpty()) 0 else currentIndex + 1

    /** True once at least one frame carries scores, which is what reveals the legend. */
    val hasAnyScores: Boolean
        get() = images.any { it.scanResult != null }
}

@HiltViewModel
class SelectorViewModel @Inject constructor(
    private val imageRepository: ImageRepository,
    private val scanImagesUseCase: ScanImagesUseCase,
    private val moveToSelectionUseCase: MoveToSelectionUseCase,
    private val cacheRepository: CacheRepository,
    private val settingsRepository: SettingsRepository,
    private val scoreDao: ScoreDao,
    val driveAuth: GoogleDriveAuth,
    val driveClient: GoogleDriveClient,
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
            settingsRepository.fullscreenButtonsEnabled.collect { enabled ->
                _uiState.update { it.copy(fullscreenButtonsEnabled = enabled) }
            }
        }

        viewModelScope.launch {
            settingsRepository.filingAction.collect { action ->
                _uiState.update { it.copy(filingAction = action) }
            }
        }

        viewModelScope.launch {
            settingsRepository.overlayValuesVisible.collect { visible ->
                _uiState.update { it.copy(overlayValuesVisible = visible) }
            }
        }

        viewModelScope.launch {
            settingsRepository.filmstripVisible.collect { visible ->
                _uiState.update { it.copy(filmstripVisible = visible) }
            }
        }

        viewModelScope.launch {
            settingsRepository.detailsVisible.collect { visible ->
                _uiState.update { it.copy(detailsVisible = visible) }
            }
        }

        viewModelScope.launch {
            settingsRepository.hasSeenFullscreenGestureHint.collect { seen ->
                _uiState.update { it.copy(hasSeenFullscreenHint = seen) }
            }
        }

        viewModelScope.launch {
            settingsRepository.hasSeenNavHint.collect { seen ->
                _uiState.update { it.copy(hasSeenNavHint = seen) }
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
        // Handle Google Drive URIs
        if (GoogleDriveImageSource.isDriveUri(uri)) {
            val folderId = GoogleDriveImageSource.extractId(uri) ?: return
            selectDriveFolder(folderId, "Google Drive")
            return
        }

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

    /** Select a Google Drive folder by its Drive folder ID. */
    fun selectDriveFolder(folderId: String, folderName: String) {
        val driveUri = GoogleDriveImageSource.buildUri(folderId)
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    error = null,
                    folderUri = driveUri.toString(),
                    folderName = folderName,
                )
            }

            settingsRepository.setLastFolderUri(driveUri.toString())

            try {
                imageRepository.discoverImages(driveUri).collect { images ->
                    _uiState.update {
                        it.copy(
                            images = images,
                            currentIndex = 0,
                            isLoading = false,
                        )
                    }
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
                Log.e("SelectorViewModel", "Failed to load Drive folder $folderId", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Failed to load Google Drive images: ${e.message}",
                    )
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

    fun startScan(aestheticEnabled: Boolean = false) {
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
                scanImagesUseCase(images, aestheticEnabled).collect { progress ->
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

    /**
     * Delete the current frame, skipping the dialog only when the file can be
     * recovered from the system trash.
     *
     * Called from the labelled Delete control, the `Del` key and the context
     * menu — never from a swipe. A swipe used to invoke this on the phone
     * layout, which made the same horizontal gesture mean "next frame" in the
     * viewer and "destroy this file" in the feed; that binding is gone.
     */
    fun requestDelete() {
        val state = _uiState.value
        if (state.images.isEmpty()) return

        val currentImage = state.images[state.currentIndex]
        val uri = Uri.parse(currentImage.uri)

        if (imageRepository.canTrash(uri)) {
            deleteCurrentImage()
        } else {
            showDeleteConfirmation()
        }
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

    /**
     * Fill the image region with one frame, or return to three-up.
     *
     * Passing the frame that is already maximised toggles back out, so the same
     * control and the same key both open and close it.
     */
    fun toggleMaximised(frame: SelectorFrame) {
        _uiState.update {
            it.copy(maximisedFrame = if (it.maximisedFrame == frame) null else frame)
        }
    }

    /** Leave the maximised state, if in it. Bound to Escape. */
    fun clearMaximised() {
        if (_uiState.value.maximisedFrame != null) {
            _uiState.update { it.copy(maximisedFrame = null) }
        }
    }

    /** Persist that the one-time on-image navigation arrows have now been shown. */
    fun markNavHintSeen() {
        viewModelScope.launch {
            if (!settingsRepository.hasSeenNavHint.first()) {
                settingsRepository.setHasSeenNavHint(true)
            }
        }
    }

    /** Show or hide the filmstrip; the choice survives relaunch. */
    fun toggleFilmstrip() {
        viewModelScope.launch {
            settingsRepository.setFilmstripVisible(!settingsRepository.filmstripVisible.first())
        }
    }

    /** Show or hide the readout block; the choice survives relaunch. */
    fun toggleDetails() {
        viewModelScope.launch {
            settingsRepository.setDetailsVisible(!settingsRepository.detailsVisible.first())
        }
    }

    /** Show or hide the neighbour value overlays; the choice survives relaunch. */
    fun toggleOverlayValues() {
        viewModelScope.launch {
            settingsRepository.setOverlayValuesVisible(
                !settingsRepository.overlayValuesVisible.first()
            )
        }
    }

    /** Persist that the fullscreen gesture hint has been dismissed. */
    fun markFullscreenHintSeen() {
        viewModelScope.launch {
            if (!settingsRepository.hasSeenFullscreenGestureHint.first()) {
                settingsRepository.setHasSeenFullscreenGestureHint(true)
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun setError(message: String) {
        _uiState.update { it.copy(error = message) }
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
