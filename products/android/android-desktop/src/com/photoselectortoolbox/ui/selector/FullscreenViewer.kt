package com.photoselectortoolbox.ui.selector

import android.app.Activity
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.AsyncImage
import com.photoselectortoolbox.data.model.ImageItem
import com.photoselectortoolbox.domain.format.ExifFormatter
import com.photoselectortoolbox.ui.components.ScoreChipRow
import com.photoselectortoolbox.ui.theme.Indigo200
import com.photoselectortoolbox.ui.theme.Indigo500
import com.photoselectortoolbox.ui.theme.ScoreBad
import com.photoselectortoolbox.ui.theme.Zinc400
import com.photoselectortoolbox.ui.theme.Zinc50
import com.photoselectortoolbox.ui.theme.Zinc700
import com.photoselectortoolbox.ui.theme.Zinc800
import com.photoselectortoolbox.ui.theme.Zinc900
import com.photoselectortoolbox.ui.theme.Zinc950
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FullscreenViewer(
    images: List<ImageItem>,
    initialIndex: Int,
    onDismiss: () -> Unit,
    onDelete: (Int) -> Unit,
    onMoveToSelection: (Int) -> Unit,
    onCopyToSelection: (Int) -> Unit,
    windowSizeClass: WindowSizeClass,
    onPageSelected: (Int) -> Unit = {},
    fullscreenButtonsEnabled: Boolean = true,
    fullscreenGestureAction: String = "copy",
    showGestureHint: Boolean = false,
    onGestureHintSeen: () -> Unit = {},
) {
    val isExpanded = windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact

    // Immersive mode: hide system bars
    val view = LocalView.current
    DisposableEffect(Unit) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let {
            WindowCompat.getInsetsController(it, view)
        }

        controller?.let {
            it.hide(WindowInsetsCompat.Type.systemBars())
            it.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        FullscreenContent(
            images = images,
            initialIndex = initialIndex,
            isExpanded = isExpanded,
            onDismiss = onDismiss,
            onDelete = onDelete,
            onMoveToSelection = onMoveToSelection,
            onCopyToSelection = onCopyToSelection,
            onPageSelected = onPageSelected,
            fullscreenButtonsEnabled = fullscreenButtonsEnabled,
            fullscreenGestureAction = fullscreenGestureAction,
            showGestureHint = showGestureHint,
            onGestureHintSeen = onGestureHintSeen,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FullscreenContent(
    images: List<ImageItem>,
    initialIndex: Int,
    isExpanded: Boolean,
    onDismiss: () -> Unit,
    onDelete: (Int) -> Unit,
    onMoveToSelection: (Int) -> Unit,
    onCopyToSelection: (Int) -> Unit,
    onPageSelected: (Int) -> Unit,
    fullscreenButtonsEnabled: Boolean,
    fullscreenGestureAction: String,
    showGestureHint: Boolean = false,
    onGestureHintSeen: () -> Unit = {},
) {
    val pagerState = rememberPagerState(
        initialPage = initialIndex,
        pageCount = { images.size },
    )

    val coroutineScope = rememberCoroutineScope()
    var pagerScrollEnabled by remember { mutableStateOf(true) }
    var showOverlay by remember { mutableStateOf(true) }
    var currentPageIndex by remember { mutableStateOf(initialIndex) }
    var zoomedIn by remember { mutableStateOf(false) }

    // Chrome gets out of the way on its own. In fullscreen the user is
    // inspecting detail — a bar that stays put is a bar sitting on the part of
    // the frame they are trying to see. Any interaction brings it straight
    // back, so nothing is ever unreachable.
    var lastInteractionAt by remember { mutableStateOf(0L) }
    LaunchedEffect(showOverlay, lastInteractionAt) {
        if (showOverlay) {
            delay(CHROME_AUTO_HIDE_MILLIS)
            showOverlay = false
        }
    }

    // Track settled page and sync back to caller/viewmodel
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            currentPageIndex = page
            onPageSelected(page)
        }
    }

    // Sync from viewmodel changes (like deletion or key events shifting the index)
    LaunchedEffect(initialIndex) {
        if (pagerState.currentPage != initialIndex && initialIndex in images.indices) {
            pagerState.scrollToPage(initialIndex)
        }
    }

    val currentImage = images.getOrNull(currentPageIndex)

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
            // Near-black rather than pure black: a true #000 canvas makes deep
            // shadows in the photograph look lifted by comparison, which is
            // exactly the judgement the viewer exists to support.
            .background(Zinc950)
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.Escape -> { onDismiss(); true }
                        Key.Delete, Key.Backspace -> { onDelete(currentPageIndex); true }
                        Key.M -> { onMoveToSelection(currentPageIndex); true }
                        Key.C -> { onCopyToSelection(currentPageIndex); true }
                        Key.DirectionUp -> {
                            if (currentPageIndex > 0) {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(currentPageIndex - 1)
                                }
                            }
                            true
                        }
                        Key.DirectionDown -> {
                            if (currentPageIndex < images.size - 1) {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(currentPageIndex + 1)
                                }
                            }
                            true
                        }
                        else -> false
                    }
                } else false
            },
    ) {
        // Vertical Image pager with zoom & swipe support
        VerticalPager(
            state = pagerState,
            userScrollEnabled = pagerScrollEnabled,
            modifier = Modifier.fillMaxSize(),
            key = { images[it].uri },
            beyondViewportPageCount = 1,
        ) { page ->
            FullscreenImagePage(
                image = images[page],
                showOverlay = showOverlay,
                onTap = {
                    showOverlay = !showOverlay
                    lastInteractionAt = System.currentTimeMillis()
                },
                onZoomChanged = { isZoomed ->
                    pagerScrollEnabled = !isZoomed
                    zoomedIn = isZoomed
                },
                onDelete = { onDelete(page) },
                onDismiss = onDismiss,
                onDoubleTap = {
                    if (fullscreenGestureAction == "copy") {
                        onCopyToSelection(page)
                    } else {
                        onMoveToSelection(page)
                    }
                    showCollectionFlash = true
                },
                isGestureEnabled = pagerScrollEnabled, // only enable swipes when not zoomed
            )
        }

        // Overlays (animated visibility)
        AnimatedVisibility(
            visible = showOverlay,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200)),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                FullscreenTopBar(
                    image = currentImage,
                    position = currentPageIndex + 1,
                    total = images.size,
                    onClose = onDismiss,
                    modifier = Modifier.align(Alignment.TopStart),
                )

                // Compact chips: fullscreen is for judging the photograph, so
                // the scores get the smallest footprint that still carries the
                // bar. The labels are recoverable from the legend.
                currentImage?.scanResult?.let { scores ->
                    ScoreChipRow(
                        scores = scores,
                        compact = true,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .navigationBarsPadding()
                            .padding(12.dp),
                    )
                }

                if (fullscreenButtonsEnabled) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FullscreenActionButton(
                            icon = Icons.AutoMirrored.Filled.DriveFileMove,
                            description = "Move to Selection",
                            onClick = { onMoveToSelection(currentPageIndex) },
                        )
                        FullscreenActionButton(
                            icon = Icons.Default.ContentCopy,
                            description = "Copy to Selection",
                            onClick = { onCopyToSelection(currentPageIndex) },
                        )
                        FullscreenActionButton(
                            icon = Icons.Default.Delete,
                            description = "Delete",
                            tint = ScoreBad,
                            onClick = { onDelete(currentPageIndex) },
                        )
                    }
                }
            }
        }

        // Navigator: only meaningful while zoomed, when the visible part of the
        // frame is no longer the whole frame.
        if (zoomedIn) {
            ZoomNavigator(
                image = currentImage,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(16.dp),
            )
        }

        if (showGestureHint) {
            FullscreenGestureHint(
                onDismiss = onGestureHintSeen,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 64.dp, end = 16.dp),
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
                    .background(com.photoselectortoolbox.ui.theme.SuccessGreen.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Added to selection",
                    tint = Color.White,
                    modifier = Modifier.size(44.dp),
                )
            }
        }
    }
}

