package com.photoselectortoolbox.ui.selector

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.ViewColumn
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photoselectortoolbox.ui.theme.Indigo200
import com.photoselectortoolbox.ui.theme.Indigo500
import com.photoselectortoolbox.ui.theme.PanelSurface
import com.photoselectortoolbox.ui.theme.ScoreBad
import com.photoselectortoolbox.ui.theme.TonalIndigo
import com.photoselectortoolbox.ui.theme.TonalIndigoHover
import com.photoselectortoolbox.ui.theme.Zinc400
import com.photoselectortoolbox.ui.theme.Zinc50
import com.photoselectortoolbox.ui.theme.Zinc500
import com.photoselectortoolbox.ui.theme.Zinc700
import com.photoselectortoolbox.ui.theme.Zinc800

/** The icon and description for the layout toggle in its current state. */
fun layoutToggleIcon(focusedLayout: Boolean): ImageVector =
    if (focusedLayout) Icons.Default.ViewColumn else Icons.Default.ViewAgenda

fun layoutToggleDescription(focusedLayout: Boolean): String =
    if (focusedLayout) "Switch to three-column comparison" else "Switch to focused layout"

/**
 * The callbacks every selector layout needs, gathered so the two layouts and
 * the context menu cannot drift apart in what they offer.
 */
data class SelectorActions(
    val onMove: () -> Unit,
    val onCopy: () -> Unit,
    val onDelete: () -> Unit,
    val onFullscreen: () -> Unit,
    val onToggleLayout: () -> Unit,
    val onToggleDetails: () -> Unit,
    val onToggleFilmstrip: () -> Unit,
)

// ── Shared action row (three-column layout) ─────────────────────────────────

/**
 * The action row beneath the three columns.
 *
 * In the three-column layout the columns are equal thirds, so there is no
 * letterbox column to put a rail in — the actions go under the grid instead,
 * where they are equidistant from all three frames.
 */
@Composable
fun SharedActionRow(
    actions: SelectorActions,
    focusedLayout: Boolean,
    detailsVisible: Boolean,
    filmstripVisible: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(PanelSurface)
            .border(1.dp, Zinc800, RoundedCornerShape(12.dp))
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TonalActionButton(
            icon = Icons.AutoMirrored.Filled.DriveFileMove,
            label = "Move to Selection",
            keyHint = "M",
            onClick = actions.onMove,
            modifier = Modifier.testTag("move_button_expanded"),
        )
        TonalActionButton(
            icon = Icons.Default.ContentCopy,
            label = "Copy to Selection",
            keyHint = "C",
            onClick = actions.onCopy,
            modifier = Modifier.testTag("copy_button_expanded"),
        )
        OutlinedActionButton(
            icon = Icons.Default.Delete,
            label = "Delete",
            keyHint = "Del",
            iconTint = ScoreBad,
            onClick = actions.onDelete,
            modifier = Modifier.testTag("delete_button_expanded"),
        )
        RailButton(
            icon = Icons.Default.Fullscreen,
            description = "Open fullscreen",
            onClick = actions.onFullscreen,
            modifier = Modifier.testTag("fullscreen_button"),
        )

        Box(
            modifier = Modifier
                .padding(horizontal = 2.dp)
                .width(1.dp)
                .height(28.dp)
                .background(Zinc700),
        )

        RailButton(
            icon = layoutToggleIcon(focusedLayout),
            description = layoutToggleDescription(focusedLayout),
            onClick = actions.onToggleLayout,
            modifier = Modifier.testTag("layout_toggle"),
        )
        RailButton(
            icon = Icons.Outlined.Info,
            description = if (detailsVisible) "Hide photo details" else "Show photo details",
            onClick = actions.onToggleDetails,
            active = detailsVisible,
            modifier = Modifier.testTag("details_toggle"),
        )
        RailButton(
            icon = Icons.Default.ViewCarousel,
            description = if (filmstripVisible) "Hide the filmstrip" else "Show the filmstrip",
            onClick = actions.onToggleFilmstrip,
            active = filmstripVisible,
            modifier = Modifier.testTag("filmstrip_toggle"),
        )
    }
}

/**
 * A filled-tonal action (Move, Copy).
 *
 * The keyboard hint fades in on hover rather than sitting there permanently:
 * a photographer who already knows the shortcut does not need it, and one who
 * is reaching for the mouse is exactly the person who benefits from learning
 * it.
 */
@Composable
private fun TonalActionButton(
    icon: ImageVector,
    label: String,
    keyHint: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val background by animateColorAsState(
        targetValue = if (hovered) TonalIndigoHover else TonalIndigo,
        animationSpec = tween(150),
        label = "tonalActionBg",
    )

    Row(
        modifier = modifier
            .heightIn(min = ControlSize)
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .hoverable(interactionSource)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp)
            .semantics { contentDescription = "$label, shortcut $keyHint" },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = Indigo200,
        )
        Text(text = label, fontSize = 13.sp, color = Indigo200, maxLines = 1)
        KeyHint(text = keyHint, visible = hovered)
    }
}

