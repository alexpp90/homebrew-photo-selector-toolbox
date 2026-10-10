package com.phototok.viewmodel

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photoselector.core.model.ExifData
import com.phototok.data.model.ImageItem
import com.phototok.data.model.PhoneSettings
import com.phototok.data.model.RecentPath
import com.phototok.data.repository.ImageRepository
import com.phototok.data.repository.SettingsRepository
import com.phototok.di.ApplicationScope
import com.phototok.domain.CollectionAction
import com.phototok.domain.CopyMoveFeedback
import com.phototok.domain.FileTypeFilter
import com.phototok.domain.FirstRunHint
import com.phototok.domain.FirstRunHintText
import com.phototok.domain.FolderScanInfo
import com.phototok.domain.FolderScanLogic
import com.phototok.domain.OptimisticFeed
import com.phototok.domain.PendingDeleteLogic
import com.phototok.domain.PhoneFeedOrdering
import com.phototok.domain.PhotoFolders
import com.phototok.domain.RelatedFiles
import com.phototok.domain.SwipeAction
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PhoneModeUiState(
    val images: List<ImageItem> = emptyList(),
    /** All images before file type filtering (kept for re-filtering). */
    val allImages: List<ImageItem> = emptyList(),
    val currentIndex: Int = 0,
    val isLoading: Boolean = false,
    /**
     * True while the folder is still being enumerated in the background. The
     * feed is already usable — this only tells the UI that the photo count is
     * still growing.
     */
    val isDiscovering: Boolean = false,
    val sourceFolderUri: String? = null,
    val sourceFolderName: String = "",
    val collectionFolderUri: String? = null,
    val collectionFolderName: String = "",
    val leftSwipeAction: SwipeAction = SwipeAction.DEFAULT,
    val leftSwipeFolderUri: String? = null,
    val leftSwipeFolderName: String = "",
    val error: String? = null,
    val showGestureTutorial: Boolean = false,
    /** Set when the user opens the controls guide from the info button. */
    val showControlsGuide: Boolean = false,
    val lastActionFeedback: ActionFeedback? = null,
    /** One-time explanation for an action the user just performed for the first time. */
    val firstRunHint: FirstRunHintUi? = null,
    /** Hint keys already shown, so each explanation fires exactly once. */
    val seenFirstRunHints: Set<String> = emptySet(),
    // Settings (observed)
    val collectionAction: CollectionAction = CollectionAction.DEFAULT,
    val directDeleteConfirmEnabled: Boolean = true,
    val sortByOrientation: Boolean = false,
    val randomizeOrder: Boolean = false,
    val fileTypeFilter: FileTypeFilter = FileTypeFilter.DEFAULT,
    /** Index where portrait section starts (-1 = no split). */
    val portraitSectionStart: Int = -1,
    val showExifOverlay: Boolean = false,
    /** Deletion applied to the UI but not yet finalized on disk (revertable). */
    val pendingDelete: PendingDeleteLogic.Pending? = null,
    /** Whether collection/delete actions also move/delete same-name sibling files. */
    val moveRelatedFiles: Boolean = false,
    // Recent folders (landing quick-select)
    val recentPaths: List<RecentPath> = emptyList(),
    val recentPathsEnabled: Boolean = true,
    val recentPathsCount: Int = 3,
    /** Whether to show suggestion for folders containing both RAW and JPEG pairs. */
    val showRawJpegSuggestion: Boolean = false,
    /** Prompt shown after active session reload when new photos were discovered. */
    val reloadPrompt: ReloadPrompt? = null,
    /** Friendly explanation when a selected folder contains 0 supported photos. */
    val emptyFolderMessage: String? = null,
    /** When true, Selection folder is stored inside each scanned folder. */
    val selectionUseSourceRoot: Boolean = false,
)

data class ReloadPrompt(
    val newCount: Int,
    val firstNewIndex: Int,
)

/** True when there is a pending deletion that can still be reverted. */
val PhoneModeUiState.canRevert: Boolean
    get() = pendingDelete != null

