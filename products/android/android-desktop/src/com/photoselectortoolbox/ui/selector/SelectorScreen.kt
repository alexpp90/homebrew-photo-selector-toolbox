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
import androidx.compose.foundation.layout.Row
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
import com.photoselectortoolbox.ui.navigation.Screen
import com.photoselectortoolbox.viewmodel.SelectorFrame
import com.photoselectortoolbox.viewmodel.SelectorViewModel

/**
 * The culling workspace.
 *
 * This composable owns orchestration only — folder pickers, dialogs, sheets,
 * keyboard handling and which layout is on screen. The layouts themselves live
 * in [ThreeUpSelectorLayout] and [CompactSelectorLayout], because the geometry
 * of each is intricate enough that mixing it with dialog plumbing is how the
 * two drift apart.
 *
 * There is no app bar. Session controls live in [SelectorSidebar] to the left;
 * everything else that used to sit in a bar — folder name, position, burst chip
 * — now sits beside the photograph it describes. On a layout bound by height
 * (see [FrameGeometry]) a 44dp bar is 29dp off every frame, which is a price
 * this screen does not pay for chrome.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SelectorScreen(
    windowSizeClass: WindowSizeClass,
    currentRoute: String? = null,
    onNavigate: (Screen) -> Unit = {},
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
    var showShortcuts by remember { mutableStateOf(false) }
    var snackbarMessage by remember { mutableStateOf<String?>(null) }
    var contextMenuAt by remember { mutableStateOf<Offset?>(null) }
    var lastPointerPosition by remember { mutableStateOf(Offset.Zero) }

    val isExpanded = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded
    val isMedium = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Medium
    val useExpandedLayout = isExpanded || isMedium

    // Any open sheet swallows the shortcuts, so a stray M while configuring a
    // scan cannot move the frame behind the sheet. Esc is the exception — it is
    // what closes the sheet.
    val sheetOpen = showScanConfig || showScoreLegend || showDrivePicker || showShortcuts ||
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
        onDelete = { viewModel.requestDelete() },
        onFullscreen = { showFullscreen = true },
        onToggleDetails = viewModel::toggleDetails,
        onToggleFilmstrip = viewModel::toggleFilmstrip,
        onToggleOverlayValues = viewModel::toggleOverlayValues,
        onShowShortcuts = { showShortcuts = true },
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

    if (showShortcuts) {
        ShortcutSheet(
            filingAction = uiState.filingAction,
            onDismiss = { showShortcuts = false },
        )
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
            filingAction = uiState.filingAction,
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
                                maximised = uiState.maximisedFrame != null,
                                actions = actions,
                                onPrevious = viewModel::navigatePrevious,
                                onNext = viewModel::navigateNext,
                                onMaximise = viewModel::toggleMaximised,
                                onShowShortcuts = { showShortcuts = true },
                                onCloseOverlays = {
                                    when {
                                        showFullscreen -> showFullscreen = false
                                        contextMenuAt != null -> contextMenuAt = null
                                        showScanConfig -> showScanConfig = false
                                        showScoreLegend -> showScoreLegend = false
                                        showShortcuts -> showShortcuts = false
                                        uiState.maximisedFrame != null -> viewModel.clearMaximised()
                                    }
                                },
                            )
                        }
                } else {
                    Modifier
                }
            ),
    ) {
        // One horizontal band: sidebar, then the frames. Nothing above, nothing
        // below. The 44dp app bar that used to sit here cost 29dp of height on
        // every one of the three frames — see FrameGeometry for why height is
        // the axis that must not be spent.
        Row(modifier = Modifier.fillMaxSize()) {
            if (useExpandedLayout) {
                SelectorSidebar(
                    currentRoute = currentRoute,
                    groupingEnabled = uiState.groupingEnabled,
                    hasScores = uiState.hasAnyScores,
                    hasImages = uiState.images.isNotEmpty(),
                    driveSignedIn = viewModel.driveAuth.isSignedIn,
                    isScanning = uiState.isScanRunning,
                    scanStatusText = uiState.scanStatusText,
                    onOpenFolder = { folderPickerLauncher.launch(null) },
                    onOpenDrive = openDrive,
                    onScan = { showScanConfig = true },
                    onCancelScan = viewModel::cancelScan,
                    onToggleGrouping = viewModel::toggleGrouping,
                    onShowLegend = { showScoreLegend = true },
                    onShowMenu = { showMenu = true },
                    onNavigate = onNavigate,
                    overflowContent = {
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Scan images") },
                                onClick = { showMenu = false; showScanConfig = true },
                                enabled = uiState.images.isNotEmpty() && !uiState.isScanRunning,
                            )
                            DropdownMenuItem(
                                text = { Text("Open folder") },
                                onClick = { showMenu = false; folderPickerLauncher.launch(null) },
                            )
                            DropdownMenuItem(
                                text = { Text("Open from Google Drive") },
                                onClick = { showMenu = false; openDrive() },
                            )
                            DropdownMenuItem(
                                text = { Text("Clear scores") },
                                onClick = { showMenu = false; viewModel.clearScores() },
                                enabled = uiState.hasAnyScores,
                            )
                            DropdownMenuItem(
                                text = { Text("Keyboard shortcuts") },
                                onClick = { showMenu = false; showShortcuts = true },
                            )
                        }
                    },
                )
            }

            Column(modifier = Modifier.weight(1f).fillMaxSize()) {
                if (uiState.folderUri == null || uiState.images.isEmpty()) {
                    EmptySelectorState(
                        onOpenFolder = { folderPickerLauncher.launch(null) },
                        onOpenDrive = openDrive,
                    )
                } else {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        if (useExpandedLayout) {
                            ThreeUpSelectorLayout(
                                current = uiState.currentImage,
                                previous = uiState.previousImage,
                                next = uiState.nextImage,
                                currentIndex = uiState.currentIndex,
                                total = uiState.images.size,
                                filingAction = uiState.filingAction,
                                folderName = uiState.folderName,
                                burstLabel = burstLabelFor(uiState.groups, uiState.currentIndex)
                                    .takeIf { uiState.groupingEnabled },
                                detailsVisible = uiState.detailsVisible,
                                filmstripVisible = uiState.filmstripVisible,
                                overlayValuesVisible = uiState.overlayValuesVisible,
                                maximisedFrame = uiState.maximisedFrame,
                                actions = actions,
                                onNavigatePrevious = viewModel::navigatePrevious,
                                onNavigateNext = viewModel::navigateNext,
                                onMaximise = viewModel::toggleMaximised,
                                onLongPressFrame = { contextMenuAt = lastPointerPosition },
                                showFirstRunHint = !uiState.hasSeenNavHint,
                                onDismissFirstRunHint = viewModel::markNavHintSeen,
                            )
                        } else {
                            CompactSelectorLayout(
                                uiState = uiState,
                                onNavigateToImage = viewModel::navigateToImage,
                                onFullscreen = { showFullscreen = true },
                                onMoveToSelection = actions.onMove,
                                onCopyToSelection = actions.onCopy,
                                onDelete = actions.onDelete,
                                showGestureHint = !uiState.hasSeenFullscreenHint,
                                onDismissGestureHint = viewModel::markFullscreenHintSeen,
                            )
                        }
                    }

                    // The filmstrip is the one thing still allowed to take
                    // height, because the user asked for it explicitly and it
                    // costs nothing while hidden. It is off the critical path:
                    // maximised, it goes.
                    if (useExpandedLayout &&
                        uiState.filmstripVisible &&
                        uiState.maximisedFrame == null
                    ) {
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
                    filingAction = uiState.filingAction,
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
    maximised: Boolean,
    actions: SelectorActions,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onMaximise: (SelectorFrame) -> Unit,
    onShowShortcuts: () -> Unit,
    onCloseOverlays: () -> Unit,
): Boolean {
    if (key == Key.Escape) {
        if (sheetOpen || contextMenuOpen || fullscreenOpen || maximised) {
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
        // 1/2/3 maximise the frame in that position, left to right as they
        // appear on screen. Space used to toggle between two comparison
        // layouts; there is only one now, so the key is free and goes to the
        // thing users actually want mid-burst — a closer look at one frame.
        Key.One -> { onMaximise(SelectorFrame.PREVIOUS); true }
        Key.Two -> { onMaximise(SelectorFrame.CURRENT); true }
        Key.Three -> { onMaximise(SelectorFrame.NEXT); true }
        Key.Slash -> { onShowShortcuts(); true }
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