/** How long the chrome waits before fading out. */
private const val CHROME_AUTO_HIDE_MILLIS = 3_000L

/**
 * The fullscreen top bar: a bar, deliberately, not a gradient scrim.
 *
 * A gradient over the top of the frame darkens the sky in a way the
 * photographer will read as part of the photograph. A hard-edged bar with a
 * visible bottom border does not lie about where the image starts.
 */
@Composable
private fun FullscreenTopBar(
    image: ImageItem?,
    position: Int,
    total: Int,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Zinc900.copy(alpha = 0.9f))
                .statusBarsPadding()
                .height(52.dp)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onClose,
                modifier = Modifier.size(44.dp).testTag("fullscreen_close"),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close fullscreen",
                    tint = Zinc50,
                )
            }

            Text(
                text = image?.fileName.orEmpty(),
                fontSize = 14.sp,
                color = Zinc50,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )

            Text(
                text = "$position / $total",
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                color = Zinc400,
            )

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(22.dp)
                    .background(Zinc700),
            )

            val exifLine = ExifFormatter.summaryLine(image?.exifData)
            val lens = image?.exifData?.lens?.takeIf { it != ExifFormatter.UNKNOWN }
            if (exifLine != null || lens != null) {
                Text(
                    text = listOfNotNull(exifLine, lens).joinToString("  ·  "),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Zinc400,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.weight(1f))
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Zinc800),
        )
    }
}

