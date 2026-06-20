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
import androidx.compose.material.icons.filled.BrowseGallery
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
                title = "Delete Confirmation",
                description = "Ask before removing files",
                checked = uiState.deleteConfirmEnabled,
                onCheckedChange = { viewModel.updateDeleteConfirm(it) },
            )

            HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.2f))

            SettingsToggleItem(
                title = "Show EXIF Stats",
                description = "Display aperture and ISO data",
                checked = uiState.showExifOverlay,
                onCheckedChange = { viewModel.updateShowExifOverlay(it) },
            )

            HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.2f))

            // Double-tap action
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Text(
                    text = "Double-Tap Action",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.onSurface,
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
                                selectedColor = colors.primaryContainer,
                                unselectedColor = colors.onSurfaceVariant,
                            ),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurface,
                        )
                    }
                }
            }
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
