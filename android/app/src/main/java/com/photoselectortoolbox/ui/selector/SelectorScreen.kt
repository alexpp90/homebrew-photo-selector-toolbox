package com.photoselectortoolbox.ui.selector

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.WbShade
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import android.app.Activity
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.photoselectortoolbox.data.source.googledrive.GoogleDriveImageSource
import com.photoselectortoolbox.ui.components.DriveFolderPickerDialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.photoselectortoolbox.data.model.ImageItem
import com.photoselectortoolbox.ui.components.EmptyStateCard
import com.photoselectortoolbox.ui.components.MetadataPanel
import com.photoselectortoolbox.ui.components.ProgressIndicatorBar
import com.photoselectortoolbox.ui.components.ScoreChip
import com.photoselectortoolbox.ui.theme.Indigo500
import com.photoselectortoolbox.ui.theme.Zinc700
import com.photoselectortoolbox.ui.theme.Zinc800
import com.photoselectortoolbox.ui.theme.Zinc900
import com.photoselectortoolbox.ui.theme.Zinc950
import com.photoselectortoolbox.viewmodel.SelectorViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SelectorScreen(
    windowSizeClass: WindowSizeClass,
    viewModel: SelectorViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

    val context = LocalContext.current
    val dragAndDropTarget = remember(context, viewModel) {
        object : DragAndDropTarget {
            override fun onDrop(event: DragAndDropEvent): Boolean {
                val dragEvent = event.toAndroidDragEvent()
                val activity = context as? Activity
                val permissions = activity?.requestDragAndDropPermissions(dragEvent)
                val clipData = dragEvent.clipData
                if (clipData != null && clipData.itemCount > 0) {
                    val uri = clipData.getItemAt(0).uri
                    if (uri != null) {
                        viewModel.selectFolder(uri)
                        return true
                    }
                }
                return false
            }
        }
    }

    var showScanConfig by remember { mutableStateOf(false) }
    var showFullscreen by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showDrivePicker by remember { mutableStateOf(false) }

    // Google Sign-In launcher
    val googleSignInLauncher = rememberLauncherForActivityResult(StartActivityForResult()) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
            viewModel.driveAuth.handleSignInResult(account)
            // After sign-in, show the Drive folder picker
            showDrivePicker = true
        } catch (e: Exception) {
            android.util.Log.e("SelectorScreen", "Google Sign-In failed", e)
            viewModel.setError("Google Sign-In failed: ${e.message}")
        }
    }

    val isExpanded = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded
    val isMedium = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Medium

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let { viewModel.selectFolder(it) }
    }

    val currentImage = uiState.images.getOrNull(uiState.currentIndex)

    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            scope.launch {
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar(
                    message = error,
                    duration = SnackbarDuration.Short,
                )
            }
            viewModel.clearError()
        }
    }

    // Request focus for keyboard events when images are loaded
    LaunchedEffect(uiState.images.isNotEmpty()) {
        if (uiState.images.isNotEmpty()) {
            try {
                focusRequester.requestFocus()
            } catch (_: Exception) {
                // Focus request may fail if not yet attached
            }
        }
    }

    // Delete confirmation dialog
    if (uiState.showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissDeleteConfirmation() },
            title = { Text("Delete Image") },
            text = {
                Text("Delete \"${currentImage?.fileName ?: ""}\"? This cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.deleteCurrentImage() },
                    modifier = Modifier.testTag("dialog_confirm_delete")
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDeleteConfirmation() }) {
                    Text("Cancel")
                }
            },
            containerColor = Zinc800,
        )
    }

    // Google Drive folder picker dialog
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

    // Scan config sheet/dialog
    if (showScanConfig) {
        ScanConfigSheet(
            onStartScan = {
                showScanConfig = false
                viewModel.startScan()
            },
            onDismiss = { showScanConfig = false },
            isExpanded = isExpanded || isMedium,
        )
    }

    // Fullscreen viewer
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
                scope.launch {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(
                        message = "Moved to Selection",
                        duration = SnackbarDuration.Short,
                    )
                }
            },
            onCopyToSelection = { index ->
                viewModel.navigateToImage(index)
                viewModel.copyToSelection()
                scope.launch {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(
                        message = "Copied to Selection",
                        duration = SnackbarDuration.Short,
                    )
                }
            },
            windowSizeClass = windowSizeClass,
            onPageSelected = viewModel::navigateToImage,
            fullscreenButtonsEnabled = uiState.fullscreenButtonsEnabled,
            fullscreenGestureAction = uiState.fullscreenGestureAction,
        )
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .dragAndDropTarget(
                shouldStartDragAndDrop = { true },
                target = dragAndDropTarget
            )
            .then(
                if (uiState.images.isNotEmpty()) {
                    Modifier
                        .focusRequester(focusRequester)
                        .onKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown) {
                                when (event.key) {
                                    Key.DirectionRight -> {
                                        viewModel.navigateNext(); true
                                    }
                                    Key.DirectionLeft -> {
                                        viewModel.navigatePrevious(); true
                                    }
                                    Key.Delete -> {
                                        viewModel.showDeleteConfirmation(); true
                                    }
                                    Key.M -> {
                                        viewModel.moveToSelection()
                                        scope.launch {
                                            snackbarHostState.currentSnackbarData?.dismiss()
                                            snackbarHostState.showSnackbar(
                                                "Moved to Selection",
                                                duration = SnackbarDuration.Short,
                                            )
                                        }
                                        true
                                    }
                                    Key.C -> {
                                        viewModel.copyToSelection()
                                        scope.launch {
                                            snackbarHostState.currentSnackbarData?.dismiss()
                                            snackbarHostState.showSnackbar(
                                                "Copied to Selection",
                                                duration = SnackbarDuration.Short,
                                            )
                                        }
                                        true
                                    }
                                    Key.Escape -> {
                                        if (showFullscreen) {
                                            showFullscreen = false; true
                                        } else false
                                    }
                                    else -> false
                                }
                            } else false
                        }
                } else Modifier
            ),
        containerColor = Zinc900,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (uiState.folderUri != null) uiState.folderName else "Photo Selector",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Zinc950,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
                actions = {
                    if (uiState.images.isNotEmpty()) {
                        Text(
                            text = "${uiState.currentIndex + 1} / ${uiState.images.size}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }

                    IconButton(
                        onClick = { folderPickerLauncher.launch(null) },
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = "Open Folder",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    // Google Drive button
                    IconButton(
                        onClick = {
                            if (viewModel.driveAuth.isSignedIn) {
                                showDrivePicker = true
                            } else {
                                googleSignInLauncher.launch(viewModel.driveAuth.getSignInIntent())
                            }
                        },
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cloud,
                            contentDescription = "Google Drive",
                            tint = if (viewModel.driveAuth.isSignedIn)
                                Indigo500
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    // Scan button
                    if (uiState.images.isNotEmpty() && !uiState.isScanRunning) {
                        IconButton(
                            onClick = { showScanConfig = true },
                            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Radar,
                                contentDescription = "Scan",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Menu",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Scan Images") },
                                onClick = {
                                    showMenu = false
                                    showScanConfig = true
                                },
                                enabled = uiState.images.isNotEmpty() && !uiState.isScanRunning,
                            )
                            if (uiState.isScanRunning) {
                                DropdownMenuItem(
                                    text = { Text("Cancel Scan") },
                                    onClick = {
                                        showMenu = false
                                        viewModel.cancelScan()
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Group Similar Series")
                                        Checkbox(
                                            checked = uiState.groupingEnabled,
                                            onCheckedChange = null,
                                            colors = CheckboxDefaults.colors(
                                                checkedColor = Indigo500
                                            )
                                        )
                                    }
                                },
                                onClick = {
                                    showMenu = false
                                    viewModel.toggleGrouping()
                                },
                                enabled = uiState.images.isNotEmpty(),
                            )
                            DropdownMenuItem(
                                text = { Text("Clear Scores") },
                                onClick = {
                                    showMenu = false
                                    viewModel.clearScores()
                                },
                                enabled = uiState.images.any { it.scanResult != null },
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // Scan progress bar
            AnimatedVisibility(
                visible = uiState.isScanRunning,
                enter = slideInVertically() + fadeIn(),
                exit = slideOutVertically() + fadeOut(),
            ) {
                ProgressIndicatorBar(
                    progress = uiState.scanProgress,
                    statusText = uiState.scanStatusText,
                    isIndeterminate = uiState.scanProgress == 0f,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            if (uiState.folderUri == null || uiState.images.isEmpty()) {
                // No folder selected state
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        EmptyStateCard(
                            icon = Icons.Default.PhotoCamera,
                            title = "No Photos Loaded",
                            description = "Select a folder to start reviewing and culling your photos.",
                            actionLabel = "Select a Folder",
                            onAction = { folderPickerLauncher.launch(null) },
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        FilledTonalButton(
                            onClick = {
                                if (viewModel.driveAuth.isSignedIn) {
                                    showDrivePicker = true
                                } else {
                                    googleSignInLauncher.launch(viewModel.driveAuth.getSignInIntent())
                                }
                            },
                        ) {
                            Icon(Icons.Default.Cloud, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Open from Google Drive")
                        }
                    }
                }
            } else if (isExpanded || isMedium) {
                // Expanded layout: two-column for tablet / DeX
                ExpandedSelectorLayout(
                    uiState = uiState,
                    windowSizeClass = windowSizeClass,
                    onNavigateToImage = viewModel::navigateToImage,
                    onNavigateNext = viewModel::navigateNext,
                    onNavigatePrevious = viewModel::navigatePrevious,
                    onFullscreen = { showFullscreen = true },
                    onScan = { showScanConfig = true },
                    onMoveToSelection = {
                        viewModel.moveToSelection()
                        scope.launch {
                            snackbarHostState.currentSnackbarData?.dismiss()
                            snackbarHostState.showSnackbar(
                                "Moved to Selection",
                                duration = SnackbarDuration.Short,
                            )
                        }
                    },
                    onCopyToSelection = {
                        viewModel.copyToSelection()
                        scope.launch {
                            snackbarHostState.currentSnackbarData?.dismiss()
                            snackbarHostState.showSnackbar(
                                "Copied to Selection",
                                duration = SnackbarDuration.Short,
                            )
                        }
                    },
                    onDelete = { viewModel.showDeleteConfirmation() },
                )
            } else {
                // Compact layout: phone
                CompactSelectorLayout(
                    uiState = uiState,
                    onNavigateToImage = viewModel::navigateToImage,
                    onFullscreen = { showFullscreen = true },
                    onMoveToSelection = {
                        viewModel.moveToSelection()
                        scope.launch {
                            snackbarHostState.currentSnackbarData?.dismiss()
                            snackbarHostState.showSnackbar(
                                "Moved to Selection",
                                duration = SnackbarDuration.Short,
                            )
                        }
                    },
                    onCopyToSelection = {
                        viewModel.copyToSelection()
                        scope.launch {
                            snackbarHostState.currentSnackbarData?.dismiss()
                            snackbarHostState.showSnackbar(
                                "Copied to Selection",
                                duration = SnackbarDuration.Short,
                            )
                        }
                    },
                    onDelete = { viewModel.showDeleteConfirmation() },
                    onSwipeDelete = { viewModel.deleteWithSwipe() },
                )
            }
        }
    }
}

// -- Expanded (tablet / DeX) layout --------------------------------------------------

@Composable
private fun ExpandedSelectorLayout(
    uiState: com.photoselectortoolbox.viewmodel.SelectorUiState,
    windowSizeClass: WindowSizeClass,
    onNavigateToImage: (Int) -> Unit,
    onNavigateNext: () -> Unit,
    onNavigatePrevious: () -> Unit,
    onFullscreen: () -> Unit,
    onScan: () -> Unit,
    onMoveToSelection: () -> Unit,
    onCopyToSelection: () -> Unit,
    onDelete: () -> Unit,
) {
    var useFocusedLayout by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (useFocusedLayout) {
            FocusedSelectorLayout(
                uiState = uiState,
                windowSizeClass = windowSizeClass,
                onNavigateToImage = onNavigateToImage,
                onNavigateNext = onNavigateNext,
                onNavigatePrevious = onNavigatePrevious,
                onFullscreen = onFullscreen,
                onMoveToSelection = onMoveToSelection,
                onCopyToSelection = onCopyToSelection,
                onDelete = onDelete,
            )
        } else {
            ThreeColumnSelectorLayout(
                uiState = uiState,
                windowSizeClass = windowSizeClass,
                onNavigateToImage = onNavigateToImage,
                onNavigateNext = onNavigateNext,
                onNavigatePrevious = onNavigatePrevious,
                onFullscreen = onFullscreen,
                onMoveToSelection = onMoveToSelection,
                onCopyToSelection = onCopyToSelection,
                onDelete = onDelete,
            )
        }

        // Layout toggle button (top-right corner)
        IconButton(
            onClick = { useFocusedLayout = !useFocusedLayout },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(32.dp),
        ) {
            Icon(
                imageVector = if (useFocusedLayout) Icons.Default.GridView else Icons.Default.ViewAgenda,
                contentDescription = if (useFocusedLayout) "Three column view" else "Focused view",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/**
 * Original three-column layout: Previous | Current | Next side by side.
 */
@Composable
private fun ThreeColumnSelectorLayout(
    uiState: com.photoselectortoolbox.viewmodel.SelectorUiState,
    windowSizeClass: WindowSizeClass,
    onNavigateToImage: (Int) -> Unit,
    onNavigateNext: () -> Unit,
    onNavigatePrevious: () -> Unit,
    onFullscreen: () -> Unit,
    onMoveToSelection: () -> Unit,
    onCopyToSelection: () -> Unit,
    onDelete: () -> Unit,
) {
    val currentImage = uiState.images.getOrNull(uiState.currentIndex)

    val isHeightCompact = windowSizeClass.heightSizeClass == WindowHeightSizeClass.Compact
    val verticalPadding = if (isHeightCompact) 4.dp else 8.dp
    val startPadding = if (isHeightCompact) 8.dp else 16.dp

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = startPadding, vertical = verticalPadding),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Column 1: Previous Image
            val prevImage = uiState.images.getOrNull(uiState.currentIndex - 1)
            ImageComparisonColumn(
                modifier = Modifier.weight(1f),
                imageItem = prevImage,
                title = "Previous",
                isCurrent = false,
                onClick = onNavigatePrevious
            )

            // Column 2: Current Image
            ImageComparisonColumn(
                modifier = Modifier.weight(1f),
                imageItem = currentImage,
                title = "Current",
                isCurrent = true,
                onClick = onFullscreen,
                actionsContent = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilledTonalButton(
                            onClick = onMoveToSelection,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("move_button_expanded")
                                .pointerHoverIcon(PointerIcon.Hand),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.DriveFileMove, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Move", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium)
                        }
                        FilledTonalButton(
                            onClick = onCopyToSelection,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("copy_button_expanded")
                                .pointerHoverIcon(PointerIcon.Hand),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Copy", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium)
                        }
                        Button(
                            onClick = onDelete,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("delete_button_expanded")
                                .pointerHoverIcon(PointerIcon.Hand),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Delete", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            )

            // Column 3: Next Image
            val nextImage = uiState.images.getOrNull(uiState.currentIndex + 1)
            ImageComparisonColumn(
                modifier = Modifier.weight(1f),
                imageItem = nextImage,
                title = "Next",
                isCurrent = false,
                onClick = onNavigateNext
            )
        }

        // Bottom candidate strip
        CandidateStrip(
            images = uiState.images,
            currentIndex = uiState.currentIndex,
            onImageSelected = onNavigateToImage,
            groups = if (uiState.groupingEnabled) uiState.groups else null,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
        )
    }
}

/**
 * Focused layout optimized for tablets:
 * - Top row: large current image with controls on the right side
 * - Bottom row: previous and next images side by side (smaller)
 * Optimized for 3:2 aspect ratio images on typical tablet screens.
 */
@Composable
private fun FocusedSelectorLayout(
    uiState: com.photoselectortoolbox.viewmodel.SelectorUiState,
    windowSizeClass: WindowSizeClass,
    onNavigateToImage: (Int) -> Unit,
    onNavigateNext: () -> Unit,
    onNavigatePrevious: () -> Unit,
    onFullscreen: () -> Unit,
    onMoveToSelection: () -> Unit,
    onCopyToSelection: () -> Unit,
    onDelete: () -> Unit,
) {
    val currentImage = uiState.images.getOrNull(uiState.currentIndex)
    val prevImage = uiState.images.getOrNull(uiState.currentIndex - 1)
    val nextImage = uiState.images.getOrNull(uiState.currentIndex + 1)

    // Vertical drag gesture state
    var dragAccumulator by remember(uiState.currentIndex) { mutableStateOf(0f) }
    val dragThreshold = 150f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Top: Large centered current image with overlay details and floating actions
        Box(
            modifier = Modifier
                .weight(0.55f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Zinc950)
                .pointerInput(uiState.currentIndex) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            if (dragAccumulator < -dragThreshold) {
                                onNavigateNext()
                            } else if (dragAccumulator > dragThreshold) {
                                onNavigatePrevious()
                            }
                            dragAccumulator = 0f
                        },
                        onDragCancel = {
                            dragAccumulator = 0f
                        },
                        onVerticalDrag = { _, dragAmount ->
                            // Dragging up (moving next) is a negative dragAmount in Android coordinates.
                            // Dragging down (moving previous) is a positive dragAmount in Android coordinates.
                            dragAccumulator += dragAmount
                        }
                    )
                }
                .then(
                    if (currentImage != null) Modifier
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable { onFullscreen() }
                    else Modifier
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (currentImage != null) {
                val parsedUri = remember(currentImage.uri) { Uri.parse(currentImage.uri) }
                AsyncImage(
                    model = parsedUri,
                    contentDescription = currentImage.fileName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )

                // Floating Action Buttons (Top Right Corner)
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalIconButton(
                        onClick = onMoveToSelection,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = Color.Black.copy(alpha = 0.6f),
                            contentColor = Color.White
                        ),
                        modifier = Modifier.size(36.dp).pointerHoverIcon(PointerIcon.Hand)
                    ) {
                        Icon(Icons.Default.DriveFileMove, contentDescription = "Move to Selection", modifier = Modifier.size(18.dp))
                    }

                    FilledTonalIconButton(
                        onClick = onCopyToSelection,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = Color.Black.copy(alpha = 0.6f),
                            contentColor = Color.White
                        ),
                        modifier = Modifier.size(36.dp).pointerHoverIcon(PointerIcon.Hand)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy to Selection", modifier = Modifier.size(18.dp))
                    }

                    FilledTonalIconButton(
                        onClick = onDelete,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = Color.Black.copy(alpha = 0.6f),
                            contentColor = Color.White
                        ),
                        modifier = Modifier.size(36.dp).pointerHoverIcon(PointerIcon.Hand)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    }

                    FilledTonalIconButton(
                        onClick = onFullscreen,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = Color.Black.copy(alpha = 0.6f),
                            contentColor = Color.White
                        ),
                        modifier = Modifier.size(36.dp).pointerHoverIcon(PointerIcon.Hand)
                    ) {
                        Icon(Icons.Default.Fullscreen, contentDescription = "Fullscreen", modifier = Modifier.size(18.dp))
                    }
                }

                // Overlay Info Panel (Bottom Left Corner)
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Black.copy(alpha = 0.65f),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = currentImage.fileName,
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Text(
                                text = "${uiState.currentIndex + 1} / ${uiState.images.size}",
                                style = MaterialTheme.typography.labelLarge,
                                color = Indigo500,
                            )
                        }

                        currentImage.exifData?.let { exif ->
                            val metadataItems = buildList {
                                exif.iso?.let { add("ISO $it") }
                                exif.shutterSpeed?.let { speed ->
                                    val formatted = if (speed < 1.0 && speed > 0.0) {
                                        "1/${(1.0 / speed).toInt()}s"
                                    } else "${speed}s"
                                    add(formatted)
                                }
                                exif.aperture?.let { add("f/%.1f".format(java.util.Locale.US, it)) }
                                exif.focalLength?.let { add("${it.toInt()}mm") }
                                if (exif.lens != "Unknown") add(exif.lens)
                            }

                            if (metadataItems.isNotEmpty()) {
                                Text(
                                    text = metadataItems.joinToString("  ·  "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.8f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }

                        currentImage.scanResult?.let { scores ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ScoreChip(
                                    icon = Icons.Default.CenterFocusStrong,
                                    label = "Sharpness",
                                    value = scores.sharpnessScore,
                                )
                                ScoreChip(
                                    icon = Icons.Default.Grain,
                                    label = "Noise",
                                    value = scores.noiseLevel,
                                )
                                ScoreChip(
                                    icon = Icons.Default.Highlight,
                                    label = "Highlight",
                                    value = scores.highlightClipping,
                                    format = "%.1f%%",
                                )
                                ScoreChip(
                                    icon = Icons.Default.WbShade,
                                    label = "Shadow",
                                    value = scores.shadowClipping,
                                    format = "%.1f%%",
                                )
                            }
                        }
                    }
                }
            } else {
                Text("No Image", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            }
        }

        // Bottom: Previous and Next side by side (optimized height)
        Row(
            modifier = Modifier
                .weight(0.45f)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Previous
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Zinc950)
                    .then(
                        if (prevImage != null) Modifier
                            .pointerHoverIcon(PointerIcon.Hand)
                            .clickable { onNavigatePrevious() }
                        else Modifier
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (prevImage != null) {
                    val parsedPrevUri = remember(prevImage.uri) { Uri.parse(prevImage.uri) }
                    AsyncImage(
                        model = parsedPrevUri,
                        contentDescription = prevImage.fileName,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.1f)))
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(8.dp),
                    ) {
                        Text("Previous", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
                        Text(prevImage.fileName, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Previous",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier
                            .size(32.dp)
                            .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                            .padding(6.dp),
                    )
                } else {
                    Text("No Previous", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f), style = MaterialTheme.typography.bodySmall)
                }
            }

            // Next
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Zinc950)
                    .then(
                        if (nextImage != null) Modifier
                            .pointerHoverIcon(PointerIcon.Hand)
                            .clickable { onNavigateNext() }
                        else Modifier
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (nextImage != null) {
                    val parsedNextUri = remember(nextImage.uri) { Uri.parse(nextImage.uri) }
                    AsyncImage(
                        model = parsedNextUri,
                        contentDescription = nextImage.fileName,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.1f)))
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(8.dp),
                    ) {
                        Text("Next", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
                        Text(nextImage.fileName, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Next",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier
                            .size(32.dp)
                            .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                            .padding(6.dp),
                    )
                } else {
                    Text("No Next", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f), style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // Bottom candidate strip
        CandidateStrip(
            images = uiState.images,
            currentIndex = uiState.currentIndex,
            onImageSelected = onNavigateToImage,
            groups = if (uiState.groupingEnabled) uiState.groups else null,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ImageComparisonColumn(
    modifier: Modifier = Modifier,
    imageItem: ImageItem?,
    title: String,
    isCurrent: Boolean,
    onClick: () -> Unit,
    actionsContent: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = modifier.fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = if (isCurrent) Indigo500 else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Zinc950)
                .testTag("column_${title.lowercase()}")
                .then(
                    if (imageItem != null) {
                        Modifier
                            .pointerHoverIcon(PointerIcon.Hand)
                            .clickable { onClick() }
                    } else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            if (imageItem != null) {
                AsyncImage(
                    model = imageItem.uri,
                    contentDescription = imageItem.fileName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
                
                if (!isCurrent) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.15f))
                    )
                    Icon(
                        imageVector = if (title == "Previous") Icons.AutoMirrored.Filled.ArrowBack else Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier
                            .size(36.dp)
                            .align(Alignment.Center)
                            .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                            .padding(8.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Fullscreen,
                        contentDescription = "Fullscreen",
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                    )
                }
            } else {
                Text(
                    text = "No Image",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
            }
        }

        if (imageItem != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = imageItem.fileName,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                imageItem.exifData?.let { exif ->
                    MetadataPanel(
                        exifData = exif,
                        compact = true
                    )
                }

                imageItem.scanResult?.let { scores ->
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        ScoreChip(
                            icon = Icons.Default.CenterFocusStrong,
                            label = "Sharpness",
                            value = scores.sharpnessScore
                        )
                        ScoreChip(
                            icon = Icons.Default.Grain,
                            label = "Noise",
                            value = scores.noiseLevel
                        )
                        ScoreChip(
                            icon = Icons.Default.Highlight,
                            label = "Highlight",
                            value = scores.highlightClipping,
                            format = "%.1f%%"
                        )
                        ScoreChip(
                            icon = Icons.Default.WbShade,
                            label = "Shadow",
                            value = scores.shadowClipping,
                            format = "%.1f%%"
                        )
                    }
                }
                
                actionsContent?.let {
                    Spacer(modifier = Modifier.height(4.dp))
                    it()
                }
            }
        } else {
            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

// -- Compact (phone) layout ----------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompactSelectorLayout(
    uiState: com.photoselectortoolbox.viewmodel.SelectorUiState,
    onNavigateToImage: (Int) -> Unit,
    onFullscreen: () -> Unit,
    onMoveToSelection: () -> Unit,
    onCopyToSelection: () -> Unit,
    onDelete: () -> Unit,
    onSwipeDelete: () -> Unit,
) {
    val pagerState = rememberPagerState(
        initialPage = uiState.currentIndex,
        pageCount = { uiState.images.size },
    )

    // Sync pager with viewmodel state
    LaunchedEffect(uiState.currentIndex) {
        if (pagerState.currentPage != uiState.currentIndex) {
            pagerState.animateScrollToPage(uiState.currentIndex)
        }
    }

    // Sync viewmodel with pager swipes
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            onNavigateToImage(page)
        }
    }

    val currentImage = uiState.images.getOrNull(uiState.currentIndex)

    // Gesture tutorial overlay state — always shown when images load
    var showGestureTutorial by remember { mutableStateOf(false) }
    LaunchedEffect(uiState.images.isNotEmpty()) {
        if (uiState.images.isNotEmpty()) {
            showGestureTutorial = true
        }
    }

    // Swipe-to-dismiss state for the bottom info card, keyed to current image
    // so it resets when the image changes or after a delete
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onSwipeDelete()
                false // Don't visually settle at dismissed — the ViewModel handles the result
            } else {
                false
            }
        },
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Swipeable image pager
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    key = { uiState.images[it].uri },
                ) { page ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Zinc950),
                        contentAlignment = Alignment.Center,
                    ) {
                        AsyncImage(
                            model = uiState.images[page].uri,
                            contentDescription = uiState.images[page].fileName,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                        )
                    }
                }

                // Fullscreen button
                IconButton(
                    onClick = onFullscreen,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Fullscreen,
                        contentDescription = "Fullscreen",
                        tint = Color.White.copy(alpha = 0.8f),
                    )
                }

                // Filename overlay
                currentImage?.let { image ->
                    Text(
                        text = image.fileName,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(12.dp)
                            .background(
                                Color.Black.copy(alpha = 0.5f),
                                RoundedCornerShape(4.dp),
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }

            // Bottom info card with swipe-to-delete
            key(uiState.currentIndex, uiState.images.size) {
                SwipeToDismissBox(
                    state = dismissState,
                    enableDismissFromStartToEnd = false,
                    enableDismissFromEndToStart = true,
                    backgroundContent = {
                        // Red delete background revealed on swipe left
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.error),
                            contentAlignment = Alignment.CenterEnd,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = Color.White,
                                modifier = Modifier.padding(end = 24.dp),
                            )
                        }
                    },
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Zinc900),
                    ) {
                        // Score chips row
                        currentImage?.scanResult?.let { scores ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                ScoreChip(
                                    icon = Icons.Default.CenterFocusStrong,
                                    label = "Sharpness",
                                    value = scores.sharpnessScore,
                                )
                                ScoreChip(
                                    icon = Icons.Default.Grain,
                                    label = "Noise",
                                    value = scores.noiseLevel,
                                )
                                ScoreChip(
                                    icon = Icons.Default.Highlight,
                                    label = "Highlight",
                                    value = scores.highlightClipping,
                                    format = "%.0f%%",
                                )
                                ScoreChip(
                                    icon = Icons.Default.WbShade,
                                    label = "Shadow",
                                    value = scores.shadowClipping,
                                    format = "%.0f%%",
                                )
                            }
                        }

                        // Compact metadata (one-line)
                        currentImage?.exifData?.let { exif ->
                            MetadataPanel(
                                exifData = exif,
                                compact = true,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            )
                        }

                        // Action buttons row (compact icons)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            FilledTonalIconButton(
                                onClick = onMoveToSelection,
                                modifier = Modifier.testTag("move_button_compact")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DriveFileMove,
                                    contentDescription = "Move to Selection",
                                )
                            }

                            FilledTonalIconButton(
                                onClick = onCopyToSelection,
                                modifier = Modifier.testTag("copy_button_compact")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy to Selection",
                                )
                            }

                            FilledTonalIconButton(
                                onClick = onDelete,
                                modifier = Modifier.testTag("delete_button_compact")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Gesture tutorial overlay — always shown on launch
        GestureTutorialOverlay(
            visible = showGestureTutorial,
            onDismiss = { showGestureTutorial = false },
        )
    }
}

// -- Gesture tutorial overlay ---------------------------------------------------------

@Composable
private fun GestureTutorialOverlay(
    visible: Boolean,
    onDismiss: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.75f))
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.padding(32.dp),
            ) {
                Text(
                    text = "Gestures",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                )

                // Swipe image left/right to browse
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp),
                    )
                    Text(
                        text = "Swipe image to browse",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White,
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp),
                    )
                }

                // Swipe bottom card left to delete
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(28.dp),
                    )
                    Text(
                        text = "Swipe info card left to delete",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White,
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(28.dp),
                    )
                }

                // Tap image for fullscreen
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Fullscreen,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp),
                    )
                    Text(
                        text = "Tap fullscreen to zoom & inspect",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White,
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Tap anywhere to dismiss",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.6f),
                )
            }
        }
    }
}
