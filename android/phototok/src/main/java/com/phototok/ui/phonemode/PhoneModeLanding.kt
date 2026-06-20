package com.phototok.ui.phonemode

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.phototok.ui.theme.Indigo500
import com.phototok.ui.theme.Zinc700
import com.phototok.ui.theme.Zinc800
import com.phototok.ui.theme.Zinc900
import com.phototok.ui.theme.Zinc950

/**
 * Minimal landing screen for phone mode.
 * Lets the user pick a source folder and an optional collection target.
 */
@Composable
fun PhoneModeLanding(
    sourceFolderName: String,
    collectionFolderName: String,
    hasSourceFolder: Boolean,
    isLoading: Boolean,
    onSelectSource: (Uri) -> Unit,
    onSelectCollection: (Uri) -> Unit,
    onStart: () -> Unit,
    fileTypeFilter: String = "all",
    onFileTypeFilterChange: (String) -> Unit = {},
    externalVolumes: List<com.phototok.data.source.ExternalVolume> = emptyList(),
    onBrowseExternalVolume: (String) -> Unit = {},
    onOpenGoogleDrive: () -> Unit = {},
    isGoogleDriveSignedIn: Boolean = false,
) {
    val sourcePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri: Uri? -> uri?.let { onSelectSource(it) } }

    val collectionPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri: Uri? -> uri?.let { onSelectCollection(it) } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Zinc900)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.weight(0.2f))

            // App icon / branding area
            Icon(
                imageVector = Icons.Default.PhotoLibrary,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = Indigo500,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Photo-Tok",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Swipe. Select. Snap.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.5f),
            )

            Spacer(modifier = Modifier.weight(0.15f))

            // Source folder picker
            FolderPickerCard(
                icon = Icons.Default.FolderOpen,
                label = "Source",
                folderName = sourceFolderName.ifEmpty { "Select photos folder" },
                isSet = hasSourceFolder,
                onClick = { sourcePickerLauncher.launch(null) },
            )

            // External storage suggestion
            if (externalVolumes.isNotEmpty() && !hasSourceFolder) {
                Spacer(modifier = Modifier.height(8.dp))
                externalVolumes.forEach { volume ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onBrowseExternalVolume(volume.path) },
                        shape = RoundedCornerShape(14.dp),
                        color = Indigo500.copy(alpha = 0.12f),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Default.SdCard,
                                contentDescription = null,
                                tint = Indigo500,
                                modifier = Modifier.size(24.dp),
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = volume.description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White,
                                )
                                Text(
                                    text = "Tap to browse",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.5f),
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = Zinc700,
                            )
                        }
                    }
                }
            }

            // Google Drive option
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenGoogleDrive() },
                shape = RoundedCornerShape(14.dp),
                color = Indigo500.copy(alpha = 0.12f),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.Cloud,
                        contentDescription = null,
                        tint = if (isGoogleDriveSignedIn) Indigo500 else Zinc700,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Google Drive",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                        )
                        Text(
                            text = if (isGoogleDriveSignedIn) "Tap to browse folders" else "Sign in to browse",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.5f),
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = Zinc700,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Collection target picker
            FolderPickerCard(
                icon = Icons.Default.Collections,
                label = "Collection",
                folderName = collectionFolderName.ifEmpty { "Same as source (Selection subfolder)" },
                isSet = collectionFolderName.isNotEmpty(),
                onClick = { collectionPickerLauncher.launch(null) },
            )

            Spacer(modifier = Modifier.height(12.dp))

            // File type filter
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = Zinc800,
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                ) {
                    Text(
                        text = "File Type",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.5f),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FileTypeFilterRow(
                        selected = fileTypeFilter,
                        onSelect = onFileTypeFilterChange,
                    )
                }
            }

            Spacer(modifier = Modifier.weight(0.2f))

            // Start button
            Button(
                onClick = onStart,
                enabled = hasSourceFolder && !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Indigo500,
                    contentColor = Color.White,
                    disabledContainerColor = Indigo500.copy(alpha = 0.3f),
                    disabledContentColor = Color.White.copy(alpha = 0.4f),
                ),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(
                    text = if (isLoading) "Loading..." else "Start Browsing",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun FolderPickerCard(
    icon: ImageVector,
    label: String,
    folderName: String,
    isSet: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = Zinc800,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSet) Indigo500 else Zinc700,
                modifier = Modifier.size(28.dp),
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.5f),
                )
                Text(
                    text = folderName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isSet) Color.White else Color.White.copy(alpha = 0.35f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Zinc700,
            )
        }
    }
}