@Composable
private fun FullscreenActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
    tint: Color = Zinc50,
) {
    FilledTonalIconButton(
        onClick = onClick,
        modifier = Modifier.size(48.dp),
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = Zinc900.copy(alpha = 0.9f),
            contentColor = tint,
        ),
    ) {
        Icon(imageVector = icon, contentDescription = description, tint = tint)
    }
}

/**
 * A thumbnail of the whole frame with the visible region outlined, shown while
 * zoomed so the user knows which part of the photograph they are looking at.
 */
@Composable
private fun ZoomNavigator(
    image: ImageItem?,
    modifier: Modifier = Modifier,
) {
    if (image == null) return

    Box(
        modifier = modifier
            .width(104.dp)
            .height(78.dp)
            .background(Zinc900.copy(alpha = 0.9f))
            .border(1.dp, Zinc700)
            .testTag("zoom_navigator"),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = image.uri,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
        )
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .width(48.dp)
                .height(36.dp)
                .border(1.dp, Indigo500),
        )
    }
}

/**
 * The one-time gesture card.
 *
 * Fullscreen has no visible controls for pinch, double-tap or swipe, so the
 * gestures have to be stated once. After "Got it" the flag is persisted and
 * the card never returns — a permanent legend over a photograph would defeat
 * the purpose of fullscreen.
 */
@Composable
private fun FullscreenGestureHint(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(270.dp)
            .background(Zinc800, RoundedCornerShape(10.dp))
            .border(1.dp, Zinc700, RoundedCornerShape(10.dp))
            .padding(14.dp)
            .testTag("fullscreen_gesture_hint"),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        listOf(
            "pinch" to "zoom",
            "double-tap" to "fit ↔ 100%",
            "swipe ← →" to "navigate",
            "swipe down" to "dismiss",
            "Esc" to "exit",
        ).forEach { (gesture, effect) ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = gesture,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Zinc400,
                )
                Text(text = effect, fontSize = 12.sp, color = Zinc50)
            }
        }

        Text(
            text = "Got it",
            fontSize = 12.sp,
            color = Indigo200,
            modifier = Modifier
                .align(Alignment.End)
                .clickable(onClick = onDismiss)
                .padding(top = 4.dp),
        )
    }
}

