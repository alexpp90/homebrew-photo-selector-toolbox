package com.photoselectortoolbox.ui.selector

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.photoselectortoolbox.data.model.ImageItem
import com.photoselectortoolbox.data.model.ScanResult
import com.photoselectortoolbox.domain.format.ExifFormatter
import com.photoselectortoolbox.domain.scoring.ScoreMetric
import com.photoselectortoolbox.ui.components.ScoreChipRow
import com.photoselectortoolbox.ui.theme.Indigo500
import com.photoselectortoolbox.ui.theme.PanelSurface
import com.photoselectortoolbox.ui.theme.WarningAmber
import com.photoselectortoolbox.ui.theme.Zinc400
import com.photoselectortoolbox.ui.theme.Zinc50
import com.photoselectortoolbox.ui.theme.Zinc500
import com.photoselectortoolbox.ui.theme.Zinc600
import com.photoselectortoolbox.ui.theme.Zinc700
import com.photoselectortoolbox.ui.theme.Zinc800

// ── Shared measurements ─────────────────────────────────────────────────────
//
// Named rather than inlined because the focused layout's vertical budget is
// arithmetic: 924 = app bar + filmstrip + padding + image region. A stray
// literal in one place silently steals height from the photographs.

/** Width of the left and right control rails. */
val RailWidth = 56.dp

/** Every interactive control is at least this big (accessibility minimum). */
val ControlSize = 48.dp

/** Outer padding of the focused layout's image region. */
val FocusedOuterPadding = 6.dp

/** Fixed width of the details panel beside the current frame. */
val DetailsPanelWidth = 296.dp

/** Landscape frames are 4:3; portrait frames are the reciprocal. */
const val LANDSCAPE_ASPECT = 4f / 3f
const val PORTRAIT_ASPECT = 3f / 4f

/** The aspect ratio a tile should lock to for [image]. */
fun tileAspectRatio(image: ImageItem?): Float =
    if (image == null || image.isLandscape) LANDSCAPE_ASPECT else PORTRAIT_ASPECT

// ── Rails ───────────────────────────────────────────────────────────────────

/** The 56dp outlined shell both rails share. */
@Composable
fun SelectorRail(
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.Center,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .width(RailWidth)
            .fillMaxHeight()
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, Zinc800, RoundedCornerShape(10.dp))
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = verticalArrangement,
        content = content,
    )
}

/**
 * A rail control.
 *
 * Deliberately flat: the design separates surfaces with outlines and value
 * steps rather than elevation, and a filled button on every rail slot would
 * out-shout the photographs the rail exists to serve. [active] tints the icon
 * indigo for the toggles that have an on state.
 */
@Composable
fun RailButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    active: Boolean = false,
    tint: Color? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()

    val contentColor = when {
        !enabled -> Zinc600
        tint != null -> tint
        active -> Indigo500
        else -> Zinc400
    }
    val borderColor by animateColorAsState(
        targetValue = when {
            !enabled -> Zinc800
            hovered -> Zinc600
            else -> Color.Transparent
        },
        animationSpec = tween(150),
        label = "railBorder",
    )

    Box(
        modifier = modifier
            .size(ControlSize)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .hoverable(interactionSource, enabled = enabled)
            .then(
                if (enabled) {
                    Modifier
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable(onClick = onClick)
                } else {
                    Modifier
                }
            )
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = contentColor,
        )
    }
}

/** The short rule that separates action groups inside a rail. */
@Composable
fun RailDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(vertical = 8.dp)
            .width(28.dp)
            .height(1.dp)
            .background(Zinc700),
    )
}

/**
 * "127 over 842", stacked vertically so it fits a 56dp rail.
 *
 * A horizontal "127 / 842" would either overflow the rail or force a type size
 * too small to read at arm's length on a tablet.
 */
@Composable
fun RailPositionCounter(
    position: Int,
    total: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.semantics { contentDescription = "Image $position of $total" },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "$position",
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            color = Zinc50,
        )
        Box(
            modifier = Modifier
                .padding(vertical = 3.dp)
                .width(22.dp)
                .height(1.dp)
                .background(Zinc700),
        )
        Text(
            text = "$total",
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = Zinc400,
        )
    }
}

// ── Image tile ──────────────────────────────────────────────────────────────

/**
 * A photograph, and nothing else.
 *
 * The tile locks to the frame's own aspect ratio and fills the available
 * height, so its box *is* the image — there is never a grey surface visible
 * around a photo. That matters more than it sounds: an empty panel behind a
 * frame changes the apparent brightness of the photo next to it, and the whole
 * task here is comparing photographs.
 *
 * The active frame is marked only by a 2dp indigo border. Neighbours are never
 * dimmed or shrunk: a dimmed frame cannot be judged for exposure, and a smaller
 * one cannot be judged for sharpness.
 */
