package com.photoselectortoolbox.ui.selector

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import coil.compose.AsyncImage
import com.photoselectortoolbox.data.model.ImageItem
import com.photoselectortoolbox.domain.format.SelectorLabels
import com.photoselectortoolbox.domain.scoring.ScoreMetric
import com.photoselectortoolbox.ui.components.goodnessColor
import com.photoselectortoolbox.ui.theme.Indigo500
import com.photoselectortoolbox.ui.theme.Indigo600
import com.photoselectortoolbox.ui.theme.Zinc500
import com.photoselectortoolbox.ui.theme.Zinc700
import com.photoselectortoolbox.ui.theme.Zinc800
import com.photoselectortoolbox.ui.theme.Zinc900

/** Total height of the filmstrip, including its border and padding. */
val FilmstripHeight = 76.dp

private val ThumbnailHeight = 56.dp
private val LandscapeThumbnailWidth = 78.dp
private val PortraitThumbnailWidth = 44.dp

/** Extra space that separates one burst from the next. */
private val BurstGap = 10.dp

/**
 * The filmstrip along the bottom edge: where the current frame sits in the
 * folder, and roughly how good its neighbours are.
 *
 * Thumbnails carry a single score dot rather than text. At 56dp there is no
 * room for a legible number, and the question the strip answers is "is there
 * anything better nearby", which a colour answers faster than digits. Sharpness
 * is the metric shown because it is the one that most often decides a cull.
 */
@Composable
fun CandidateStrip(
    images: List<ImageItem>,
    currentIndex: Int,
    onImageSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    groups: List<List<Int>>? = null,
) {
    val listState = rememberLazyListState()

    // Index -> burst id, so a thumbnail knows both which burst it belongs to
    // and whether it starts a new one.
    val indexToGroup = remember(groups) {
        if (groups == null) {
            emptyMap()
        } else {
            buildMap {
                groups.forEachIndexed { groupIdx, memberIndices ->
                    memberIndices.forEach { imageIdx -> put(imageIdx, groupIdx) }
                }
            }
        }
    }
    val currentGroup = indexToGroup[currentIndex]

    // Keep the current frame visible without yanking the strip about: scrolling
    // to a fixed offset puts it left-of-centre, where the frames the user is
    // about to reach are still on screen.
    LaunchedEffect(currentIndex) {
        if (images.isNotEmpty() && currentIndex in images.indices) {
            listState.animateScrollToItem(index = currentIndex, scrollOffset = -200)
        }
    }

    val visibleRange by remember {
        derivedStateOf {
            val info = listState.layoutInfo.visibleItemsInfo
            if (info.isEmpty()) null else info.first().index to info.last().index
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(FilmstripHeight)
            .background(Zinc900)
            .testTag("filmstrip"),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Zinc800)
                .align(Alignment.TopStart),
        )

        LazyRow(
            modifier = Modifier.fillMaxWidth().align(Alignment.CenterStart),
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            contentPadding = PaddingValues(horizontal = 8.dp),
        ) {
            itemsIndexed(
                items = images,
                key = { _, image -> image.uri },
            ) { index, image ->
                val group = indexToGroup[index]
                val startsNewBurst = group != null && group != indexToGroup[index - 1]

                CandidateThumbnail(
                    image = image,
                    isCurrent = index == currentIndex,
                    inCurrentBurst = group != null && group == currentGroup,
                    position = index + 1,
                    total = images.size,
                    onClick = { onImageSelected(index) },
                    modifier = Modifier.padding(
                        start = if (startsNewBurst) BurstGap else 0.dp,
                    ),
                )
            }
        }

        visibleRange?.let { (first, last) ->
            Text(
                text = SelectorLabels.filmstripRange(first, last, images.size),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = Zinc500,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .background(Zinc900)
                    .padding(horizontal = 8.dp),
            )
        }
    }
}

@Composable
private fun CandidateThumbnail(
    image: ImageItem,
    isCurrent: Boolean,
    inCurrentBurst: Boolean,
    position: Int,
    total: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(3.dp)
    val width = if (image.isLandscape) LandscapeThumbnailWidth else PortraitThumbnailWidth

    Box(
        modifier = modifier
            .height(ThumbnailHeight + if (inCurrentBurst) 4.dp else 0.dp)
            .width(width),
        contentAlignment = Alignment.TopStart,
    ) {
        Box(
            modifier = Modifier
                .height(ThumbnailHeight)
                .width(width)
                // Neighbours are held back a little so the current frame reads
                // first, but never so far that their content is unreadable.
                .alpha(if (isCurrent) 1f else 0.85f)
                .clip(shape)
                .background(Zinc800)
                .border(
                    width = if (isCurrent) 2.dp else 1.dp,
                    color = if (isCurrent) Indigo500 else Zinc700,
                    shape = shape,
                )
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable(onClick = onClick)
                .semantics { contentDescription = "${image.fileName}, $position of $total" },
        ) {
            AsyncImage(
                model = image.uri,
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
            )

            image.scanResult?.sharpnessScore?.let { sharpness ->
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(3.dp)
                        .size(6.dp)
                        .background(
                            goodnessColor(ScoreMetric.SHARPNESS.goodness(sharpness)),
                            CircleShape,
                        ),
                )
            }
        }

        // Burst underline: the frames that belong to the same series as the
        // current one, so a photographer can see the shape of the burst they
        // are inside without reading filenames.
        if (inCurrentBurst) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .width(width)
                    .height(2.dp)
                    .background(Indigo600),
            )
        }
    }
}

/** Kept for callers that only need the dot colour. */
internal fun sharpnessDotColor(sharpness: Double): Color =
    goodnessColor(ScoreMetric.SHARPNESS.goodness(sharpness))
