package com.phototok.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.phototok.data.model.ExifData
import com.phototok.data.model.ImageItem
import com.phototok.data.reader.AndroidExifReader
import com.phototok.data.reader.MediaStoreReader
import com.phototok.data.source.LocalImageSource
import com.phototok.data.source.googledrive.GoogleDriveClient
import com.phototok.data.source.googledrive.GoogleDriveImageSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImageRepositoryImpl @Inject constructor(
    private val localImageSource: LocalImageSource,
    private val androidExifReader: AndroidExifReader,
    private val mediaStoreReader: MediaStoreReader,
    private val driveImageSource: GoogleDriveImageSource,
    private val driveClient: GoogleDriveClient,
) : ImageRepository {

    companion object {
        private const val TAG = "ImageRepositoryImpl"
        const val SELECTION_FOLDER_NAME = "PhotoTok_Selection"
        const val LEFT_SWIPE_FOLDER_NAME = "PhotoTok_LeftSwipe"
        private const val RAW_SUBFOLDER = "RAW"
        private const val JPEG_SUBFOLDER = "JPEG"

        private val RAW_EXTENSIONS = setOf(
            "arw", "cr2", "cr3", "nef", "nrw", "orf", "raf", "rw2",
            "pef", "srw", "dng", "raw", "3fr", "ari", "bay", "cap",
            "iiq", "eip", "erf", "fff", "mef", "mdc", "mos", "mrw",
            "obm", "ptx", "pxn", "rwl", "rwz", "sr2", "srf", "x3f"
        )

        private val JPEG_EXTENSIONS = setOf("jpg", "jpeg")
        private const val XMP_EXTENSION = "xmp"
        private const val EDIT_SUFFIX = "-Edit"
    }

    override fun discoverImages(folderUri: Uri): Flow<List<ImageItem>> {
        if (GoogleDriveImageSource.isDriveUri(folderUri)) {
            val folderId = GoogleDriveImageSource.extractId(folderUri)
                ?: return localImageSource.discoverImages(folderUri)
            return driveImageSource.discoverImages(folderId)
        }
        return localImageSource.discoverImages(folderUri)
    }

    override suspend fun getExifData(context: Context, uri: Uri): ExifData? {
        if (GoogleDriveImageSource.isDriveUri(uri)) {
            val fileId = GoogleDriveImageSource.extractId(uri) ?: return null
            val cached = driveImageSource.ensureCached(fileId, fileId) ?: return null
            return androidExifReader.readExif(context, Uri.fromFile(cached))
        }
        val exifData = androidExifReader.readExif(context, uri)
        if (exifData != null) return exifData
        return mediaStoreReader.readExif(context, uri)
    }

    override suspend fun deleteImage(context: Context, uri: Uri): Boolean =
        withContext(Dispatchers.IO) {
            if (GoogleDriveImageSource.isDriveUri(uri)) {
                val fileId = GoogleDriveImageSource.extractId(uri) ?: return@withContext false
                return@withContext driveClient.trashFile(fileId)
            }
            try {
                val docFile = DocumentFile.fromSingleUri(context, uri)
                docFile?.delete() ?: false
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete image: $uri", e)
                false
            }
        }

    override suspend fun moveImage(
        context: Context,
        sourceUri: Uri,
        destFolderUri: Uri,
        sorting: Boolean,
        subfolderName: String
    ): Boolean = withContext(Dispatchers.IO) {
        if (GoogleDriveImageSource.isDriveUri(sourceUri) && GoogleDriveImageSource.isDriveUri(destFolderUri)) {
            return@withContext driveMoveCopy(sourceUri, destFolderUri, sorting, move = true, subfolderName = subfolderName)
        }
        try {
            val copied = copyImageInternal(context, sourceUri, destFolderUri, sorting, subfolderName = subfolderName)
            if (copied) { deleteImage(context, sourceUri) } else { false }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to move image: $sourceUri", e)
            false
        }
    }

    override suspend fun copyImage(
        context: Context,
        sourceUri: Uri,
        destFolderUri: Uri,
        sorting: Boolean,
        subfolderName: String
    ): Boolean = withContext(Dispatchers.IO) {
        if (GoogleDriveImageSource.isDriveUri(sourceUri) && GoogleDriveImageSource.isDriveUri(destFolderUri)) {
            return@withContext driveMoveCopy(sourceUri, destFolderUri, sorting, move = false, subfolderName = subfolderName)
        }
        try {
            copyImageInternal(context, sourceUri, destFolderUri, sorting, subfolderName = subfolderName)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy image: $sourceUri", e)
            false
        }
    }

    private suspend fun driveMoveCopy(
        sourceUri: Uri, destFolderUri: Uri, sorting: Boolean, move: Boolean, subfolderName: String,
    ): Boolean {
        val fileId = GoogleDriveImageSource.extractId(sourceUri) ?: return false
        val destFolderId = GoogleDriveImageSource.extractId(destFolderUri) ?: return false
        val targetFolderId = if (sorting) {
            driveClient.findOrCreateFolder(destFolderId, subfolderName) ?: return false
        } else { destFolderId }
        return if (move) {
            driveClient.moveFile(fileId, destFolderId, targetFolderId)
        } else {
            driveClient.copyFile(fileId, targetFolderId) != null
        }
    }

    private fun copyImageInternal(
        context: Context,
        sourceUri: Uri,
        destFolderUri: Uri,
        sorting: Boolean,
        subfolderName: String
    ): Boolean {
        val sourceDoc = DocumentFile.fromSingleUri(context, sourceUri) ?: return false
        val fileName = sourceDoc.name ?: return false
        val mimeType = sourceDoc.type ?: "application/octet-stream"

        val destFolder = DocumentFile.fromTreeUri(context, destFolderUri) ?: return false

        val targetFolder = if (sorting) {
            // Create target folder
            val selectionDir = destFolder.findFile(subfolderName)
                ?: destFolder.createDirectory(subfolderName)
                ?: return false

            // Determine the correct subfolder based on file extension
            determineTargetFolder(fileName, selectionDir)
        } else {
            destFolder
        }

        val destFile = targetFolder.createFile(mimeType, fileName) ?: return false

        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            context.contentResolver.openOutputStream(destFile.uri)?.use { output ->
                input.copyTo(output)
                return true
            }
        }

        return false
    }

    /**
     * Determine which subfolder a file belongs in based on its extension.
     * RAW files → RAW subfolder, JPEG → JPEG subfolder,
     * Lightroom edits → RAW subfolder, XMP sidecars → follow parent type.
     */
    private fun determineTargetFolder(
        fileName: String,
        selectionDir: DocumentFile
    ): DocumentFile {
        val extension = fileName.substringAfterLast('.', "").lowercase()
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
            extension in RAW_EXTENSIONS -> rawDir ?: selectionDir
            extension in JPEG_EXTENSIONS -> jpegDir ?: selectionDir
            stem.endsWith(EDIT_SUFFIX) -> rawDir ?: selectionDir
            extension == XMP_EXTENSION -> {
                val dotIndex = stem.lastIndexOf('.')
                val parentExt = if (dotIndex > 0) stem.substring(dotIndex + 1).lowercase() else null
                if (parentExt != null && parentExt in RAW_EXTENSIONS) {
                    rawDir ?: selectionDir
                } else {
                    selectionDir
                }
            }
            else -> selectionDir
        }
    }

    override suspend fun getImageDimensions(context: Context, uri: Uri): Pair<Int, Int> {
        if (GoogleDriveImageSource.isDriveUri(uri)) {
            val fileId = GoogleDriveImageSource.extractId(uri) ?: return Pair(0, 0)
            val cached = driveImageSource.ensureCached(fileId, fileId) ?: return Pair(0, 0)
            return localImageSource.getImageDimensions(Uri.fromFile(cached))
        }
        return localImageSource.getImageDimensions(uri)
    }
}
