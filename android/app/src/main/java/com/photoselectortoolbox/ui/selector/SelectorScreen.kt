package com.photoselectortoolbox.ui.selector

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.photoselectortoolbox.domain.format.SelectorLabels
import com.photoselectortoolbox.ui.components.DriveFolderPickerDialog
import com.photoselectortoolbox.ui.components.EmptyStateCard
import com.photoselectortoolbox.ui.components.ScoreLegendSheet
import com.photoselectortoolbox.ui.theme.Zinc800
import com.photoselectortoolbox.ui.theme.Zinc900
import com.photoselectortoolbox.viewmodel.SelectorViewModel

/**
 * The culling workspace.
 *
 * This composable owns orchestration only — folder pickers, dialogs, sheets,
 * keyboard handling and which layout is on screen. The layouts themselves live
 * in [FocusedSelectorLayout], [ThreeColumnSelectorLayout] and
 * [CompactSelectorLayout], because the geometry of each is intricate enough
 * that mixing it with dialog plumbing is how the two drift apart.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SelectorScreen(
    windowSizeClass: WindowSizeClass,
    viewModel: SelectorViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }

    var showScanConfig by remember { mutableStateOf(false) }
    var showFullscreen by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showDrivePicker by remember { mutableStateOf(false) }
    var showScoreLegend by remember { mutableStateOf(false) }
    var snackbarMessage by remember { mutableStateOf<String?>(null) }
    var contextMenuAt by remember { mutableStateOf<Offset?>(null) }
    var lastPointerPosition by remember { mutableStateOf(Offset.Zero) }

    val isExpanded = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded
    val isMedium = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Medium
    val useExpandedLayout = isExpanded || isMedium

    // Any open sheet swallows the shortcuts, so a stray M while configuring a
    // scan cannot move the frame behind the sheet. Esc is the exception — it is
    // what closes the sheet.
    val sheetOpen = showScanConfig || showScoreLegend || showDrivePicker ||
        uiState.showDeleteConfirmation

    val dragAndDropTarget = remember(context, viewModel) {
        object : DragAndDropTarget {
            override fun onDrop(event: DragAndDropEvent): Boolean {
                val dragEvent = event.toAndroidDragEvent()
                (context as? Activity)?.requestDragAndDropPermissions(dragEvent)
                val clipData = dragEvent.clipData
                if (clipData != null && clipData.itemCount > 0) {
                    clipData.getItemAt(0).uri?.let { uri ->
                        viewModel.selectFolder(uri)
                        return true
                    }
                }
                return false
            }
        }
    }

    val googleSignInLauncher = rememberLauncherForActivityResult(StartActivityForResult()) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
            viewModel.driveAuth.handleSignInResult(account)
            showDrivePicker = true
        } catch (e: Exception) {
            android.util.Log.e("SelectorScreen", "Google Sign-In failed", e)
            viewModel.setError("Google Sign-In failed: ${e.message}")
        }
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? -> uri?.let { viewModel.selectFolder(it) } }

    val openDrive: () -> Unit = {
        if (viewModel.driveAuth.isSignedIn) {
            showDrivePicker = true
        } else {
            googleSignInLauncher.launch(viewModel.driveAuth.getSignInIntent())
        }
    }

    // Move and Delete advance to the next frame; Copy stays put. That asymmetry
    // is the culling loop: a moved or deleted frame is finished with, a copied
    // one may still be compared against its neighbours.
    val actions = SelectorActions(
        onMove = {
            viewModel.moveToSelection()
            snackbarMessage = "Moved to Selection"
        },
        onCopy = {
            viewModel.copyToSelection()
            snackbarMessage = "Copied to Selection"
        },
        onDelete = { viewModel.showDeleteConfirmation() },
        onFullscreen = { showFullscreen = true },
        onToggleLayout = viewModel::toggleSelectorLayout,
        onToggleDetails = viewModel::toggleDetails,
        onToggleFilmstrip = viewModel::toggleFilmstrip,
    )

    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarMessage = error
            viewModel.clearError()
        }
    }

    LaunchedEffect(uiState.images.isNotEmpty()) {
        if (uiState.images.isNotEmpty()) {
            try {
                focusRequester.requestFocus()
            } catch (_: Exception) {
                // The node may not be attached yet; the next state change retries.
            }
        }
    }

    if (uiState.showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissDeleteConfirmation() },
            title = { Text("Delete Image") },
            text = {
                Text("Delete \"${uiState.currentImage?.fileName ?: ""}\"? This cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteCurrentImage()
                        snackbarMessage = SelectorLabels.deletedMessage(1)
                    },
                    modifier = Modifier.testTag("dialog_confirm_delete"),
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDeleteConfirmation() }) { Text("Cancel") }
            },
            containerColor = Zinc800,
        )
    }

    if (showDrivePicker) {
        DriveFolderPickerDialog(
            driveClient = viewModel.driveClient,
            onFolderSelected = { folderId, folderName ->
                showDrivePicker = false
                viewModel.selectDriveFolder(folderId, folderName)
            },
            onDismiss = { showDrivePicker = false },
        )
    }

    if (showScoreLegend) {
        ScoreLegendSheet(onDismiss = { showScoreLegend = false })
    }

    if (showScanConfig) {
        ScanConfigSheet(
            onStartScan = { config ->
                showScanConfig = false
                viewModel.startScan(config.aesthetic)
            },
            onDismiss = { showScanConfig = false },
            isExpanded = useExpandedLayout,
        )
    }

    if (showFullscreen && uiState.images.isNotEmpty()) {
        FullscreenViewer(
            images = uiState.images,
            initialIndex = uiState.currentIndex,
            onDismiss = { showFullscreen = false },
            onDelete = { index ->
                viewModel.navigateToImage(index)
                viewModel.showDeleteConfirmation()
            },
            onMoveToSelection = { index ->
                viewModel.navigateToImage(index)
                viewModel.moveToSelection()
                snackbarMessage = "Moved to Selection"
            },
            onCopyToSelection = { index ->
                viewModel.navigateToImage(index)
                viewModel.copyToSelection()
                snackbarMessage = "Copied to Selection"
            },
            windowSizeClass = windowSizeClass,
            onPageSelected = viewModel::navigateToImage,
            fullscreenButtonsEnabled = uiState.fullscreenButtonsEnabled,
            fullscreenGestureAction = uiState.fullscreenGestureAction,
            showGestureHint = !uiState.hasSeenFullscreenHint,
            onGestureHintSeen = viewModel::markFullscreenHintSeen,
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Zinc900)
            .dragAndDropTarget(
                shouldStartDragAndDrop = { true },
                target = dragAndDropTarget,
            )
            .trackPointerPosition { lastPointerPosition = it }
            .then(
                if (uiState.images.isNotEmpty()) {
                    Modifier
                        .focusRequester(focusRequester)
                        .onKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                            handleSelectorKey(
                                key = event.key,
                                sheetOpen = sheetOpen,
                                contextMenuOpen = contextMenuAt != null,
                                fullscreenOpen = showFullscreen,
                                actions = actions,
                                onPrevious = viewModel::navigatePrevious,
                                onNext = viewModel::navigateNext,
                                onCloseOverlays = {
                                    when {
                                        showFullscreen -> showFullscreen = false
                                        contextMenuAt != null -> contextMenuAt = null
                                        showScanConfig -> showScanConfig = false
                                        showScoreLegend -> showScoreLegend = false
                                    }
                                },
                            )
                        }
                } else {
                    Modifier
                }
            ),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            SelectorTopBar(
                folderName = if (uiState.folderUri != null) uiState.folderName else "Photo Selector",
                folderPath = uiState.folderUri,
                position = uiState.position,
                total = uiState.images.size,
                burstLabel = burstLabelFor(uiState.groups, uiState.currentIndex)
                    .takeIf { uiState.groupingEnabled },
                groupingEnabled = uiState.groupingEnabled,
                hasScores = uiState.hasAnyScores,
                isScanning = uiState.isScanRunning,
                scanProgress = uiState.scanProgress,
                scanStatusText = uiState.scanStatusText,
                driveSignedIn = viewModel.driveAuth.isSignedIn,
                onOpenFolder = { folderPickerLauncher.launch(null) },
                onOpenDrive = openDrive,
                onScan = { showScanConfig = true },
                onCancelScan = viewModel::cancelScan,
                onToggleGrouping = viewModel::toggleGrouping,
                onShowLegend = { showScoreLegend = true },
                onShowMenu = { showMenu = true },
                overflowContent = {
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Scan Images") },
                            onClick = { showMenu = false; showScanConfig = true },
                            enabled = uiState.images.isNotEmpty() && !uiState.isScanRunning,
                        )
                        DropdownMenuItem(
                            text = { Text("Open Folder") },
                            onClick = { showMenu = false; folderPickerLauncher.launch(null) },
                        )
                        DropdownMenuItem(
                            text = { Text("Open from Google Drive") },
                            onClick = { showMenu = false; openDrive() },
                        )
                        DropdownMenuItem(
                            text = { Text("Clear Scores") },
                            onClick = { showMenu = false; viewModel.clearScores() },
                            enabled = uiState.hasAnyScores,
                        )
                    }
                },
            )

            if (uiState.folderUri == null || uiState.images.isEmpty()) {
                EmptySelectorState(
                    onOpenFolder = { folderPickerLauncher.launch(null) },
                    onOpenDrive = openDrive,
                )
            } else {
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (useExpandedLayout) {
                        if (uiState.selectorLayoutFocused) {
                            FocusedSelectorLayout(
                                current = uiState.currentImage,
                                previous = uiState.previousImage,
                                next = uiState.nextImage,
                                currentIndex = uiState.currentIndex,
                                total = uiState.images.size,
                                detailsVisible = uiState.detailsVisible,
                                filmstripVisible = uiState.filmstripVisible,
                                actions = actions,
                                onNavigatePrevious = viewModel::navigatePrevious,
                                onNavigateNext = viewModel::navigateNext,
                                onLongPressFrame = { contextMenuAt = lastPointerPosition },
                                showFirstRunHint = !uiState.hasSeenNavHint,
                                onDismissFirstRunHint = viewModel::markNavHintSeen,
                            )
                        } else {
                            ThreeColumnSelectorLayout(
                                current = uiState.currentImage,
                                previous = uiState.previousImage,
                                next = uiState.nextImage,
                                currentIndex = uiState.currentIndex,
                                total = uiState.images.size,
                                detailsVisible = uiState.detailsVisible,
                                filmstripVisible = uiState.filmstripVisible,
                                actions = actions,
                                onNavigatePrevious = viewModel::navigatePrevious,
                                onNavigateNext = viewModel::navigateNext,
                                onLongPressFrame = { contextMenuAt = lastPointerPosition },
                            )
                        }
                    } else {
                        CompactSelectorLayout(
                            uiState = uiState,
                            onNavigateToImage = viewModel::navigateToImage,
                            onFullscreen = { showFullscreen = true },
                            onMoveToSelection = actions.onMove,
                            onCopyToSelection = actions.onCopy,
                            onDelete = actions.onDelete,
                            onSwipeDelete = { viewModel.deleteWithSwipe() },
                        )
                    }
                }

                if (useExpandedLayout && uiState.filmstripVisible) {
                    CandidateStrip(
                        images = uiState.images,
                        currentIndex = uiState.currentIndex,
                        onImageSelected = viewModel::navigateToImage,
                        groups = if (uiState.groupingEnabled) uiState.groups else null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        SelectorSnackbar(
            message = snackbarMessage,
            onUndo = null,
            onDismiss = { snackbarMessage = null },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 96.dp),
        )

        contextMenuAt?.let { offset ->
            Popup(
                offset = IntOffset(offset.x.toInt(), offset.y.toInt()),
                onDismissRequest = { contextMenuAt = null },
            ) {
                SelectorContextMenu(
                    actions = actions,
                    onDismissRequest = { contextMenuAt = null },
                )
            }
        }
    }
}

@Composable
private fun EmptySelectorState(
    onOpenFolder: () -> Unit,
    onOpenDrive: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            EmptyStateCard(
                icon = Icons.Default.PhotoCamera,
                title = "Select a Folder",
                description = "Choose a shoot folder to start comparing and culling frames.",
                actionLabel = "Open Folder",
                onAction = onOpenFolder,
            )
            FilledTonalButton(onClick = onOpenDrive) {
                Icon(Icons.Default.Cloud, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Open from Google Drive")
            }
        }
    }
}

/**
 * Keyboard and DeX shortcuts.
 *
 * Extracted as a plain function so the routing — including "shortcuts are
 * suppressed while a sheet is open, except Escape" — can be reasoned about and
 * tested without composing the screen.
 */
