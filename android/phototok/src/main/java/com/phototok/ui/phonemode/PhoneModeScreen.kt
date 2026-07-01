package com.phototok.ui.phonemode

import android.net.Uri
import android.content.res.Configuration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import com.phototok.ui.components.DriveFolderPickerDialog
import com.phototok.ui.components.ViewerBottomBar
import androidx.compose.material3.Checkbox
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Lens
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.phototok.data.model.ExifData
import com.phototok.data.model.ImageItem
import com.phototok.domain.SwipeAction
import com.phototok.ui.settings.SettingsScreen
import com.phototok.viewmodel.PhoneModeViewModel
import com.phototok.viewmodel.SelectionViewerViewModel
import com.phototok.viewmodel.canRevert

/**
 * Root composable for the phone-mode experience.
 * Integrates top app bar, bottom action bar, and content (landing / loading / viewer).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneModeScreen(
    viewModel: PhoneModeViewModel = hiltViewModel(),
    selectionViewModel: SelectionViewerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectionState by selectionViewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val colors = MaterialTheme.colorScheme

    val openSelectionViewer = {
        viewModel.finalizePendingDelete()
        selectionViewModel.open(uiState.collectionFolderUri ?: uiState.sourceFolderUri)
    }

    var showSettingsSheet by remember { mutableStateOf(false) }
    var showDrivePicker by remember { mutableStateOf(false) }
    var showTrashConfirmDialog by remember { mutableStateOf(false) }
    var showDirectDeleteConfirmDialog by remember { mutableStateOf(false) }
    var dontShowAgainChecked by remember { mutableStateOf(false) }

    val performDeleteSwipe = {
        if (uiState.leftSwipeAction == SwipeAction.DELETE) {
            val currentImage = uiState.images.getOrNull(uiState.currentIndex)
            if (currentImage != null) {
                if (currentImage.isRemote) {
                    if (uiState.trashConfirmEnabled) {
                        dontShowAgainChecked = false
                        showTrashConfirmDialog = true
                    } else {
                        viewModel.requestDelete()
                    }
                } else {
                    if (uiState.directDeleteConfirmEnabled) {
                        showDirectDeleteConfirmDialog = true
                    } else {
                        viewModel.requestDelete()
                    }
                }
            }
        } else {
            viewModel.performLeftSwipeCopyOrMove()
        }
    }

    val isViewing = uiState.images.isNotEmpty()
    val isViewingSelection = selectionState.isOpen

    // Google Sign-In launcher (result parsing/errors handled by the ViewModel)
    val googleSignInLauncher = rememberLauncherForActivityResult(StartActivityForResult()) { result ->
        if (viewModel.handleDriveSignIn(result.data)) {
            showDrivePicker = true
        }
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri: Uri? -> uri?.let { viewModel.selectSourceFolder(it) } }

    // Error snackbar
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(message = error, duration = SnackbarDuration.Short)
            viewModel.clearError()
        }
    }

    // Action feedback snackbar
    LaunchedEffect(uiState.lastActionFeedback) {
        uiState.lastActionFeedback?.let { fb ->
            snackbarHostState.showSnackbar(message = fb.message, duration = SnackbarDuration.Short)
            viewModel.clearFeedback()
        }
    }

    // Selection-viewer feedback snackbar
    LaunchedEffect(selectionState.feedback) {
        selectionState.feedback?.let { fb ->
            snackbarHostState.showSnackbar(message = fb.message, duration = SnackbarDuration.Short)
            selectionViewModel.clearFeedback()
        }
    }

    // Dialogue 1: Trash Confirmation Dialog
    if (showTrashConfirmDialog) {
        val currentImage = uiState.images.getOrNull(uiState.currentIndex)
        AlertDialog(
            onDismissRequest = { showTrashConfirmDialog = false },
            title = { Text("Move to Trash") },
            text = {
                Column {
                    Text("Are you sure you want to move \"${currentImage?.fileName ?: ""}\" to the trash?")
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { dontShowAgainChecked = !dontShowAgainChecked }
                    ) {
                        Checkbox(
                            checked = dontShowAgainChecked,
                            onCheckedChange = { dontShowAgainChecked = it }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Do not show this dialogue again", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showTrashConfirmDialog = false
                    if (dontShowAgainChecked) {
                        viewModel.updateTrashConfirm(false)
                    }
                    viewModel.requestDelete()
                }) {
                    Text("Move to Trash", color = colors.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showTrashConfirmDialog = false }) {
                    Text("Cancel")
                }
            },
            containerColor = colors.surfaceContainerHigh,
        )
    }

    // Dialogue 2: Direct Delete Confirmation Dialog (trash unsupported)
    if (showDirectDeleteConfirmDialog) {
        val currentImage = uiState.images.getOrNull(uiState.currentIndex)
        AlertDialog(
            onDismissRequest = { showDirectDeleteConfirmDialog = false },
            title = { Text("Delete Permanently", color = colors.error) },
            text = {
                Text("This picture will be directly deleted (permanently) because trash is not supported for this location.\n\nHint: You can disable this confirmation in Settings.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showDirectDeleteConfirmDialog = false
                    viewModel.requestDelete()
                }) {
                    Text("Delete", color = colors.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDirectDeleteConfirmDialog = false }) {
                    Text("Cancel")
                }
            },
            containerColor = colors.surfaceContainerHigh,
        )
    }

    // Settings bottom sheet
    if (showSettingsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSettingsSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = colors.surfaceContainerLow,
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(top = 16.dp, bottom = 8.dp)
                        .size(width = 32.dp, height = 4.dp)
                        .background(colors.outlineVariant, RoundedCornerShape(50)),
                )
            },
        ) {
            SettingsScreen(
                onChangeSourceFolder = {
                    showSettingsSheet = false
                    folderPickerLauncher.launch(null)
                },
            )
        }
    }

    // Google Drive folder picker dialog
    if (showDrivePicker) {
        DriveFolderPickerDialog(
            onFolderSelected = { folderId, folderName ->
                showDrivePicker = false
                viewModel.selectDriveFolder(folderId, folderName)
            },
            onDismiss = { showDrivePicker = false },
        )
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    Box(modifier = Modifier.fillMaxSize()) {
        // ── Read-only Selection-folder viewer (view + back only) ────────────
        if (isViewingSelection) {
            SelectionFolderViewer(
                images = selectionState.images,
                currentIndex = selectionState.currentIndex,
                folderName = selectionState.folderName,
                showExifOverlay = uiState.showExifOverlay,
                onNavigate = selectionViewModel::navigateTo,
                onClose = selectionViewModel::close,
            )
        } else if (isLandscape && isViewing) {
            // ── Landscape viewer with side panels ───────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(androidx.compose.ui.graphics.Color.Black)
            ) {
                // Left Side Panel: Navigation
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.8f))
                        .windowInsetsPadding(
                            WindowInsets.safeDrawing.only(
                                WindowInsetsSides.Left + WindowInsetsSides.Top + WindowInsetsSides.Bottom
                            )
                        )
                        .width(72.dp)
                        .padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Logo toggles the EXIF overlay
                    Image(
                        painter = painterResource(id = com.phototok.R.mipmap.ic_launcher_foreground),
                        contentDescription = "Toggle EXIF stats",
                        modifier = Modifier
                            .size(32.dp)
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() },
                            ) { viewModel.toggleExifOverlay() }
                    )

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Sources (back to landing)
                        SidePanelButton(
                            icon = Icons.Default.FolderOpen,
                            description = "Sources",
                            isActive = false,
                            enabled = true,
                            onClick = { viewModel.goBackToLanding() },
                        )
                        // Selection
                        SidePanelButton(
                            icon = Icons.Default.Star,
                            description = "Selection",
                            isActive = false,
                            enabled = true,
                            onClick = openSelectionViewer,
                        )
                        // Revert (active only when there is a pending deletion)
                        SidePanelButton(
                            icon = Icons.AutoMirrored.Filled.Undo,
                            description = "Revert",
                            isActive = uiState.canRevert,
                            enabled = uiState.canRevert,
                            onClick = { viewModel.revertDelete() },
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }

                // Center Panel: Image Viewer
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    PhoneModeViewer(
                        images = uiState.images,
                        currentIndex = uiState.currentIndex,
                        portraitSectionStart = uiState.portraitSectionStart,
                        onNavigate = viewModel::navigateToImage,
                        onAddToCollection = viewModel::addToCollection,
                        onRequestDelete = performDeleteSwipe,
                        leftSwipeAction = uiState.leftSwipeAction,
                        leftSwipeFolderName = uiState.leftSwipeFolderName,
                        showExifOverlay = false,
                        showPageCounter = false,
                    )
                }

                // Right Side Panel: Settings, EXIF and Page Counter
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.8f))
                        .windowInsetsPadding(
                            WindowInsets.safeDrawing.only(
                                WindowInsetsSides.Right + WindowInsetsSides.Top + WindowInsetsSides.Bottom
                            )
                        )
                        .width(125.dp)
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = { showSettingsSheet = true },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = colors.onSurfaceVariant
                        )
                    }

                    val currentImage = uiState.images.getOrNull(uiState.currentIndex)
                    if (uiState.showExifOverlay && currentImage?.exifData != null) {
                        ExifSidebarPanel(
                            exif = currentImage.exifData!!,
                            fileName = currentImage.fileName,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }

                    Surface(
                        shape = RoundedCornerShape(50),
                        color = colors.surfaceContainerHigh.copy(alpha = 0.8f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            colors.outline.copy(alpha = 0.2f)
                        )
                    ) {
                        Text(
                            text = "${uiState.currentIndex + 1} / ${uiState.images.size}",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        } else {
            // ── Portrait: viewer / loading / landing ────────────────────────
            if (isViewing) {
                PhoneModeViewer(
                    images = uiState.images,
                    currentIndex = uiState.currentIndex,
                    portraitSectionStart = uiState.portraitSectionStart,
                    onNavigate = viewModel::navigateToImage,
                    onAddToCollection = viewModel::addToCollection,
                    onRequestDelete = performDeleteSwipe,
                    leftSwipeAction = uiState.leftSwipeAction,
                    leftSwipeFolderName = uiState.leftSwipeFolderName,
                    showExifOverlay = uiState.showExifOverlay,
                )
            } else if (uiState.isLoading) {
                PhoneModeLoading(folderName = uiState.sourceFolderName)
            } else {
                PhoneModeLanding(
                    sourceFolderUri = uiState.sourceFolderUri,
                    sourceFolderName = uiState.sourceFolderName,
                    collectionFolderName = uiState.collectionFolderName,
                    isLoading = uiState.isLoading,
                    onSelectSource = { viewModel.selectSourceFolder(it) },
                    onSelectCollection = { viewModel.selectCollectionFolder(it) },
                    onStart = {
                        uiState.sourceFolderUri?.let {
                            viewModel.selectSourceFolder(Uri.parse(it))
                        }
                    },
                    externalVolumes = uiState.externalVolumes,
                    onBrowseExternalVolume = { path ->
                        folderPickerLauncher.launch(Uri.parse(path))
                    },
                    onOpenGoogleDrive = {
                        if (uiState.isDriveSignedIn) {
                            showDrivePicker = true
                        } else {
                            googleSignInLauncher.launch(viewModel.driveSignInIntent())
                        }
                    },
                    isGoogleDriveSignedIn = uiState.isDriveSignedIn,
                    recentPaths = uiState.recentPaths,
                    recentPathsEnabled = uiState.recentPathsEnabled,
                    recentPathsCount = uiState.recentPathsCount,
                    onSelectRecentPath = { viewModel.selectRecentPath(it) },
                )
            }
        }

        // ── Gesture tutorial overlay (Always on top of content & bars) ────────
        if (isViewing && !isViewingSelection) {
            GestureTutorialOverlay(
                visible = uiState.showGestureTutorial,
                onDismiss = viewModel::dismissGestureTutorial,
            )
        }

        // ── Overlay App Bars (Only when not in Landscape Viewer / selection) ──
        if (!isViewingSelection && !(isLandscape && isViewing) && !uiState.showGestureTutorial) {
            // ── Top app bar ──────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Logo toggles the EXIF overlay
                Image(
                    painter = painterResource(id = com.phototok.R.mipmap.ic_launcher_foreground),
                    contentDescription = "Toggle EXIF stats",
                    modifier = Modifier
                        .size(36.dp)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                        ) { viewModel.toggleExifOverlay() }
                )
                IconButton(
                    onClick = { showSettingsSheet = true },
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = colors.onSurfaceVariant,
                    )
                }
            }

            // ── Bottom action bar ────────────────────────────────────
            if (isViewing) {
                ViewerBottomBar(
                    canRevert = uiState.canRevert,
                    onRevert = { viewModel.revertDelete() },
                    onJumpToSelection = openSelectionViewer,
                    onGoToLanding = { viewModel.goBackToLanding() },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }

        // ── Snackbar ─────────────────────────────────────────────
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = if (isLandscape && isViewing) 16.dp else 80.dp),
        )
    }
}

/** Side-panel circular button used in the landscape viewer. */
@Composable
private fun SidePanelButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    isActive: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val tint = when {
        isActive -> colors.onPrimaryContainer
        enabled -> colors.secondary
        else -> colors.onSurface.copy(alpha = 0.25f)
    }
    Box(
        modifier = Modifier
            .size(48.dp)
            .then(
                if (isActive) Modifier.clip(CircleShape).background(colors.primaryContainer)
                else Modifier
            )
            .then(
                if (enabled) Modifier.clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onClick,
                ) else Modifier
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = description, tint = tint)
    }
}

