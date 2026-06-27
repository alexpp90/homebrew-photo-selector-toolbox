package com.phototok.ui.phonemode

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lens
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration
import coil.compose.SubcomposeAsyncImage
import com.phototok.data.model.ExifData
import com.phototok.data.model.ImageItem
import com.phototok.ui.theme.SuccessGreen
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Full-screen TikTok-style vertical-pager image viewer.
 *
 * - Swipe up/down → navigate images
 * - Double-tap → add to collection (green check flash)
 * - Swipe left → delete (card rotates, pulsing trash indicator)
 * - Single tap → toggle HUD overlay
 * - Gradient vignettes for readability
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
    showPageCounter: Boolean = true,
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
            delay(900)
            showCollectionFlash = false
        }
    }

    // HUD visibility toggle (single tap)
    var hudVisible by remember { mutableStateOf(true) }
    val hudAlpha by animateFloatAsState(
        targetValue = if (hudVisible) 1f else 0f,
        animationSpec = tween(300),
        label = "hud",
    )

    val colors = MaterialTheme.colorScheme

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
                showPageCounter = showPageCounter,
                hudAlpha = hudAlpha,
                onDoubleTap = {
                    onAddToCollection()
                    showCollectionFlash = true
                },
                onSingleTap = { hudVisible = !hudVisible },
                onSwipeLeftDelete = onRequestDelete,
            )
        }

        // ── Collection flash (centered green check with glow) ────────
        AnimatedVisibility(
            visible = showCollectionFlash,
            modifier = Modifier.align(Alignment.Center),
            enter = scaleIn(spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
            exit = scaleOut(tween(300)) + fadeOut(tween(300)),
        ) {
            Box(contentAlignment = Alignment.Center) {
                // Glow ring
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(SuccessGreen.copy(alpha = 0.15f))
                        .border(1.dp, SuccessGreen.copy(alpha = 0.3f), CircleShape),
                )
                // Icon
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Added to collection",
                    tint = SuccessGreen.copy(alpha = 0.9f),
                    modifier = Modifier
                        .size(72.dp)
                        .shadow(20.dp, CircleShape, ambientColor = SuccessGreen),
                )
            }
        }
    }
}

/**
 * A single page in the vertical pager.
 * Handles horizontal-drag (delete) and double-tap (collection) gestures.
 */
