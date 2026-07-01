package com.phototok.data.source.googledrive

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.phototok.data.model.ExifData
import com.phototok.data.model.ImageItem
import com.phototok.data.reader.AndroidExifReader
import com.phototok.data.source.ImageSource
import com.phototok.data.source.LocalImageSourceImpl
import com.phototok.data.source.SelectionListing
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Google Drive image backend (URIs of the form gdrive://<fileOrFolderId>). */
@Singleton
class GoogleDriveImageSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val driveClient: GoogleDriveClient,
    private val androidExifReader: AndroidExifReader,
) : ImageSource {

    companion object {
        private const val TAG = "GoogleDriveImageSource"
        const val SCHEME = "gdrive"

        /** Hard cap for the on-disk Drive download cache. */
        private const val MAX_CACHE_BYTES = 512L * 1024 * 1024

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

    // ── ImageSource ───────────────────────────────────────────────────────

    override fun owns(uri: Uri): Boolean = isDriveUri(uri)

    override fun discoverImages(folderUri: Uri): Flow<List<ImageItem>> = flow {
        val folderId = extractId(folderUri)
        if (folderId == null) {
            emit(emptyList())
            return@flow
        }
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

    /** Drive folders need no permission grant; the display name is picker-provided. */
    override suspend fun prepareSourceFolder(folderUri: Uri): String? =
        if (extractId(folderUri) != null) "Google Drive" else null

    /** Drive folder names are supplied by the picker/recents; nothing to resolve. */
    override suspend fun resolveFolderName(folderUri: Uri): String? = null

    override suspend fun getExifData(uri: Uri): ExifData? {
        val fileId = extractId(uri) ?: return null
        val cached = ensureCached(fileId, fileId) ?: return null
        return androidExifReader.readExif(context, Uri.fromFile(cached))
    }

    override suspend fun getImageDimensions(uri: Uri): Pair<Int, Int> {
        val fileId = extractId(uri) ?: return Pair(0, 0)
        return readImageDimensions(fileId, fileId)
    }

    override suspend fun deleteImage(uri: Uri): Boolean {
        val fileId = extractId(uri) ?: return false
        return driveClient.trashFile(fileId)
    }

    override suspend fun copyImage(
        sourceUri: Uri,
        destFolderUri: Uri,
        sorting: Boolean,
        subfolderName: String,
    ): Boolean = moveCopyInternal(sourceUri, destFolderUri, sorting, move = false, subfolderName)

    override suspend fun moveImage(
        sourceUri: Uri,
        destFolderUri: Uri,
        sorting: Boolean,
        subfolderName: String,
    ): Boolean = moveCopyInternal(sourceUri, destFolderUri, sorting, move = true, subfolderName)

    /** The read-only selection viewer is local-only. */
    override suspend fun listSelectionImages(
        folderUri: Uri,
        subfolderName: String,
    ): SelectionListing = SelectionListing.NotSupported

    private suspend fun moveCopyInternal(
        sourceUri: Uri,
        destFolderUri: Uri,
        sorting: Boolean,
        move: Boolean,
        subfolderName: String,
    ): Boolean {
        val fileId = extractId(sourceUri) ?: return false
        val destFolderId = extractId(destFolderUri) ?: return false
        val targetFolderId = if (sorting) {
            driveClient.findOrCreateFolder(destFolderId, subfolderName) ?: return false
        } else {
            destFolderId
        }
        return if (move) {
            driveClient.moveFile(fileId, destFolderId, targetFolderId)
        } else {
            driveClient.copyFile(fileId, targetFolderId) != null
        }
    }

    // ── Download cache ────────────────────────────────────────────────────

    suspend fun ensureCached(fileId: String, fileName: String): File? {
        val cached = getCacheFile(fileId, fileName)
        if (cached.exists() && cached.length() > 0) {
            // Touch for LRU eviction ordering.
            cached.parentFile?.setLastModified(System.currentTimeMillis())
            return cached
        }
        val success = driveClient.downloadFile(fileId, cached)
        if (success) enforceCacheLimit()
        return if (success) cached else null
    }

    /** Evict least-recently-used cache entries until the cache fits [MAX_CACHE_BYTES]. */
    private fun enforceCacheLimit() {
        try {
            val dirs = cacheDir.listFiles()?.filter { it.isDirectory } ?: return
            val sizes = dirs.associateWith { dir ->
                dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            }
            var total = sizes.values.sum()
            if (total <= MAX_CACHE_BYTES) return
            for (dir in dirs.sortedBy { it.lastModified() }) {
                if (total <= MAX_CACHE_BYTES) break
                val size = sizes[dir] ?: 0
                if (dir.deleteRecursively()) total -= size
            }
        } catch (e: Exception) {
            Log.w(TAG, "Cache eviction failed", e)
        }
    }

    fun getCacheFile(fileId: String, fileName: String): File {
        val dir = File(cacheDir, fileId)
        dir.mkdirs()
        return File(dir, fileName)
    }

    suspend fun readImageDimensions(fileId: String, fileName: String): Pair<Int, Int> {
        val localFile = ensureCached(fileId, fileName) ?: return Pair(0, 0)
        return withContext(Dispatchers.IO) {
            try {
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(localFile.absolutePath, opts)
                Pair(opts.outWidth.coerceAtLeast(0), opts.outHeight.coerceAtLeast(0))
            } catch (e: Exception) {
                Log.d(TAG, "Cannot read dimensions for $fileName: ${e.message}")
                Pair(0, 0)
            }
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
