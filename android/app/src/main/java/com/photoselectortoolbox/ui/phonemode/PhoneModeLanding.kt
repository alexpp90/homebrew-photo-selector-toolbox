package com.photoselectortoolbox.ui.phonemode

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.photoselectortoolbox.ui.theme.Indigo500
import com.photoselectortoolbox.ui.theme.Zinc700
import com.photoselectortoolbox.ui.theme.Zinc800
import com.photoselectortoolbox.ui.theme.Zinc900
import com.photoselectortoolbox.ui.theme.Zinc950

/**
 * Minimal landing screen for phone mode.
 * Lets the user pick a source folder and an optional collection target.
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
) {
    val sourcePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri: Uri? -> uri?.let { onSelectSource(it) } }

    val collectionPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri: Uri? -> uri?.let { onSelectCollection(it) } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Zinc900)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.weight(0.2f))

            // App icon / branding area
            Icon(
                imageVector = Icons.Default.PhotoLibrary,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = Indigo500,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Photo Selector",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Swipe through your photos",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.5f),
            )

            Spacer(modifier = Modifier.weight(0.15f))

            // Source folder picker
            FolderPickerCard(
                icon = Icons.Default.FolderOpen,
                label = "Source",
                folderName = sourceFolderName.ifEmpty { "Select photos folder" },
                isSet = hasSourceFolder,
                onClick = { sourcePickerLauncher.launch(null) },
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Collection target picker
            FolderPickerCard(
                icon = Icons.Default.Collections,
                label = "Collection",
                folderName = collectionFolderName.ifEmpty { "Same as source (Selection subfolder)" },
                isSet = collectionFolderName.isNotEmpty(),
                onClick = { collectionPickerLauncher.launch(null) },
            )

            Spacer(modifier = Modifier.weight(0.2f))

            // Start button
            Button(
                onClick = onStart,
                enabled = hasSourceFolder && !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Indigo500,
                    contentColor = Color.White,
                    disabledContainerColor = Indigo500.copy(alpha = 0.3f),
                    disabledContentColor = Color.White.copy(alpha = 0.4f),
                ),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(
                    text = if (isLoading) "Loading..." else "Start Browsing",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun FolderPickerCard(
    icon: ImageVector,
    label: String,
    folderName: String,
    isSet: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = Zinc800,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSet) Indigo500 else Zinc700,
                modifier = Modifier.size(28.dp),
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.5f),
                )
                Text(
                    text = folderName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isSet) Color.White else Color.White.copy(alpha = 0.35f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Zinc700,
            )
        }
    }
}