@Composable
fun ImageTile(
    image: ImageItem?,
    isCurrent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    emptyLabel: String = "No image",
    contentScale: ContentScale = ContentScale.Fit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()

    val borderColor by animateColorAsState(
        targetValue = when {
            isCurrent -> Indigo500
            hovered -> Zinc600
            else -> Zinc700
        },
        animationSpec = tween(150),
        label = "tileBorder",
    )
    val borderWidth = if (isCurrent) 2.dp else 1.dp
    val shape = RoundedCornerShape(4.dp)

    Box(
        modifier = modifier
            .clip(shape)
            .background(Zinc800)
            .border(borderWidth, borderColor, shape)
            .hoverable(interactionSource)
            .then(
                if (image != null) {
                    Modifier
                        .pointerHoverIcon(PointerIcon.Hand)
                        .tileClickable(onClick = onClick, onLongClick = onLongClick)
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (image != null) {
            AsyncImage(
                model = image.uri,
                contentDescription = image.fileName,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
            )
        } else {
            Text(
                text = emptyLabel,
                fontSize = 12.sp,
                color = Zinc600,
            )
        }
    }
}

/**
 * Centres an [ImageTile] that is locked to [aspectRatio] and as tall as the
 * row allows, so the tile's width follows from its height rather than from the
 * available width. This is what keeps all three frames the same size.
 */
@Composable
fun CenteredImageTile(
    image: ImageItem?,
    isCurrent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    emptyLabel: String = "No image",
    tileModifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        ImageTile(
            image = image,
            isCurrent = isCurrent,
            onClick = onClick,
            onLongClick = onLongClick,
            emptyLabel = emptyLabel,
            modifier = tileModifier
                .fillMaxHeight()
                // Height first: the row's height is what all three frames
                // share, so the width must follow from it. Resolving width
                // first would let a wide frame decide its own height and break
                // the "all three the same size" rule the layout rests on.
                .aspectRatio(tileAspectRatio(image), matchHeightConstraintsFirst = true),
        )
    }
}

// ── Details panel ───────────────────────────────────────────────────────────

/**
 * Filename, EXIF and scores for the current frame, in the letterbox column
 * beside it.
 *
 * Lives here rather than on top of the photograph because a scrim over a frame
 * changes how its shadows read, and shadow detail is one of the things being
 * judged.
 */
@Composable
fun DetailsPanel(
    image: ImageItem?,
    bestMetrics: Set<ScoreMetric>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(DetailsPanelWidth)
            .fillMaxHeight()
            .clip(RoundedCornerShape(10.dp))
            .background(PanelSurface)
            .border(1.dp, Zinc800, RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (image == null) {
            Text(text = "No image", fontSize = 12.sp, color = Zinc600)
            return@Column
        }

        Text(
            text = image.fileName,
            fontSize = 14.sp,
            color = Zinc50,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            ExifFormatter.detailRows(image.exifData).forEach { (label, value) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        text = label,
                        fontSize = 11.5.sp,
                        color = Zinc400,
                    )
                    Text(
                        text = value,
                        fontSize = 11.5.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Zinc50,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Zinc700),
        )

        ScoreChipRow(
            scores = image.scanResult,
            bestMetrics = bestMetrics,
            showNotScanned = true,
            modifier = Modifier.fillMaxWidth(),
        )

        // A frame whose EXIF came from the fallback reader may be missing
        // fields entirely; say so rather than letting "Unknown" read as a
        // property of the photograph.
        if (image.exifData?.isFallback == true) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    modifier = Modifier.size(13.dp),
                    tint = WarningAmber,
                )
                Text(
                    text = "limited metadata",
                    fontSize = 11.sp,
                    color = WarningAmber,
                )
            }
        }
    }
}

// ── Small shared pieces ─────────────────────────────────────────────────────

/** Uppercase micro-label ("PREVIOUS", "CURRENT", "NEXT"). */
@Composable
fun ColumnLabel(
    text: String,
    isCurrent: Boolean,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text.uppercase(),
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 1.2.sp,
        color = if (isCurrent) Indigo500 else Zinc400,
        modifier = modifier,
    )
}

/** `127 / 842` in tabular mono. */
@Composable
fun PositionText(
    position: Int,
    total: Int,
    modifier: Modifier = Modifier,
    color: Color = Zinc500,
) {
    Text(
        text = "$position / $total",
        fontSize = 10.sp,
        fontFamily = FontFamily.Monospace,
        color = color,
        modifier = modifier,
    )
}

/** Filename plus its position, used beside the neighbour tiles. */
@Composable
fun NeighbourCaption(
    label: String,
    image: ImageItem?,
    position: Int?,
    total: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.widthIn(max = 180.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        ColumnLabel(text = label, isCurrent = false)
        Text(
            text = image?.fileName ?: "—",
            fontSize = 12.sp,
            color = if (image != null) Zinc50 else Zinc600,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (position != null) {
            PositionText(position = position, total = total)
        }
    }
}

/** The one-line EXIF summary under a frame; renders nothing when unknown. */
@Composable
fun ExifSummaryLine(
    image: ImageItem?,
    modifier: Modifier = Modifier,
) {
    val line = ExifFormatter.summaryLine(image?.exifData) ?: return
    Text(
        text = line,
        fontSize = 11.sp,
        fontFamily = FontFamily.Monospace,
        color = Zinc400,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/**
 * Reports where the pointer last went down, without consuming the event.
 *
 * Used to open the context menu under the finger or cursor. Observing on the
 * Initial pass matters: the tile's own click handling runs on Main, so reading
 * the position first leaves the tap itself untouched.
 */
fun Modifier.trackPointerPosition(onPosition: (Offset) -> Unit): Modifier =
    this.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                event.changes.firstOrNull()?.let { change ->
                    if (change.pressed) onPosition(change.position)
                }
            }
        }
    }

/** Best-of-three metrics for each of [frames], indexed the same way. */
fun bestMetricSets(frames: List<ScanResult?>): List<Set<ScoreMetric>> =
    frames.indices.map { index ->
        ScoreMetric.entries
            .filter { metric ->
                metric.bestIndexOf(frames.map { r -> r?.let { metric.valueOf(it) } }) == index
            }
            .toSet()
    }

/**
 * Tap to act, long-press for the context menu.
 *
 * Falls back to a plain click when no long-press handler is supplied, so a
 * tile without a menu does not pay for gesture detection it will not use.
 */
@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.tileClickable(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
): Modifier = if (onLongClick == null) {
    this.clickable(onClick = onClick)
} else {
    this.combinedClickable(onClick = onClick, onLongClick = onLongClick)
}
