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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.TouchApp
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Full-screen overlay teaching the three phone-mode gestures.
 * Glassmorphic card design with animated icons.
 */
@Composable
fun GestureTutorialOverlay(
    visible: Boolean,
    onDismiss: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(400)),
        exit = fadeOut(tween(250)),
    ) {
        val colors = MaterialTheme.colorScheme

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF09090B).copy(alpha = 0.88f))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) { /* absorb taps */ },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .padding(top = 32.dp, bottom = 32.dp)
                    .navigationBarsPadding(),
            ) {
                Spacer(modifier = Modifier.height(32.dp))

                // ── Header ───────────────────────────────────────────
                Text(
                    text = "How to Photo-Tok",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    modifier = Modifier.padding(start = 4.dp),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Master the curation flow with these simple gestures",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp),
                )

                Spacer(modifier = Modifier.height(32.dp))

                // ── Gesture cards ────────────────────────────────────
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    GestureCard(
                        icon = Icons.Default.SwapVert,
                        title = "Swipe Up/Down",
                        description = "Browse through your photos",
                        iconTint = colors.primary,
                        circleBorder = colors.primary.copy(alpha = 0.2f),
                        circleFill = colors.primary.copy(alpha = 0.1f),
                        animationType = GestureAnimation.VERTICAL,
                        animDelay = 0,
                    )
                    GestureCard(
                        icon = Icons.Default.TouchApp,
                        title = "Double Tap",
                        description = "Add to your collection",
                        iconTint = colors.primary,
                        circleBorder = colors.primary.copy(alpha = 0.2f),
                        circleFill = colors.primary.copy(alpha = 0.1f),
                        animationType = GestureAnimation.DOUBLE_TAP,
                        animDelay = 500,
                    )
                    GestureCard(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        title = "Swipe Left",
                        description = "Delete photo",
                        iconTint = colors.error,
                        circleBorder = colors.error.copy(alpha = 0.2f),
                        circleFill = colors.error.copy(alpha = 0.1f),
                        animationType = GestureAnimation.SWIPE_LEFT,
                        animDelay = 1000,
                    )
                }

                // ── Sticky bottom "Got it" button with gradient ──────
                Box(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    // Gradient fade above button
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
                                text = "GOT IT",
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

// ── Gesture card composable ──────────────────────────────────────────────

private enum class GestureAnimation { VERTICAL, DOUBLE_TAP, SWIPE_LEFT }

@Composable
private fun GestureCard(
    icon: ImageVector,
    title: String,
    description: String,
    iconTint: Color,
    circleBorder: Color,
    circleFill: Color,
    animationType: GestureAnimation,
    animDelay: Int,
) {
    val colors = MaterialTheme.colorScheme

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.Transparent,
        modifier = Modifier
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
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Animated icon circle
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(circleFill)
                    .border(1.dp, circleBorder, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedGestureIcon(animationType, iconTint, animDelay)
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AnimatedGestureIcon(
    type: GestureAnimation,
    tint: Color,
    delayMs: Int,
) {
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
                    .size(32.dp)
                    .offset { IntOffset(0, offsetY.dp.roundToPx()) },
            )
        }

        GestureAnimation.DOUBLE_TAP -> {
            val scale = if (anim.value < 0.5f) 1f + anim.value * 0.4f else 1.2f - (anim.value - 0.5f) * 0.4f
            Icon(
                imageVector = Icons.Default.TouchApp,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size((32 * scale).dp),
            )
        }

        GestureAnimation.SWIPE_LEFT -> {
            val offsetX = (-(anim.value) * 12f)
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                tint = tint,
                modifier = Modifier
                    .size(32.dp)
                    .offset { IntOffset(offsetX.dp.roundToPx(), 0) },
            )
        }
    }
}
