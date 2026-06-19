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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Radar
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import android.app.Activity
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

    val isExpanded = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded
    val isMedium = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Medium

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let { viewModel.selectFolder(it) }
    }

    val currentImage = uiState.images.getOrNull(uiState.currentIndex)

    // Show error messages via snackbar
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(
                message = error,
                duration = SnackbarDuration.Short,
            )
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
                TextButton(onClick = { viewModel.deleteCurrentImage() }) {
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
                    snackbarHostState.showSnackbar(
                        message = "Copied to Selection",
                        duration = SnackbarDuration.Short,
                    )
                }
            },
            windowSizeClass = windowSizeClass,
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
        floatingActionButton = {
            if (!isExpanded && uiState.images.isNotEmpty() && !uiState.isScanRunning) {
                ExtendedFloatingActionButton(
                    onClick = { showScanConfig = true },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Radar,
                            contentDescription = null,
                        )
                    },
                    text = { Text("Scan") },
                    containerColor = Indigo500,
                    contentColor = Color.White,
                )
            }
        },
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
                    EmptyStateCard(
                        icon = Icons.Default.PhotoCamera,
                        title = "No Photos Loaded",
                        description = "Select a folder to start reviewing and culling your photos.",
                        actionLabel = "Select a Folder",
                        onAction = { folderPickerLauncher.launch(null) },
                    )
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
                            snackbarHostState.showSnackbar(
                                "Moved to Selection",
                                duration = SnackbarDuration.Short,
                            )
                        }
                    },
                    onCopyToSelection = {
                        viewModel.copyToSelection()
                        scope.launch {
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
                            snackbarHostState.showSnackbar(
                                "Moved to Selection",
                                duration = SnackbarDuration.Short,
                            )
                        }
                    },
                    onCopyToSelection = {
                        viewModel.copyToSelection()
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                "Copied to Selection",
                                duration = SnackbarDuration.Short,
                            )
                        }
                    },
                    onDelete = { viewModel.showDeleteConfirmation() },
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
            val prevIndex = uiState.currentIndex - 1
            val prevImage = uiState.images.getOrNull(prevIndex)
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
            val nextIndex = uiState.currentIndex + 1
            val nextImage = uiState.images.getOrNull(nextIndex)
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

@Composable
private fun CompactSelectorLayout(
    uiState: com.photoselectortoolbox.viewmodel.SelectorUiState,
    onNavigateToImage: (Int) -> Unit,
    onFullscreen: () -> Unit,
    onMoveToSelection: () -> Unit,
    onCopyToSelection: () -> Unit,
    onDelete: () -> Unit,
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
            FilledTonalIconButton(onClick = onMoveToSelection) {
                Icon(
                    imageVector = Icons.Default.DriveFileMove,
                    contentDescription = "Move to Selection",
                )
            }

            FilledTonalIconButton(onClick = onCopyToSelection) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy to Selection",
                )
            }

            FilledTonalIconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
