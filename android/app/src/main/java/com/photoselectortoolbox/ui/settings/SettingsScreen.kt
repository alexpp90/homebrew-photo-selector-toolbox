package com.photoselectortoolbox.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.photoselectortoolbox.domain.grouping.GroupingLevel
import com.photoselectortoolbox.ui.theme.ErrorRed
import com.photoselectortoolbox.ui.theme.Indigo500
import com.photoselectortoolbox.ui.theme.Zinc400
import com.photoselectortoolbox.ui.theme.Zinc700
import com.photoselectortoolbox.ui.theme.Zinc800
import com.photoselectortoolbox.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var showClearCacheDialog by remember { mutableStateOf(false) }
    var editingFolderName by remember(uiState.selectionFolderName) {
        mutableStateOf(uiState.selectionFolderName)
    }

    if (showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            title = { Text("Clear Cache") },
            text = {
                Text("Are you sure you want to clear all cached analysis scores? This will not affect your images.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearCacheDialog = false
                        viewModel.clearCache()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheDialog = false }) {
                    Text("Cancel")
                }
            },
            containerColor = Zinc800,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 16.dp),
    ) {
        // --- Storage Section ---
        SettingsSection(title = "Storage") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Text(
                    text = "Selection Folder Name",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
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
                        Text(
                            text = "Selection",
                            color = Zinc400,
                        )
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

            SettingsToggleItem(
                title = "File Sorting",
                description = "Sort RAW and JPEG files into separate subfolders",
                checked = uiState.sortingEnabled,
                onCheckedChange = { viewModel.updateSortingEnabled(it) },
                icon = Icons.Default.Sort,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // --- Analysis Section ---
        SettingsSection(title = "Analysis") {
            SettingsToggleItem(
                title = "Group Similar Series",
                description = "Group photos shot in close succession",
                checked = uiState.groupingEnabled,
                onCheckedChange = { viewModel.updateGroupingEnabled(it) },
                icon = Icons.Default.Collections,
            )

            AnimatedVisibility(
                visible = uiState.groupingEnabled,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    HorizontalDivider(color = Zinc700.copy(alpha = 0.5f))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    ) {
                        Text(
                            text = "Grouping Level",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        GroupingLevel.entries.forEach { level ->
                            val (label, description) = when (level) {
                                GroupingLevel.TIME_FILENAME -> "Time & Filename" to
                                    "Group by capture time and filename similarity"
                                GroupingLevel.TIME_FAST_SIMILARITY -> "Time + Fast Similarity" to
                                    "Group by time with fast perceptual hash comparison"
                                GroupingLevel.DETAILED_SIMILARITY -> "Detailed Similarity" to
                                    "Full similarity analysis (slower, more accurate)"
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = uiState.groupingLevel == level,
                                    onClick = { viewModel.updateGroupingLevel(level) },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = Indigo500,
                                        unselectedColor = Zinc400,
                                    ),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        text = description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = Zinc700.copy(alpha = 0.5f))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Analysis Threads",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "${uiState.analysisThreadCount}",
                        style = MaterialTheme.typography.titleMedium,
                        color = Indigo500,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Slider(
                    value = uiState.analysisThreadCount.toFloat(),
                    onValueChange = { viewModel.updateThreadCount(it.toInt()) },
                    valueRange = 1f..4f,
                    steps = 2,
                    colors = SliderDefaults.colors(
                        thumbColor = Indigo500,
                        activeTrackColor = Indigo500,
                        inactiveTrackColor = Zinc700,
                        activeTickColor = Color.Transparent,
                        inactiveTickColor = Color.Transparent,
                    ),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "1",
                        style = MaterialTheme.typography.labelSmall,
                        color = Zinc400,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "4",
                        style = MaterialTheme.typography.labelSmall,
                        color = Zinc400,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // --- Phone Mode Section ---
        SettingsSection(title = "Phone Mode") {
            SettingsToggleItem(
                title = "Sort by Orientation",
                description = "Show landscape images first, then portrait",
                checked = uiState.phoneSortByOrientation,
                onCheckedChange = { viewModel.updatePhoneSortByOrientation(it) },
                icon = Icons.Default.SwapVert,
            )

            HorizontalDivider(color = Zinc700.copy(alpha = 0.5f))

            SettingsToggleItem(
                title = "Delete Confirmation",
                description = "Ask before deleting images on swipe",
                checked = uiState.phoneDeleteConfirmEnabled,
                onCheckedChange = { viewModel.updatePhoneDeleteConfirm(it) },
                icon = Icons.Default.DeleteSweep,
            )

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
                            selected = uiState.phoneCollectionAction == value,
                            onClick = { viewModel.updatePhoneCollectionAction(value) },
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

        // --- Cache Section ---
        SettingsSection(title = "Cache") {
            SettingsClickItem(
                title = "Cached Scores",
                description = "${uiState.cachedScoreCount} analysis results stored",
                onClick = {},
                icon = Icons.Default.Cached,
            )

            HorizontalDivider(color = Zinc700.copy(alpha = 0.5f))

            SettingsClickItem(
                title = "Clear Cache",
                description = "Remove all cached analysis scores",
                onClick = { showClearCacheDialog = true },
                icon = Icons.Default.DeleteSweep,
                trailing = {
                    Button(
                        onClick = { showClearCacheDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ErrorRed.copy(alpha = 0.15f),
                            contentColor = ErrorRed,
                        ),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text("Clear")
                    }
                },
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // --- About Section ---
        SettingsSection(title = "About") {
            SettingsClickItem(
                title = "Photo Selector Toolbox",
                description = "Version 0.1.0",
                onClick = {},
                icon = Icons.Default.Info,
            )

            HorizontalDivider(color = Zinc700.copy(alpha = 0.5f))

            SettingsClickItem(
                title = "Source Code",
                description = "View on GitHub",
                onClick = {
                    // Intent to open GitHub would be handled here
                },
                icon = Icons.Default.Code,
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
