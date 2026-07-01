package com.phototok.viewmodel

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import com.phototok.data.model.ExifData
import com.phototok.data.model.ImageItem
import com.phototok.data.model.PhoneSettings
import com.phototok.data.model.RecentPath
import com.phototok.data.repository.ImageRepository
import com.phototok.data.repository.SettingsRepository
import com.phototok.data.source.ExternalStorageDetector
import com.phototok.data.source.ExternalVolume
import com.phototok.data.source.googledrive.GoogleDriveAuth
import com.phototok.data.source.googledrive.GoogleDriveImageSource
import com.phototok.di.ApplicationScope
import com.phototok.domain.CollectionAction
import com.phototok.domain.CopyMoveFeedback
import com.phototok.domain.FileTypeFilter
import com.phototok.domain.PendingDeleteLogic
import com.phototok.domain.PhoneFeedOrdering
import com.phototok.domain.PhotoFolders
import com.phototok.domain.RelatedFiles
import com.phototok.domain.SwipeAction
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    /** Whether a Google account with Drive access is currently signed in. */
    val isDriveSignedIn: Boolean = false,
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
    private val driveAuth: GoogleDriveAuth,
    @ApplicationScope private val appScope: CoroutineScope,
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

        /** Dimension results are applied to the UI state in batches of this size. */
        internal const val DIMENSION_BATCH_SIZE = 24
    }

    init {
        observeSettings()
        observeDriveSignIn()
        restoreLastFolders()
        detectExternalStorage()
    }

    // ── Settings observation ──────────────────────────────────────────────

    private fun observeSettings() {
        // One typed flow for all simple settings: a single state update per
        // DataStore emission, no positional casts.
        viewModelScope.launch {
            var previous: PhoneSettings? = null
            settingsRepository.phoneSettings.collect { s ->
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
        // URI settings resolve display names via the repository (I/O) — kept separate.
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

    private fun observeDriveSignIn() {
        viewModelScope.launch {
            driveAuth.signedInAccount.collect { account ->
                _uiState.update { it.copy(isDriveSignedIn = account != null) }
            }
        }
    }

    private suspend fun resolveFolderName(uri: String?, fallback: String): String {
        if (uri == null) return ""
        return imageRepository.resolveFolderName(Uri.parse(uri)) ?: fallback
    }

    // ── Google Drive sign-in (state only; no client/auth objects leak to UI) ──

    /** Intent to launch the Google sign-in flow. */
    fun driveSignInIntent(): Intent = driveAuth.getSignInIntent()

    /**
     * Handle the sign-in activity result. Returns true when a Drive account is
     * now signed in (the caller may open the Drive folder picker).
     */
    fun handleDriveSignIn(data: Intent?): Boolean {
        return try {
            val account = GoogleSignIn.getSignedInAccountFromIntent(data)
                .getResult(ApiException::class.java)
            driveAuth.handleSignInResult(account)
            account != null
        } catch (e: Exception) {
            Log.e(TAG, "Google Sign-In failed", e)
            setError("Google Sign-In failed: ${e.message}")
            false
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
        // Google Drive URIs are routed to the Drive loader (single entry-point
        // dispatch; per-image operations are routed inside the data layer).
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

            val folderName = imageRepository.prepareSourceFolder(uri)
            if (folderName == null) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Cannot access folder. Permission may have been revoked.",
                    )
                }
                return@launch
            }

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
            imageRepository.prepareSourceFolder(uri) // persists the URI permission
            val name = resolveFolderName(uri.toString(), fallback = "Collection")
            settingsRepository.setPhoneCollectionUri(uri.toString())
            _uiState.update {
                it.copy(collectionFolderUri = uri.toString(), collectionFolderName = name)
            }
        }
    }

    fun selectLeftSwipeFolder(uri: Uri) {
        viewModelScope.launch {
            imageRepository.prepareSourceFolder(uri) // persists the URI permission
            val name = resolveFolderName(uri.toString(), fallback = "Folder")
            settingsRepository.setPhoneLeftSwipeUri(uri.toString())
            _uiState.update {
                it.copy(leftSwipeFolderUri = uri.toString(), leftSwipeFolderName = name)
            }
        }
    }

    // ── Sorting ───────────────────────────────────────────────────────────

    private suspend fun sortImages(images: List<ImageItem>): PhoneFeedOrdering.Result {
        // Read from the repository (not uiState) to avoid a race on first load,
        // before the observed settings have emitted.
        val settings = settingsRepository.phoneSettings.first()
        return PhoneFeedOrdering.order(images, settings.randomizeOrder, settings.sortByOrientation)
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
            subfolderName = PhotoFolders.SELECTION,
            destinationNoun = "collection",
        )
    }

    /** Swipe left (copy/move mode): copy or move the current photo to the custom folder. */
    fun performLeftSwipeCopyOrMove() {
        val state = _uiState.value
        copyOrMoveCurrent(
            targetUri = state.leftSwipeFolderUri ?: state.sourceFolderUri,
            isCopy = state.leftSwipeAction == SwipeAction.COPY,
            subfolderName = PhotoFolders.LEFT_SWIPE,
            destinationNoun = "folder",
        )
    }

    /**
     * Copy or move the current image plus its related siblings, tracking each
     * file's result individually so partial failures are reported accurately
     * and only files that actually moved disappear from the feed.
     */
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

                val succeededUris = mutableSetOf<String>()
                var failed = 0
                targets.forEach { img ->
                    val ok = if (isCopy) {
                        imageRepository.copyImage(
                            sourceUri = Uri.parse(img.uri),
                            destFolderUri = folderUri,
                            sorting = sortingEnabled,
                            subfolderName = subfolderName,
                        )
                    } else {
                        imageRepository.moveImage(
                            sourceUri = Uri.parse(img.uri),
                            destFolderUri = folderUri,
                            sorting = sortingEnabled,
                            subfolderName = subfolderName,
                        )
                    }
                    if (ok) succeededUris.add(img.uri) else failed++
                }

                if (!isCopy && succeededUris.isNotEmpty()) {
                    // Only files that actually moved leave the feed.
                    removeImagesFromLists(succeededUris)
                }

                val message = CopyMoveFeedback.message(
                    isCopy = isCopy,
                    destinationNoun = destinationNoun,
                    succeeded = succeededUris.size,
                    failed = failed,
                    relatedCount = related.size,
                )
                _uiState.update {
                    it.copy(
                        lastActionFeedback = ActionFeedback(
                            message = message,
                            isError = CopyMoveFeedback.isError(failed),
                        )
                    )
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
                    val deleted = imageRepository.deleteImage(Uri.parse(img.uri))
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
                        val exif = imageRepository.getExifData(Uri.parse(image.uri))
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
     * Load missing dimensions for all images, nearest-to-current first.
     * Results are applied in batches of [DIMENSION_BATCH_SIZE]: one state update
     * (and one recomposition) per batch instead of per image, which matters in
     * folders with thousands of photos.
     */
    private fun loadDimensionsAsynchronously(images: List<ImageItem>) {
        dimensionsJob?.cancel()
        dimensionsJob = viewModelScope.launch {
            val currentIndex = _uiState.value.currentIndex
            val sortedIndices = images.indices.sortedBy { abs(it - currentIndex) }

            val batch = mutableMapOf<String, Pair<Int, Int>>()
            for (idx in sortedIndices) {
                val image = images.getOrNull(idx) ?: continue
                if (image.imageWidth == 0 && image.imageHeight == 0) {
                    val (w, h) = try {
                        imageRepository.getImageDimensions(Uri.parse(image.uri))
                    } catch (e: Exception) {
                        Pair(0, 0)
                    }
                    if (w > 0 && h > 0) {
                        batch[image.uri] = Pair(w, h)
                        if (batch.size >= DIMENSION_BATCH_SIZE) {
                            applyDimensions(batch.toMap())
                            batch.clear()
                        }
                    }
                }
            }
            if (batch.isNotEmpty()) {
                applyDimensions(batch.toMap())
            }
        }
    }

    /**
     * Publish freshly loaded dimensions for a batch of images and — when
     * orientation sorting is active — regroup the feed, since the images'
     * orientation groups may only now be known.
     */
    private fun applyDimensions(dimensions: Map<String, Pair<Int, Int>>) {
        _uiState.update { state ->
            fun ImageItem.withDims(): ImageItem {
                val dims = dimensions[uri] ?: return this
                return copy(imageWidth = dims.first, imageHeight = dims.second)
            }

            val updatedAll = state.allImages.map { it.withDims() }
            val updatedImages = state.images.map { it.withDims() }

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
