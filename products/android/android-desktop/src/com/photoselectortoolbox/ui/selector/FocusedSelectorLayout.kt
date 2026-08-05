package com.photoselectortoolbox.ui.selector

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.outlined.Info
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.photoselectortoolbox.data.model.ImageItem
import com.photoselectortoolbox.domain.scoring.ScoreMetric
import com.photoselectortoolbox.ui.theme.ScoreBad

/**
 * The default selector layout: the current frame on top, its two neighbours
 * below, all three exactly the same size.
 *
 * The geometry is the design. On a 16:10 tablet a 4:3 photograph fitted to the
 * full width is height-limited, which leaves a wide empty column on each side.
 * The two 56dp rails live in that dead space, so they cost zero vertical room
 * and never cover a photograph. The image region is then two equal rows, which
 * is what makes all three frames resolve to the same height — and equal size is
 * what makes them comparable. A shrunken or dimmed neighbour cannot be judged
 * for sharpness or exposure, which is the entire task.
 *
 *   ┌────┬──────────────────────┬───────────┬────┐
 *   │    │       CURRENT        │  details  │    │  row 1
 *   │nav ├───────────┬──────────┴───────────┤act │
 *   │    │ PREVIOUS  │         NEXT         │    │  row 2
 *   └────┴───────────┴──────────────────────┴────┘
 *   [ filmstrip — collapsible ]
 */
@Composable
fun FocusedSelectorLayout(
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
    showFirstRunHint: Boolean,
    onDismissFirstRunHint: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bestSets = bestMetricSets(listOf(previous?.scanResult, current?.scanResult, next?.scanResult))
    val currentBest: Set<ScoreMetric> = bestSets[1]

    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(FocusedOuterPadding),
        horizontalArrangement = Arrangement.spacedBy(FocusedOuterPadding),
    ) {
        FocusedNavigationRail(
            position = currentIndex + 1,
            total = total,
            canGoPrevious = previous != null,
            canGoNext = next != null,
            onPrevious = onNavigatePrevious,
            onNext = onNavigateNext,
        )

        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // Row 1 — the frame under judgement, plus its metadata.
                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CenteredImageTile(
                        image = current,
                        isCurrent = true,
                        onClick = actions.onFullscreen,
                        onLongClick = onLongPressFrame,
                        emptyLabel = "No image",
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        tileModifier = Modifier.testTag("column_current"),
                    )
                    if (detailsVisible) {
                        Spacer(modifier = Modifier.width(12.dp))
                        DetailsPanel(image = current, bestMetrics = currentBest)
                    }
                }

                // Row 2 — the two neighbours, sharing the height equally with
                // row 1 so every frame is the same size.
                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    NeighbourCell(
                        image = previous,
                        label = "Previous",
                        position = if (previous != null) currentIndex else null,
                        total = total,
                        onClick = onNavigatePrevious,
                        onLongClick = onLongPressFrame,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        testTag = "column_previous",
                    )
                    NeighbourCell(
                        image = next,
                        label = "Next",
                        position = if (next != null) currentIndex + 2 else null,
                        total = total,
                        onClick = onNavigateNext,
                        onLongClick = onLongPressFrame,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        testTag = "column_next",
                    )
                }
            }

            // The only overlay allowed near a photograph, and only once ever.
            FirstRunNavigationHint(
                visible = showFirstRunHint,
                onDismiss = onDismissFirstRunHint,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp),
            )
        }

        FocusedActionRail(
            actions = actions,
            detailsVisible = detailsVisible,
            filmstripVisible = filmstripVisible,
        )
    }
}

/** One neighbour: the frame itself, then its caption in the letterbox beside it. */
@Composable
private fun NeighbourCell(
    image: ImageItem?,
    label: String,
    position: Int?,
    total: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        CenteredImageTile(
            image = image,
            isCurrent = false,
            onClick = onClick,
            onLongClick = onLongClick,
            emptyLabel = "No $label",
            modifier = Modifier.weight(1f, fill = false).fillMaxHeight(),
            tileModifier = Modifier.testTag(testTag),
        )
        Spacer(modifier = Modifier.width(12.dp))
        NeighbourCaption(
            label = label,
            image = image,
            position = position,
            total = total,
        )
    }
}

/** Left rail: paging, and where in the folder you are. */
@Composable
private fun FocusedNavigationRail(
    position: Int,
    total: Int,
    canGoPrevious: Boolean,
    canGoNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    SelectorRail(verticalArrangement = Arrangement.Top) {
        RailButton(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            description = "Previous image",
            enabled = canGoPrevious,
            onClick = onPrevious,
            modifier = Modifier.testTag("rail_previous"),
        )
        RailSpacer()
        RailButton(
            icon = Icons.AutoMirrored.Filled.ArrowForward,
            description = "Next image",
            enabled = canGoNext,
            onClick = onNext,
            modifier = Modifier.testTag("rail_next"),
        )
        Spacer(modifier = Modifier.weight(1f))
        RailPositionCounter(
            position = position,
            total = total,
            modifier = Modifier.padding(bottom = 8.dp),
        )
    }
}

/** Right rail: what you can do to the current frame, then what you can show. */
@Composable
private fun FocusedActionRail(
    actions: SelectorActions,
    detailsVisible: Boolean,
    filmstripVisible: Boolean,
) {
    SelectorRail {
        RailButton(
            icon = Icons.AutoMirrored.Filled.DriveFileMove,
            description = "Move to Selection, shortcut M",
            onClick = actions.onMove,
            modifier = Modifier.testTag("move_button_expanded"),
        )
        RailSpacer()
        RailButton(
            icon = Icons.Default.ContentCopy,
            description = "Copy to Selection, shortcut C",
            onClick = actions.onCopy,
            modifier = Modifier.testTag("copy_button_expanded"),
        )
        RailSpacer()
        RailButton(
            icon = Icons.Default.Delete,
            description = "Delete, shortcut Delete",
            onClick = actions.onDelete,
            tint = ScoreBad,
            modifier = Modifier.testTag("delete_button_expanded"),
        )
        RailSpacer()
        RailButton(
            icon = Icons.Default.Fullscreen,
            description = "Open fullscreen, shortcut F",
            onClick = actions.onFullscreen,
            modifier = Modifier.testTag("fullscreen_button"),
        )

        RailDivider()

        // The layout toggle lives here in this layout and nowhere else — a
        // control that floats over the whole screen ends up on top of whatever
        // the active layout puts in the same corner.
        RailButton(
            icon = layoutToggleIcon(focusedLayout = true),
            description = layoutToggleDescription(focusedLayout = true),
            onClick = actions.onToggleLayout,
            modifier = Modifier.testTag("layout_toggle"),
        )
        RailSpacer()
        RailButton(
            icon = Icons.Outlined.Info,
            description = if (detailsVisible) "Hide photo details" else "Show photo details",
            onClick = actions.onToggleDetails,
            active = detailsVisible,
            modifier = Modifier.testTag("details_toggle"),
        )
        RailSpacer()
        RailButton(
            icon = Icons.Default.ViewCarousel,
            description = if (filmstripVisible) "Hide the filmstrip" else "Show the filmstrip",
            onClick = actions.onToggleFilmstrip,
            active = filmstripVisible,
            modifier = Modifier.testTag("filmstrip_toggle"),
        )
    }
}
