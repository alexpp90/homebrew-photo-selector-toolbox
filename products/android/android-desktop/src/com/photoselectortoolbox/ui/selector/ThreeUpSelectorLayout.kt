package com.photoselectortoolbox.ui.selector

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.photoselectortoolbox.data.model.ImageItem
import com.photoselectortoolbox.domain.interaction.FilingAction
import com.photoselectortoolbox.domain.scoring.ScoreMetric
import com.photoselectortoolbox.viewmodel.SelectorFrame

/**
 * The selector: three equal frames, one over two, the current one centred.
 *
 * ```
 * ┌────┬──────────┬──────────────┬──────────┐
 * │    │ readouts │   CURRENT    │ controls │   row 1
 * │side├──────────┼──────┬───────┴──────────┤
 * │bar │          │ PREV │  NEXT │          │   row 2
 * └────┴──────────┴──────┴───────┴──────────┘
 * ```
 *
 * Three design facts, each of which replaced a shipped mistake:
 *
 * 1. **One over two, not a row.** Three 4:3 frames abreast are width-bound and
 *    cap at 493 × 370 dp while abandoning 60 % of the display's height. One over
 *    two is height-bound and reaches 600 × 450 — 76 % more area. See
 *    [FrameGeometry] for the arithmetic.
 * 2. **The current frame is centred**, flanked by its readouts and its controls.
 *    Centring plus the 2 dp Indigo border is what marks it as the frame under
 *    judgement. It is never made *larger* than its neighbours: a frame you
 *    cannot compare at equal size is a frame you cannot judge.
 * 3. **Every value touches the frame it describes.** Named beside the current
 *    frame, icon-and-value on each neighbour's own right edge. An earlier draft
 *    put all fifteen numbers in one matrix, which compared well and left the
 *    user unable to say whose number was whose.
 */