/** Read-only viewer for the PhotoTok_Selection folder: scroll through and go back. */
@Composable
private fun SelectionFolderViewer(
    images: List<ImageItem>,
    currentIndex: Int,
    folderName: String,
    showExifOverlay: Boolean,
    onNavigate: (Int) -> Unit,
    onClose: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme

    Box(modifier = Modifier.fillMaxSize()) {
        if (images.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.background),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "No photos in the selection folder yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }
        } else {
            PhoneModeViewer(
                images = images,
                currentIndex = currentIndex,
                portraitSectionStart = -1,
                onNavigate = onNavigate,
                onAddToCollection = { },
                onRequestDelete = { },
                showExifOverlay = showExifOverlay,
                readOnly = true,
            )
        }

        // Top bar: back + title (view + back only)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = colors.onSurface,
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = folderName.ifEmpty { "Selection" },
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun ExifSidebarPanel(
    exif: ExifData,
    fileName: String,
    modifier: Modifier = Modifier,
) {
    val items = buildList {
        exif.iso?.let { add("ISO $it") }
        exif.shutterSpeed?.let { speed ->
            val formatted = if (speed < 1.0 && speed > 0.0) "1/${(1.0 / speed).toInt()}s" else "${speed}s"
            add(formatted)
        }
        exif.aperture?.let { add("f/%.1f".format(java.util.Locale.US, it)) }
        exif.focalLength?.let { add("${it.toInt()}mm") }
    }
    val colors = MaterialTheme.colorScheme

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = colors.surfaceContainerLowest.copy(alpha = 0.5f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            colors.outlineVariant.copy(alpha = 0.3f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = fileName.uppercase(),
                style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.5.sp),
                color = colors.onSurface.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            items.forEach { item ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoCamera,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = colors.primary,
                    )
                    Text(
                        text = item,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            }

            if (exif.lens != "Unknown") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Lens,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = colors.tertiary,
                    )
                    Text(
                        text = exif.lens,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
