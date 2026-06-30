package com.phototok.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phototok.data.model.ExifData
import com.phototok.data.model.ImageItem
import com.phototok.data.model.RecentPath
import com.phototok.data.repository.ImageRepository
import com.phototok.data.repository.ImageRepositoryImpl
import com.phototok.data.repository.SettingsRepository
import com.phototok.data.source.ExternalStorageDetector
import com.phototok.data.source.ExternalVolume
import com.phototok.data.source.googledrive.GoogleDriveAuth
import com.phototok.data.source.googledrive.GoogleDriveClient
import com.phototok.data.source.googledrive.GoogleDriveImageSource
import com.phototok.domain.PhoneFeedOrdering
import com.phototok.domain.RelatedFiles
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
import kotlin.math.abs
import kotlinx.coroutines.Job

data class PhoneModeUiState(
    val images: List<ImageItem> = emptyList(),
    /** All images before file type filtering (kept for re-filtering). */
    val allImages: List<ImageItem> = emptyList(),
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
    val trashConfirmEnabled: Boolean = true,
    val directDeleteConfirmEnabled: Boolean = true,
    val sortByOrientation: Boolean = false,
    val randomizeOrder: Boolean = false,
    /** "all", "raw", or "jpg" */
    val fileTypeFilter: String = "all",
    /** Index where portrait section starts (-1 = no split). */
    val portraitSectionStart: Int = -1,
    /** Detected external/removable storage volumes. */
    val externalVolumes: List<ExternalVolume> = emptyList(),
    val showExifOverlay: Boolean = false,
    val pendingDeleteImage: ImageItem? = null,
    val pendingDeleteIndex: Int = -1,
    val pendingDeleteAllImagesIndex: Int = -1,
    val revertAllowedImageUri: String? = null,
    /** Sibling files removed alongside the pending delete (restored together on revert). */
    val pendingDeleteRelated: List<ImageItem> = emptyList(),
    /** Whether collection/delete actions also move/delete same-name sibling files. */
    val moveRelatedFiles: Boolean = false,
    // Recent folders (landing quick-select)
    val recentPaths: List<RecentPath> = emptyList(),
    val recentPathsEnabled: Boolean = true,
    val recentPathsCount: Int = 3,
    // Read-only selection-folder viewer
    val isViewingSelection: Boolean = false,
    val selectionImages: List<ImageItem> = emptyList(),
    val selectionFolderName: String = "",
    val selectionCurrentIndex: Int = 0,
)

/** True when there is a pending deletion that can still be reverted. */
val PhoneModeUiState.canRevert: Boolean
    get() = pendingDeleteImage != null

data class ActionFeedback(
    val message: String,
    val isError: Boolean = false,
    val id: Long = System.nanoTime(),
)