data class ActionFeedback(
    val message: String,
    val isError: Boolean = false,
    val id: Long = System.nanoTime(),
)

/** A one-time explanation, resolved against the user's current settings. */
data class FirstRunHintUi(
    val hint: FirstRunHint,
    val title: String,
    val message: String,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null,
    val id: Long = System.nanoTime(),
)

@HiltViewModel
class PhoneModeViewModel @Inject constructor(
    private val imageRepository: ImageRepository,
    private val settingsRepository: SettingsRepository,
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

    /**
     * Every URI published for the current folder. Discovery streams cumulative
     * batches, and the user may remove photos from the feed while it is still
     * running, so "absent from the feed" must not be read as "newly found".
     */
    private val publishedUris = mutableSetOf<String>()

    /** URIs whose dimensions have been read, so batch restarts do no double I/O. */
    private val dimensionsResolved = mutableSetOf<String>()

    /** Saved feed position still to be restored as more photos stream in. */
    private var pendingRestoreIndex: Int? = null

    /** Set once the user changes page themselves, which cancels position restore. */
    private var userHasNavigated = false

    companion object {
        private const val TAG = "PhoneModeVM"
        private const val MAX_EXIF_CACHE_SIZE = 30
        private const val ONE_WEEK_MS = 7L * 24 * 60 * 60 * 1000

        /** Dimension results are applied to the UI state in batches of this size. */
        internal const val DIMENSION_BATCH_SIZE = 24
    }

    init {
        observeSettings()
        restoreLastFolders()
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
                        seenFirstRunHints = s.seenFirstRunHints,
                        selectionUseSourceRoot = s.selectionUseSourceRoot,
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

    private suspend fun resolveFolderName(uri: String?, fallback: String): String {
        if (uri == null) return ""
        return imageRepository.resolveFolderName(Uri.parse(uri)) ?: fallback
    }

    /** Toggle the EXIF stats overlay (driven by tapping the Photo-Tok logo). */
    fun toggleExifOverlay() {
        viewModelScope.launch {
            settingsRepository.setPhoneShowExifOverlay(!_uiState.value.showExifOverlay)
        }
    }

    /** Re-open a previously used source folder from the recents list. */
    fun selectRecentPath(path: RecentPath) {
        selectSourceFolder(Uri.parse(path.uri))
    }

    /**
     * Quick-start for default phone camera roll.
     * If permission is already granted, opens immediately;
     * otherwise calls [onLaunchPicker] with the camera tree URI to prompt the user.
     */
    fun selectCameraFolder(onLaunchPicker: (Uri) -> Unit) {
        val cameraUri = android.provider.DocumentsContract.buildTreeDocumentUri(
            "com.android.externalstorage.documents",
            "primary:DCIM/Camera",
        )
        viewModelScope.launch {
            val name = imageRepository.prepareSourceFolder(cameraUri)
            if (name != null) {
                selectSourceFolder(cameraUri)
            } else {
                onLaunchPicker(cameraUri)
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
        finalizePendingDelete()
        discoveryJob?.cancel()
        dimensionsJob?.cancel()
        publishedUris.clear()
        dimensionsResolved.clear()
        _uiState.update { it.copy(emptyFolderMessage = null) }
        discoveryJob = viewModelScope.launch {
            // The previous folder's feed is dropped up front: batches are merged
            // by appending, so a stale list would be prefixed to the new folder.
            _uiState.update {
                it.copy(
                    isLoading = true,
                    isDiscovering = true,
                    error = null,
                    emptyFolderMessage = null,
                    sourceFolderUri = uri.toString(),
                    images = emptyList(),
                    allImages = emptyList(),
                    currentIndex = 0,
                    portraitSectionStart = -1,
                    showRawJpegSuggestion = false,
                )
            }

            val folderName = imageRepository.prepareSourceFolder(uri)
            if (folderName == null) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isDiscovering = false,
                        error = "Cannot access folder. Permission may have been revoked.",
                    )
                }
                return@launch
            }

            _uiState.update { it.copy(sourceFolderName = folderName) }
            settingsRepository.setLastFolderUri(uri.toString())
            settingsRepository.addRecentPath(uri.toString(), folderName)

            val scanInfo = settingsRepository.getFolderScanInfo(uri.toString())
            collectDiscoveredImages(
                folderUri = uri,
                restorePosition = true,
                errorLabel = "images",
                previousMaxLastModified = scanInfo.maxLastModified,
                wasActiveSession = false,
            )
        }
    }

    /**
     * Shared tail of folder selection: stream, filter, order, publish.
     *
     * Discovery emits progressively (a small first batch, then chunks), so this
     * publishes each batch as it arrives instead of waiting for the whole tree.
     * The user is swiping from the first batch onwards.
     */
    private suspend fun collectDiscoveredImages(
        folderUri: Uri,
        restorePosition: Boolean,
        errorLabel: String,
        targetUriToKeep: String? = null,
        previousMaxLastModified: Long = 0L,
        wasActiveSession: Boolean = false,
        urisBeforeReload: Set<String> = emptySet(),
    ) {
        publishedUris.clear()
        dimensionsResolved.clear()
        userHasNavigated = false
        // Read once here rather than per batch: on a fresh launch the observed
        // settings may not have emitted into uiState yet.
        val settings = settingsRepository.phoneSettings.first()
        pendingRestoreIndex = if (restorePosition) {
            settingsRepository.getFolderLastPosition(folderUri.toString()).takeIf { it > 0 }
        } else {
            null
        }

        try {
            imageRepository.discoverImages(folderUri).collect { discovered ->
                publishBatch(discovered, settings, targetUriToKeep)
            }
            // Enumeration finished:
            // If not randomized, sort cumulative list chronologically to guarantee whole folder order,
            // while preserving current item or targetUriToKeep.
            val current = _uiState.value
            val uriToPreserve = targetUriToKeep
                ?: current.images.getOrNull(current.currentIndex)?.uri
            val filtered = PhoneFeedOrdering.filterByType(current.allImages, settings.fileTypeFilter)
            val sorted = if (!settings.randomizeOrder && current.allImages.isNotEmpty()) {
                PhoneFeedOrdering.order(
                    filtered,
                    randomize = false,
                    sortByOrientation = settings.sortByOrientation,
                )
            } else {
                PhoneFeedOrdering.Result(current.images, current.portraitSectionStart)
            }

            var promptToSet: ReloadPrompt? = null
            val newIndex: Int

            if (wasActiveSession) {
                // In an active session, preserve current viewing position
                val preservedIdx = if (uriToPreserve != null) {
                    sorted.images.indexOfFirst { it.uri == uriToPreserve }
                } else -1
                newIndex = if (preservedIdx >= 0) {
                    preservedIdx
                } else {
                    resolveFeedIndex(current.currentIndex, sorted.images.size, targetUriToKeep, sorted.images)
                }

                // Identify newly added photos
                val newPhotos = sorted.images.filter {
                    (previousMaxLastModified > 0L && it.lastModified > previousMaxLastModified) ||
                        (urisBeforeReload.isNotEmpty() && it.uri !in urisBeforeReload)
                }
                if (newPhotos.isNotEmpty()) {
                    val firstNewIdx = sorted.images.indexOfFirst { img ->
                        newPhotos.any { it.uri == img.uri }
                    }
                    promptToSet = ReloadPrompt(
                        newCount = newPhotos.size,
                        firstNewIndex = firstNewIdx,
                    )
                } else {
                    showFeedback("Folder reloaded — no new photos found")
                    val updatedScan = FolderScanLogic.computeUpdatedScanInfo(sorted.images)
                    settingsRepository.setFolderScanInfo(folderUri.toString(), updatedScan)
                }
            } else {
                // Outside an active session:
                // If there are new files that were not yet scanned, jump to the first new file
                val firstNewIndex = FolderScanLogic.findFirstNewImageIndex(sorted.images, previousMaxLastModified)
                if (firstNewIndex >= 0 && !userHasNavigated) {
                    newIndex = firstNewIndex
                } else {
                    newIndex = if (uriToPreserve != null) {
                        val idx = sorted.images.indexOfFirst { it.uri == uriToPreserve }
                        if (idx >= 0) idx else resolveFeedIndex(current.currentIndex, sorted.images.size, targetUriToKeep, sorted.images)
                    } else {
                        resolveFeedIndex(current.currentIndex, sorted.images.size, targetUriToKeep, sorted.images)
                    }
                }
                val updatedScan = FolderScanLogic.computeUpdatedScanInfo(sorted.images)
                settingsRepository.setFolderScanInfo(folderUri.toString(), updatedScan)
            }

            val emptyMsg = if (sorted.images.isEmpty()) {
                val fName = current.sourceFolderName.ifEmpty { "folder" }
                "No supported photos found in $fName"
            } else null

            _uiState.update {
                it.copy(
                    images = sorted.images,
                    portraitSectionStart = sorted.portraitSectionStart,
                    currentIndex = newIndex,
                    isLoading = false,
                    isDiscovering = false,
                    reloadPrompt = promptToSet,
                    emptyFolderMessage = emptyMsg,
                )
            }
            maybeShowRawJpegSuggestion()
            maybeShowFilterMismatchHint()
            loadDimensionsAsynchronously(force = true)
        } catch (e: CancellationException) {
            // A newer discovery job owns the state now — do not touch it.
            throw e
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    isDiscovering = false,
                    error = "Failed to load $errorLabel: ${e.message}",
                )
            }
        }
    }

    /**
     * Merge one discovery batch into the feed.
     *
     * Batches are **appended**, never re-sorted into the existing feed, so a
     * photo the user has already swiped past cannot jump back in front of them
     * while the folder is still loading.
     */
    private fun publishBatch(
        discovered: List<ImageItem>,
        settings: PhoneSettings,
        targetUriToKeep: String? = null,
    ) {
        val state = _uiState.value
        val isFirstBatch = state.images.isEmpty() && state.allImages.isEmpty()
        val fresh = PhoneFeedOrdering.newItems(discovered, publishedUris)
        if (fresh.isEmpty() && !isFirstBatch) {
            _uiState.update { it.copy(isLoading = false) }
            return
        }
        publishedUris += fresh.map { it.uri }

        val freshFiltered = PhoneFeedOrdering.filterByType(fresh, settings.fileTypeFilter)
        val merged = PhoneFeedOrdering.appendBatch(
            current = state.images,
            fresh = freshFiltered,
            randomize = settings.randomizeOrder,
            sortByOrientation = settings.sortByOrientation,
        )

        // Resolved outside the update lambda: it consumes pendingRestoreIndex and
        // must run exactly once per batch.
        val nextIndex = resolveFeedIndex(state.currentIndex, merged.images.size, targetUriToKeep, merged.images)

        _uiState.update {
            it.copy(
                allImages = state.allImages + fresh,
                images = merged.images,
                portraitSectionStart = merged.portraitSectionStart,
                currentIndex = nextIndex,
                isLoading = false,
            )
        }

        if (isFirstBatch) checkGestureTutorial()
        maybeShowRawJpegSuggestion()
        maybeShowFilterMismatchHint()
        loadExifForCurrent()
        loadDimensionsAsynchronously()
    }

    /**
     * The index to show after a batch landed: honour a saved position while it
     * is still out of reach of the partially loaded feed, otherwise leave the
     * user exactly where they are.
     */
    private fun resolveFeedIndex(
        currentIndex: Int,
        size: Int,
        targetUri: String? = null,
        currentImages: List<ImageItem> = emptyList(),
    ): Int {
        if (size == 0) return 0
        if (userHasNavigated) return currentIndex.coerceIn(0, size - 1)
        if (targetUri != null) {
            val idx = currentImages.indexOfFirst { it.uri == targetUri }
            if (idx >= 0) return idx
        }
        val target = pendingRestoreIndex
        if (target == null) return currentIndex.coerceIn(0, size - 1)
        val restored = target.coerceAtMost(size - 1)
        if (restored == target) pendingRestoreIndex = null
        return restored
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
            if (index != state.currentIndex) {
                // A real page change means the user has taken over; stop chasing
                // the saved position as further batches arrive.
                userHasNavigated = true
                pendingRestoreIndex = null
            }
            _uiState.update { it.copy(currentIndex = index) }
            maybeShowRawJpegSuggestion()
            maybeShowFilterMismatchHint()
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
        maybeShowFirstRunHint(FirstRunHint.SWIPE_RIGHT)
        val target = resolveCollectionTargetUri(state)
        copyOrMoveCurrent(
            targetUri = target,
            isCopy = state.collectionAction == CollectionAction.COPY,
            subfolderName = PhotoFolders.SELECTION,
            destinationNoun = "collection",
        )
    }

    private fun resolveCollectionTargetUri(state: PhoneModeUiState): String? {
        if (state.collectionFolderUri != null) return state.collectionFolderUri
        return state.sourceFolderUri
    }

    /** Swipe left (copy/move mode): copy or move the current photo to the custom folder. */
    fun performLeftSwipeCopyOrMove() {
        val state = _uiState.value
        maybeShowFirstRunHint(FirstRunHint.SWIPE_LEFT_FOLDER)
        copyOrMoveCurrent(
            targetUri = state.leftSwipeFolderUri ?: state.sourceFolderUri,
            isCopy = state.leftSwipeAction == SwipeAction.COPY,
            subfolderName = PhotoFolders.LEFT_SWIPE,
            destinationNoun = "folder",
        )
    }

    /**
     * Copy or move the current image plus its related siblings.
     *
     * The feed is updated **before** any I/O runs — a move drops the photo out
     * of the feed, a copy advances to the next one — so the next photo is on
     * screen the instant the gesture completes. The transfer itself runs on the
     * application scope, which means it also finishes when the user immediately
     * leaves the screen.
     *
     * Each file's result is tracked individually: partial failures are reported
     * accurately, and any file that did **not** actually move is put back into
     * the feed at its original position rather than silently disappearing.
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
        val slots = OptimisticFeed.slotsOf(state.images, state.allImages, targets)

        // ── Optimistic UI: show the next photo, then do the work ──────────
        if (isCopy) {
            advanceToNextImage()
        } else {
            removeImagesFromLists(targets.map { it.uri }.toSet())
        }

        appScope.launch {
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

                if (!isCopy && failed > 0) {
                    // The optimistic removal was wrong for these files.
                    restoreToFeed(slots.filter { it.image.uri !in succeededUris })
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
                if (!isCopy) restoreToFeed(slots)
                _uiState.update {
                    it.copy(lastActionFeedback = ActionFeedback("Failed: ${e.message}", isError = true))
                }
            }
        }
    }

    /** Move to the next photo without waiting for anything. */
    private fun advanceToNextImage() {
        val state = _uiState.value
        val next = state.currentIndex + 1
        if (next in state.images.indices) navigateToImage(next)
    }

    /** Put optimistically removed photos back where they were. */
    private fun restoreToFeed(slots: List<OptimisticFeed.Slot>) {
        if (slots.isEmpty()) return
        val state = _uiState.value
        val lists = OptimisticFeed.restore(
            images = state.images,
            allImages = state.allImages,
            slots = slots,
            currentIndex = state.currentIndex,
            sortByOrientation = state.sortByOrientation,
        )
        _uiState.update {
            it.copy(
                images = lists.images,
                allImages = lists.allImages,
                currentIndex = lists.currentIndex,
                portraitSectionStart = lists.portraitSectionStart,
            )
        }
    }

    /** Swipe left: request delete (does a temporary/pending delete). */
    fun requestDelete() {
        val state = _uiState.value
        if (state.images.isEmpty()) return

        maybeShowFirstRunHint(leftSwipeHint())

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

        maybeShowFirstRunHint(FirstRunHint.REVERT)

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
            val tutorialTs = settingsRepository.phoneGestureTutorialTs.first()
            val lastUsed = settingsRepository.lastAppUsedTs.first()
            val now = System.currentTimeMillis()
            // Tutorial fires on fresh install (never seen) OR after 7 days of inactivity (app not used for 7 days)
            if (tutorialTs == 0L || (lastUsed > 0L && (now - lastUsed) > ONE_WEEK_MS)) {
                _uiState.update { it.copy(showGestureTutorial = true) }
            }
            settingsRepository.recordAppUsed(now)
        }
    }

    fun dismissGestureTutorial() {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            settingsRepository.setPhoneGestureTutorialTs(now)
            settingsRepository.recordAppUsed(now)
            _uiState.update { it.copy(showGestureTutorial = false) }
        }
    }

    // ── Controls guide (info button) ──────────────────────────────────────

    fun showControlsGuide() {
        _uiState.update { it.copy(showControlsGuide = true) }
    }

    fun hideControlsGuide() {
        _uiState.update { it.copy(showControlsGuide = false) }
    }

    // ── One-time action explanations ──────────────────────────────────────

    /**
     * Show the explanation for [hint] the first time — and only the first
     * time — the user triggers that action. The wording is resolved against
     * the current settings so it describes what actually happened (copy vs.
     * move, and to which folder).
     *
     * The seen-key is persisted immediately and mirrored into the UI state so
     * a rapid second trigger cannot slip through before DataStore emits.
     */
    fun maybeShowFirstRunHint(hint: FirstRunHint) {
        val state = _uiState.value
        if (hint.key in state.seenFirstRunHints) return
        // Never stack a hint on top of the full-screen tutorial.
        if (state.showGestureTutorial) return

        _uiState.update {
            it.copy(
                seenFirstRunHints = it.seenFirstRunHints + hint.key,
                firstRunHint = FirstRunHintUi(
                    hint = hint,
                    title = FirstRunHintText.title(hint),
                    message = FirstRunHintText.message(
                        hint = hint,
                        collectionAction = state.collectionAction,
                        leftSwipeAction = state.leftSwipeAction,
                        collectionFolderName = state.collectionFolderName,
                        leftSwipeFolderName = state.leftSwipeFolderName,
                    ),
                ),
            )
        }

        viewModelScope.launch { settingsRepository.markFirstRunHintSeen(hint) }
    }

    fun dismissFirstRunHint() {
        _uiState.update { it.copy(firstRunHint = null) }
    }

    /** The hint that matches the configured left-swipe action. */
    private fun leftSwipeHint(): FirstRunHint =
        if (_uiState.value.leftSwipeAction == SwipeAction.DELETE) {
            FirstRunHint.SWIPE_LEFT_DELETE
        } else {
            FirstRunHint.SWIPE_LEFT_FOLDER
        }

    // ── RAW + JPEG pair suggestions ──────────────────────────────────────

    fun maybeShowRawJpegSuggestion() {
        val state = _uiState.value
        if (state.showRawJpegSuggestion) return
        if (FirstRunHint.RAW_JPEG_PAIRS.key in state.seenFirstRunHints) return
        if (state.moveRelatedFiles && state.fileTypeFilter != FileTypeFilter.ALL) return
        if (RelatedFiles.hasRawJpegPairs(state.allImages)) {
            _uiState.update { it.copy(showRawJpegSuggestion = true) }
        }
    }

    fun dismissRawJpegSuggestion() {
        _uiState.update { it.copy(showRawJpegSuggestion = false) }
        viewModelScope.launch {
            settingsRepository.markFirstRunHintSeen(FirstRunHint.RAW_JPEG_PAIRS)
        }
    }

    fun applyRawJpegFilter(filter: FileTypeFilter) {
        _uiState.update { it.copy(showRawJpegSuggestion = false, firstRunHint = null) }
        viewModelScope.launch {
            settingsRepository.markFirstRunHintSeen(FirstRunHint.RAW_JPEG_PAIRS)
            settingsRepository.markFirstRunHintSeen(FirstRunHint.FILTER_MISMATCH)
            settingsRepository.setPhoneFileTypeFilter(filter)
        }
        val label = when (filter) {
            FileTypeFilter.RAW -> "RAW files"
            FileTypeFilter.JPG -> "JPEG files"
            FileTypeFilter.ALL -> "all files"
        }
        showFeedback("Filtered to $label")
    }

    fun enableMoveRelatedFiles() {
        _uiState.update { it.copy(showRawJpegSuggestion = false) }
        viewModelScope.launch {
            settingsRepository.markFirstRunHintSeen(FirstRunHint.RAW_JPEG_PAIRS)
            settingsRepository.setPhoneMoveRelatedFiles(true)
        }
        showFeedback("Actions now apply to both RAW and JPEG")
    }

    // ── Filter mismatch hint ─────────────────────────────────────────────

    fun maybeShowFilterMismatchHint() {
        val state = _uiState.value
        if (state.showGestureTutorial || state.showControlsGuide) return
        if (state.showRawJpegSuggestion) return
        if (state.firstRunHint != null) return
        if (FirstRunHint.FILTER_MISMATCH.key in state.seenFirstRunHints) return
        if (state.fileTypeFilter == FileTypeFilter.ALL) return
        if (state.allImages.isEmpty()) return

        if (RelatedFiles.hasFilterMismatch(state.allImages, state.fileTypeFilter)) {
            val title = FirstRunHintText.title(FirstRunHint.FILTER_MISMATCH)
            val message = FirstRunHintText.message(
                hint = FirstRunHint.FILTER_MISMATCH,
                collectionAction = state.collectionAction,
                leftSwipeAction = state.leftSwipeAction,
                collectionFolderName = state.collectionFolderName,
                leftSwipeFolderName = state.leftSwipeFolderName,
                fileTypeFilter = state.fileTypeFilter,
            )
            _uiState.update {
                it.copy(
                    seenFirstRunHints = it.seenFirstRunHints + FirstRunHint.FILTER_MISMATCH.key,
                    firstRunHint = FirstRunHintUi(
                        hint = FirstRunHint.FILTER_MISMATCH,
                        title = title,
                        message = message,
                        actionLabel = "SHOW ALL",
                        onAction = { applyRawJpegFilter(FileTypeFilter.ALL) },
                    ),
                )
            }
            viewModelScope.launch {
                settingsRepository.markFirstRunHintSeen(FirstRunHint.FILTER_MISMATCH)
            }
        }
    }

    /**
     * Rescan the current source folder from disk.
     *
     * Discards memory caches of published URIs, dimensions, and EXIF, but preserves
     * the currently viewed photo so the user does not lose their place.
     */
    fun reloadSourceFolder() {
        val folderUriStr = _uiState.value.sourceFolderUri ?: return
        val folderUri = Uri.parse(folderUriStr)
        val currentUri = _uiState.value.images.getOrNull(_uiState.value.currentIndex)?.uri
        val wasActive = _uiState.value.images.isNotEmpty()
        val urisBefore = _uiState.value.images.map { it.uri }.toSet()
        finalizePendingDelete()
        discoveryJob?.cancel()
        dimensionsJob?.cancel()
        publishedUris.clear()
        dimensionsResolved.clear()
        loadedExifCache.clear()

        discoveryJob = viewModelScope.launch {
            val scanInfo = settingsRepository.getFolderScanInfo(folderUriStr)
            _uiState.update {
                it.copy(
                    isLoading = true,
                    isDiscovering = true,
                    error = null,
                    images = emptyList(),
                    allImages = emptyList(),
                    currentIndex = 0,
                    portraitSectionStart = -1,
                    showRawJpegSuggestion = false,
                    reloadPrompt = null,
                )
            }
            collectDiscoveredImages(
                folderUri = folderUri,
                restorePosition = false,
                errorLabel = "images",
                targetUriToKeep = currentUri,
                previousMaxLastModified = scanInfo.maxLastModified,
                wasActiveSession = wasActive,
                urisBeforeReload = urisBefore,
            )
        }
    }

    /** User chose to jump to latest/new photos discovered during active session reload. */
    fun confirmReloadJumpToLatest() {
        val prompt = _uiState.value.reloadPrompt ?: return
        val target = if (prompt.firstNewIndex in _uiState.value.images.indices) {
            prompt.firstNewIndex
        } else {
            (_uiState.value.images.size - 1).coerceAtLeast(0)
        }
        _uiState.update { it.copy(reloadPrompt = null) }
        navigateToImage(target)
        showFeedback("Jumped to newest photos")
        _uiState.value.sourceFolderUri?.let { uri ->
            viewModelScope.launch {
                val updatedScan = FolderScanLogic.computeUpdatedScanInfo(_uiState.value.images)
                settingsRepository.setFolderScanInfo(uri, updatedScan)
            }
        }
    }

    /** User chose to stay at current photo after active session reload. */
    fun dismissReloadPrompt() {
        _uiState.update { it.copy(reloadPrompt = null) }
        _uiState.value.sourceFolderUri?.let { uri ->
            viewModelScope.launch {
                val updatedScan = FolderScanLogic.computeUpdatedScanInfo(_uiState.value.images)
                settingsRepository.setFolderScanInfo(uri, updatedScan)
            }
        }
    }

    private fun showFeedback(message: String, isError: Boolean = false) {
        _uiState.update {
            it.copy(lastActionFeedback = ActionFeedback(message = message, isError = isError))
        }
    }

    // ── Navigation helpers ───────────────────────────────────────────────

    /** Go back to the landing screen (clears images). */
    fun goBackToLanding() {
        finalizePendingDelete()
        discoveryJob?.cancel()
        dimensionsJob?.cancel()
        publishedUris.clear()
        dimensionsResolved.clear()
        pendingRestoreIndex = null
        _uiState.update {
            it.copy(
                images = emptyList(),
                allImages = emptyList(),
                currentIndex = 0,
                portraitSectionStart = -1,
                isDiscovering = false,
                reloadPrompt = null,
            )
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
     * Load missing dimensions for the current feed, nearest-to-current first.
     * Results are applied in batches of [DIMENSION_BATCH_SIZE]: one state update
     * (and one recomposition) per batch instead of per image, which matters in
     * folders with thousands of photos.
     *
     * Discovery calls this once per streamed batch, so it deliberately does
     * **not** restart a run that is still in flight — cancelling and re-sorting
     * on every batch would throw away in-progress work and re-read dimensions
     * that were already resolved. [dimensionsResolved] makes each URI read once.
     * Pass [force] to restart (used when enumeration has finished).
     */
    private fun loadDimensionsAsynchronously(force: Boolean = false) {
        if (!force && dimensionsJob?.isActive == true) return
        dimensionsJob?.cancel()
        dimensionsJob = viewModelScope.launch {
            val images = _uiState.value.images
            val currentIndex = _uiState.value.currentIndex
            val sortedIndices = images.indices.sortedBy { abs(it - currentIndex) }

            val batch = mutableMapOf<String, Pair<Int, Int>>()
            for (idx in sortedIndices) {
                val image = images.getOrNull(idx) ?: continue
                if (image.uri in dimensionsResolved) continue
                if (image.imageWidth == 0 && image.imageHeight == 0) {
                    val (w, h) = try {
                        imageRepository.getImageDimensions(Uri.parse(image.uri))
                    } catch (e: Exception) {
                        Pair(0, 0)
                    }
                    dimensionsResolved.add(image.uri)
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

    public override fun onCleared() {
        // Finalizes primary AND sibling files on the application scope, so the
        // deletion completes even though the ViewModel is going away.
        finalizePendingDelete()
        super.onCleared()
    }
}
