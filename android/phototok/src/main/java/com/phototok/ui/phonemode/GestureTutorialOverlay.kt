package com.phototok.ui.phonemode

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phototok.domain.CollectionAction
import com.phototok.domain.SwipeAction

/**
 * Full-screen guide to every Photo-Tok control.
 *
 * Serves two entry points:
 *  - the first-launch tutorial (shown automatically), and
 *  - the info button in the viewer, which opens the same guide on demand.
 *
 * The swipe entries are described using the user's *configured* actions rather
 * than the defaults, so the guide never claims a left swipe deletes when the
 * user has set it to copy.
 */
@Composable
fun GestureTutorialOverlay(
    visible: Boolean,
    onDismiss: () -> Unit,
    leftSwipeAction: SwipeAction = SwipeAction.DEFAULT,
    collectionAction: CollectionAction = CollectionAction.DEFAULT,
    leftSwipeFolderName: String = "",
    collectionFolderName: String = "",
    title: String = "How to Photo-Tok",
    subtitle: String = "Master the curation flow with these simple gestures",
    dismissLabel: String = "GOT IT",
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(400)),
        exit = fadeOut(tween(250)),
    ) {
        val colors = MaterialTheme.colorScheme
        val entries = rememberControlEntries(
            leftSwipeAction = leftSwipeAction,
            collectionAction = collectionAction,
            leftSwipeFolderName = leftSwipeFolderName,
            collectionFolderName = collectionFolderName,
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF09090B).copy(alpha = 0.94f))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) { /* absorb taps */ }
                .testTag("controls_guide"),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp)
                    .padding(top = 16.dp, bottom = 24.dp),
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                // ── Header ───────────────────────────────────────────
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    modifier = Modifier.padding(start = 4.dp),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp),
                )

                Spacer(modifier = Modifier.height(20.dp))

                // ── Control list ─────────────────────────────────────
                val configuration = androidx.compose.ui.platform.LocalConfiguration.current
                val isLandscape =
                    configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                val columns = if (isLandscape) 2 else 1

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    entries.chunked(columns).forEach { rowEntries ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            rowEntries.forEach { entry ->
                                ControlCard(entry = entry, modifier = Modifier.weight(1f))
                            }
                            // Keep the last odd card from stretching across the row.
                            repeat(columns - rowEntries.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // ── Sticky bottom dismiss button with gradient ───────
                Box(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .align(Alignment.TopCenter)
                            .offset(y = (-48).dp)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color(0xFF09090B)),
                                ),
                            ),
                    )

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onDismiss() },
                        shape = RoundedCornerShape(12.dp),
                        color = colors.primaryContainer,
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            Text(
                                text = dismissLabel,
                                style = MaterialTheme.typography.labelLarge.copy(
                                    letterSpacing = 3.sp,
                                ),
                                color = colors.onPrimaryContainer,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Control entries ──────────────────────────────────────────────────────

private enum class GestureAnimation { NONE, VERTICAL, DOUBLE_TAP, SWIPE_LEFT, SWIPE_RIGHT }

private data class ControlEntry(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val animation: GestureAnimation = GestureAnimation.NONE,
    val animDelay: Int = 0,
    val isDestructive: Boolean = false,
)

@Composable
private fun rememberControlEntries(
    leftSwipeAction: SwipeAction,
    collectionAction: CollectionAction,
    leftSwipeFolderName: String,
    collectionFolderName: String,
): List<ControlEntry> {
    val collectionVerb = if (collectionAction == CollectionAction.COPY) "Copy" else "Move"
    val collectionTarget = collectionFolderName.ifBlank { "your collection" }

    val leftIcon = when (leftSwipeAction) {
        SwipeAction.DELETE -> Icons.Default.Delete
        SwipeAction.COPY -> Icons.Default.ContentCopy
        SwipeAction.MOVE -> Icons.AutoMirrored.Filled.DriveFileMove
    }
    val leftDescription = when (leftSwipeAction) {
        SwipeAction.DELETE -> "Send to trash — undo it with Revert"
        SwipeAction.COPY -> "Copy to ${leftSwipeFolderName.ifBlank { "your chosen folder" }}"
        SwipeAction.MOVE -> "Move to ${leftSwipeFolderName.ifBlank { "your chosen folder" }}"
    }

    return listOf(
        ControlEntry(
            icon = Icons.Default.SwapVert,
            title = "Swipe up / down",
            description = "Move through your photos",
            animation = GestureAnimation.VERTICAL,
        ),
        ControlEntry(
            icon = Icons.AutoMirrored.Filled.ArrowForward,
            title = "Swipe right",
            description = "$collectionVerb to $collectionTarget",
            animation = GestureAnimation.SWIPE_RIGHT,
            animDelay = 400,
        ),
        ControlEntry(
            icon = leftIcon,
            title = "Swipe left",
            description = leftDescription,
            animation = GestureAnimation.SWIPE_LEFT,
            animDelay = 800,
            isDestructive = leftSwipeAction == SwipeAction.DELETE,
        ),
        ControlEntry(
            icon = Icons.Default.TouchApp,
            title = "Single tap",
            description = "Hide or show the on-screen info",
        ),
        ControlEntry(
            icon = Icons.Default.ZoomIn,
            title = "Double tap or pinch",
            description = "Zoom in on the detail, drag to pan",
            animation = GestureAnimation.DOUBLE_TAP,
        ),
        ControlEntry(
            icon = Icons.Default.PhotoCamera,
            title = "Tap the logo",
            description = "Show or hide the camera settings overlay",
        ),
        ControlEntry(
            icon = Icons.Default.Star,
            title = "Selection",
            description = "Browse everything you have kept",
        ),
        ControlEntry(
            icon = Icons.AutoMirrored.Filled.Undo,
            title = "Revert",
            description = "Bring back the photo you just deleted",
        ),
        ControlEntry(
            icon = Icons.Default.FolderOpen,
            title = "Sources",
            description = "Go back and pick another folder",
        ),
        ControlEntry(
            icon = Icons.Default.Settings,
            title = "Settings",
            description = "Change swipe actions, sorting and filters",
        ),
    )
}

@Composable
private fun ControlCard(
    entry: ControlEntry,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val tint = if (entry.isDestructive) colors.error else colors.primary

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.Transparent,
        modifier = modifier
            .fillMaxWidth()
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        colors.surfaceContainerHigh.copy(alpha = 0.5f),
                        colors.surfaceContainerLow.copy(alpha = 0.8f),
                    ),
                ),
                shape = RoundedCornerShape(12.dp),
            )
            .border(1.dp, colors.outlineVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = 0.1f))
                    .border(1.dp, tint.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedGestureIcon(entry.animation, entry.icon, tint, entry.animDelay)
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = entry.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AnimatedGestureIcon(
    type: GestureAnimation,
    icon: ImageVector,
    tint: Color,
    delayMs: Int,
) {
    if (type == GestureAnimation.NONE) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(24.dp),
        )
        return
    }

    val anim = remember { Animatable(0f) }

    LaunchedEffect(type) {
        kotlinx.coroutines.delay(delayMs.toLong())
        anim.animateTo(
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1800, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        )
    }

    when (type) {
        GestureAnimation.VERTICAL -> {
            val offsetY = ((anim.value * 2f - 1f) * 8f)
            Icon(
                imageVector = Icons.Default.SwapVert,
                contentDescription = null,
                tint = tint,
                modifier = Modifier
                    .size(26.dp)
                    .offset { IntOffset(0, offsetY.dp.roundToPx()) },
            )
        }

        GestureAnimation.DOUBLE_TAP -> {
            val scale =
                if (anim.value < 0.5f) 1f + anim.value * 0.4f else 1.2f - (anim.value - 0.5f) * 0.4f
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size((26 * scale).dp),
            )
        }

        GestureAnimation.SWIPE_LEFT -> {
            val offsetX = (-(anim.value) * 10f)
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier
                    .size(26.dp)
                    .offset { IntOffset(offsetX.dp.roundToPx(), 0) },
            )
        }

        GestureAnimation.SWIPE_RIGHT -> {
            val offsetX = (anim.value * 10f)
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = tint,
                modifier = Modifier
                    .size(26.dp)
                    .offset { IntOffset(offsetX.dp.roundToPx(), 0) },
            )
        }

        GestureAnimation.NONE -> Unit
    }
}