@Composable
fun ThreeUpSelectorLayout(
    current: ImageItem?,
    previous: ImageItem?,
    next: ImageItem?,
    currentIndex: Int,
    total: Int,
    filingAction: FilingAction,
    folderName: String,
    burstLabel: String?,
    detailsVisible: Boolean,
    filmstripVisible: Boolean,
    overlayValuesVisible: Boolean,
    maximisedFrame: SelectorFrame?,
    actions: SelectorActions,
    onNavigatePrevious: () -> Unit,
    onNavigateNext: () -> Unit,
    onMaximise: (SelectorFrame) -> Unit,
    onLongPressFrame: () -> Unit,
    showFirstRunHint: Boolean,
    onDismissFirstRunHint: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bestSets = bestMetricSets(
        listOf(previous?.scanResult, current?.scanResult, next?.scanResult)
    )

    Row(
        modifier = modifier.fillMaxSize().padding(FrameGeometry.OuterPadding),
        horizontalArrangement = Arrangement.spacedBy(FrameGeometry.Gap),
    ) {
        if (maximisedFrame != null) {
            MaximisedFrame(
                frame = maximisedFrame,
                image = when (maximisedFrame) {
                    SelectorFrame.PREVIOUS -> previous
                    SelectorFrame.CURRENT -> current
                    SelectorFrame.NEXT -> next
                },
                bestMetrics = bestSets[maximisedFrame.readoutIndex],
                detailsVisible = detailsVisible,
                onExit = { onMaximise(maximisedFrame) },
                onLongPress = onLongPressFrame,
                modifier = Modifier.weight(1f),
            )
            return@Row
        }

        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxSize()) {
            val activeImage = current ?: previous ?: next
            val aspect = activeImage?.aspectRatio?.let { raw ->
                if (raw < 1f) 1f / raw else raw
            } ?: FrameGeometry.DefaultLandscapeAspect

            val minFlank = if (detailsVisible) FrameGeometry.MinimumFlankWidth else 0.dp
            val topRowFit = FrameGeometry.frameSize(
                regionWidth = maxWidth - minFlank * 2 - FrameGeometry.Gap * 2,
                regionHeight = maxHeight,
                aspect = aspect,
                columns = 1,
                rows = 2,
            )
            val bottomRowFit = FrameGeometry.frameSize(
                regionWidth = maxWidth,
                regionHeight = maxHeight,
                aspect = aspect,
                columns = 2,
                rows = 2,
            )
            val frameSize = if (bottomRowFit.width < topRowFit.width) bottomRowFit else topRowFit

            val flankWidth = if (detailsVisible) {
                ((maxWidth - frameSize.width - FrameGeometry.Gap * 2) / 2)
                    .coerceAtLeast(FrameGeometry.MinimumFlankWidth)
            } else {
                0.dp
            }

            val overlayOutside = FrameGeometry.overlayFitsOutside(
                rowWidth = maxWidth,
                frameWidth = frameSize.width,
            )

            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(FrameGeometry.Gap),
            ) {
                // ── Row 1: readouts · CURRENT · controls ────────────────
                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier.width(flankWidth),
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        if (detailsVisible) {
                            CurrentFrameReadout(
                                image = current,
                                bestMetrics = bestSets[1],
                                folderName = folderName,
                                burstLabel = burstLabel,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(FrameGeometry.Gap))

                    FrameTile(
                        image = current,
                        size = frameSize,
                        isCurrent = true,
                        emptyLabel = "No image",
                        badgeAlignment = Alignment.BottomEnd,
                        onClick = actions.onFullscreen,
                        onLongClick = onLongPressFrame,
                        onMaximise = { onMaximise(SelectorFrame.CURRENT) },
                        testTag = "column_current",
                    )

                    Spacer(modifier = Modifier.width(FrameGeometry.Gap))

                    Box(
                        modifier = Modifier.width(flankWidth),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        SelectorControlBlock(
                            actions = actions,
                            filingAction = filingAction,
                            position = currentIndex + 1,
                            total = total,
                            canGoPrevious = previous != null,
                            canGoNext = next != null,
                            detailsVisible = detailsVisible,
                            filmstripVisible = filmstripVisible,
                            overlayValuesVisible = overlayValuesVisible,
                            onNavigatePrevious = onNavigatePrevious,
                            onNavigateNext = onNavigateNext,
                        )
                    }
                }

                // ── Row 2: PREVIOUS · NEXT, centred as a pair ───────────
                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(
                        FrameGeometry.Gap,
                        Alignment.CenterHorizontally,
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NeighbourFrame(
                        frame = SelectorFrame.PREVIOUS,
                        image = previous,
                        size = frameSize,
                        position = if (previous != null) currentIndex else null,
                        total = total,
                        bestMetrics = bestSets[0],
                        showValues = overlayValuesVisible,
                        overlayOutside = overlayOutside,
                        onClick = onNavigatePrevious,
                        onLongClick = onLongPressFrame,
                        onMaximise = { onMaximise(SelectorFrame.PREVIOUS) },
                        testTag = "column_previous",
                    )
                    NeighbourFrame(
                        frame = SelectorFrame.NEXT,
                        image = next,
                        size = frameSize,
                        position = if (next != null) currentIndex + 2 else null,
                        total = total,
                        bestMetrics = bestSets[2],
                        showValues = overlayValuesVisible,
                        overlayOutside = overlayOutside,
                        onClick = onNavigateNext,
                        onLongClick = onLongPressFrame,
                        onMaximise = { onMaximise(SelectorFrame.NEXT) },
                        testTag = "column_next",
                    )
                }
            }

            // The only overlay allowed near a photograph besides the values,
            // and only once ever.
            FirstRunNavigationHint(
                visible = showFirstRunHint,
                onDismiss = onDismissFirstRunHint,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
            )
        }
    }
}

/** Which readout column a frame's best-metric set lives in. */
private val SelectorFrame.readoutIndex: Int
    get() = when (this) {
        SelectorFrame.PREVIOUS -> 0
        SelectorFrame.CURRENT -> 1
        SelectorFrame.NEXT -> 2
    }