@HiltViewModel
class PhoneModeViewModel @Inject constructor(
    private val imageRepository: ImageRepository,
    private val settingsRepository: SettingsRepository,
    private val externalStorageDetector: ExternalStorageDetector,
    val driveAuth: GoogleDriveAuth,
    val driveClient: GoogleDriveClient,
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

        private val RAW_EXTENSIONS = setOf(
            "arw", "cr2", "cr3", "nef", "nrw", "orf", "raf", "rw2",
            "pef", "srw", "dng", "raw", "3fr", "ari", "bay", "cap",
            "iiq", "eip", "erf", "fff", "mef", "mdc", "mos", "mrw",
            "obm", "ptx", "pxn", "rwl", "rwz", "sr2", "srf", "x3f"
        )
        private val JPG_EXTENSIONS = setOf("jpg", "jpeg")
    }

    init {
        observeSettings()
        restoreLastFolders()
        detectExternalStorage()
    }

    // ── Settings observation ──────────────────────────────────────────────

    private fun observeSettings() {
        viewModelScope.launch {
            settingsRepository.phoneCollectionAction.collect { action ->
                _uiState.update { it.copy(collectionAction = action) }
            }
        }
        viewModelScope.launch {
            settingsRepository.phoneTrashConfirmEnabled.collect { enabled ->
                _uiState.update { it.copy(trashConfirmEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            settingsRepository.phoneDirectDeleteConfirmEnabled.collect { enabled ->
                _uiState.update { it.copy(directDeleteConfirmEnabled = enabled) }
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
            settingsRepository.phoneRandomizeOrder.collect { enabled ->
                _uiState.update { it.copy(randomizeOrder = enabled) }
                if (_uiState.value.images.isNotEmpty()) {
                    resortImages()
                }
            }
        }
        viewModelScope.launch {
            settingsRepository.phoneFileTypeFilter.collect { filter ->
                _uiState.update { it.copy(fileTypeFilter = filter) }
                if (_uiState.value.allImages.isNotEmpty()) {
                    refilterAndSort()
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
        viewModelScope.launch {
            settingsRepository.phoneShowExifOverlay.collect { enabled ->
                _uiState.update { it.copy(showExifOverlay = enabled) }
            }
        }
        viewModelScope.launch {
            settingsRepository.phoneMoveRelatedFiles.collect { enabled ->
                _uiState.update { it.copy(moveRelatedFiles = enabled) }
            }
        }
        viewModelScope.launch {
            settingsRepository.phoneRecentPathsEnabled.collect { enabled ->
                _uiState.update { it.copy(recentPathsEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            settingsRepository.phoneRecentPathsCount.collect { count ->
                _uiState.update { it.copy(recentPathsCount = count) }
            }
        }
        viewModelScope.launch {
            settingsRepository.phoneRecentPaths.collect { paths ->
                _uiState.update { it.copy(recentPaths = paths) }
            }
        }
    }

    /** Toggle the EXIF stats overlay (driven by tapping the Photo-Tok logo). */
    fun toggleExifOverlay() {
        viewModelScope.launch {
            settingsRepository.setPhoneShowExifOverlay(!_uiState.value.showExifOverlay)
        }
    }

    /** Re-open a previously used source folder from the recents list. */
    fun selectRecentPath(path: RecentPath) {
        val uri = Uri.parse(path.uri)
        if (GoogleDriveImageSource.isDriveUri(uri)) {
            val folderId = GoogleDriveImageSource.extractId(uri) ?: return
            selectDriveFolder(folderId, path.name.ifEmpty { "Google Drive" })
        } else {
            selectSourceFolder(uri)
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

    private fun detectExternalStorage() {
        viewModelScope.launch(Dispatchers.IO) {
            val volumes = externalStorageDetector.detectRemovableVolumes()
            _uiState.update { it.copy(externalVolumes = volumes) }
        }
    }

    // ── Folder selection ──────────────────────────────────────────────────

    fun selectSourceFolder(uri: Uri) {
        // Handle Google Drive URIs
        if (GoogleDriveImageSource.isDriveUri(uri)) {
            val folderId = GoogleDriveImageSource.extractId(uri) ?: return
            selectDriveFolder(folderId, "Google Drive")
            return
        }
        finalizePendingDelete()
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

            val folderName = folderDoc.name ?: "Photos"
            _uiState.update { it.copy(sourceFolderName = folderName) }
            settingsRepository.setLastFolderUri(uri.toString())
            settingsRepository.addRecentPath(uri.toString(), folderName)

            try {
                imageRepository.discoverImages(uri).collect { images ->
                    val filtered = filterByType(images, _uiState.value.fileTypeFilter)
                    val sorted = sortImages(filtered)
                    val lastPos = settingsRepository.getFolderLastPosition(uri.toString())
                    val startIndex = lastPos.coerceIn(0, (sorted.first.size - 1).coerceAtLeast(0))
                    _uiState.update {
                        it.copy(
                            allImages = images,
                            images = sorted.first,
                            portraitSectionStart = sorted.second,
                            currentIndex = startIndex,
                            isLoading = false,
                        )
                    }
                    checkGestureTutorial()
                    loadExifForCurrent()
                    loadDimensionsAsynchronously(images)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Failed to load images: ${e.message}") }
            }
        }
    }

    /** Select a Google Drive folder as the source. */
    fun selectDriveFolder(folderId: String, folderName: String) {
        val driveUri = GoogleDriveImageSource.buildUri(folderId)
        finalizePendingDelete()
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    error = null,
                    sourceFolderUri = driveUri.toString(),
                    sourceFolderName = folderName,
                )
            }
            settingsRepository.setLastFolderUri(driveUri.toString())
            settingsRepository.addRecentPath(driveUri.toString(), folderName)

            try {
                imageRepository.discoverImages(driveUri).collect { images ->
                    val filtered = filterByType(images, _uiState.value.fileTypeFilter)
                    val sorted = sortImages(filtered)
                    _uiState.update {
                        it.copy(
                            allImages = images,
                            images = sorted.first,
                            portraitSectionStart = sorted.second,
                            currentIndex = 0,
                            isLoading = false,
                        )
                    }
                    checkGestureTutorial()
                    loadExifForCurrent()
                    loadDimensionsAsynchronously(images)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = "Failed to load Drive images: ${e.message}")
                }
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
        val randomizeOrder = settingsRepository.phoneRandomizeOrder.first()
        val sortByOrientation = settingsRepository.phoneSortByOrientation.first()
        val result = PhoneFeedOrdering.order(images, randomizeOrder, sortByOrientation)
        return Pair(result.images, result.portraitSectionStart)
    }

    private fun resortImages() {
        finalizePendingDelete()
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
            val state = _uiState.value
            val newUri = state.images.getOrNull(index)?.uri
            if (state.pendingDeleteImage != null && newUri != state.revertAllowedImageUri) {
                finalizePendingDelete()
            }
            _uiState.update { it.copy(currentIndex = index) }
            loadExifForCurrent()
            // Persist position for this folder
            _uiState.value.sourceFolderUri?.let { uri ->
                viewModelScope.launch {
                    settingsRepository.setFolderLastPosition(uri, index)
                }
            }
        }
    }

    // ── Actions ───────────────────────────────────────────────────────────

    /**
     * Sibling files that share the same base name (stem) but differ in extension,
     * e.g. IMG_001.JPG and IMG_001.ARW. Computed from the unfiltered list so it is
     * independent of the user's file-type filter selection.
     */
    private fun relatedImages(target: ImageItem): List<ImageItem> =
        RelatedFiles.siblings(_uiState.value.allImages, target)

    /** Swipe right: copy or move the current photo (and optionally siblings) to collection. */
    fun addToCollection() {
        val state = _uiState.value
        if (state.images.isEmpty()) return

        val targetUri = state.collectionFolderUri ?: state.sourceFolderUri ?: return
        val currentImage = state.images[state.currentIndex]
        val isCopy = state.collectionAction == "copy"
        val related = if (state.moveRelatedFiles) relatedImages(currentImage) else emptyList()
        val targets = listOf(currentImage) + related

        viewModelScope.launch {
            try {
                val sortingEnabled = settingsRepository.sortingEnabled.first()
                val folderUri = Uri.parse(targetUri)

                targets.forEach { img ->
                    if (isCopy) {
                        imageRepository.copyImage(context, Uri.parse(img.uri), folderUri, sortingEnabled)
                    } else {
                        imageRepository.moveImage(context, Uri.parse(img.uri), folderUri, sortingEnabled)
                    }
                }

                if (!isCopy) {
                    removeImagesFromLists(targets.map { it.uri }.toSet())
                }

                val suffix = if (related.isNotEmpty()) " (+${related.size} related)" else ""
                val verb = if (isCopy) "Copied" else "Moved"
                _uiState.update {
                    it.copy(lastActionFeedback = ActionFeedback("$verb to collection$suffix"))
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(lastActionFeedback = ActionFeedback("Failed: ${e.message}", isError = true))
                }
            }
        }
    }

    /** Swipe left: request delete (does a temporary/pending delete). */
    fun requestDelete() {
        deleteImagePending()
    }

    private fun deleteImagePending() {
        val state = _uiState.value
        if (state.images.isEmpty()) return

        // 1. If there's already a pending deletion, finalize it first!
        finalizePendingDelete()

        val indexToDelete = state.currentIndex
        val imageToDelete = state.images[indexToDelete]
        val allImagesIndex = state.allImages.indexOfFirst { it.uri == imageToDelete.uri }

        // Sibling files removed together (independent of the file-type filter).
        val related = if (state.moveRelatedFiles) relatedImages(imageToDelete) else emptyList()
        val removedUris = (listOf(imageToDelete) + related).map { it.uri }.toSet()

        // 2. Perform the UI removal immediately
        val updatedImages = state.images.toMutableList().apply {
            removeAt(indexToDelete)
        }
        val updatedAll = state.allImages.filter { it.uri !in removedUris }
        val newIndex = indexToDelete.coerceAtMost(updatedImages.size - 1).coerceAtLeast(0)
        val newSplit = if (state.sortByOrientation && updatedImages.isNotEmpty()) {
            val firstPortrait = updatedImages.indexOfFirst { !it.isLandscape }
            if (firstPortrait >= 0) firstPortrait else -1
        } else -1

        val nextActiveImageUri = updatedImages.getOrNull(newIndex)?.uri

        _uiState.update {
            it.copy(
                images = updatedImages,
                allImages = updatedAll,
                currentIndex = newIndex,
                portraitSectionStart = newSplit,
                pendingDeleteImage = imageToDelete,
                pendingDeleteIndex = indexToDelete,
                pendingDeleteAllImagesIndex = allImagesIndex,
                pendingDeleteRelated = related,
                revertAllowedImageUri = nextActiveImageUri
            )
        }

        loadExifForCurrent()
    }

    fun finalizePendingDelete() {
        val state = _uiState.value
        val imageToDelete = state.pendingDeleteImage ?: return
        val related = state.pendingDeleteRelated

        // Clear the pending state from UI state first
        _uiState.update {
            it.copy(
                pendingDeleteImage = null,
                pendingDeleteIndex = -1,
                pendingDeleteAllImagesIndex = -1,
                pendingDeleteRelated = emptyList(),
                revertAllowedImageUri = null
            )
        }

        // Perform actual deletion on I/O thread (primary + any siblings)
        viewModelScope.launch(Dispatchers.IO) {
            (listOf(imageToDelete) + related).forEach { img ->
                try {
                    val deleted = imageRepository.deleteImage(context, Uri.parse(img.uri))
                    if (!deleted) {
                        Log.e(TAG, "Failed to delete file on disk: ${img.uri}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error deleting file: ${img.uri}", e)
                }
            }
        }
    }

    fun revertDelete() {
        val state = _uiState.value
        val imageToRestore = state.pendingDeleteImage ?: return
        val restoreIndex = state.pendingDeleteIndex
        val restoreAllIndex = state.pendingDeleteAllImagesIndex

        val updatedImages = state.images.toMutableList().apply {
            add(restoreIndex.coerceIn(0, size), imageToRestore)
        }
        val updatedAll = state.allImages.toMutableList().apply {
            if (restoreAllIndex in 0..size) {
                add(restoreAllIndex, imageToRestore)
            } else {
                add(imageToRestore)
            }
            // Restore any sibling files removed alongside the primary.
            state.pendingDeleteRelated.forEach { sibling ->
                if (none { it.uri == sibling.uri }) add(sibling)
            }
        }

        val newSplit = if (state.sortByOrientation && updatedImages.isNotEmpty()) {
            val firstPortrait = updatedImages.indexOfFirst { !it.isLandscape }
            if (firstPortrait >= 0) firstPortrait else -1
        } else -1

        _uiState.update {
            it.copy(
                images = updatedImages,
                allImages = updatedAll,
                currentIndex = restoreIndex.coerceIn(0, updatedImages.size - 1),
                portraitSectionStart = newSplit,
                pendingDeleteImage = null,
                pendingDeleteIndex = -1,
                pendingDeleteAllImagesIndex = -1,
                pendingDeleteRelated = emptyList(),
                revertAllowedImageUri = null
            )
        }
        loadExifForCurrent()
    }

    fun updateTrashConfirm(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setPhoneTrashConfirmEnabled(enabled)
        }
    }

    fun updateDirectDeleteConfirm(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setPhoneDirectDeleteConfirmEnabled(enabled)
        }
    }

    fun clearFeedback() {
        _uiState.update { it.copy(lastActionFeedback = null) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun setError(message: String) {
        _uiState.update { it.copy(error = message) }
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

    // ── File type filter ────────────────────────────────────────────────

    fun setFileTypeFilter(filter: String) {
        viewModelScope.launch {
            settingsRepository.setPhoneFileTypeFilter(filter)
        }
    }

    private fun filterByType(images: List<ImageItem>, filter: String): List<ImageItem> {
        return when (filter) {
            "raw" -> images.filter { img ->
                val ext = img.fileName.substringAfterLast('.', "").lowercase()
                ext in RAW_EXTENSIONS
            }
            "jpg" -> images.filter { img ->
                val ext = img.fileName.substringAfterLast('.', "").lowercase()
                ext in JPG_EXTENSIONS
            }
            else -> images
        }
    }

    private fun refilterAndSort() {
        viewModelScope.launch {
            val state = _uiState.value
            val currentUri = state.images.getOrNull(state.currentIndex)?.uri
            val filtered = filterByType(state.allImages, state.fileTypeFilter)
            val sorted = sortImages(filtered)
            val newIndex = if (currentUri != null) {
                sorted.first.indexOfFirst { it.uri == currentUri }.coerceAtLeast(0)
            } else 0
            _uiState.update {
                it.copy(images = sorted.first, portraitSectionStart = sorted.second, currentIndex = newIndex)
            }
            loadExifForCurrent()
        }
    }

    // ── Navigation helpers ───────────────────────────────────────────────

    /** Go back to the landing screen (clears images). */
    fun goBackToLanding() {
        finalizePendingDelete()
        _uiState.update { it.copy(images = emptyList(), allImages = emptyList(), currentIndex = 0) }
    }

    // ── Selection folder (read-only viewer) ───────────────────────────────

    /**
     * Open the PhotoTok_Selection folder in a read-only viewer (view + back only).
     * Local folders only; the folder lives inside the collection target (or source).
     */
    fun openSelectionFolder() {
        finalizePendingDelete()
        val state = _uiState.value
        val targetUri = state.collectionFolderUri ?: state.sourceFolderUri ?: run {
            _uiState.update {
                it.copy(lastActionFeedback = ActionFeedback("No selection folder yet", isError = true))
            }
            return
        }
        val parsed = Uri.parse(targetUri)
        if (GoogleDriveImageSource.isDriveUri(parsed)) {
            _uiState.update {
                it.copy(lastActionFeedback = ActionFeedback("Selection view is local-only", isError = true))
            }
            return
        }

        viewModelScope.launch {
            val selectionDir = withContext(Dispatchers.IO) {
                try {
                    DocumentFile.fromTreeUri(context, parsed)
                        ?.findFile(ImageRepositoryImpl.SELECTION_FOLDER_NAME)
                } catch (_: Exception) { null }
            }
            if (selectionDir == null || !selectionDir.exists()) {
                _uiState.update {
                    it.copy(lastActionFeedback = ActionFeedback("Selection folder is empty", isError = true))
                }
                return@launch
            }
            _uiState.update { it.copy(isViewingSelection = true, selectionCurrentIndex = 0) }
            try {
                // Enumerate the sub-folder directly: passing a child document URI to
                // discoverImages() would re-resolve to the tree root via fromTreeUri().
                val images = withContext(Dispatchers.IO) {
                    val acc = mutableListOf<ImageItem>()
                    enumerateSelectionImages(selectionDir, acc)
                    acc.sortedByDescending { it.lastModified }
                }
                _uiState.update {
                    it.copy(
                        selectionImages = images,
                        selectionFolderName = selectionDir.name ?: "Selection",
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isViewingSelection = false,
                        lastActionFeedback = ActionFeedback("Failed to open selection: ${e.message}", isError = true),
                    )
                }
            }
        }
    }

    /** Recursively collect images from the selection folder (flattens RAW/JPEG subfolders). */
    private fun enumerateSelectionImages(folder: DocumentFile, results: MutableList<ImageItem>) {
        for (file in folder.listFiles()) {
            if (file.isDirectory) {
                enumerateSelectionImages(file, results)
                continue
            }
            if (!file.isFile) continue
            val name = file.name ?: continue
            if (name.startsWith(".")) continue
            val ext = name.substringAfterLast('.', "").lowercase()
            if (ext !in com.phototok.data.source.LocalImageSourceImpl.SUPPORTED_EXTENSIONS) continue
            results.add(
                ImageItem(
                    uri = file.uri.toString(),
                    fileName = name,
                    fileSize = file.length(),
                    lastModified = file.lastModified(),
                    mimeType = file.type,
                )
            )
        }
    }

    fun closeSelectionFolder() {
        _uiState.update {
            it.copy(isViewingSelection = false, selectionImages = emptyList(), selectionCurrentIndex = 0)
        }
    }

    fun navigateSelection(index: Int) {
        if (index in _uiState.value.selectionImages.indices) {
            _uiState.update { it.copy(selectionCurrentIndex = index) }
        }
    }

    // ── Internal helpers ──────────────────────────────────────────────────

    /** Remove a set of images (by URI) from both the filtered and unfiltered lists. */
    private fun removeImagesFromLists(uris: Set<String>) {
        val state = _uiState.value
        val updatedImages = state.images.filter { it.uri !in uris }
        val updatedAll = state.allImages.filter { it.uri !in uris }
        val newIndex = state.currentIndex.coerceAtMost(updatedImages.size - 1).coerceAtLeast(0)
        val newSplit = if (state.sortByOrientation && updatedImages.isNotEmpty()) {
            val firstPortrait = updatedImages.indexOfFirst { !it.isLandscape }
            if (firstPortrait >= 0) firstPortrait else -1
        } else -1

        _uiState.update {
            it.copy(
                images = updatedImages,
                allImages = updatedAll,
                currentIndex = newIndex,
                portraitSectionStart = newSplit,
            )
        }
        loadExifForCurrent()
    }

    private fun removeCurrentImageFromList() {
        val state = _uiState.value
        val removedImage = state.images.getOrNull(state.currentIndex) ?: return
        val updatedImages = state.images.toMutableList().apply {
            removeAt(state.currentIndex)
        }
        // Also remove from allImages (unfiltered list)
        val updatedAll = state.allImages.filter { it.uri != removedImage.uri }
        val newIndex = state.currentIndex.coerceAtMost(updatedImages.size - 1).coerceAtLeast(0)
        // Recompute portrait section start
        val newSplit = if (state.sortByOrientation && updatedImages.isNotEmpty()) {
            val firstPortrait = updatedImages.indexOfFirst { !it.isLandscape }
            if (firstPortrait >= 0) firstPortrait else -1
        } else -1

        _uiState.update {
            it.copy(
                images = updatedImages,
                allImages = updatedAll,
                currentIndex = newIndex,
                portraitSectionStart = newSplit,
            )
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

    private var dimensionsJob: Job? = null

    private fun loadDimensionsAsynchronously(images: List<ImageItem>) {
        dimensionsJob?.cancel()
        dimensionsJob = viewModelScope.launch(Dispatchers.IO) {
            val currentIndex = _uiState.value.currentIndex
            val sortedIndices = images.indices.sortedBy { abs(it - currentIndex) }
            
            for (idx in sortedIndices) {
                val image = images.getOrNull(idx) ?: continue
                if (image.imageWidth == 0 && image.imageHeight == 0) {
                    val (w, h) = try {
                        imageRepository.getImageDimensions(context, Uri.parse(image.uri))
                    } catch (e: Exception) {
                        Pair(0, 0)
                    }
                    if (w > 0 && h > 0) {
                        _uiState.update { state ->
                            val updatedAll = state.allImages.map { img ->
                                if (img.uri == image.uri) img.copy(imageWidth = w, imageHeight = h) else img
                            }
                            val updatedImages = state.images.map { img ->
                                if (img.uri == image.uri) img.copy(imageWidth = w, imageHeight = h) else img
                            }
                            
                            val sortByOrientation = state.sortByOrientation
                            val randomizeOrder = state.randomizeOrder
                            val finalImages: List<ImageItem>
                            val splitIndex: Int
                            val newIndex: Int
                            
                            if (randomizeOrder) {
                                finalImages = updatedImages
                                splitIndex = -1
                                newIndex = state.currentIndex
                            } else if (sortByOrientation) {
                                val currentActiveUri = state.images.getOrNull(state.currentIndex)?.uri
                                val landscape = updatedImages.filter { it.isLandscape }
                                val portrait = updatedImages.filter { !it.isLandscape }
                                finalImages = landscape + portrait
                                splitIndex = if (portrait.isEmpty()) -1 else landscape.size
                                newIndex = if (currentActiveUri != null) {
                                    finalImages.indexOfFirst { it.uri == currentActiveUri }.coerceAtLeast(0)
                                } else state.currentIndex
                            } else {
                                finalImages = updatedImages
                                splitIndex = -1
                                newIndex = state.currentIndex
                            }
                            
                            state.copy(
                                allImages = updatedAll,
                                images = finalImages,
                                portraitSectionStart = splitIndex,
                                currentIndex = newIndex
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        val imageToDelete = _uiState.value.pendingDeleteImage
        if (imageToDelete != null) {
            val uri = Uri.parse(imageToDelete.uri)
            kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                try {
                    imageRepository.deleteImage(context, uri)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in onCleared deleting file: ${imageToDelete.uri}", e)
                }
            }
        }
    }
}
