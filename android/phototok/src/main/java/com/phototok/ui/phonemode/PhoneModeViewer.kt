package com.phototok.ui.phonemode

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.phototok.data.model.ExifData
import com.phototok.data.model.ImageItem
import com.phototok.ui.theme.ErrorRed
import com.phototok.ui.theme.Indigo500
import com.phototok.ui.theme.SuccessGreen
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Full-screen TikTok-style vertical-pager image viewer.
 *
 * - Swipe up/down → navigate images
 * - Double-tap → add to collection
 * - Swipe left → delete
 * - EXIF overlay in bottom-start corner
 * - Orientation section divider between landscape & portrait groups
 */
@Composable
fun PhoneModeViewer(
    images: List<ImageItem>,
    currentIndex: Int,
    portraitSectionStart: Int,
    onNavigate: (Int) -> Unit,
    onAddToCollection: () -> Unit,
    onRequestDelete: () -> Unit,
    showExifOverlay: Boolean = true,
) {
    if (images.isEmpty()) return

    val pagerState = rememberPagerState(
        initialPage = currentIndex,
        pageCount = { images.size },
    )

    // Sync pager → ViewModel
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            onNavigate(page)
        }
    }

    // Sync ViewModel → pager (e.g. after delete shifts index)
    LaunchedEffect(currentIndex, images.size) {
        if (pagerState.currentPage != currentIndex && currentIndex in images.indices) {
            pagerState.scrollToPage(currentIndex)
        }
    }

    // Collection flash feedback
    var showCollectionFlash by remember { mutableStateOf(false) }
    LaunchedEffect(showCollectionFlash) {
        if (showCollectionFlash) {
            delay(700)
            showCollectionFlash = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        VerticalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            key = { images[it].uri },
            beyondViewportPageCount = 1,
        ) { page ->
            ImagePage(
                image = images[page],
                pageIndex = page,
                totalCount = images.size,
                isOrientationDivider = portraitSectionStart in 1 until images.size && page == portraitSectionStart,
                showExifOverlay = showExifOverlay,
                onDoubleTap = {
                    onAddToCollection()
                    showCollectionFlash = true
                },
                onSwipeLeftDelete = onRequestDelete,
            )
        }

        // Collection flash (centered checkmark)
        AnimatedVisibility(
            visible = showCollectionFlash,
            modifier = Modifier.align(Alignment.Center),
            enter = scaleIn(spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
            exit = scaleOut(tween(200)) + fadeOut(tween(200)),
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(SuccessGreen.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Added to collection",
                    tint = Color.White,
                    modifier = Modifier.size(44.dp),
                )
            }
        }
    }
}

/**
 * A single page inside the vertical pager.
 * Handles its own horizontal-drag (delete) and double-tap (collection) gestures,
 * so they don't conflict with the pager's vertical scroll.
 */
@Composable
private fun ImagePage(
    image: ImageItem,
    pageIndex: Int,
    totalCount: Int,
    isOrientationDivider: Boolean,
    showExifOverlay: Boolean,
    onDoubleTap: () -> Unit,
    onSwipeLeftDelete: () -> Unit,
) {
    // Per-page horizontal drag state (resets on page change)
    var horizontalDragOffset by remember { mutableFloatStateOf(0f) }
    val deleteThreshold = -200f

    Box(
        modifier = Modifier
            .fillMaxSize()
            // Double-tap detection — does not consume single taps or swipes
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { onDoubleTap() },
                )
            },
    ) {
        // Image with horizontal-drag-to-delete
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(horizontalDragOffset.roundToInt().coerceAtMost(0), 0) }
                .graphicsLayer {
                    val progress = (abs(horizontalDragOffset) / abs(deleteThreshold)).coerceIn(0f, 1f)
                    alpha = 1f - progress * 0.3f
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (horizontalDragOffset < deleteThreshold) {
                                onSwipeLeftDelete()
                            }
                            horizontalDragOffset = 0f
                        },
                        onDragCancel = { horizontalDragOffset = 0f },
                        onHorizontalDrag = { _, dragAmount ->
                            horizontalDragOffset = (horizontalDragOffset + dragAmount).coerceAtMost(0f)
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            val parsedUri = remember(image.uri) { android.net.Uri.parse(image.uri) }
            AsyncImage(
                model = parsedUri,
                contentDescription = image.fileName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }

        // Delete indicator (right edge, appears when swiping left)
        if (horizontalDragOffset < -40f) {
            val progress = (abs(horizontalDragOffset) / abs(deleteThreshold)).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 24.dp)
                    .size((48 + 16 * progress).dp)
                    .clip(CircleShape)
                    .background(ErrorRed.copy(alpha = 0.6f + 0.4f * progress)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = Color.White,
                    modifier = Modifier.size((24 + 8 * progress).dp),
                )
            }
        }

        // Orientation section divider card
        if (isOrientationDivider) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 8.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Indigo500.copy(alpha = 0.85f),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.ScreenRotation,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = "Portrait photos — rotate your phone",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }

        // EXIF overlay (bottom-start)
        if (showExifOverlay) {
            image.exifData?.let { exif ->
                ExifOverlay(
                    exif = exif,
                    fileName = image.fileName,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .navigationBarsPadding()
                        .padding(12.dp),
                )
            }
        }

        // Page counter (bottom-end)
        Surface(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(12.dp),
            shape = RoundedCornerShape(10.dp),
            color = Color.Black.copy(alpha = 0.5f),
        ) {
            Text(
                text = "${pageIndex + 1}/${totalCount}",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun ExifOverlay(
    exif: ExifData,
    fileName: String,
    modifier: Modifier = Modifier,
) {
    val items = buildList {
        exif.iso?.let { add("ISO $it") }
        exif.shutterSpeed?.let { speed ->
            val formatted = if (speed < 1.0 && speed > 0.0) "1/${(1.0 / speed).toInt()}s" else "${speed}s"
            add(formatted)
        }
        exif.aperture?.let { add("f/%.1f".format(java.util.Locale.US, it)) }
        exif.focalLength?.let { add("${it.toInt()}mm") }
    }
    if (items.isEmpty() && exif.lens == "Unknown") return

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = Color.Black.copy(alpha = 0.55f),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = fileName,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (items.isNotEmpty()) {
                Text(
                    text = items.joinToString("  ·  "),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.9f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (exif.lens != "Unknown") {
                Text(
                    text = exif.lens,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
