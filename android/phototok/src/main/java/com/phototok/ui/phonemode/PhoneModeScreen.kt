package com.phototok.ui.phonemode

import android.net.Uri
import android.content.res.Configuration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.phototok.ui.components.DriveFolderPickerDialog
import com.phototok.ui.components.BottomNavBar
import com.phototok.ui.components.NavTab
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.safeDrawingPadding
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
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Lens
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
import com.phototok.ui.settings.SettingsScreen
import com.phototok.viewmodel.PhoneModeViewModel

/**
 * Root composable for the phone-mode experience.
 * Integrates top app bar, bottom nav, and content (landing or viewer).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneModeScreen(
    viewModel: PhoneModeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val colors = MaterialTheme.colorScheme

    var showSettingsSheet by remember { mutableStateOf(false) }
    var showDrivePicker by remember { mutableStateOf(false) }

    val isViewing = uiState.images.isNotEmpty()

    // Google Sign-In launcher
    val googleSignInLauncher = rememberLauncherForActivityResult(StartActivityForResult()) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
            viewModel.driveAuth.handleSignInResult(account)
            showDrivePicker = true
        } catch (e: Exception) {
            android.util.Log.e("PhoneModeScreen", "Google Sign-In failed", e)
            viewModel.setError("Google Sign-In failed: ${e.message}")
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

    // Delete confirmation dialog
    if (uiState.showDeleteConfirmation) {
        val currentImage = uiState.images.getOrNull(uiState.currentIndex)
        AlertDialog(
            onDismissRequest = { viewModel.dismissDeleteConfirmation() },
            title = { Text("Delete Image") },
            text = {
                Text("Delete \"${currentImage?.fileName ?: ""}\"? This cannot be undone.")
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmDelete() }) {
                    Text("Delete", color = colors.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDeleteConfirmation() }) {
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
            driveClient = viewModel.driveClient,
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
        // ── Content ──────────────────────────────────────────────
        if (isLandscape && isViewing) {
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
                    Text(
                        text = "P-T",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.primary,
                        fontWeight = FontWeight.Bold
                    )

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        val activeTab = if (isViewing) NavTab.Cards else NavTab.Sources

                        // Sources Tab
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .then(
                                    if (activeTab == NavTab.Sources) {
                                        Modifier
                                            .clip(CircleShape)
                                            .background(colors.primaryContainer)
                                    } else {
                                        Modifier
                                    }
                                )
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() }
                                ) {
                                    if (isViewing) viewModel.goBackToLanding()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = "Sources",
                                tint = if (activeTab == NavTab.Sources) colors.onPrimaryContainer else colors.secondary
                            )
                        }

                        // Cards Tab
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .then(
                                    if (activeTab == NavTab.Cards) {
                                        Modifier
                                            .clip(CircleShape)
                                            .background(colors.primaryContainer)
                                    } else {
                                        Modifier
                                    }
                                )
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() }
                                ) {
                                    // Cards tab is currently active when viewing
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Style,
                                contentDescription = "Cards",
                                tint = if (activeTab == NavTab.Cards) colors.onPrimaryContainer else colors.secondary
                            )
                        }

                        // History Tab
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .then(
                                    if (activeTab == NavTab.History) {
                                        Modifier
                                            .clip(CircleShape)
                                            .background(colors.primaryContainer)
                                    } else {
                                        Modifier
                                    }
                                )
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() }
                                ) {
                                    // Placeholder for history click
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = "History",
                                tint = if (activeTab == NavTab.History) colors.onPrimaryContainer else colors.secondary
                            )
                        }
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
                        onRequestDelete = viewModel::requestDelete,
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
                        .width(200.dp)
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
            // Default/Portrait Layout
            if (!isViewing) {
                PhoneModeLanding(
                    sourceFolderName = uiState.sourceFolderName,
                    collectionFolderName = uiState.collectionFolderName,
                    hasSourceFolder = uiState.sourceFolderUri != null,
                    isLoading = uiState.isLoading,
                    onSelectSource = { viewModel.selectSourceFolder(it) },
                    onSelectCollection = { viewModel.selectCollectionFolder(it) },
                    onStart = {
                        uiState.sourceFolderUri?.let {
                            viewModel.selectSourceFolder(Uri.parse(it))
                        }
                    },
                    fileTypeFilter = uiState.fileTypeFilter,
                    onFileTypeFilterChange = viewModel::setFileTypeFilter,
                    externalVolumes = uiState.externalVolumes,
                    onBrowseExternalVolume = { path ->
                        folderPickerLauncher.launch(Uri.parse(path))
                    },
                    onOpenGoogleDrive = {
                        if (viewModel.driveAuth.isSignedIn) {
                            showDrivePicker = true
                        } else {
                            googleSignInLauncher.launch(viewModel.driveAuth.getSignInIntent())
                        }
                    },
                    isGoogleDriveSignedIn = viewModel.driveAuth.isSignedIn,
                )
            } else {
                PhoneModeViewer(
                    images = uiState.images,
                    currentIndex = uiState.currentIndex,
                    portraitSectionStart = uiState.portraitSectionStart,
                    onNavigate = viewModel::navigateToImage,
                    onAddToCollection = viewModel::addToCollection,
                    onRequestDelete = viewModel::requestDelete,
                    showExifOverlay = uiState.showExifOverlay,
                )
            }
        }

        // ── Gesture tutorial overlay (Always on top of content & bars) ────────
        if (isViewing) {
            GestureTutorialOverlay(
                visible = uiState.showGestureTutorial,
                onDismiss = viewModel::dismissGestureTutorial,
            )
        }

        // ── Overlay App Bars (Only when not in Landscape Viewer) ───────────
        if (!(isLandscape && isViewing)) {
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
                Text(
                    text = "Photo-Tok",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.primary,
                    fontWeight = FontWeight.Bold,
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

            // ── Bottom nav bar ───────────────────────────────────────
            BottomNavBar(
                activeTab = if (isViewing) NavTab.Cards else NavTab.Sources,
                onTabSelected = { tab ->
                    when (tab) {
                        NavTab.Sources -> {
                            if (isViewing) viewModel.goBackToLanding()
                        }
                        NavTab.Cards -> { /* already viewing or no-op */ }
                        NavTab.History -> { /* placeholder for future */ }
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
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
