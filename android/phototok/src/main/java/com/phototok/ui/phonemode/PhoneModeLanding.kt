package com.phototok.ui.phonemode

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Source type the user can pick on the landing screen. */
enum class SourceType { Local, SdCard }

/**
 * Redesigned landing screen: hero branding, source grid,
 * pill filter chips, and prominent CTA.
 *
 * Cloud storage (e.g. Google Drive) is reached through the same SAF folder
 * picker as local folders: document providers expose it in the picker UI.
 */
@Composable
fun PhoneModeLanding(
    sourceFolderUri: String?,
    sourceFolderName: String,
    collectionFolderName: String,
    isLoading: Boolean,
    onSelectSource: (Uri) -> Unit,
    onSelectCollection: (Uri) -> Unit,
    onStart: () -> Unit,
    externalVolumes: List<com.phototok.data.source.ExternalVolume> = emptyList(),
    onBrowseExternalVolume: (String) -> Unit = {},
    recentPaths: List<com.phototok.data.model.RecentPath> = emptyList(),
    recentPathsEnabled: Boolean = true,
    recentPathsCount: Int = 3,
    onSelectRecentPath: (com.phototok.data.model.RecentPath) -> Unit = {},
) {
    val sourcePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri: Uri? -> uri?.let { onSelectSource(it) } }

    val hasSourceFolder = sourceFolderUri != null
    var selectedSource by remember(sourceFolderUri) {
        mutableStateOf(
            when {
                sourceFolderUri == null -> SourceType.Local
                else -> {
                    val isSdCard = externalVolumes.any { vol ->
                        sourceFolderUri.contains(vol.path) ||
                        (Uri.parse(sourceFolderUri).path?.contains(vol.path) == true)
                    }
                    if (isSdCard) SourceType.SdCard else SourceType.Local
                }
            }
        )
    }
    val colors = MaterialTheme.colorScheme

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        // Decorative background blurs
        Box(
            modifier = Modifier
                .size(300.dp)
                .offset(x = (-100).dp, y = 150.dp)
                .background(colors.primary.copy(alpha = 0.08f), CircleShape)
                .blur(80.dp),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(300.dp)
                .offset(x = 100.dp, y = (-100).dp)
                .background(colors.tertiary.copy(alpha = 0.06f), CircleShape)
                .blur(80.dp),
        )

        // Scrollable content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 64.dp, bottom = 100.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // ── Hero branding ────────────────────────────────────────
            Image(
                painter = painterResource(id = com.phototok.R.mipmap.ic_launcher_foreground),
                contentDescription = "Logo",
                modifier = Modifier.size(120.dp),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Photo-Tok",
                style = MaterialTheme.typography.headlineMedium,
                color = colors.onSurface,
            )
            Text(
                text = "SWIPE. SELECT. SNAP.",
                style = MaterialTheme.typography.labelSmall.copy(
                    letterSpacing = 3.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = colors.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ── Sample Image Mockup Cards ────────────────────────────
            Row(
                horizontalArrangement = Arrangement.spacedBy((-16).dp),
                modifier = Modifier.padding(vertical = 12.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .size(width = 80.dp, height = 110.dp)
                        .graphicsLayer { rotationZ = -8f }
                        .border(2.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                    shape = RoundedCornerShape(8.dp),
                    color = colors.surfaceVariant
                ) {
                    Image(
                        painter = painterResource(id = com.phototok.R.drawable.placeholder_landscape),
                        contentDescription = "Sample Landscape",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Surface(
                    modifier = Modifier
                        .size(width = 90.dp, height = 120.dp)
                        .graphicsLayer { translationY = -8f }
                        .border(2.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                        .shadow(8.dp, RoundedCornerShape(8.dp)),
                    shape = RoundedCornerShape(8.dp),
                    color = colors.surfaceVariant
                ) {
                    Image(
                        painter = painterResource(id = com.phototok.R.drawable.placeholder_portrait),
                        contentDescription = "Sample Lens",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Surface(
                    modifier = Modifier
                        .size(width = 80.dp, height = 110.dp)
                        .graphicsLayer { rotationZ = 8f }
                        .border(2.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                    shape = RoundedCornerShape(8.dp),
                    color = colors.surfaceVariant
                ) {
                    Image(
                        painter = painterResource(id = com.phototok.R.drawable.placeholder_architecture),
                        contentDescription = "Sample Architecture",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Source selection ──────────────────────────────────────
            Text(
                text = "SELECT SOURCE",
                style = MaterialTheme.typography.labelLarge,
                color = colors.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, bottom = 12.dp),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SourceCard(
                    icon = Icons.Default.FolderOpen,
                    label = "Local Folder",
                    isActive = selectedSource == SourceType.Local,
                    onClick = {
                        selectedSource = SourceType.Local
                        sourcePickerLauncher.launch(null)
                    },
                    modifier = Modifier.weight(1f),
                )
                if (externalVolumes.isNotEmpty()) {
                    SourceCard(
                        icon = Icons.Default.Storage,
                        label = "External Storage",
                        isActive = selectedSource == SourceType.SdCard,
                        onClick = {
                            selectedSource = SourceType.SdCard
                            onBrowseExternalVolume(externalVolumes.first().path)
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            if (hasSourceFolder && sourceFolderName.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = sourceFolderName,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.primary.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // ── Recent folders (quick re-select) ─────────────────────
            val recentToShow = recentPaths.take(recentPathsCount.coerceAtLeast(1))
            if (recentPathsEnabled && recentToShow.isNotEmpty()) {
                Spacer(modifier = Modifier.height(32.dp))
                Text(
                    text = "RECENT FOLDERS",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, bottom = 12.dp),
                )
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    recentToShow.forEach { recent ->
                        RecentPathRow(recent = recent, onClick = { onSelectRecentPath(recent) })
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // ── CTA button ───────────────────────────────────────────
            val enabled = hasSourceFolder && !isLoading
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clickable(enabled = enabled) { onStart() },
                shape = RoundedCornerShape(12.dp),
                color = if (enabled) colors.primaryContainer else colors.primaryContainer.copy(alpha = 0.3f),
                shadowElevation = if (enabled) 8.dp else 0.dp,
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (isLoading) "Loading..." else "Start Browsing",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (enabled) colors.onPrimaryContainer else Color.White.copy(alpha = 0.4f),
                    )
                    if (!isLoading) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = if (enabled) colors.onPrimaryContainer else Color.White.copy(alpha = 0.4f),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Connect a source to begin your high-velocity curation.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SourceCard(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val borderColor = if (isActive) colors.primaryContainer else colors.outlineVariant

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (isActive) colors.primaryContainer.copy(alpha = 0.1f) else colors.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 20.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isActive) colors.primary else colors.secondary,
                modifier = Modifier.size(28.dp),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurface,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun RecentPathRow(
    recent: com.phototok.data.model.RecentPath,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, colors.outlineVariant, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = colors.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Default.History,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = recent.name.ifEmpty { "Folder" },
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurface,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
