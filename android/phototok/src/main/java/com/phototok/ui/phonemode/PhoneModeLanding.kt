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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
enum class SourceType { Local, SdCard, GoogleDrive }

/**
 * Redesigned landing screen: hero branding, 3-column source grid,
 * pill filter chips, and prominent CTA.
 */
@Composable
fun PhoneModeLanding(
    sourceFolderName: String,
    collectionFolderName: String,
    hasSourceFolder: Boolean,
    isLoading: Boolean,
    onSelectSource: (Uri) -> Unit,
    onSelectCollection: (Uri) -> Unit,
    onStart: () -> Unit,
    fileTypeFilter: String = "all",
    onFileTypeFilterChange: (String) -> Unit = {},
    externalVolumes: List<com.phototok.data.source.ExternalVolume> = emptyList(),
    onBrowseExternalVolume: (String) -> Unit = {},
    onOpenGoogleDrive: () -> Unit = {},
    isGoogleDriveSignedIn: Boolean = false,
) {
    val sourcePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri: Uri? -> uri?.let { onSelectSource(it) } }

    var selectedSource by remember { mutableStateOf(SourceType.Local) }
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
            Spacer(modifier = Modifier.weight(0.15f))

            // ── Hero branding ────────────────────────────────────────
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.primaryContainer.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.PhotoLibrary,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = colors.primary,
                )
            }
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

            Spacer(modifier = Modifier.weight(0.15f))

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
                SourceCard(
                    icon = Icons.Default.SdCard,
                    label = "SD Card",
                    isActive = selectedSource == SourceType.SdCard,
                    onClick = {
                        selectedSource = SourceType.SdCard
                        if (externalVolumes.isNotEmpty()) {
                            onBrowseExternalVolume(externalVolumes.first().path)
                        } else {
                            sourcePickerLauncher.launch(null)
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
                SourceCard(
                    icon = Icons.Default.Cloud,
                    label = "Google Drive",
                    isActive = selectedSource == SourceType.GoogleDrive,
                    onClick = {
                        selectedSource = SourceType.GoogleDrive
                        onOpenGoogleDrive()
                    },
                    modifier = Modifier.weight(1f),
                )
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

            Spacer(modifier = Modifier.height(32.dp))

            // ── Format filter ────────────────────────────────────────
            Text(
                text = "FORMAT FILTER",
                style = MaterialTheme.typography.labelLarge,
                color = colors.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, bottom = 12.dp),
            )

            FormatFilterRow(
                selected = fileTypeFilter,
                onSelect = onFileTypeFilterChange,
            )

            Spacer(modifier = Modifier.weight(0.2f))

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
private fun FormatFilterRow(
    selected: String,
    onSelect: (String) -> Unit,
) {
    val colors = MaterialTheme.colorScheme

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("all" to "Both", "raw" to "RAW", "jpg" to "JPG").forEach { (value, label) ->
            val isSelected = selected == value
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .then(
                        if (!isSelected) Modifier.border(1.dp, colors.outlineVariant, RoundedCornerShape(50))
                        else Modifier,
                    )
                    .clickable { onSelect(value) },
                shape = RoundedCornerShape(50),
                color = if (isSelected) colors.primaryContainer else colors.surfaceContainer,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSelected) colors.onPrimaryContainer else colors.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
        }
    }
}
