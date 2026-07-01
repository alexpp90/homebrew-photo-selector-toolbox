package com.phototok.data.source

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.phototok.data.model.ExifData
import com.phototok.data.model.ImageItem
import com.phototok.data.reader.AndroidExifReader
import com.phototok.data.reader.MediaStoreReader
import com.phototok.domain.PhotoExtensions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Local (SAF / DocumentFile) image backend. */
interface LocalImageSource : ImageSource

@Singleton
class LocalImageSourceImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val androidExifReader: AndroidExifReader,
    private val mediaStoreReader: MediaStoreReader,
) : LocalImageSource {

    companion object {
        private const val TAG = "LocalImageSource"
        private const val RAW_SUBFOLDER = "RAW"
        private const val JPEG_SUBFOLDER = "JPEG"
        private const val EDIT_SUFFIX = "-Edit"

        val SUPPORTED_EXTENSIONS = setOf(
            "jpg", "jpeg", "png", "tiff", "tif", "bmp", "gif", "webp",
            "heif", "heic", "dng", "cr2", "cr3", "nef", "arw", "orf",
            "rw2", "pef", "srw", "raf", "nrw"
        )

        private val EXCLUDED_FOLDER_NAMES = setOf("selection", "selected", "phototok_selection")
    }

    // ── ImageSource ───────────────────────────────────────────────────────

    /** The local source owns every URI no other source claims (content://, file://). */
    override fun owns(uri: Uri): Boolean =
        uri.scheme == "content" || uri.scheme == "file"

    override fun discoverImages(folderUri: Uri): Flow<List<ImageItem>> = flow {
        val folder = DocumentFile.fromTreeUri(context, folderUri)
        if (folder == null || !folder.isDirectory) {
            Log.w(TAG, "Invalid folder URI: $folderUri")
            emit(emptyList())
            return@flow
        }

        val images = mutableListOf<ImageItem>()
        enumerateImages(folder, images)

        val sorted = images.sortedBy { it.fileName.lowercase() }
        emit(sorted)
    }.flowOn(Dispatchers.IO)

    override suspend fun prepareSourceFolder(folderUri: Uri): String? =
        withContext(Dispatchers.IO) {
            try {
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(folderUri, takeFlags)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to persist URI permission for $folderUri", e)
            }
            val folderDoc = try {
                DocumentFile.fromTreeUri(context, folderUri)?.takeIf { it.exists() }
            } catch (e: SecurityException) {
                null
            }
            folderDoc?.let { it.name ?: "Photos" }
        }

    override suspend fun resolveFolderName(folderUri: Uri): String? =
        withContext(Dispatchers.IO) {
            try {
                DocumentFile.fromTreeUri(context, folderUri)?.name
            } catch (_: Exception) {
                null
            }
        }

    override suspend fun getExifData(uri: Uri): ExifData? {
        val exifData = androidExifReader.readExif(context, uri)
        if (exifData != null) return exifData
        return mediaStoreReader.readExif(context, uri)
    }

    override suspend fun getImageDimensions(uri: Uri): Pair<Int, Int> =
        withContext(Dispatchers.IO) { readImageDimensions(uri) }

    override suspend fun deleteImage(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val docFile = DocumentFile.fromSingleUri(context, uri)
            docFile?.delete() ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete image: $uri", e)
            false
        }
    }

    override suspend fun copyImage(
        sourceUri: Uri,
        destFolderUri: Uri,
        sorting: Boolean,
        subfolderName: String,
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            copyImageInternal(sourceUri, destFolderUri, sorting, subfolderName)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy image: $sourceUri", e)
            false
        }
    }

    override suspend fun moveImage(
        sourceUri: Uri,
        destFolderUri: Uri,
        sorting: Boolean,
        subfolderName: String,
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val copied = copyImageInternal(sourceUri, destFolderUri, sorting, subfolderName)
            if (copied) deleteImage(sourceUri) else false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to move image: $sourceUri", e)
            false
        }
    }

    override suspend fun listSelectionImages(
        folderUri: Uri,
        subfolderName: String,
    ): SelectionListing = withContext(Dispatchers.IO) {
        val selectionDir = try {
            DocumentFile.fromTreeUri(context, folderUri)?.findFile(subfolderName)
        } catch (_: Exception) {
            null
        }
        if (selectionDir == null || !selectionDir.exists()) {
            return@withContext SelectionListing.Missing
        }
        // Enumerate the sub-folder directly: passing a child document URI through
        // discoverImages() would re-resolve to the tree root via fromTreeUri().
        val images = mutableListOf<ImageItem>()
        enumerateImages(selectionDir, images)
        SelectionListing.Available(
            images = images.sortedByDescending { it.lastModified },
            folderName = selectionDir.name ?: "Selection",
        )
    }

    // ── Internals ─────────────────────────────────────────────────────────

    /**
     * Read width and height from image header only (no pixel decode).
     * Returns (0, 0) if the dimensions cannot be determined.
     */
    private fun readImageDimensions(uri: Uri): Pair<Int, Int> {
        return try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, opts)
            }
            val w = opts.outWidth.coerceAtLeast(0)
            val h = opts.outHeight.coerceAtLeast(0)
            Pair(w, h)
        } catch (e: Exception) {
            Log.d(TAG, "Cannot read dimensions for $uri: ${e.message}")
            Pair(0, 0)
        }
    }

    /** Recursively collect supported images, skipping excluded/hidden entries. */
    private fun enumerateImages(folder: DocumentFile, results: MutableList<ImageItem>) {
        val files = folder.listFiles()

        for (file in files) {
            if (file.isDirectory) {
                val folderName = file.name?.lowercase() ?: continue
                if (folderName in EXCLUDED_FOLDER_NAMES) {
                    Log.d(TAG, "Skipping excluded folder: ${file.name}")
                    continue
                }
                enumerateImages(file, results)
                continue
            }

            if (!file.isFile) continue

            val fileName = file.name ?: continue
            if (fileName.startsWith(".")) continue
            val extension = fileName.substringAfterLast('.', "").lowercase()

            if (extension !in SUPPORTED_EXTENSIONS) continue

            val imageItem = ImageItem(
                uri = file.uri.toString(),
                fileName = fileName,
                fileSize = file.length(),
                lastModified = file.lastModified(),
                mimeType = file.type,
                imageWidth = 0,
                imageHeight = 0,
            )
            results.add(imageItem)
        }
    }

    private fun copyImageInternal(
        sourceUri: Uri,
        destFolderUri: Uri,
        sorting: Boolean,
        subfolderName: String,
    ): Boolean {
        val sourceDoc = DocumentFile.fromSingleUri(context, sourceUri) ?: return false
        val fileName = sourceDoc.name ?: return false
        val mimeType = sourceDoc.type ?: "application/octet-stream"

        val destFolder = DocumentFile.fromTreeUri(context, destFolderUri) ?: return false

        val targetFolder = if (sorting) {
            val selectionDir = destFolder.findFile(subfolderName)
                ?: destFolder.createDirectory(subfolderName)
                ?: return false
            determineTargetFolder(fileName, selectionDir)
        } else {
            destFolder
        }

        val destFile = targetFolder.createFile(mimeType, fileName) ?: return false

        try {
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                context.contentResolver.openOutputStream(destFile.uri)?.use { output ->
                    input.copyTo(output)
                    return true
                }
            }
        } catch (e: Exception) {
            // Don't leave a partially written file behind.
            cleanUpFailedCopy(destFile)
            throw e
        }

        // Streams could not be opened — remove the empty placeholder file.
        cleanUpFailedCopy(destFile)
        return false
    }

    private fun cleanUpFailedCopy(destFile: DocumentFile) {
        try {
            destFile.delete()
        } catch (e: Exception) {
            Log.w(TAG, "Could not clean up failed copy: ${destFile.uri}", e)
        }
    }

    /**
     * Determine which subfolder a file belongs in based on its extension.
     * RAW files → RAW subfolder, JPEG → JPEG subfolder,
     * Lightroom edits → RAW subfolder, XMP sidecars → follow parent type.
     */
    // Internal (not private) so the sorting rules can be unit-tested directly.
    internal fun determineTargetFolder(
        fileName: String,
        selectionDir: DocumentFile,
    ): DocumentFile {
        val extension = PhotoExtensions.extensionOf(fileName)
        val stem = fileName.substringBeforeLast('.')

        val rawDir by lazy {
            selectionDir.findFile(RAW_SUBFOLDER)
                ?: selectionDir.createDirectory(RAW_SUBFOLDER)
        }
        val jpegDir by lazy {
            selectionDir.findFile(JPEG_SUBFOLDER)
                ?: selectionDir.createDirectory(JPEG_SUBFOLDER)
        }

        return when {
            extension in PhotoExtensions.RAW -> rawDir ?: selectionDir
            extension in PhotoExtensions.JPEG -> jpegDir ?: selectionDir
            stem.endsWith(EDIT_SUFFIX) -> rawDir ?: selectionDir
            extension == PhotoExtensions.XMP -> {
                val dotIndex = stem.lastIndexOf('.')
                val parentExt = if (dotIndex > 0) stem.substring(dotIndex + 1).lowercase() else null
                if (parentExt != null && parentExt in PhotoExtensions.RAW) {
                    rawDir ?: selectionDir
                } else {
                    selectionDir
                }
            }
            else -> selectionDir
        }
    }
}
