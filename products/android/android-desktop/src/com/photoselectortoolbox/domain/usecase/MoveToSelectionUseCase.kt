package com.photoselectortoolbox.domain.usecase

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.photoselectortoolbox.data.model.ImageItem
import com.photoselectortoolbox.data.repository.FileOperationResult
import com.photoselectortoolbox.data.repository.ImageRepository
import com.photoselectortoolbox.domain.format.ImageFormats
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Result of a move/copy operation for a single image.
 *
 * This was the first result shape in the product to carry the destination URI,
 * and it is now *the* shape: [ImageRepository.moveImage]/`copyImage` return the
 * same type instead of a `Boolean` that discarded where the file went. Kept as
 * an alias rather than a second data class so there is one result to reason
 * about, and so existing call sites and tests are untouched.
 */
typealias MoveResult = FileOperationResult

/**
 * Handles moving or copying images into a Selection folder structure.
 * Organizes files into subfolders by type:
 * - RAW files -> "RAW" subfolder
 * - JPEG files -> "JPEG" subfolder
 * - XMP sidecars follow their parent image's subfolder
 * - Lightroom edit files (*-Edit.*) -> "RAW" subfolder
 */
class MoveToSelectionUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val SELECTION_FOLDER_NAME = "Selection"
        private const val RAW_SUBFOLDER = "RAW"
        private const val JPEG_SUBFOLDER = "JPEG"

    }

    /**
     * Move or copy a list of images to the Selection folder within the given
     * parent directory.
     *
     * @param images The images to move/copy.
     * @param parentDirUri The URI of the parent directory where "Selection" will be created.
     * @param copy If true, copy files instead of moving.
     * @return List of results for each image operation.
     */
    suspend fun invoke(
        images: List<ImageItem>,
        parentDirUri: Uri,
        copy: Boolean = false,
        selectionFolderName: String = SELECTION_FOLDER_NAME
    ): List<MoveResult> = withContext(Dispatchers.IO) {
        val folderName = selectionFolderName.trim().ifEmpty { SELECTION_FOLDER_NAME }
        val parentDir = DocumentFile.fromTreeUri(context, parentDirUri)
            ?: return@withContext images.map { image ->
                MoveResult(image.uri, null, false, "Cannot access parent directory")
            }

        // Create or get Selection folder
        val selectionDir = parentDir.findFile(folderName)
            ?: parentDir.createDirectory(folderName)
            ?: return@withContext images.map { image ->
                MoveResult(image.uri, null, false, "Cannot create $folderName folder")
            }

        // Create subfolders
        val rawDir = selectionDir.findFile(RAW_SUBFOLDER)
            ?: selectionDir.createDirectory(RAW_SUBFOLDER)
        val jpegDir = selectionDir.findFile(JPEG_SUBFOLDER)
            ?: selectionDir.createDirectory(JPEG_SUBFOLDER)

        val results = images.map { image ->
            async {
                ensureActive()
                processImage(image, selectionDir, rawDir, jpegDir, copy)
            }
        }.awaitAll()

        results
    }

    /**
     * Process a single image: determine its target subfolder and copy/move it.
     */
    private suspend fun processImage(
        image: ImageItem,
        selectionDir: DocumentFile,
        rawDir: DocumentFile?,
        jpegDir: DocumentFile?,
        copy: Boolean
    ): MoveResult = withContext(Dispatchers.IO) {
        try {
            val targetDir = determineTargetFolder(image.fileName, selectionDir, rawDir, jpegDir)
                ?: return@withContext MoveResult(image.uri, null, false, "Cannot determine target folder")

            val sourceUri = Uri.parse(image.uri)
            val mimeType = image.mimeType ?: "application/octet-stream"

            // Create destination file
            val destFile = targetDir.createFile(mimeType, image.fileName)
                ?: return@withContext MoveResult(image.uri, null, false, "Cannot create destination file")

            // Copy content and clean up orphan destination file if streams fail
            var copied = false
            try {
                context.contentResolver.openInputStream(sourceUri)?.use { input ->
                    context.contentResolver.openOutputStream(destFile.uri)?.use { output ->
                        input.copyTo(output)
                        copied = true
                    }
                }
            } catch (e: Exception) {
                try {
                    destFile.delete()
                } catch (_: Exception) {
                }
                throw e
            }

            if (!copied) {
                try {
                    destFile.delete()
                } catch (_: Exception) {
                }
                return@withContext MoveResult(image.uri, null, false, "Cannot read source file")
            }

            // If moving (not copying), delete the source
            if (!copy) {
                try {
                    val sourceDoc = DocumentFile.fromSingleUri(context, sourceUri)
                    sourceDoc?.delete()
                } catch (e: Exception) {
                    // Source deletion failure is non-fatal for the copy operation
                }
            }

            MoveResult(
                sourceUri = image.uri,
                destinationUri = destFile.uri.toString(),
                success = true
            )
        } catch (e: Exception) {
            MoveResult(image.uri, null, false, e.message)
        }
    }

    /**
     * The subfolder an image is sorted into; the rule is
     * [ImageFormats.selectionSubfolder] (Lightroom `-Edit` files and RAWs → RAW,
     * JPEG → JPEG, XMP follows a RAW parent, everything else → the root).
     */
    private fun determineTargetFolder(
        fileName: String,
        selectionDir: DocumentFile,
        rawDir: DocumentFile?,
        jpegDir: DocumentFile?
    ): DocumentFile? = when (ImageFormats.selectionSubfolder(fileName)) {
        ImageFormats.SelectionSubfolder.RAW -> rawDir ?: selectionDir
        ImageFormats.SelectionSubfolder.JPEG -> jpegDir ?: selectionDir
        ImageFormats.SelectionSubfolder.ROOT -> selectionDir
    }
}
