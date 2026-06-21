package com.phototok.data.source.googledrive

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.phototok.data.model.ImageItem
import com.phototok.data.source.LocalImageSourceImpl
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleDriveImageSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val driveClient: GoogleDriveClient,
) {
    companion object {
        private const val TAG = "GoogleDriveImageSource"
        const val SCHEME = "gdrive"

        fun buildUri(driveId: String): Uri = Uri.parse("$SCHEME://$driveId")
        fun extractId(uri: Uri): String? {
            if (uri.scheme != SCHEME) return null
            return uri.host ?: uri.path?.trimStart('/')
        }
        fun isDriveUri(uri: Uri): Boolean = uri.scheme == SCHEME
        fun isDriveUri(uriString: String): Boolean = uriString.startsWith("$SCHEME://")
    }

    private val cacheDir: File
        get() = File(context.cacheDir, "gdrive_cache").also { it.mkdirs() }

    fun discoverImages(folderId: String): Flow<List<ImageItem>> = flow {
        val driveFiles = driveClient.listImages(folderId, recursive = true)
        val images = driveFiles
            .filter { !it.isFolder }
            .filter { !it.name.startsWith(".") }
            .filter { hasImageExtension(it.name) }
            .map { driveFile ->
                ImageItem(
                    uri = buildUri(driveFile.id).toString(),
                    fileName = driveFile.name,
                    fileSize = driveFile.size,
                    lastModified = driveFile.modifiedTime,
                    mimeType = driveFile.mimeType,
                    imageWidth = driveFile.imageWidth,
                    imageHeight = driveFile.imageHeight,
                )
            }
        emit(images)
    }.flowOn(Dispatchers.IO)

    suspend fun ensureCached(fileId: String, fileName: String): File? {
        val cached = getCacheFile(fileId, fileName)
        if (cached.exists() && cached.length() > 0) return cached
        val success = driveClient.downloadFile(fileId, cached)
        return if (success) cached else null
    }

    fun getCacheFile(fileId: String, fileName: String): File {
        val dir = File(cacheDir, fileId)
        dir.mkdirs()
        return File(dir, fileName)
    }

    suspend fun readImageDimensions(fileId: String, fileName: String): Pair<Int, Int> {
        val localFile = ensureCached(fileId, fileName) ?: return Pair(0, 0)
        return try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(localFile.absolutePath, opts)
            Pair(opts.outWidth.coerceAtLeast(0), opts.outHeight.coerceAtLeast(0))
        } catch (e: Exception) {
            Log.d(TAG, "Cannot read dimensions for $fileName: ${e.message}")
            Pair(0, 0)
        }
    }

    fun clearCache() { cacheDir.deleteRecursively() }

    fun evictOldCache(maxAgeMs: Long = 7L * 24 * 60 * 60 * 1000) {
        val cutoff = System.currentTimeMillis() - maxAgeMs
        cacheDir.listFiles()?.forEach { dir ->
            if (dir.isDirectory && dir.lastModified() < cutoff) {
                dir.deleteRecursively()
            }
        }
    }

    private fun hasImageExtension(fileName: String): Boolean {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return ext in LocalImageSourceImpl.SUPPORTED_EXTENSIONS
    }
}
