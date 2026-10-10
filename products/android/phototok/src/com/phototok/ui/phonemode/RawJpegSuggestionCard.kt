package com.phototok.ui.phonemode

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Modal dialog presented when a folder contains matching RAW and JPEG pairs of the same photos.
 *
 * Provides clear choices:
 * 1. Link actions across both formats (move/copy/delete applies to both files at once).
 * 2. Filter feed to RAW only.
 * 3. Filter feed to JPEG only.
 * 4. Don't change settings (keep all files visible and separate).
 *
 * Rendered in a dialog window so it is centered and can never be covered or occluded
 * by bottom action bars, navigation buttons, or side panels in either portrait or landscape.
 */
@Composable
fun RawJpegSuggestionDialog(
    visible: Boolean,
    onFilterRaw: () -> Unit,
    onFilterJpg: () -> Unit,
    onEnableMoveRelatedFiles: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!visible) return

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
        ),
    ) {
        val colors = MaterialTheme.colorScheme

        Surface(
            modifier = modifier
                .widthIn(min = 280.dp, max = 460.dp)
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .testTag("raw_jpeg_suggestion_dialog")
                .testTag("raw_jpeg_suggestion_card"),
            shape = RoundedCornerShape(20.dp),
            color = colors.surfaceContainerHigh,
            border = BorderStroke(
                1.dp,
                colors.primary.copy(alpha = 0.35f),
            ),
            tonalElevation = 6.dp,
            shadowElevation = 10.dp,
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                // Header with icon, title, and close button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(colors.primary.copy(alpha = 0.12f))
                            .border(1.dp, colors.primary.copy(alpha = 0.25f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Style,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    Text(
                        text = "RAW + JPEG Pairs Detected",
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("raw_jpeg_dismiss"),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = colors.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "This folder contains matching RAW and JPEG versions of your photos. Choose how you would like to handle them:",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Option 1: Link actions across both
                Surface(
                    onClick = onEnableMoveRelatedFiles,
                    shape = RoundedCornerShape(12.dp),
                    color = colors.primaryContainer.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("apply_both_button"),
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Link actions across both",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = colors.onPrimaryContainer,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Moving, copying, or deleting a photo applies to both files at once.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onPrimaryContainer.copy(alpha = 0.85f),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Or filter the feed to one file type:",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Option 2 & 3: Filter RAW or JPEG
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = onFilterRaw,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("filter_raw_button"),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(vertical = 2.dp),
                        ) {
                            Text("RAW only", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                            Text("Hide JPEGs", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                        }
                    }

                    OutlinedButton(
                        onClick = onFilterJpg,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("filter_jpg_button"),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(vertical = 2.dp),
                        ) {
                            Text("JPEG only", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                            Text("Hide RAWs", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Option 4: Don't change settings
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dont_change_settings_button")
                        .testTag("keep_settings_button"),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(vertical = 2.dp),
                    ) {
                        Text(
                            text = "Don't change settings",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "Keep all files separate without linking or filtering",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Backward-compatible alias for [RawJpegSuggestionDialog].
 */
@Composable
fun RawJpegSuggestionCard(
    visible: Boolean,
    onFilterRaw: () -> Unit,
    onFilterJpg: () -> Unit,
    onEnableMoveRelatedFiles: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) = RawJpegSuggestionDialog(
    visible = visible,
    onFilterRaw = onFilterRaw,
    onFilterJpg = onFilterJpg,
    onEnableMoveRelatedFiles = onEnableMoveRelatedFiles,
    onDismiss = onDismiss,
    modifier = modifier,
)
