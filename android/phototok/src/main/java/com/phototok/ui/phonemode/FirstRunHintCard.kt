package com.phototok.ui.phonemode

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.phototok.domain.FirstRunHint
import com.phototok.viewmodel.FirstRunHintUi
import kotlinx.coroutines.delay

/** How long an explanation stays up before it fades on its own. */
private const val AUTO_DISMISS_MS = 7000L

/**
 * The one-time explanation shown after a first-time action.
 *
 * Deliberately a card rather than a snackbar: the text is two lines and points
 * at a setting, so it needs room and an explicit "Got it" rather than a
 * three-second flash the user can miss mid-swipe.
 */
@Composable
fun FirstRunHintCard(
    hint: FirstRunHintUi?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Restart the timer whenever a *different* hint arrives.
    LaunchedEffect(hint?.id) {
        if (hint != null) {
            delay(AUTO_DISMISS_MS)
            onDismiss()
        }
    }

    // Hold on to the last hint so the card still has content to draw while it
    // animates out (by then [hint] is already null).
    var lastHint by remember { mutableStateOf(hint) }
    if (hint != null) lastHint = hint

    AnimatedVisibility(
        visible = hint != null,
        enter = slideInVertically(tween(250)) { it / 2 } + fadeIn(tween(250)),
        exit = slideOutVertically(tween(200)) { it / 2 } + fadeOut(tween(200)),
        modifier = modifier,
    ) {
        val colors = MaterialTheme.colorScheme
        val current = lastHint ?: return@AnimatedVisibility

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .testTag("first_run_hint"),
            shape = RoundedCornerShape(16.dp),
            color = colors.surfaceContainerHigh.copy(alpha = 0.97f),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                colors.primary.copy(alpha = 0.35f),
            ),
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(colors.primary.copy(alpha = 0.12f))
                        .border(1.dp, colors.primary.copy(alpha = 0.25f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = iconFor(current.hint),
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = current.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.onSurface,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = current.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "GOT IT",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onDismiss() }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                            .testTag("first_run_hint_dismiss"),
                    )
                }
            }
        }
    }
}

private fun iconFor(hint: FirstRunHint): ImageVector = when (hint) {
    FirstRunHint.SWIPE_RIGHT -> Icons.AutoMirrored.Filled.ArrowForward
    FirstRunHint.SWIPE_LEFT_FOLDER -> Icons.AutoMirrored.Filled.ArrowBack
    FirstRunHint.SWIPE_LEFT_DELETE -> Icons.Default.Delete
    FirstRunHint.SWIPE_VERTICAL -> Icons.Default.SwapVert
    FirstRunHint.TAP_HUD -> Icons.Default.TouchApp
    FirstRunHint.DOUBLE_TAP_ZOOM -> Icons.Default.ZoomIn
    FirstRunHint.REVERT -> Icons.AutoMirrored.Filled.Undo
}
