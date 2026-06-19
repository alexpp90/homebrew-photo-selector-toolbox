package com.photoselectortoolbox.ui.phonemode

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.photoselectortoolbox.ui.theme.Indigo500

/**
 * Full-screen animated overlay that teaches the three phone-mode gestures.
 * Shows once on first launch and again after a one-week gap.
 */
@Composable
fun GestureTutorialOverlay(
    visible: Boolean,
    onDismiss: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(300)),
        exit = fadeOut(tween(200)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.88f))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) { /* absorb taps */ },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                Text(
                    text = "How it works",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )

                Spacer(modifier = Modifier.height(4.dp))

                GestureRow(
                    icon = Icons.Default.SwapVert,
                    title = "Swipe Up / Down",
                    description = "Browse through your photos",
                    animationType = GestureAnimation.VERTICAL,
                )

                GestureRow(
                    icon = Icons.Default.TouchApp,
                    title = "Double Tap",
                    description = "Add to your collection",
                    animationType = GestureAnimation.DOUBLE_TAP,
                )

                GestureRow(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    title = "Swipe Left",
                    description = "Delete photo",
                    animationType = GestureAnimation.SWIPE_LEFT,
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Indigo500,
                        contentColor = Color.White,
                    ),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text(
                        text = "Got it",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

private enum class GestureAnimation { VERTICAL, DOUBLE_TAP, SWIPE_LEFT }

@Composable
private fun GestureRow(
    icon: ImageVector,
    title: String,
    description: String,
    animationType: GestureAnimation,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Animated gesture indicator
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(Indigo500.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedGestureIcon(animationType)
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.65f),
            )
        }
    }
}

@Composable
private fun AnimatedGestureIcon(type: GestureAnimation) {
    val anim = remember { Animatable(0f) }

    LaunchedEffect(type) {
        anim.animateTo(
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1400, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        )
    }

    when (type) {
        GestureAnimation.VERTICAL -> {
            val offsetY = ((anim.value * 2f - 1f) * 10f) // bounce -10..+10
            Icon(
                imageVector = Icons.Default.SwapVert,
                contentDescription = null,
                tint = Indigo500,
                modifier = Modifier
                    .size(28.dp)
                    .offset { IntOffset(0, offsetY.dp.roundToPx()) },
            )
        }

        GestureAnimation.DOUBLE_TAP -> {
            val scale = if (anim.value < 0.5f) 1f + anim.value * 0.5f else 1.25f - (anim.value - 0.5f) * 0.5f
            Icon(
                imageVector = Icons.Default.TouchApp,
                contentDescription = null,
                tint = Indigo500,
                modifier = Modifier.size((28 * scale).dp),
            )
        }

        GestureAnimation.SWIPE_LEFT -> {
            val offsetX = (-(anim.value) * 14f) // slide left
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                tint = Indigo500,
                modifier = Modifier
                    .size(28.dp)
                    .offset { IntOffset(offsetX.dp.roundToPx(), 0) },
            )
        }
    }
}