internal fun handleSelectorKey(
    key: Key,
    sheetOpen: Boolean,
    contextMenuOpen: Boolean,
    fullscreenOpen: Boolean,
    actions: SelectorActions,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onCloseOverlays: () -> Unit,
): Boolean {
    if (key == Key.Escape) {
        if (sheetOpen || contextMenuOpen || fullscreenOpen) {
            onCloseOverlays()
            return true
        }
        return false
    }

    if (sheetOpen) return false

    return when (key) {
        Key.DirectionLeft -> { onPrevious(); true }
        Key.DirectionRight -> { onNext(); true }
        Key.M -> { actions.onMove(); true }
        Key.C -> { actions.onCopy(); true }
        Key.Delete, Key.Backspace -> { actions.onDelete(); true }
        Key.F -> { actions.onFullscreen(); true }
        Key.Spacebar -> { actions.onToggleLayout(); true }
        else -> false
    }
}

/**
 * The `burst 3/7` label for the frame at [currentIndex], or null when it is
 * not part of a series.
 */
internal fun burstLabelFor(groups: List<List<Int>>, currentIndex: Int): String? {
    val series = groups.firstOrNull { currentIndex in it } ?: return null
    return SelectorLabels.burstChip(
        indexInSeries = series.indexOf(currentIndex),
        seriesLength = series.size,
    )
}
