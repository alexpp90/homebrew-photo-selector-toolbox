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
import com.phototok.data.source.LocalImageSourceImpl
import com.phototok.data.source.googledrive.GoogleDriveAuth
import com.phototok.data.source.googledrive.GoogleDriveClient
import com.phototok.data.source.googledrive.GoogleDriveImageSource
import com.phototok.di.ApplicationScope
import com.phototok.domain.CollectionAction
import com.phototok.domain.FileTypeFilter
import com.phototok.domain.PendingDeleteLogic
import com.phototok.domain.PhoneFeedOrdering
import com.phototok.domain.RelatedFiles
import com.phototok.domain.SwipeAction
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
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
import kotlin.math.abs

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
    val leftSwipeAction: SwipeAction = SwipeAction.DEFAULT,
    val leftSwipeFolderUri: String? = null,
    val leftSwipeFolderName: String = "",
    val error: String? = null,
    val showGestureTutorial: Boolean = false,
    val lastActionFeedback: ActionFeedback? = null,
    // Settings (observed)
    val collectionAction: CollectionAction = CollectionAction.DEFAULT,
    val trashConfirmEnabled: Boolean = true,
    val directDeleteConfirmEnabled: Boolean = true,
    val sortByOrientation: Boolean = false,
    val randomizeOrder: Boolean = false,
    val fileTypeFilter: FileTypeFilter = FileTypeFilter.DEFAULT,
    /** Index where portrait section starts (-1 = no split). */
    val portraitSectionStart: Int = -1,
    /** Detected external/removable storage volumes. */
    val externalVolumes: List<ExternalVolume> = emptyList(),
    val showExifOverlay: Boolean = false,
    /** Deletion applied to the UI but not yet finalized on disk (revertable). */
    val pendingDelete: PendingDeleteLogic.Pending? = null,
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
    get() = pendingDelete != null

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
    @ApplicationScope private val appScope: CoroutineScope,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PhoneModeUiState())
    val uiState: StateFlow<PhoneModeUiState> = _uiState.asStateFlow()

    private val loadedExifCache = object : LinkedHashMap<String, ExifData>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ExifData>?): Boolean =
            size > MAX_EXIF_CACHE_SIZE
    }

    /** Active folder-discovery collection; cancelled when a new folder is selected. */
    private var discoveryJob: Job? = null
    private var dimensionsJob: Job? = null

    companion object {
        private const val TAG = "PhoneModeVM"
        private const val MAX_EXIF_CACHE_SIZE = 30
        private const val ONE_WEEK_MS = 7L * 24 * 60 * 60 * 1000
    }

    init {
        observeSettings()
        restoreLastFolders()
        detectExternalStorage()
    }

    // ── Settings observation ──────────────────────────────────────────────

    /** Snapshot of all simple observed settings, combined into one flow. */
    private data class ObservedSettings(
        val collectionAction: CollectionAction,
        val trashConfirmEnabled: Boolean,
        val directDeleteConfirmEnabled: Boolean,
        val sortByOrientation: Boolean,
        val randomizeOrder: Boolean,
        val fileTypeFilter: FileTypeFilter,
        val leftSwipeAction: SwipeAction,
        val showExifOverlay: Boolean,
        val moveRelatedFiles: Boolean,
        val recentPathsEnabled: Boolean,
        val recentPathsCount: Int,
        val recentPaths: List<RecentPath>,
    )

    private fun observeSettings() {
        // All simple settings in a single combine: one state update per emission
        // instead of 13 independent collectors racing each other.
        viewModelScope.launch {
            var previous: ObservedSettings? = null
            combine(
                settingsRepository.phoneCollectionAction,
                settingsRepository.phoneTrashConfirmEnabled,
                settingsRepository.phoneDirectDeleteConfirmEnabled,
                settingsRepository.phoneSortByOrientation,
                settingsRepository.phoneRandomizeOrder,
                settingsRepository.phoneFileTypeFilter,
                settingsRepository.phoneLeftSwipeAction,
                settingsRepository.phoneShowExifOverlay,
                settingsRepository.phoneMoveRelatedFiles,
                settingsRepository.phoneRecentPathsEnabled,
                settingsRepository.phoneRecentPathsCount,
                settingsRepository.phoneRecentPaths,
            ) { values ->
                @Suppress("UNCHECKED_CAST")
                val recentPaths = values[11] as List<RecentPath>
                ObservedSettings(
                    collectionAction = values[0] as CollectionAction,
                    trashConfirmEnabled = values[1] as Boolean,
                    directDeleteConfirmEnabled = values[2] as Boolean,
                    sortByOrientation = values[3] as Boolean,
                    randomizeOrder = values[4] as Boolean,
                    fileTypeFilter = values[5] as FileTypeFilter,
                    leftSwipeAction = values[6] as SwipeAction,
                    showExifOverlay = values[7] as Boolean,
                    moveRelatedFiles = values[8] as Boolean,
                    recentPathsEnabled = values[9] as Boolean,
                    recentPathsCount = values[10] as Int,
                    recentPaths = recentPaths,
                )
            }.collect { s ->
                _uiState.update {
                    it.copy(
                        collectionAction = s.collectionAction,
                        trashConfirmEnabled = s.trashConfirmEnabled,
                        directDeleteConfirmEnabled = s.directDeleteConfirmEnabled,
                        sortByOrientation = s.sortByOrientation,
                        randomizeOrder = s.randomizeOrder,
                        fileTypeFilter = s.fileTypeFilter,
                        leftSwipeAction = s.leftSwipeAction,
                        showExifOverlay = s.showExifOverlay,
                        moveRelatedFiles = s.moveRelatedFiles,
                        recentPathsEnabled = s.recentPathsEnabled,
                        recentPathsCount = s.recentPathsCount,
                        recentPaths = s.recentPaths,
                    )
                }
                val prev = previous
                previous = s
                if (prev != null) {
                    if (s.fileTypeFilter != prev.fileTypeFilter &&
                        _uiState.value.allImages.isNotEmpty()
                    ) {
                        refilterAndSort()
                    } else if ((s.sortByOrientation != prev.sortByOrientation ||
                            s.randomizeOrder != prev.randomizeOrder) &&
                        _uiState.value.images.isNotEmpty()
                    ) {
                        resortImages()
                    }
                }
            }
        }
        // URI settings resolve display names via DocumentFile (I/O) — kept separate.
        viewModelScope.launch {
            settingsRepository.phoneCollectionUri.collect { uri ->
                val name = resolveFolderName(uri, fallback = "Collection")
                _uiState.update {
                    it.copy(collectionFolderUri = uri, collectionFolderName = name)
                }
            }
        }
        viewModelScope.launch {
            settingsRepository.phoneLeftSwipeUri.collect { uri ->
                val name = resolveFolderName(uri, fallback = "Folder")
                _uiState.update {
                    it.copy(leftSwipeFolderUri = uri, leftSwipeFolderName = name)
                }
            }
        }
    }

    private suspend fun resolveFolderName(uri: String?, fallback: String): String {
        if (uri == null) return ""
        return withContext(Dispatchers.IO) {
            try {
                DocumentFile.fromTreeUri(context, Uri.parse(uri))?.name ?: fallback
            } catch (_: Exception) {
                fallback
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
        // Google Drive URIs are routed to the Drive loader.
        if (GoogleDriveImageSource.isDriveUri(uri)) {
            val folderId = GoogleDriveImageSource.extractId(uri) ?: return
            selectDriveFolder(folderId, "Google Drive")
            return
        }
        finalizePendingDelete()
        discoveryJob?.cancel()
        discoveryJob = viewModelScope.launch {
            _uiState.update {
                it.copy(isLoading = true, error = null, sourceFolderUri = uri.toString())
            }

            try {
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, takeFlags)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to persist URI permission for $uri", e)
            }

            val folderDoc = withContext(Dispatchers.IO) {
                try {
                    DocumentFile.fromTreeUri(context, uri)?.takeIf { it.exists() }
                } catch (e: SecurityException) {
                    null
                }
            }

            if (folderDoc == null) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Cannot access folder. Permission may have been revoked.",
                    )
                }
                return@launch
            }

            val folderName = folderDoc.name ?: "Photos"
            _uiState.update { it.copy(sourceFolderName = folderName) }
            settingsRepository.setLastFolderUri(uri.toString())
            settingsRepository.addRecentPath(uri.toString(), folderName)

            collectDiscoveredImages(uri, restorePosition = true, errorLabel = "images")
        }
    }

    /** Select a Google Drive folder as the source. */
    fun selectDriveFolder(folderId: String, folderName: String) {
        val driveUri = GoogleDriveImageSource.buildUri(folderId)
        finalizePendingDelete()
        discoveryJob?.cancel()
        discoveryJob = viewModelScope.launch {
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

            collectDiscoveredImages(driveUri, restorePosition = false, errorLabel = "Drive images")
        }
    }

    /** Shared tail of local/Drive folder selection: discover, filter, sort, publish. */
    private suspend fun collectDiscoveredImages(
        folderUri: Uri,
        restorePosition: Boolean,
        errorLabel: String,
    ) {
        try {
            imageRepository.discoverImages(folderUri).collect { images ->
                val filtered = PhoneFeedOrdering.filterByType(images, _uiState.value.fileTypeFilter)
                val sorted = sortImages(filtered)
                val startIndex = if (restorePosition) {
                    settingsRepository.getFolderLastPosition(folderUri.toString())
                        .coerceIn(0, (sorted.images.size - 1).coerceAtLeast(0))
                } else {
                    0
                }
                _uiState.update {
                    it.copy(
                        allImages = images,
                        images = sorted.images,
                        portraitSectionStart = sorted.portraitSectionStart,
                        currentIndex = startIndex,
                        isLoading = false,
                    )
                }
                checkGestureTutorial()
                loadExifForCurrent()
                loadDimensionsAsynchronously(images)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _uiState.update {
                it.copy(isLoading = false, error = "Failed to load $errorLabel: ${e.message}")
            }
        }
    }

    fun selectCollectionFolder(uri: Uri) {
        viewModelScope.launch {
            takePersistablePermission(uri)
            val name = resolveFolderName(uri.toString(), fallback = "Collection")
            settingsRepository.setPhoneCollectionUri(uri.toString())
            _uiState.update {
                it.copy(collectionFolderUri = uri.toString(), collectionFolderName = name)
            }
        }
    }

    fun selectLeftSwipeFolder(uri: Uri) {
        viewModelScope.launch {
            takePersistablePermission(uri)
            val name = resolveFolderName(uri.toString(), fallback = "Folder")
            settingsRepository.setPhoneLeftSwipeUri(uri.toString())
            _uiState.update {
                it.copy(leftSwipeFolderUri = uri.toString(), leftSwipeFolderName = name)
            }
        }
    }

    private fun takePersistablePermission(uri: Uri) {
        try {
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, takeFlags)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to persist URI permission for $uri", e)
        }
    }

    // ── Sorting ───────────────────────────────────────────────────────────

    private suspend fun sortImages(images: List<ImageItem>): PhoneFeedOrdering.Result {
        val randomizeOrder = settingsRepository.phoneRandomizeOrder.first()
        val sortByOrientation = settingsRepository.phoneSortByOrientation.first()
        return PhoneFeedOrdering.order(images, randomizeOrder, sortByOrientation)
    }

    private fun resortImages() {
        finalizePendingDelete()
        viewModelScope.launch {
            val current = _uiState.value
            if (current.images.isEmpty()) return@launch
            val currentUri = current.images.getOrNull(current.currentIndex)?.uri
            val sorted = sortImages(current.images)
            val newIndex = if (currentUri != null) {
                sorted.images.indexOfFirst { it.uri == currentUri }.coerceAtLeast(0)
            } else {
                0
            }
            _uiState.update {
                it.copy(
                    images = sorted.images,
                    portraitSectionStart = sorted.portraitSectionStart,
                    currentIndex = newIndex,
                )
            }
        }
    }

    private fun refilterAndSort() {
        viewModelScope.launch {
            val state = _uiState.value
            val currentUri = state.images.getOrNull(state.currentIndex)?.uri
            val filtered = PhoneFeedOrdering.filterByType(state.allImages, state.fileTypeFilter)
            val sorted = sortImages(filtered)
            val newIndex = if (currentUri != null) {
                sorted.images.indexOfFirst { it.uri == currentUri }.coerceAtLeast(0)
            } else {
                0
            }
            _uiState.update {
                it.copy(
                    images = sorted.images,
                    portraitSectionStart = sorted.portraitSectionStart,
                    currentIndex = newIndex,
                )
            }
            loadExifForCurrent()
        }
    }

    // ── Navigation ────────────────────────────────────────────────────────

    fun navigateToImage(index: Int) {
        if (index in _uiState.value.images.indices) {
            val state = _uiState.value
            val newUri = state.images.getOrNull(index)?.uri
            if (state.pendingDelete != null && newUri != state.pendingDelete.revertAllowedUri) {
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
        copyOrMoveCurrent(
            targetUri = state.collectionFolderUri ?: state.sourceFolderUri,
            isCopy = state.collectionAction == CollectionAction.COPY,
            subfolderName = ImageRepositoryImpl.SELECTION_FOLDER_NAME,
            destinationNoun = "collection",
        )
    }

    /** Swipe left (copy/move mode): copy or move the current photo to the custom folder. */
    fun performLeftSwipeCopyOrMove() {
        val state = _uiState.value
        copyOrMoveCurrent(
            targetUri = state.leftSwipeFolderUri ?: state.sourceFolderUri,
            isCopy = state.leftSwipeAction == SwipeAction.COPY,
            subfolderName = ImageRepositoryImpl.LEFT_SWIPE_FOLDER_NAME,
            destinationNoun = "folder",
        )
    }

    private fun copyOrMoveCurrent(
        targetUri: String?,
        isCopy: Boolean,
        subfolderName: String,
        destinationNoun: String,
    ) {
        val state = _uiState.value
        if (state.images.isEmpty()) return
        if (targetUri == null) return

        val currentImage = state.images[state.currentIndex]
        val related = if (state.moveRelatedFiles) relatedImages(currentImage) else emptyList()
        val targets = listOf(currentImage) + related

        viewModelScope.launch {
            try {
                val sortingEnabled = settingsRepository.sortingEnabled.first()
                val folderUri = Uri.parse(targetUri)

                targets.forEach { img ->
                    if (isCopy) {
                        imageRepository.copyImage(
                            context = context,
                            sourceUri = Uri.parse(img.uri),
                            destFolderUri = folderUri,
                            sorting = sortingEnabled,
                            subfolderName = subfolderName,
                        )
                    } else {
                        imageRepository.moveImage(
                            context = context,
                            sourceUri = Uri.parse(img.uri),
                            destFolderUri = folderUri,
                            sorting = sortingEnabled,
                            subfolderName = subfolderName,
                        )
                    }
                }

                if (!isCopy) {
                    removeImagesFromLists(targets.map { it.uri }.toSet())
                }

                val suffix = if (related.isNotEmpty()) " (+${related.size} related)" else ""
                val verb = if (isCopy) "Copied" else "Moved"
                _uiState.update {
                    it.copy(lastActionFeedback = ActionFeedback("$verb to $destinationNoun$suffix"))
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
        val state = _uiState.value
        if (state.images.isEmpty()) return

        // If there's already a pending deletion, finalize it first.
        finalizePendingDelete()

        val imageToDelete = state.images[state.currentIndex]
        val related = if (state.moveRelatedFiles) relatedImages(imageToDelete) else emptyList()

        val (lists, pending) = PendingDeleteLogic.remove(
            images = state.images,
            allImages = state.allImages,
            currentIndex = state.currentIndex,
            related = related,
            sortByOrientation = state.sortByOrientation,
        ) ?: return

        _uiState.update {
            it.copy(
                images = lists.images,
                allImages = lists.allImages,
                currentIndex = lists.currentIndex,
                portraitSectionStart = lists.portraitSectionStart,
                pendingDelete = pending,
            )
        }
        loadExifForCurrent()
    }

    /**
     * Delete the pending image (and its siblings) from disk. Runs on the
     * application scope so it also completes when the ViewModel is cleared.
     */
    fun finalizePendingDelete() {
        val pending = _uiState.value.pendingDelete ?: return
        _uiState.update { it.copy(pendingDelete = null) }

        appScope.launch {
            (listOf(pending.image) + pending.related).forEach { img ->
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
        val pending = state.pendingDelete ?: return

        val lists = PendingDeleteLogic.restore(
            images = state.images,
            allImages = state.allImages,
            pending = pending,
            sortByOrientation = state.sortByOrientation,
        )

        _uiState.update {
            it.copy(
                images = lists.images,
                allImages = lists.allImages,
                currentIndex = lists.currentIndex,
                portraitSectionStart = lists.portraitSectionStart,
                pendingDelete = null,
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

    // ── Navigation helpers ───────────────────────────────────────────────

    /** Go back to the landing screen (clears images). */
    fun goBackToLanding() {
        finalizePendingDelete()
        discoveryJob?.cancel()
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
                } catch (_: Exception) {
                    null
                }
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
                        lastActionFeedback = ActionFeedback(
                            "Failed to open selection: ${e.message}",
                            isError = true,
                        ),
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
            if (ext !in LocalImageSourceImpl.SUPPORTED_EXTENSIONS) continue
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

        _uiState.update {
            it.copy(
                images = updatedImages,
                allImages = updatedAll,
                currentIndex = newIndex,
                portraitSectionStart = PhoneFeedOrdering.portraitSplit(
                    updatedImages,
                    state.sortByOrientation,
                ),
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
                        applyDimensions(image.uri, w, h)
                    }
                }
            }
        }
    }

    /**
     * Publish freshly loaded dimensions for one image and — when orientation
     * sorting is active — regroup the feed, since the image's orientation group
     * may only now be known.
     */
    private fun applyDimensions(uri: String, w: Int, h: Int) {
        _uiState.update { state ->
            val updatedAll = state.allImages.map { img ->
                if (img.uri == uri) img.copy(imageWidth = w, imageHeight = h) else img
            }
            val updatedImages = state.images.map { img ->
                if (img.uri == uri) img.copy(imageWidth = w, imageHeight = h) else img
            }

            if (!state.randomizeOrder && state.sortByOrientation) {
                val currentActiveUri = state.images.getOrNull(state.currentIndex)?.uri
                // Stable regroup: landscape first, then portrait, order within groups kept.
                val landscape = updatedImages.filter { it.isLandscape }
                val portrait = updatedImages.filter { !it.isLandscape }
                val regrouped = landscape + portrait
                val newIndex = if (currentActiveUri != null) {
                    regrouped.indexOfFirst { it.uri == currentActiveUri }.coerceAtLeast(0)
                } else {
                    state.currentIndex
                }
                state.copy(
                    allImages = updatedAll,
                    images = regrouped,
                    portraitSectionStart = if (portrait.isEmpty()) -1 else landscape.size,
                    currentIndex = newIndex,
                )
            } else {
                state.copy(allImages = updatedAll, images = updatedImages)
            }
        }
    }

    override fun onCleared() {
        // Finalizes primary AND sibling files on the application scope, so the
        // deletion completes even though the ViewModel is going away.
        finalizePendingDelete()
        super.onCleared()
    }
}