@Composable
private fun ImagePage(
    image: ImageItem,
    pageIndex: Int,
    totalCount: Int,
    isOrientationDivider: Boolean,
    showExifOverlay: Boolean,
    showPageCounter: Boolean,
    hudAlpha: Float,
    onDoubleTap: () -> Unit,
    onSingleTap: () -> Unit,
    onSwipeLeftDelete: () -> Unit,
) {
    var horizontalDragOffset by remember { mutableFloatStateOf(0f) }
    val deleteThreshold = -200f
    val swipeProgress = (abs(horizontalDragOffset) / abs(deleteThreshold)).coerceIn(0f, 1f)
    val isSwiping = horizontalDragOffset < -40f

    val colors = MaterialTheme.colorScheme

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { onDoubleTap() },
                    onTap = { onSingleTap() },
                )
            },
    ) {
        // ── Image with swipe-to-delete (rotation + translation) ──────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = horizontalDragOffset.coerceAtMost(0f)
                    rotationZ = -2f * swipeProgress
                    alpha = 1f - swipeProgress * 0.3f
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
            val configuration = LocalConfiguration.current
            val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            SubcomposeAsyncImage(
                model = parsedUri,
                contentDescription = image.fileName,
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (!isLandscape) {
                            Modifier.padding(top = 64.dp, bottom = 80.dp)
                        } else {
                            Modifier
                        }
                    ),
                contentScale = ContentScale.Fit,
                loading = {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        androidx.compose.material3.CircularProgressIndicator(
                            color = colors.primary
                        )
                    }
                }
            )
        }

        // ── Gradient vignettes for readability ───────────────────────
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.25f)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.33f)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)),
                    ),
                ),
        )

        // ── Delete indicator (pulsing trash with glow) ───────────────
        if (isSwiping) {
            val pulseTransition = rememberInfiniteTransition(label = "trash-pulse")
            val pulseScale by pulseTransition.animateFloat(
                initialValue = 1f,
                targetValue = 1.1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1000, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "trash-scale",
            )

            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    // Outer glow
                    Box(
                        modifier = Modifier
                            .size((96 * pulseScale).dp)
                            .clip(CircleShape)
                            .background(colors.errorContainer.copy(alpha = 0.3f)),
                    )
                    // Inner circle
                    Box(
                        modifier = Modifier
                            .size((64 + 16 * swipeProgress).dp)
                            .shadow(
                                elevation = (20 * swipeProgress).dp,
                                shape = CircleShape,
                                ambientColor = colors.error.copy(alpha = 0.4f),
                            )
                            .clip(CircleShape)
                            .background(colors.errorContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = colors.onErrorContainer,
                            modifier = Modifier.size((28 + 8 * swipeProgress).dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "DISCARD",
                    style = MaterialTheme.typography.labelLarge.copy(
                        letterSpacing = 3.sp,
                    ),
                    color = colors.error,
                )
            }
        }

        // ── Trash peek (subtle idle hint on right edge) ──────────────
        if (!isSwiping && hudAlpha > 0.5f) {
            val peekTransition = rememberInfiniteTransition(label = "trash-peek")
            val peekOffset by peekTransition.animateFloat(
                initialValue = 12f,
                targetValue = -4f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1500, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "peek-offset",
            )

            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = peekOffset.dp)
                    .graphicsLayer { alpha = hudAlpha * 0.4f }
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(colors.surfaceContainerHigh.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = colors.error,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        // ── Orientation section divider ──────────────────────────────
        if (isOrientationDivider) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 8.dp)
                    .graphicsLayer { alpha = hudAlpha },
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = colors.primaryContainer.copy(alpha = 0.85f),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.ScreenRotation,
                            contentDescription = null,
                            tint = colors.onPrimaryContainer,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = "Portrait photos — rotate your phone",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.onPrimaryContainer,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }

        // ── EXIF overlay (glassmorphic card, bottom-start) ───────────
        if (showExifOverlay && hudAlpha > 0f) {
            image.exifData?.let { exif ->
                ExifOverlay(
                    exif = exif,
                    fileName = image.fileName,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .navigationBarsPadding()
                        .padding(start = 16.dp, bottom = 100.dp) // above nav bar
                        .graphicsLayer { alpha = hudAlpha },
                )
            }
        }

        // ── Page counter (pill badge, bottom-end) ────────────────────
        if (showPageCounter && hudAlpha > 0f) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 16.dp, bottom = 100.dp)
                    .graphicsLayer { alpha = hudAlpha },
                shape = RoundedCornerShape(50),
                color = colors.surfaceContainerHigh.copy(alpha = 0.8f),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    colors.outline.copy(alpha = 0.2f),
                ),
            ) {
                Text(
                    text = "${pageIndex + 1} / $totalCount",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                )
            }
        }
    }
}

// ── Glassmorphic EXIF overlay ────────────────────────────────────────────

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

    val colors = MaterialTheme.colorScheme

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = colors.surfaceContainerLowest.copy(alpha = 0.5f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            colors.outlineVariant.copy(alpha = 0.3f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Filename
            Text(
                text = fileName.uppercase(),
                style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 1.sp),
                color = colors.onSurface.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            // Camera EXIF
            if (items.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoCamera,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = colors.primary,
                    )
                    Text(
                        text = items.joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Lens
            if (exif.lens != "Unknown") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Lens,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = colors.tertiary,
                    )
                    Text(
                        text = exif.lens,
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
