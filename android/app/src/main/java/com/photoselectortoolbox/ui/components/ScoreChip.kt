package com.photoselectortoolbox.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.WbShade
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.photoselectortoolbox.data.model.ScanResult
import com.photoselectortoolbox.domain.scoring.ScoreMetric

/** The icon that represents each metric. */
fun ScoreMetric.icon(): ImageVector = when (this) {
    ScoreMetric.SHARPNESS -> Icons.Default.CenterFocusStrong
    ScoreMetric.NOISE -> Icons.Default.Grain
    ScoreMetric.HIGHLIGHT_CLIPPING -> Icons.Default.Highlight
    ScoreMetric.SHADOW_CLIPPING -> Icons.Default.WbShade
    ScoreMetric.AESTHETIC -> Icons.Default.AutoAwesome
}

/**
 * A single scan metric.
 *
 * Shows icon + short label + value rather than icon + value: an unlabelled
 * icon tells the user nothing about what "3.2" means. Set [compact] where
 * horizontal space is genuinely tight (thumbnail overlays) to drop the label;
 * the accessibility description still carries the full name and direction.
 */
@Composable
fun ScoreChip(
    metric: ScoreMetric,
    value: Double?,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    if (value == null) return

    Surface(
        modifier = modifier.semantics { contentDescription = metric.accessibilityLabel(value) },
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = metric.icon(),
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!compact) {
                Text(
                    text = metric.shortLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = metric.format(value),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/**
 * Every metric present in [scores], wrapped so it never overflows its parent.
 * One call site instead of five repeated ScoreChip blocks.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScoreChipRow(
    scores: ScanResult?,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val present = ScoreMetric.present(scores)
    if (present.isEmpty()) return

    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        present.forEach { (metric, value) ->
            ScoreChip(metric = metric, value = value, compact = compact)
        }
    }
}
