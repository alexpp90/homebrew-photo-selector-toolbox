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
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.WbShade
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.AsyncImage
import com.photoselectortoolbox.data.model.ImageItem
import com.photoselectortoolbox.ui.components.ScoreChip
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

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
) {
    val pagerState = rememberPagerState(
        initialPage = initialIndex,
        pageCount = { images.size },
    )

    val coroutineScope = rememberCoroutineScope()
    var pagerScrollEnabled by remember { mutableStateOf(true) }
    var showOverlay by remember { mutableStateOf(true) }
    var currentPageIndex by remember { mutableStateOf(initialIndex) }

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
            .background(Color.Black)
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
                onTap = { showOverlay = !showOverlay },
                onZoomChanged = { isZoomed ->
                    pagerScrollEnabled = !isZoomed
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
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Top-right action buttons
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (fullscreenButtonsEnabled) {
                        FilledTonalIconButton(
                            onClick = { onDelete(currentPageIndex) },
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = Color.Black.copy(alpha = 0.6f),
                                contentColor = Color.White,
                            ),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }

                        FilledTonalIconButton(
                            onClick = { onMoveToSelection(currentPageIndex) },
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = Color.Black.copy(alpha = 0.6f),
                                contentColor = Color.White,
                            ),
                        ) {
                            Icon(
                                imageVector = Icons.Default.DriveFileMove,
                                contentDescription = "Move to Selection",
                            )
                        }

                        FilledTonalIconButton(
                            onClick = { onCopyToSelection(currentPageIndex) },
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = Color.Black.copy(alpha = 0.6f),
                                contentColor = Color.White,
                            ),
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy to Selection",
                            )
                        }
                    }

                    FilledTonalIconButton(
                        onClick = onDismiss,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = Color.Black.copy(alpha = 0.6f),
                            contentColor = Color.White,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                        )
                    }
                }

                // Bottom-left metadata overlay
                currentImage?.let { image ->
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .navigationBarsPadding()
                            .padding(12.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Black.copy(alpha = 0.65f),
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            // Filename
                            Text(
                                text = image.fileName,
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )

                            // EXIF metadata
                            image.exifData?.let { exif ->
                                val metadataItems = buildList {
                                    exif.iso?.let { add("ISO $it") }
                                    exif.shutterSpeed?.let { speed ->
                                        val formatted = if (speed < 1.0 && speed > 0.0) {
                                            "1/${(1.0 / speed).toInt()}s"
                                        } else "${speed}s"
                                        add(formatted)
                                    }
                                    exif.aperture?.let { add("f/%.1f".format(java.util.Locale.US, it)) }
                                    exif.focalLength?.let { add("${it.toInt()}mm") }
                                    if (exif.lens != "Unknown") add(exif.lens)
                                }

                                if (metadataItems.isNotEmpty()) {
                                    Text(
                                        text = metadataItems.joinToString(" | "),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.8f),
                                        maxLines = if (isExpanded) 2 else 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }

                            // Score chips
                            image.scanResult?.let { scores ->
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    ScoreChip(
                                        icon = Icons.Default.CenterFocusStrong,
                                        label = "Sharpness",
                                        value = scores.sharpnessScore,
                                    )
                                    ScoreChip(
                                        icon = Icons.Default.Grain,
                                        label = "Noise",
                                        value = scores.noiseLevel,
                                    )
                                    ScoreChip(
                                        icon = Icons.Default.Highlight,
                                        label = "Highlight",
                                        value = scores.highlightClipping,
                                        format = "%.1f%%",
                                    )
                                    ScoreChip(
                                        icon = Icons.Default.WbShade,
                                        label = "Shadow",
                                        value = scores.shadowClipping,
                                        format = "%.1f%%",
                                    )
                                }
                            }
                        }
                    }
                }

                // Page indicator
                Text(
                    text = "${currentPageIndex + 1} / ${images.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 16.dp)
                        .background(
                            Color.Black.copy(alpha = 0.5f),
                            RoundedCornerShape(12.dp),
                        )
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
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