/**
 * A neighbour and its values.
 *
 * The values go outside the frame when there is room and on it when there is
 * not — one measurement, both branches, rather than a hard-coded choice. On the
 * reference tablet the bottom row leaves 84 dp per side so they overlay; a wide
 * DeX window puts them outside with no code change.
 */
@Composable
private fun NeighbourFrame(
    frame: SelectorFrame,
    image: ImageItem?,
    size: DpSize,
    position: Int?,
    total: Int,
    bestMetrics: Set<ScoreMetric>,
    showValues: Boolean,
    overlayOutside: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMaximise: () -> Unit,
    testTag: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box {
            FrameTile(
                image = image,
                size = size,
                isCurrent = false,
                emptyLabel = if (frame == SelectorFrame.PREVIOUS) "No previous" else "No next",
                // Bottom-left here, bottom-right on the current frame: the
                // right edge belongs to the value overlay, and two controls
                // must never share bounds.
                badgeAlignment = Alignment.BottomStart,
                onClick = onClick,
                onLongClick = onLongClick,
                onMaximise = onMaximise,
                testTag = testTag,
            )

            if (showValues && !overlayOutside) {
                NeighbourValueOverlay(
                    image = image,
                    position = position,
                    total = total,
                    bestMetrics = bestMetrics,
                    isOverlaid = true,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(8.dp),
                )
            }
        }

        if (showValues && overlayOutside) {
            NeighbourValueOverlay(
                image = image,
                position = position,
                total = total,
                bestMetrics = bestMetrics,
                isOverlaid = false,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

/**
 * One frame at exactly [size], with its maximise badge.
 *
 * The size arrives as an explicit [DpSize] rather than as a modifier chain, so
 * all three tiles are the same by construction rather than by all three
 * happening to resolve their constraints the same way.
 */
@Composable
private fun FrameTile(
    image: ImageItem?,
    size: DpSize,
    isCurrent: Boolean,
    emptyLabel: String,
    badgeAlignment: Alignment,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMaximise: () -> Unit,
    testTag: String,
) {
    Box {
        ImageTile(
            image = image,
            isCurrent = isCurrent,
            onClick = onClick,
            onLongClick = onLongClick,
            emptyLabel = emptyLabel,
            modifier = Modifier
                .size(size)
                .testTag(testTag),
        )
        if (image != null) {
            MaximiseBadge(
                onClick = onMaximise,
                modifier = Modifier.align(badgeAlignment).padding(6.dp),
            )
        }
    }
}

/**
 * One frame filling the whole region.
 *
 * On the reference tablet this is 1211 × 908 dp against 600 × 450 in three-up —
 * 4.1× the area. The readouts stay beside it so the numbers do not vanish
 * exactly when the user looks closest; the neighbours' overlays go, because
 * their frames have.
 */
@Composable
private fun MaximisedFrame(
    frame: SelectorFrame,
    image: ImageItem?,
    bestMetrics: Set<ScoreMetric>,
    detailsVisible: Boolean,
    onExit: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val aspect = image?.aspectRatio?.let { raw ->
            if (raw < 1f) 1f / raw else raw
        } ?: FrameGeometry.DefaultLandscapeAspect

        val readoutWidth = if (detailsVisible) FrameGeometry.FlankWidth else 0.dp
        val size = FrameGeometry.maximisedFrameSize(
            regionWidth = maxWidth - readoutWidth - FrameGeometry.Gap,
            regionHeight = maxHeight,
            aspect = aspect,
        )

        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(
                FrameGeometry.Gap,
                Alignment.CenterHorizontally,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (detailsVisible) {
                Box(
                    modifier = Modifier.width(FrameGeometry.FlankWidth),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    CurrentFrameReadout(image = image, bestMetrics = bestMetrics)
                }
            }

            Box {
                ImageTile(
                    image = image,
                    isCurrent = frame == SelectorFrame.CURRENT,
                    onClick = onExit,
                    onLongClick = onLongPress,
                    emptyLabel = "No image",
                    modifier = Modifier
                        .size(size)
                        .testTag("maximised_frame"),
                )
                MaximiseBadge(
                    active = true,
                    onClick = onExit,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp),
                )
            }
        }
    }
}
