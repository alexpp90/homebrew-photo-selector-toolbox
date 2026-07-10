package com.photoselectortoolbox.ui.selector

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.photoselectortoolbox.data.model.ImageItem
import com.photoselectortoolbox.ui.theme.Indigo500
import com.photoselectortoolbox.ui.theme.Zinc700
import com.photoselectortoolbox.ui.theme.Zinc800
import com.photoselectortoolbox.ui.theme.Zinc950

// Group indicator colors for visual differentiation
private val GroupColors = listOf(
    Color(0xFF8B5CF6), // Violet
    Color(0xFF22C55E), // Green
    Color(0xFFF59E0B), // Amber
    Color(0xFFEC4899), // Pink
    Color(0xFF06B6D4), // Cyan
    Color(0xFFA855F7), // Purple
    Color(0xFFF97316), // Orange
    Color(0xFF14B8A6), // Teal
)

@Composable
fun CandidateStrip(
    images: List<ImageItem>,
    currentIndex: Int,
    onImageSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    groups: List<List<Int>>? = null,
) {
    val listState = rememberLazyListState()

    // Build a map from image index to group index for fast lookup
    val indexToGroup = remember(groups) {
        if (groups == null) {
            emptyMap()
        } else {
            buildMap {
                groups.forEachIndexed { groupIdx, memberIndices ->
                    memberIndices.forEach { imageIdx ->
                        put(imageIdx, groupIdx)
                    }
                }
            }
        }
    }

    // Auto-scroll to keep the current image visible
    LaunchedEffect(currentIndex) {
        if (images.isNotEmpty() && currentIndex in images.indices) {
            listState.animateScrollToItem(
                index = currentIndex,
                scrollOffset = -200, // offset to center roughly
            )
        }
    }

    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .background(Zinc950)
            .padding(vertical = 6.dp),
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        contentPadding = PaddingValues(horizontal = 12.dp),
    ) {
        itemsIndexed(
            items = images,
            key = { _, image -> image.uri },
        ) { index, image ->
            val isCurrent = index == currentIndex
            val groupIndex = indexToGroup[index]
            val groupColor = groupIndex?.let {
                GroupColors[it % GroupColors.size]
            }

            CandidateThumbnail(
                image = image,
                isCurrent = isCurrent,
                groupColor = groupColor,
                onClick = { onImageSelected(index) },
            )
        }
    }
}

@Composable
private fun CandidateThumbnail(
    image: ImageItem,
    isCurrent: Boolean,
    groupColor: Color?,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .height(64.dp)
                .aspectRatio(1.5f)
                .clip(RoundedCornerShape(6.dp))
                .then(
                    if (isCurrent) {
                        Modifier.border(
                            width = 3.dp,
                            color = Indigo500,
                            shape = RoundedCornerShape(6.dp),
                        )
                    } else Modifier
                )
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable(onClick = onClick),
        ) {
            // Group color indicator (left border)
            if (groupColor != null) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(64.dp)
                        .background(groupColor)
                        .align(Alignment.CenterStart),
                )
            }

            AsyncImage(
                model = image.uri,
                contentDescription = image.fileName,
                modifier = Modifier
                    .matchParentSize()
                    .padding(start = if (groupColor != null) 3.dp else 0.dp),
                contentScale = ContentScale.Crop,
            )
        }

        // Tiny score icons below thumbnail
        image.scanResult?.let { scores ->
            Row(
                modifier = Modifier.padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (scores.sharpnessScore != null) {
                    Icon(
                        imageVector = Icons.Default.CenterFocusStrong,
                        contentDescription = null,
                        modifier = Modifier.size(10.dp),
                        tint = scoreColor(scores.sharpnessScore, isHighGood = true),
                    )
                }
                if (scores.noiseLevel != null) {
                    Icon(
                        imageVector = Icons.Default.Grain,
                        contentDescription = null,
                        modifier = Modifier.size(10.dp),
                        tint = scoreColor(scores.noiseLevel, isHighGood = false),
                    )
                }
                if (scores.highlightClipping != null) {
                    Icon(
                        imageVector = Icons.Default.Highlight,
                        contentDescription = null,
                        modifier = Modifier.size(10.dp),
                        tint = scoreColor(scores.highlightClipping, isHighGood = false),
                    )
                }
            }
        }
    }
}

/**
 * Returns a color indicating score quality.
 * For sharpness, higher is better (green). For noise / clipping, lower is better.
 */
private fun scoreColor(value: Double, isHighGood: Boolean): Color {
    val normalized = value.coerceIn(0.0, 100.0) / 100.0
    val quality = if (isHighGood) normalized else 1.0 - normalized

    return when {
        quality >= 0.7 -> Color(0xFF22C55E) // Green
        quality >= 0.4 -> Color(0xFFF59E0B) // Amber
        else -> Color(0xFFEF4444)           // Red
    }
}
