package com.photoselectortoolbox.ui.phonemode

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.photoselectortoolbox.ui.theme.Zinc800
import com.photoselectortoolbox.viewmodel.PhoneModeViewModel

/**
 * Root composable for the phone-mode experience.
 * Shows either the landing screen (folder selection) or the full-screen viewer.
 */
@Composable
fun PhoneModeScreen(
    viewModel: PhoneModeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

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
                    // If folder is already selected but images haven't loaded yet
                    // the selectSourceFolder call already triggers discovery.
                    // This button re-triggers if needed.
                    uiState.sourceFolderUri?.let {
                        viewModel.selectSourceFolder(Uri.parse(it))
                    }
                },
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
            )

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