/** An outlined action (Delete) — only the icon carries the destructive colour. */
@Composable
private fun OutlinedActionButton(
    icon: ImageVector,
    label: String,
    keyHint: String,
    iconTint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val borderColor by animateColorAsState(
        targetValue = if (hovered) Zinc500 else Zinc700,
        animationSpec = tween(150),
        label = "outlinedActionBorder",
    )

    Row(
        modifier = modifier
            .heightIn(min = ControlSize)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .hoverable(interactionSource)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp)
            .semantics { contentDescription = "$label, shortcut $keyHint" },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = iconTint,
        )
        Text(text = label, fontSize = 13.sp, color = Zinc50, maxLines = 1)
        KeyHint(text = keyHint, visible = hovered)
    }
}

/** The small superscript key hint that appears on hover. */
@Composable
private fun KeyHint(text: String, visible: Boolean) {
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(150),
        label = "keyHint",
    )
    Text(
        text = text,
        fontSize = 9.sp,
        fontFamily = FontFamily.Monospace,
        color = Zinc400,
        modifier = Modifier.alpha(alpha),
    )
}

// ── Context menu ────────────────────────────────────────────────────────────

/**
 * Long-press / right-click menu for a frame.
 *
 * Repeats what the rails already offer on purpose: on a tablet held in two
 * hands the rails are a thumb-stretch away, and a menu at the pointer is not.
 */
@Composable
fun SelectorContextMenu(
    actions: SelectorActions,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(230.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Zinc800)
            .border(1.dp, Zinc700, RoundedCornerShape(10.dp))
            .padding(5.dp)
            .testTag("selector_context_menu"),
    ) {
        ContextMenuRow(
            icon = Icons.AutoMirrored.Filled.DriveFileMove,
            label = "Move to Selection",
            keyHint = "M",
            onClick = { onDismissRequest(); actions.onMove() },
        )
        ContextMenuRow(
            icon = Icons.Default.ContentCopy,
            label = "Copy to Selection",
            keyHint = "C",
            onClick = { onDismissRequest(); actions.onCopy() },
        )
        ContextMenuRow(
            icon = Icons.Default.Delete,
            label = "Delete",
            keyHint = "Del",
            labelColor = ScoreBad,
            onClick = { onDismissRequest(); actions.onDelete() },
        )
        ContextMenuRow(
            icon = Icons.Outlined.Info,
            label = "Photo details",
            keyHint = null,
            onClick = { onDismissRequest(); actions.onToggleDetails() },
        )
        ContextMenuRow(
            icon = Icons.Default.Fullscreen,
            label = "Open fullscreen",
            keyHint = "F",
            onClick = { onDismissRequest(); actions.onFullscreen() },
        )
    }
}

@Composable
private fun ContextMenuRow(
    icon: ImageVector,
    label: String,
    keyHint: String?,
    onClick: () -> Unit,
    labelColor: Color = Zinc50,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(6.dp))
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = if (labelColor == ScoreBad) ScoreBad else Zinc400,
        )
        Text(
            text = label,
            fontSize = 13.sp,
            color = labelColor,
            modifier = Modifier.weight(1f),
        )
        if (keyHint != null) {
            Text(
                text = keyHint,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = Zinc500,
            )
        }
    }
}

// ── First-run hint ──────────────────────────────────────────────────────────

/**
 * The one permitted image-adjacent overlay: a single pill telling the user the
 * neighbour frames are tappable.
 *
 * Everything else in this design stays out of the letterbox because chrome
 * over a photograph changes how the photograph reads. This one earns its place
 * because the layout has no arrows and no other cue that the tiles navigate —
 * and it is shown exactly once, ever.
 */
@Composable
fun FirstRunNavigationHint(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(200)),
        exit = fadeOut(tween(200)),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(Zinc800)
                .border(1.dp, Zinc700, RoundedCornerShape(999.dp))
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .testTag("first_run_hint"),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = Zinc400,
            )
            Text(text = "Tap either side to browse", fontSize = 12.sp, color = Zinc50)
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = Zinc400,
            )
            Text(
                text = "Got it",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Indigo200,
                modifier = Modifier
                    .pointerHoverIcon(PointerIcon.Hand)
                    .clickable(onClick = onDismiss)
                    .padding(start = 4.dp),
            )
        }
    }
}

// ── Undo snackbar ───────────────────────────────────────────────────────────

/** How long an undoable action stays undoable. */
const val UNDO_WINDOW_MILLIS = 30_000L

/**
 * Confirmation of an action, with the time remaining to take it back drawn as
 * a draining line.
 *
 * The window is 30 seconds rather than the Material default of a few seconds
 * because culling is fast and inattentive: the user is three frames further on
 * before they register that the last one was a mistake.
 */
@Composable
fun SelectorSnackbar(
    message: String?,
    onUndo: (() -> Unit)?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (message == null) return

    LaunchedEffect(message) {
        kotlinx.coroutines.delay(UNDO_WINDOW_MILLIS)
        onDismiss()
    }

    Column(
        modifier = modifier
            .widthIn(min = 340.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Zinc800)
            .border(1.dp, Zinc700, RoundedCornerShape(10.dp))
            .testTag("selector_snackbar"),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = message,
                fontSize = 13.sp,
                color = Zinc50,
                modifier = Modifier.weight(1f),
            )
            if (onUndo != null) {
                Text(
                    text = "UNDO",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Indigo200,
                    modifier = Modifier
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable { onUndo(); onDismiss() }
                        .testTag("snackbar_undo"),
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(Indigo500),
        )
    }
}

/** Vertical spacer used between action clusters in a rail. */
@Composable
fun RailSpacer(height: androidx.compose.ui.unit.Dp = 6.dp) {
    Spacer(modifier = Modifier.height(height))
}
