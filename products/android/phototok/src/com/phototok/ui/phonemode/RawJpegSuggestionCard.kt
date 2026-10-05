package com.phototok.ui.phonemode

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.FilledTonalButton
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

@Composable
fun RawJpegSuggestionCard(
    visible: Boolean,
    onFilterRaw: () -> Unit,
    onFilterJpg: () -> Unit,
    onEnableMoveRelatedFiles: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(tween(250)) { it / 2 } + fadeIn(tween(250)),
        exit = slideOutVertically(tween(200)) { it / 2 } + fadeOut(tween(200)),
        modifier = modifier,
    ) {
        val colors = MaterialTheme.colorScheme

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .testTag("raw_jpeg_suggestion_card"),
            shape = RoundedCornerShape(16.dp),
            color = colors.surfaceContainerHigh.copy(alpha = 0.97f),
            border = BorderStroke(
                1.dp,
                colors.primary.copy(alpha = 0.35f),
            ),
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
            ) {
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

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "RAW + JPEG Pairs Detected",
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.onSurface,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }

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

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "This folder contains matching RAW and JPEG pairs of the same pictures. You can filter to view only one type, or link actions to apply across both.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = onFilterRaw,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("filter_raw_button"),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text("RAW only", style = MaterialTheme.typography.labelMedium)
                    }

                    OutlinedButton(
                        onClick = onFilterJpg,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("filter_jpg_button"),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text("JPEG only", style = MaterialTheme.typography.labelMedium)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                FilledTonalButton(
                    onClick = onEnableMoveRelatedFiles,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("apply_both_button"),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(
                        "Apply actions to both (move/copy/delete)",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}
