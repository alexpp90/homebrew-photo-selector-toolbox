package com.phototok.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrowseGallery
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.phototok.domain.CollectionAction
import com.phototok.domain.FileTypeFilter
import com.phototok.domain.SwipeAction
import com.phototok.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onChangeSourceFolder: (() -> Unit)? = null,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme

    var editingFolderName by remember(uiState.selectionFolderName) {
        mutableStateOf(uiState.selectionFolderName)
    }

    val selectionFolderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri: Uri? ->
        uri?.let {
            try {
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(it, takeFlags)
            } catch (_: Exception) {}
            viewModel.updateCollectionUri(it.toString())
        }
    }

    val leftSwipeFolderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri: Uri? ->
        uri?.let {
            try {
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(it, takeFlags)
            } catch (_: Exception) {}
            viewModel.updateLeftSwipeUri(it.toString())
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 16.dp),
    ) {
        // ── Storage ──────────────────────────────────────────────────
        SettingsSection(title = "Storage", icon = Icons.Default.Storage) {
            SettingsClickItem(
                title = "Source Folder",
                description = uiState.sourceFolderName.ifEmpty { "No source folder selected" },
                onClick = { onChangeSourceFolder?.invoke() },
                icon = Icons.Default.FolderOpen,
            )

            HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.2f))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Text(
                    text = "Selection Subfolder",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.onSurface,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Created inside the source folder by default",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = editingFolderName,
                    onValueChange = { editingFolderName = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.primary,
                        unfocusedBorderColor = colors.outlineVariant,
                        cursorColor = colors.primary,
                        focusedTextColor = colors.onSurface,
                        unfocusedTextColor = colors.onSurface,
                        focusedContainerColor = colors.surfaceDim,
                        unfocusedContainerColor = colors.surfaceDim,
                    ),
                    placeholder = {
                        Text(text = "Selection", color = colors.onSurfaceVariant)
                    },
                )
                Spacer(modifier = Modifier.height(4.dp))

                if (editingFolderName != uiState.selectionFolderName &&
                    editingFolderName.isNotBlank()
                ) {
                    TextButton(
                        onClick = { viewModel.updateSelectionFolder(editingFolderName) },
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        Text("Save", color = colors.primary)
                    }
                }
            }

            HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.2f))

            SettingsClickItem(
                title = "Custom Collection Location",
                description = if (uiState.collectionUri != null) "Using custom folder" else "Default: subfolder in source",
                onClick = { selectionFolderPickerLauncher.launch(null) },
                trailing = {
                    if (uiState.collectionUri != null) {
                        TextButton(onClick = { viewModel.updateCollectionUri(null) }) {
                            Text("Reset", color = colors.primary)
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Change",
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.primaryContainer,
                            )
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = colors.primaryContainer,
                            )
                        }
                    }
                },
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ── Browsing ─────────────────────────────────────────────────
        SettingsSection(title = "Browsing", icon = Icons.Default.BrowseGallery) {
            SettingsToggleItem(
                title = "Sort by Orientation",
                description = "Prioritize portraits in feed",
                checked = uiState.sortByOrientation,
                onCheckedChange = { viewModel.updateSortByOrientation(it) },
            )

            HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.2f))

            SettingsToggleItem(
                title = "Randomize Order",
                description = "Shuffle photos on every load",
                checked = uiState.randomizeOrder,
                onCheckedChange = { viewModel.updateRandomizeOrder(it) },
            )

            HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.2f))

            SettingsToggleItem(
                title = "Trash Confirmation",
                description = "Ask before moving files to trash",
                checked = uiState.trashConfirmEnabled,
                onCheckedChange = { viewModel.updateTrashConfirm(it) },
            )

            HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.2f))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.updateDirectDeleteConfirm(!uiState.directDeleteConfirmEnabled) }
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Direct Delete Confirmation",
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.onSurface,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Ask before permanently deleting files (trash unsupported)",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.onSurfaceVariant,
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Switch(
                        checked = uiState.directDeleteConfirmEnabled,
                        onCheckedChange = { viewModel.updateDirectDeleteConfirm(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = colors.onPrimary,
                            checkedTrackColor = colors.primaryContainer,
                            uncheckedThumbColor = colors.onSurfaceVariant,
                            uncheckedTrackColor = colors.secondaryContainer,
                            uncheckedBorderColor = colors.secondaryContainer,
                        ),
                    )
                }

                if (!uiState.directDeleteConfirmEnabled) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Warning",
                            tint = colors.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Warning: Disabling this deletes files permanently without confirmation!",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.error,
                        )
                    }
                }
            }

            HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.2f))

            SettingsToggleItem(
                title = "Show EXIF Stats",
                description = "Display aperture and ISO data",
                checked = uiState.showExifOverlay,
                onCheckedChange = { viewModel.updateShowExifOverlay(it) },
            )

            HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.2f))

            SettingsToggleItem(
                title = "Move Related Files",
                description = "Also move/delete same-name files (e.g. .JPG + .RAW)",
                checked = uiState.moveRelatedFiles,
                onCheckedChange = { viewModel.updateMoveRelatedFiles(it) },
            )

            HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.2f))

            // Format filter (moved here from the landing screen; default Both)
            SettingsRadioGroup(
                title = "Format Filter",
                options = listOf(
                    FileTypeFilter.ALL to "Both",
                    FileTypeFilter.RAW to "RAW only",
                    FileTypeFilter.JPG to "JPG only",
                ),
                selected = uiState.fileTypeFilter,
                onSelect = { viewModel.updateFileTypeFilter(it) },
            )

            HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.2f))

            // Add-to-collection action (triggered by swipe-right or the Star button)
            SettingsRadioGroup(
                title = "Add to Collection Action",
                options = listOf(
                    CollectionAction.COPY to "Copy to Collection",
                    CollectionAction.MOVE to "Move to Collection",
                ),
                selected = uiState.collectionAction,
                onSelect = { viewModel.updateCollectionAction(it) },
            )

            HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.2f))

            // Left swipe action (triggered by swipe-left)
            SettingsRadioGroup(
                title = "Left Swipe Action",
                options = listOf(
                    SwipeAction.DELETE to "Delete / Trash",
                    SwipeAction.COPY to "Copy to Custom Folder",
                    SwipeAction.MOVE to "Move to Custom Folder",
                ),
                selected = uiState.leftSwipeAction,
                onSelect = { viewModel.updateLeftSwipeAction(it) },
            )

            if (uiState.leftSwipeAction != SwipeAction.DELETE) {
                HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.2f))

                SettingsClickItem(
                    title = "Left Swipe Custom Location",
                    description = if (uiState.leftSwipeUri != null) "Using custom folder" else "Default: subfolder in source",
                    onClick = { leftSwipeFolderPickerLauncher.launch(null) },
                    trailing = {
                        if (uiState.leftSwipeUri != null) {
                            TextButton(onClick = { viewModel.updateLeftSwipeUri(null) }) {
                                Text("Reset", color = colors.primary)
                            }
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Change",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = colors.primaryContainer,
                                )
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = colors.primaryContainer,
                                )
                            }
                        }
                    },
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ── Recent folders ───────────────────────────────────────────
        SettingsSection(title = "Recent Folders", icon = Icons.Default.History) {
            SettingsToggleItem(
                title = "Show Recent Folders",
                description = "List last-used folders on the start screen",
                checked = uiState.recentPathsEnabled,
                onCheckedChange = { viewModel.updateRecentPathsEnabled(it) },
            )

            HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.2f))

            SettingsRadioGroup(
                title = "How Many to Show",
                options = listOf("1" to "1", "3" to "3", "5" to "5", "10" to "10"),
                selected = uiState.recentPathsCount.toString(),
                onSelect = { viewModel.updateRecentPathsCount(it.toIntOrNull() ?: 3) },
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ── About ────────────────────────────────────────────────────
        SettingsSection(title = "About", icon = Icons.Default.Info) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "App version",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                )
                Text(
                    text = com.phototok.BuildConfig.VERSION_NAME,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurface,
                )
            }

            HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.2f))

            SettingsClickItem(
                title = "Source Code",
                description = "View on GitHub",
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/alexpp90/homebrew-photo-selector-toolbox"))
                    context.startActivity(intent)
                },
                icon = Icons.Default.Code,
            )
        }

        // Footer branding
        Spacer(modifier = Modifier.height(32.dp))
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Photo-Tok",
                style = MaterialTheme.typography.titleLarge,
                color = colors.onSurface.copy(alpha = 0.3f),
            )
            Text(
                text = "Professional Curation Engine",
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurface.copy(alpha = 0.2f),
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
