package com.phototok.ui.phonemode

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.phototok.ui.components.DriveFolderPickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.phototok.ui.settings.SettingsScreen
import com.phototok.ui.theme.Indigo500
import com.phototok.ui.theme.Zinc700
import com.phototok.ui.theme.Zinc800
import com.phototok.ui.theme.Zinc900
import com.phototok.viewmodel.PhoneModeViewModel

/**
 * Root composable for the phone-mode experience.
 * Shows either the landing screen (folder selection) or the full-screen viewer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneModeScreen(
    viewModel: PhoneModeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var showSettingsSheet by remember { mutableStateOf(false) }
    var showDrivePicker by remember { mutableStateOf(false) }

    // Google Sign-In launcher
    val googleSignInLauncher = rememberLauncherForActivityResult(StartActivityForResult()) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
            viewModel.driveAuth.handleSignInResult(account)
            showDrivePicker = true
        } catch (_: Exception) {}
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

    // Settings bottom sheet
    if (showSettingsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSettingsSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Zinc900,
        ) {
            SettingsScreen(
                onChangeSourceFolder = {
                    showSettingsSheet = false
                    folderPickerLauncher.launch(null)
                }
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

    Box(modifier = Modifier.fillMaxSize()) {
        if (uiState.images.isEmpty()) {
            // Landing screen
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
            // Full-screen viewer
            PhoneModeViewer(
                images = uiState.images,
                currentIndex = uiState.currentIndex,
                portraitSectionStart = uiState.portraitSectionStart,
                onNavigate = viewModel::navigateToImage,
                onAddToCollection = viewModel::addToCollection,
                onRequestDelete = viewModel::requestDelete,
                showExifOverlay = uiState.showExifOverlay,
            )

            // Subtle floating settings button at top right
            IconButton(
                onClick = { showSettingsSheet = true },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(16.dp)
                    .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                    .size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.size(24.dp)
                )
            }

            // Gesture tutorial overlay (on top of everything)
            GestureTutorialOverlay(
                visible = uiState.showGestureTutorial,
                onDismiss = viewModel::dismissGestureTutorial,
            )
        }

        // Snackbar
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp),
        )
    }
}



/**
 * Row of filter chips for RAW / JPG / Both selection.
 */
@Composable
fun FileTypeFilterRow(
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        listOf("all" to "Both", "raw" to "RAW", "jpg" to "JPG").forEach { (value, label) ->
            FilterChip(
                selected = selected == value,
                onClick = { onSelect(value) },
                label = {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Indigo500,
                    selectedLabelColor = Color.White,
                    containerColor = Color.White.copy(alpha = 0.15f),
                    labelColor = Color.White.copy(alpha = 0.8f),
                ),
                border = null,
            )
        }
    }
}
