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
import com.phototok.domain.PhotoFolders
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Google Drive image backend. Two URI forms are owned by this source:
 * - `gdrive://<fileOrFolderId>` — a single file, or a folder the app created
 *   (under the `drive.file` scope only app-created folders are listable).
 * - `gdrive-picked://<key>` — a set of files the user granted via the Google
 *   Picker, persisted in [DrivePickedStore].
 */
@Singleton
class GoogleDriveImageSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val driveClient: GoogleDriveClient,
    private val androidExifReader: AndroidExifReader,
    private val drivePickedStore: com.phototok.data.repository.DrivePickedStore,
) : ImageSource {

    companion object {
        private const val TAG = "GoogleDriveImageSource"
        const val SCHEME = "gdrive"
        const val PICKED_SCHEME = "gdrive-picked"

        /** Hard cap for the on-disk Drive download cache. */
        private const val MAX_CACHE_BYTES = 512L * 1024 * 1024

        fun buildUri(driveId: String): Uri = Uri.parse("$SCHEME://$driveId")
        fun buildPickedUri(key: String): Uri = Uri.parse("$PICKED_SCHEME://$key")
        fun extractId(uri: Uri): String? {
            if (uri.scheme != SCHEME) return null
            return uri.host ?: uri.path?.trimStart('/')
        }
        fun extractPickedKey(uri: Uri): String? {
            if (uri.scheme != PICKED_SCHEME) return null
            return uri.host ?: uri.path?.trimStart('/')
        }
        fun isPickedUri(uri: Uri): Boolean = uri.scheme == PICKED_SCHEME
        fun isDriveUri(uri: Uri): Boolean = uri.scheme == SCHEME || uri.scheme == PICKED_SCHEME
        fun isDriveUri(uriString: String): Boolean =
            uriString.startsWith("$SCHEME://") || uriString.startsWith("$PICKED_SCHEME://")
    }

    private val cacheDir: File
        get() = File(context.cacheDir, "gdrive_cache").also { it.mkdirs() }

    // ── ImageSource ───────────────────────────────────────────────────────

    override fun owns(uri: Uri): Boolean = isDriveUri(uri)

    override fun discoverImages(folderUri: Uri): Flow<List<ImageItem>> = flow {
        val driveFiles = when {
            isPickedUri(folderUri) -> {
                val key = extractPickedKey(folderUri)
                val selection = key?.let { drivePickedStore.load(it) }
                if (selection == null) {
                    emit(emptyList())
                    return@flow
                }
                // Under drive.file the flat listing returns exactly the files the
                // app can access; intersect with this selection's picked IDs so
                // other selections and app-created copies stay out of the feed.
                val pickedIds = selection.fileIds.toSet()
                driveClient.listAccessibleImages().filter { it.id in pickedIds }
            }
            else -> {
                val folderId = extractId(folderUri)
                if (folderId == null) {
                    emit(emptyList())
                    return@flow
                }
                driveClient.listImages(folderId, recursive = true)
            }
        }
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

    /** Drive sources need no permission grant; the display name is picker-provided. */
    override suspend fun prepareSourceFolder(folderUri: Uri): String? = when {
        isPickedUri(folderUri) ->
            extractPickedKey(folderUri)?.let { drivePickedStore.load(it)?.name } ?: "Drive photos"
        extractId(folderUri) != null -> "Google Drive"
        else -> null
    }

    /** Picked selections resolve their stored display name; folders are picker-named. */
    override suspend fun resolveFolderName(folderUri: Uri): String? =
        if (isPickedUri(folderUri)) {
            extractPickedKey(folderUri)?.let { drivePickedStore.load(it)?.name }
        } else {
            null
        }

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
        // A picked selection is not a folder — collection actions on picked
        // sources land in the app-created "PhotoTok" folder in the Drive root
        // (creating files/folders in the root is allowed under drive.file).
        val destFolderId = extractId(destFolderUri)
            ?: driveClient.findOrCreateFolder("root", PhotoFolders.DRIVE_APP_FOLDER)
            ?: return false
        val targetFolderId = if (sorting) {
            driveClient.findOrCreateFolder(destFolderId, subfolderName) ?: return false
        } else {
            destFolderId
        }
        return if (move) {
            // Under drive.file, removing the old parent can be rejected when the
            // app has no access to that parent folder (picked files). Fall back
            // to copy + trash, which only needs access to the file itself.
            driveClient.moveFile(fileId, targetFolderId) ||
                (driveClient.copyFile(fileId, targetFolderId) != null &&
                    driveClient.trashFile(fileId))
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
