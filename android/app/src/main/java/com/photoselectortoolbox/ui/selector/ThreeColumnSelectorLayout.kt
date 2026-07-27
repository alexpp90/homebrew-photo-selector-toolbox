package com.photoselectortoolbox.ui.selector

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photoselectortoolbox.data.model.ImageItem
import com.photoselectortoolbox.domain.scoring.ScoreMetric
import com.photoselectortoolbox.ui.components.ScoreChipRow
import com.photoselectortoolbox.ui.components.ScoreChipRowMinHeight
import com.photoselectortoolbox.ui.theme.Zinc50
import com.photoselectortoolbox.ui.theme.Zinc600

/**
 * The comparison layout: Previous, Current and Next in equal thirds, with a
 * shared action row beneath.
 *
 * Unlike the focused layout, each column keeps a fixed 4:3 footprint and
 * pillarboxes portrait frames inside it. That is a deliberate exception to
 * "the tile is the image": if columns resized to each frame's own aspect
 * ratio, a portrait frame in the middle of a burst would reflow the whole grid
 * and the eye would lose its place mid-comparison. A stable grid is worth more
 * here than a perfectly tight tile.
 */
@Composable
fun ThreeColumnSelectorLayout(
    current: ImageItem?,
    previous: ImageItem?,
    next: ImageItem?,
    currentIndex: Int,
    total: Int,
    detailsVisible: Boolean,
    filmstripVisible: Boolean,
    actions: SelectorActions,
    onNavigatePrevious: () -> Unit,
    onNavigateNext: () -> Unit,
    onLongPressFrame: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val frames = listOf(previous, current, next)
    val bestSets = bestMetricSets(frames.map { it?.scanResult })

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ComparisonColumn(
                image = previous,
                label = "Previous",
                isCurrent = false,
                position = if (previous != null) currentIndex else null,
                total = total,
                bestMetrics = bestSets[0],
                detailsVisible = detailsVisible,
                onClick = onNavigatePrevious,
                onLongClick = onLongPressFrame,
                testTag = "column_previous",
                modifier = Modifier.weight(1f),
            )
            ComparisonColumn(
                image = current,
                label = "Current",
                isCurrent = true,
                position = if (current != null) currentIndex + 1 else null,
                total = total,
                bestMetrics = bestSets[1],
                detailsVisible = detailsVisible,
                onClick = actions.onFullscreen,
                onLongClick = onLongPressFrame,
                testTag = "column_current",
                modifier = Modifier.weight(1f),
            )
            ComparisonColumn(
                image = next,
                label = "Next",
                isCurrent = false,
                position = if (next != null) currentIndex + 2 else null,
                total = total,
                bestMetrics = bestSets[2],
                detailsVisible = detailsVisible,
                onClick = onNavigateNext,
                onLongClick = onLongPressFrame,
                testTag = "column_next",
                modifier = Modifier.weight(1f),
            )
        }

        Box(modifier = Modifier.padding(top = 18.dp)) {
            SharedActionRow(
                actions = actions,
                focusedLayout = false,
                detailsVisible = detailsVisible,
                filmstripVisible = filmstripVisible,
            )
        }
    }
}

@Composable
private fun ComparisonColumn(
    image: ImageItem?,
    label: String,
    isCurrent: Boolean,
    position: Int?,
    total: Int,
    bestMetrics: Set<ScoreMetric>,
    detailsVisible: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ColumnLabel(text = label, isCurrent = isCurrent)
            if (position != null) {
                PositionText(position = position, total = total)
            }
        }

        ImageTile(
            image = image,
            isCurrent = isCurrent,
            onClick = onClick,
            onLongClick = onLongClick,
            emptyLabel = "No $label",
            // Fixed 4:3 footprint; a portrait frame is pillarboxed inside it
            // rather than reshaping the column.
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(LANDSCAPE_ASPECT)
                .testTag(testTag),
        )

        Text(
            text = image?.fileName ?: "—",
            fontSize = 14.sp,
            color = if (image != null) Zinc50 else Zinc600,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        ExifSummaryLine(image = image)

        if (detailsVisible) {
            ScoreChipRow(
                scores = image?.scanResult,
                bestMetrics = bestMetrics,
                showNotScanned = image != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = ScoreChipRowMinHeight),
            )
        }
    }
}
