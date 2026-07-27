package com.photoselectortoolbox.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.photoselectortoolbox.domain.scoring.ScoreDirection
import com.photoselectortoolbox.domain.scoring.ScoreMetric

/**
 * Explains what each post-scan chip means.
 *
 * The chips are necessarily terse — this sheet is where the icon, the metric
 * and "which way is good" are spelled out, reachable from the info button in
 * the app bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScoreLegendSheet(onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        modifier = Modifier.testTag("score_legend_sheet"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column {
                Text(
                    text = "What the scan icons mean",
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.onSurface,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Each badge on a photo is one quality measurement from the scan.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }

            ScoreMetric.entries.forEach { metric ->
                LegendRow(metric)
            }

            // The worked example is the point of the sheet. Three sharpness
            // values on wildly different scales are hard to rank by reading;
            // the same three as bars are not. Showing them side by side is
            // what teaches the user to stop reading the digits.
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.testTag("legend_bar_example"),
                ) {
                    listOf(22.4, 61.9, 88.3).forEach { value ->
                        ScoreChip(metric = ScoreMetric.SHARPNESS, value = value)
                    }
                }
                Text(
                    text = "Compare the bars, not the numbers.",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface,
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "A badge is only shown when that measurement was part of the scan. " +
                    "Choose which ones run in Scan settings.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant.copy(alpha = 0.8f),
            )
        }
    }
}

@Composable
private fun LegendRow(metric: ScoreMetric) {
    val colors = MaterialTheme.colorScheme
    val betterUp = metric.direction == ScoreDirection.HIGHER_IS_BETTER

    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(colors.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = metric.icon(),
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = metric.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.size(8.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = colors.surfaceVariant.copy(alpha = 0.6f),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Icon(
                            imageVector = if (betterUp) {
                                Icons.Default.ArrowUpward
                            } else {
                                Icons.Default.ArrowDownward
                            },
                            contentDescription = null,
                            tint = colors.onSurfaceVariant,
                            modifier = Modifier.size(12.dp),
                        )
                        Text(
                            text = metric.direction.hint,
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = metric.description,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
    }
}
