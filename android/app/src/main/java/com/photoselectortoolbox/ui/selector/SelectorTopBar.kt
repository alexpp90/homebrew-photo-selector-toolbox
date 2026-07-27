package com.photoselectortoolbox.ui.selector

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BurstMode
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photoselectortoolbox.ui.theme.Indigo200
import com.photoselectortoolbox.ui.theme.Indigo500
import com.photoselectortoolbox.ui.theme.ScoreBad
import com.photoselectortoolbox.ui.theme.TonalIndigo
import com.photoselectortoolbox.ui.theme.TonalIndigoHover
import com.photoselectortoolbox.ui.theme.Zinc400
import com.photoselectortoolbox.ui.theme.Zinc50
import com.photoselectortoolbox.ui.theme.Zinc700
import com.photoselectortoolbox.ui.theme.Zinc800
import com.photoselectortoolbox.ui.theme.Zinc900

/**
 * Height of the selector's app bar.
 *
 * Well under Material's default. On a 16:10 tablet the vertical axis is the
 * scarce one — every dp the bar takes is a dp the photographs do not get — and
 * nothing in this bar needs more room than this.
 */
val SelectorTopBarHeight = 44.dp

/**
 * The selector's app bar, in its resting and scanning states.
 *
 * During a scan the Scan button is replaced in place by a counter and a Cancel
 * action, rather than a dialog or an overlay: culling continues while the scan
 * runs, so the scan must not take the screen.
 */
@Composable
fun SelectorTopBar(
    folderName: String,
    folderPath: String?,
    position: Int,
    total: Int,
    burstLabel: String?,
    groupingEnabled: Boolean,
    hasScores: Boolean,
    isScanning: Boolean,
    scanProgress: Float,
    scanStatusText: String,
    driveSignedIn: Boolean,
    onOpenFolder: () -> Unit,
    onOpenDrive: () -> Unit,
    onScan: () -> Unit,
    onCancelScan: () -> Unit,
    onToggleGrouping: () -> Unit,
    onShowLegend: () -> Unit,
    onShowMenu: () -> Unit,
    modifier: Modifier = Modifier,
    overflowContent: @Composable () -> Unit = {},
) {
    Column(modifier = modifier.fillMaxWidth().background(Zinc900)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(SelectorTopBarHeight)
                .padding(start = 12.dp, end = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.FolderOpen,
                contentDescription = folderPath ?: "Open folder",
                modifier = Modifier
                    .size(18.dp)
                    .pointerHoverIcon(PointerIcon.Hand)
                    .clickable(onClick = onOpenFolder),
                tint = Zinc400,
            )
            Text(
                text = folderName,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Medium,
                color = Zinc50,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )

            if (total > 0) {
                BarDivider()
                Text(
                    text = "$position / $total",
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Zinc50,
                    modifier = Modifier.testTag("position_counter"),
                )
            }

            if (burstLabel != null) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, Zinc700, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.BurstMode,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = Zinc400,
                    )
                    Text(
                        text = burstLabel,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Zinc400,
                    )
                }
            }

            Box(modifier = Modifier.weight(1f))

            if (isScanning) {
                Text(
                    text = scanStatusText,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Zinc400,
                )
                Text(
                    text = "Cancel",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = ScoreBad,
                    modifier = Modifier
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable(onClick = onCancelScan)
                        .padding(horizontal = 6.dp, vertical = 8.dp)
                        .testTag("cancel_scan_button"),
                )
            } else if (total > 0) {
                ScanButton(onClick = onScan)
            }

            BarIconButton(
                icon = Icons.Default.BurstMode,
                description = if (groupingEnabled) {
                    "Group Similar Series, on"
                } else {
                    "Group Similar Series, off"
                },
                active = groupingEnabled,
                enabled = total > 0,
                onClick = onToggleGrouping,
                modifier = Modifier.testTag("grouping_toggle"),
            )

            BarIconButton(
                icon = Icons.Default.Cloud,
                description = "Open from Google Drive",
                active = driveSignedIn,
                onClick = onOpenDrive,
            )

            if (hasScores) {
                BarIconButton(
                    icon = Icons.Outlined.Info,
                    description = "What the scan icons mean",
                    onClick = onShowLegend,
                    modifier = Modifier.testTag("score_legend_button"),
                )
            }

            Box {
                BarIconButton(
                    icon = Icons.Default.MoreVert,
                    description = "More options",
                    onClick = onShowMenu,
                )
                overflowContent()
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Zinc800),
        )

        // A 2dp line directly under the bar, not a dialog: the scan is
        // background work and must not interrupt culling. Drawn by hand rather
        // than with LinearProgressIndicator so it is exactly 2dp with no track
        // gap or stop indicator to break the line.
        if (isScanning) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(Zinc800)
                    .testTag("scan_progress"),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(scanProgress.coerceIn(0f, 1f))
                        .height(2.dp)
                        .background(Indigo500),
                )
            }
        }
    }
}

@Composable
private fun BarDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(22.dp)
            .background(Zinc700),
    )
}

@Composable
private fun ScanButton(onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()

    Row(
        modifier = Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (hovered) TonalIndigoHover else TonalIndigo)
            .hoverable(interactionSource)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp)
            .testTag("scan_button"),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Radar,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = Indigo200,
        )
        Text(text = "Scan Images", fontSize = 13.sp, color = Indigo200)
    }
}

@Composable
private fun BarIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .size(34.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) TonalIndigo else Color.Transparent)
            .then(
                if (enabled) {
                    Modifier
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable(onClick = onClick)
                } else {
                    Modifier
                }
            )
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = when {
                !enabled -> Zinc700
                active -> Indigo500
                else -> Zinc400
            },
        )
    }
}