@Composable
private fun FullscreenImagePage(
    image: ImageItem,
    showOverlay: Boolean,
    onTap: () -> Unit,
    onZoomChanged: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    onDoubleTap: () -> Unit,
    isGestureEnabled: Boolean,
) {
    var horizontalDragOffset by remember { mutableFloatStateOf(0f) }
    var isZoomed by remember { mutableStateOf(false) }
    val deleteThreshold = -200f
    val dismissThreshold = 200f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(isGestureEnabled, isZoomed) {
                if (isGestureEnabled && !isZoomed) {
                    detectTapGestures(
                        onTap = { onTap() },
                        onDoubleTap = { onDoubleTap() }
                    )
                }
            }
    ) {
        // Horizontal offset and alpha applied to image container
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(horizontalDragOffset.roundToInt(), 0) }
                .graphicsLayer {
                    if (isGestureEnabled && !isZoomed) {
                        alpha = (1f - (abs(horizontalDragOffset) / 400f)).coerceIn(0.2f, 1f)
                    }
                }
                .pointerInput(isGestureEnabled, isZoomed) {
                    if (isGestureEnabled && !isZoomed) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                if (horizontalDragOffset < deleteThreshold) {
                                    onDelete()
                                } else if (horizontalDragOffset > dismissThreshold) {
                                    onDismiss()
                                }
                                horizontalDragOffset = 0f
                            },
                            onDragCancel = { horizontalDragOffset = 0f },
                            onHorizontalDrag = { _, dragAmount ->
                                horizontalDragOffset += dragAmount
                            }
                        )
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            val parsedUri = remember(image.uri) { Uri.parse(image.uri) }
            ZoomableImage(
                imageUri = parsedUri.toString(),
                contentDescription = image.fileName,
                onTap = { onTap() },
                onZoomChanged = { zoomed ->
                    isZoomed = zoomed
                    onZoomChanged(zoomed)
                }
            )
        }

        // Delete indicator (right edge, appears when swiping left)
        if (isGestureEnabled && !isZoomed && horizontalDragOffset < -40f) {
            val progress = (abs(horizontalDragOffset) / abs(deleteThreshold)).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 24.dp)
                    .size((48 + 16 * progress).dp)
                    .clip(CircleShape)
                    .background(com.photoselectortoolbox.ui.theme.ErrorRed.copy(alpha = 0.6f + 0.4f * progress)),
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
    }
}

@Composable
private fun ZoomableImage(
    imageUri: String,
    contentDescription: String,
    onTap: () -> Unit,
    onZoomChanged: (Boolean) -> Unit,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var isZoomed by remember { mutableStateOf(false) }

    LaunchedEffect(isZoomed) {
        onZoomChanged(isZoomed)
    }

    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        val newScale = (scale * zoomChange).coerceIn(1f, 5f)
        val newOffset = if (newScale > 1f) {
            Offset(
                x = offset.x + panChange.x,
                y = offset.y + panChange.y,
            )
        } else {
            Offset.Zero
        }

        scale = newScale
        offset = newOffset
        isZoomed = newScale > 1.05f
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        if (!isZoomed) {
                            onTap()
                        }
                    },
                    onDoubleTap = {
                        if (isZoomed) {
                            // Reset to fit
                            scale = 1f
                            offset = Offset.Zero
                            isZoomed = false
                        } else {
                            // Zoom to 2.5x for usability
                            scale = 2.5f
                            offset = Offset.Zero
                            isZoomed = true
                        }
                    },
                )
            }
            .transformable(state = transformState, enabled = scale > 1f),
        contentAlignment = Alignment.Center,
    ) {
        val parsedUri = remember(imageUri) { Uri.parse(imageUri) }
        AsyncImage(
            model = parsedUri,
            contentDescription = contentDescription,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
            contentScale = ContentScale.Fit,
        )
    }
}

