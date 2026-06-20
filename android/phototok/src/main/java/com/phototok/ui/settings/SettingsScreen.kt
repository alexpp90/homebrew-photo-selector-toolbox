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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.phototok.ui.theme.Indigo500
import com.phototok.ui.theme.Zinc400
import com.phototok.ui.theme.Zinc700
import com.phototok.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onChangeSourceFolder: (() -> Unit)? = null,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 16.dp),
    ) {
        // --- Storage Section ---
        SettingsSection(title = "Storage") {
            SettingsClickItem(
                title = "Source Folder Location",
                description = if (uiState.sourceFolderName.isNotEmpty()) {
                    uiState.sourceFolderName
                } else {
                    "No source folder selected"
                },
                onClick = { onChangeSourceFolder?.invoke() },
                icon = Icons.Default.FolderOpen,
            )

            HorizontalDivider(color = Zinc700.copy(alpha = 0.5f))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Text(
                    text = "Selection Subfolder Name",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Created inside the source folder by default",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = editingFolderName,
                    onValueChange = { editingFolderName = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Indigo500,
                        unfocusedBorderColor = Zinc700,
                        cursorColor = Indigo500,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    ),
                    placeholder = {
                        Text(text = "Selection", color = Zinc400)
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
                        Text("Save", color = Indigo500)
                    }
                }
            }

            HorizontalDivider(color = Zinc700.copy(alpha = 0.5f))

            SettingsClickItem(
                title = "Custom Collection Location",
                description = if (uiState.collectionUri != null) {
                    "Using custom folder"
                } else {
                    "Default: subfolder in source folder"
                },
                onClick = { selectionFolderPickerLauncher.launch(null) },
                icon = Icons.Default.FolderOpen,
                trailing = {
                    if (uiState.collectionUri != null) {
                        TextButton(onClick = { viewModel.updateCollectionUri(null) }) {
                            Text("Reset", color = Indigo500)
                        }
                    }
                },
            )

            HorizontalDivider(color = Zinc700.copy(alpha = 0.5f))

            SettingsToggleItem(
                title = "File Sorting",
                description = "Sort RAW and JPEG files into separate subfolders",
                checked = uiState.sortingEnabled,
                onCheckedChange = { viewModel.updateSortingEnabled(it) },
                icon = Icons.Default.Sort,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // --- Browsing Section ---
        SettingsSection(title = "Browsing") {
            SettingsToggleItem(
                title = "Sort by Orientation",
                description = "Show landscape images first, then portrait",
                checked = uiState.sortByOrientation,
                onCheckedChange = { viewModel.updateSortByOrientation(it) },
                icon = Icons.Default.SwapVert,
            )

            HorizontalDivider(color = Zinc700.copy(alpha = 0.5f))

            SettingsToggleItem(
                title = "Randomize Order",
                description = "Show pictures in random order",
                checked = uiState.randomizeOrder,
                onCheckedChange = { viewModel.updateRandomizeOrder(it) },
                icon = Icons.Default.Shuffle,
            )

            HorizontalDivider(color = Zinc700.copy(alpha = 0.5f))

            SettingsToggleItem(
                title = "Delete Confirmation",
                description = "Ask before deleting images on swipe",
                checked = uiState.deleteConfirmEnabled,
                onCheckedChange = { viewModel.updateDeleteConfirm(it) },
                icon = Icons.Default.DeleteSweep,
            )

            HorizontalDivider(color = Zinc700.copy(alpha = 0.5f))

            SettingsToggleItem(
                title = "Show EXIF Stats Overlay",
                description = "Display focal length, aperture, lens info on screen",
                checked = uiState.showExifOverlay,
                onCheckedChange = { viewModel.updateShowExifOverlay(it) },
                icon = Icons.Default.Info,
            )

            HorizontalDivider(color = Zinc700.copy(alpha = 0.5f))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Text(
                    text = "File Type Filter",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(8.dp))

                listOf("all" to "Show Both (RAW & JPEG)", "raw" to "RAW Only", "jpg" to "JPEG Only").forEach { (value, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = uiState.fileTypeFilter == value,
                            onClick = { viewModel.updateFileTypeFilter(value) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = Indigo500,
                                unselectedColor = Zinc400,
                            ),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            HorizontalDivider(color = Zinc700.copy(alpha = 0.5f))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Text(
                    text = "Double-Tap Action",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(8.dp))

                listOf("copy" to "Copy to Collection", "move" to "Move to Collection").forEach { (value, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = uiState.collectionAction == value,
                            onClick = { viewModel.updateCollectionAction(value) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = Indigo500,
                                unselectedColor = Zinc400,
                            ),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // --- About Section ---
        SettingsSection(title = "About") {
            SettingsClickItem(
                title = "Photo-Tok",
                description = "Version ${com.phototok.BuildConfig.VERSION_NAME}",
                onClick = {},
                icon = Icons.Default.Info,
            )

            HorizontalDivider(color = Zinc700.copy(alpha = 0.5f))

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

        Spacer(modifier = Modifier.height(24.dp))
    }
}
