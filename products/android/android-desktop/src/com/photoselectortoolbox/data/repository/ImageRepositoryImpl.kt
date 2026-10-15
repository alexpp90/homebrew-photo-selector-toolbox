package com.photoselectortoolbox.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.photoselector.core.model.ExifData
import com.photoselector.core.reader.AndroidExifReader
import com.photoselector.core.reader.MediaStoreReader
import com.photoselectortoolbox.data.model.ImageDimensions
import com.photoselectortoolbox.data.model.ImageItem
import com.photoselectortoolbox.data.source.LocalImageSource
import com.photoselectortoolbox.domain.format.ImageFormats
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.lastOrNull
import kotlinx.coroutines.withContext

@Singleton
class ImageRepositoryImpl @Inject constructor(
    private val localImageSource: LocalImageSource,
    private val androidExifReader: AndroidExifReader,
    private val mediaStoreReader: MediaStoreReader,
    private val settingsRepository: SettingsRepository,
) : ImageRepository {

    companion object {
        private const val TAG = "ImageRepositoryImpl"
        private const val SELECTION_FOLDER_NAME = "Selection"
        private const val RAW_SUBFOLDER = "RAW"
        private const val JPEG_SUBFOLDER = "JPEG"

    }

    /**
     * Persist the grant, then confirm the folder resolves and still exists.
     *
     * The grant is taken on a best-effort basis: a folder handed over for this
     * process only — which is what the test fakes and some pickers give — is
     * still perfectly readable, so a failure to persist is logged and the open
     * continues. A folder that does not resolve is the real failure.
     */
    override suspend fun openFolder(context: Context, folderUri: Uri): String? =
        withContext(Dispatchers.IO) {
            try {
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(folderUri, takeFlags)
            } catch (e: Exception) {
                Log.w(TAG, "Could not persist URI permission for $folderUri", e)
            }

            val folderDoc = try {
                DocumentFile.fromTreeUri(context, folderUri)
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException opening folder $folderUri", e)
                null
            }

            if (folderDoc == null || !folderDoc.exists()) return@withContext null
            folderDoc.name ?: "Unknown"
        }

    override fun discoverImages(folderUri: Uri): Flow<List<ImageItem>> =
        localImageSource.discoverImages(folderUri)

    /**
     * The last emission is the complete folder by contract, so collecting to it
     * is the whole implementation — and it keeps the "progressive discovery
     * exists" knowledge in one place rather than at each one-shot call site.
     */
    override suspend fun discoverAllImages(folderUri: Uri): List<ImageItem> =
        withContext(Dispatchers.IO) { discoverImages(folderUri).lastOrNull().orEmpty() }

    override suspend fun getExifData(context: Context, uri: Uri): ExifData? {
        // Try the primary ExifInterface reader first
        val exifData = androidExifReader.readExif(context, uri)
        if (exifData != null) return exifData

        // Fall back to MediaStore reader
        return mediaStoreReader.readExif(context, uri)
    }

    /**
     * SAF deletes are unlinks with no trash behind them, so this is always false
     * and the UI offers no UNDO — the honest answer, and better than an UNDO
     * that silently does nothing. Kept as a question the repository answers
     * rather than a constant the UI assumes, because it is a property of the
     * backend and the selector must not start guessing at those again.
     */
    override fun canTrash(uri: Uri): Boolean = false

    override suspend fun restoreFromTrash(context: Context, uri: Uri): Boolean = false

    override suspend fun deleteImage(context: Context, uri: Uri): Boolean =
        withContext(Dispatchers.IO) {
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
        sorting: Boolean
    ): FileOperationResult = withContext(Dispatchers.IO) {
        val source = sourceUri.toString()
        try {
            val folderName = settingsRepository.selectionFolderName.first()
                .trim().ifEmpty { SELECTION_FOLDER_NAME }
            val destination = copyImageInternal(context, sourceUri, destFolderUri, sorting, folderName)
                ?: return@withContext FileOperationResult.failure(source, "Copy step failed")
            if (deleteImage(context, sourceUri)) {
                FileOperationResult.success(source, destination)
            } else {
                // The copy landed but the original is still there. Report the
                // destination anyway: the caller has two files now and needs the
                // URI to clean one of them up.
                FileOperationResult(
                    sourceUri = source,
                    destinationUri = destination,
                    success = false,
                    error = "Copied, but the original could not be removed",
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to move image: $sourceUri", e)
            FileOperationResult.failure(source, e.message)
        }
    }

    override suspend fun copyImage(
        context: Context,
        sourceUri: Uri,
        destFolderUri: Uri,
        sorting: Boolean
    ): FileOperationResult = withContext(Dispatchers.IO) {
        val source = sourceUri.toString()
        try {
            val folderName = settingsRepository.selectionFolderName.first()
                .trim().ifEmpty { SELECTION_FOLDER_NAME }
            val destination = copyImageInternal(context, sourceUri, destFolderUri, sorting, folderName)
            if (destination != null) {
                FileOperationResult.success(source, destination)
            } else {
                FileOperationResult.failure(source, "Could not write to the $folderName folder")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy image: $sourceUri", e)
            FileOperationResult.failure(source, e.message)
        }
    }

    /**
     * Take a filed photograph back out of the Selection.
     *
     * Implemented as an unsorted move in the opposite direction rather than as a
     * second code path that could disagree with the first. See
     * [ImageRepository.undoMove] for the parent-folder limitation.
     */
    override suspend fun undoMove(
        context: Context,
        movedTo: Uri,
        restoreFolderUri: Uri,
    ): FileOperationResult = moveImage(
        context = context,
        sourceUri = movedTo,
        destFolderUri = restoreFolderUri,
        sorting = false,
    )

    /** Copy the file and return the destination URI, or null if it did not land. */
    private fun copyImageInternal(
        context: Context,
        sourceUri: Uri,
        destFolderUri: Uri,
        sorting: Boolean,
        selectionFolderName: String = SELECTION_FOLDER_NAME,
    ): String? {
        val sourceDoc = DocumentFile.fromSingleUri(context, sourceUri) ?: return null
        val fileName = sourceDoc.name ?: return null
        val mimeType = sourceDoc.type ?: "application/octet-stream"

        val destFolder = DocumentFile.fromTreeUri(context, destFolderUri) ?: return null

        val targetFolder = if (sorting) {
            // Create Selection folder using the user's configured selectionFolderName
            val selectionDir = destFolder.findFile(selectionFolderName)
                ?: destFolder.createDirectory(selectionFolderName)
                ?: return null

            // Determine the correct subfolder based on file extension
            determineTargetFolder(fileName, selectionDir)
        } else {
            destFolder
        }

        val destFile = targetFolder.createFile(mimeType, fileName) ?: return null

        try {
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                context.contentResolver.openOutputStream(destFile.uri)?.use { output ->
                    input.copyTo(output)
                    return destFile.uri.toString()
                }
            }
        } catch (e: Exception) {
            // Never leave a half-written file in the Selection: a truncated
            // photograph that looks filed is worse than a failed move.
            cleanUpFailedCopy(destFile)
            throw e
        }

        // Streams could not be opened — remove the empty placeholder file.
        cleanUpFailedCopy(destFile)
        return null
    }

    private fun cleanUpFailedCopy(destFile: DocumentFile) {
        try {
            destFile.delete()
        } catch (e: Exception) {
            Log.w(TAG, "Could not clean up failed copy: ${destFile.uri}", e)
        }
    }

    /**
     * The subfolder of [selectionDir] a file is sorted into — the rule itself
     * lives in [ImageFormats.selectionSubfolder], shared with
     * MoveToSelectionUseCase so the two filing paths cannot disagree.
     */
    private fun determineTargetFolder(
        fileName: String,
        selectionDir: DocumentFile
    ): DocumentFile {
        fun sub(name: String): DocumentFile? =
            selectionDir.findFile(name) ?: selectionDir.createDirectory(name)
        return when (ImageFormats.selectionSubfolder(fileName)) {
            ImageFormats.SelectionSubfolder.RAW -> sub(RAW_SUBFOLDER) ?: selectionDir
            ImageFormats.SelectionSubfolder.JPEG -> sub(JPEG_SUBFOLDER) ?: selectionDir
            ImageFormats.SelectionSubfolder.ROOT -> selectionDir
        }
    }

    override suspend fun getImageDimensions(context: Context, uri: Uri): Pair<Int, Int> =
        localImageSource.getImageDimensions(uri)

    override suspend fun resolveDimensions(
        context: Context,
        uris: Collection<String>,
    ): Map<String, ImageDimensions> = withContext(Dispatchers.IO) {
        localImageSource.resolveDimensions(uris)
    }
}
