package com.photoselectortoolbox.data.source

import android.content.Context
import android.os.Environment
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class ExternalVolume(
    val description: String,
    val path: String,
    val isRemovable: Boolean,
    val isPrimary: Boolean,
)

@Singleton
class ExternalStorageDetector @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val TAG = "ExtStorageDetector"
    }

    /**
     * Returns a list of mounted external storage volumes (SD cards, USB drives, etc.).
     * Excludes the primary internal storage.
     */
    fun detectExternalVolumes(): List<ExternalVolume> {
        val results = mutableListOf<ExternalVolume>()

        try {
            val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
            val volumes = storageManager.storageVolumes

            for (volume in volumes) {
                // Skip non-mounted volumes
                if (volume.state != Environment.MEDIA_MOUNTED &&
                    volume.state != Environment.MEDIA_MOUNTED_READ_ONLY
                ) continue

                val description = volume.getDescription(context) ?: "External Storage"
                val directory = volume.directory ?: continue

                results.add(
                    ExternalVolume(
                        description = description,
                        path = directory.absolutePath,
                        isRemovable = volume.isRemovable,
                        isPrimary = volume.isPrimary,
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to detect storage volumes", e)
        }

        // Also check common external paths as fallback
        val externalDirs = context.getExternalFilesDirs(null)
        for (dir in externalDirs) {
            if (dir == null) continue
            // Extract the root of the external storage from the app-specific path
            // e.g. /storage/XXXX-XXXX/Android/data/... → /storage/XXXX-XXXX
            val path = dir.absolutePath
            val storageRoot = extractStorageRoot(path) ?: continue

            // Skip if we already have this root
            if (results.any { it.path == storageRoot }) continue

            val rootFile = File(storageRoot)
            if (rootFile.exists() && rootFile.canRead()) {
                val isInternal = storageRoot == Environment.getExternalStorageDirectory().absolutePath
                results.add(
                    ExternalVolume(
                        description = if (isInternal) "Internal Storage" else rootFile.name,
                        path = storageRoot,
                        isRemovable = !isInternal,
                        isPrimary = isInternal,
                    )
                )
            }
        }

        return results
    }

    /**
     * Returns only removable volumes (SD cards, USB drives).
     */
    fun detectRemovableVolumes(): List<ExternalVolume> {
        return detectExternalVolumes().filter { it.isRemovable }
    }

    private fun extractStorageRoot(path: String): String? {
        // Paths like /storage/XXXX-XXXX/Android/data/com.app/files
        val storagePrefix = "/storage/"
        if (!path.startsWith(storagePrefix)) return null

        val afterStorage = path.substring(storagePrefix.length)
        val volumeId = afterStorage.substringBefore('/')
        return "$storagePrefix$volumeId"
    }
}
