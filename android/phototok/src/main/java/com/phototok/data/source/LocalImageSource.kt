package com.phototok.data.source

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.phototok.data.model.ExifData
import com.phototok.data.model.ImageItem
import com.phototok.data.reader.AndroidExifReader
import com.phototok.data.reader.MediaStoreReader
import com.phototok.domain.PhotoExtensions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

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

        /**
         * How many images to gather before the very first emission. Small on
         * purpose: it is the number of photos the user needs before they can
         * start swiping, and the rest of the tree keeps streaming in behind
         * them. Must be > the viewer's prefetch radius so paging stays warm.
         */
        internal const val FIRST_BATCH_SIZE = 24

        /** How many further images to gather between subsequent emissions. */
        internal const val BATCH_SIZE = 250

        /**
         * One query per directory returning everything needed to build an
         * [ImageItem]. `DocumentFile` instead costs a separate ContentResolver
         * query per file *per attribute* (`name`, `length`, `lastModified`,
         * `type`), i.e. ~5 binder round-trips per photo — which is what made
         * opening a folder of a few thousand photos take tens of seconds.
         */
        private val CHILD_PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
    }

    // ── ImageSource ───────────────────────────────────────────────────────

    /** The local source owns every URI no other source claims (content://, file://). */
    override fun owns(uri: Uri): Boolean =
        uri.scheme == "content" || uri.scheme == "file"

    /**
     * Walk the tree and emit the feed **progressively**: a first small batch as
     * soon as [FIRST_BATCH_SIZE] photos are known, then every [BATCH_SIZE]
     * photos, then a final complete list. Each emission is the cumulative list.
     *
     * The user can therefore start swiping while a folder with thousands of
     * photos is still being enumerated, instead of waiting for the whole tree.
     * Consumers must treat the emissions as append-only (see
     * `PhoneFeedOrdering.appendBatch`) so already-visible photos do not move.
     */
    override fun discoverImages(folderUri: Uri): Flow<List<ImageItem>> = flow {
        val rootDocumentId = treeDocumentIdOf(folderUri)
        if (rootDocumentId == null) {
            Log.w(TAG, "Invalid folder URI: $folderUri")
            emit(emptyList())
            return@flow
        }

        val images = mutableListOf<ImageItem>()
        var nextEmitAt = FIRST_BATCH_SIZE

        walkImages(folderUri, rootDocumentId) { image ->
            images.add(image)
            if (images.size >= nextEmitAt) {
                emit(images.toList())
                nextEmitAt = images.size + BATCH_SIZE
            }
        }

        // Final snapshot — also the only emission for folders smaller than a batch.
        emit(images.toList())
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
        // Walk the sub-folder directly: passing a child document URI through
        // discoverImages() would re-resolve to the tree root via fromTreeUri().
        val selectionDocumentId = documentIdOf(selectionDir.uri)
            ?: return@withContext SelectionListing.Missing
        val images = mutableListOf<ImageItem>()
        walkImages(folderUri, selectionDocumentId) { images.add(it) }
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

    /** The tree document id of a `content://…/tree/<id>` URI, or null. */
    private fun treeDocumentIdOf(treeUri: Uri): String? = try {
        DocumentsContract.getTreeDocumentId(treeUri)
    } catch (e: Exception) {
        Log.w(TAG, "Not a tree URI: $treeUri", e)
        null
    }

    /** The document id of a document URI inside a tree, or null. */
    private fun documentIdOf(documentUri: Uri): String? = try {
        DocumentsContract.getDocumentId(documentUri)
    } catch (e: Exception) {
        Log.w(TAG, "Not a document URI: $documentUri", e)
        null
    }

    /**
     * Breadth-first walk of [treeUri] starting at [startDocumentId], invoking
     * [onImage] for every supported image found. Sub-folders whose name is in
     * [EXCLUDED_FOLDER_NAMES] are skipped, as are hidden files.
     *
     * Iterative rather than recursive (deep trees must not risk the stack) and
     * cursor-based rather than `DocumentFile`-based, which is what makes it
     * fast enough to stream: one query per directory instead of roughly five
     * per file. [onImage] is suspending so callers can emit mid-walk.
     */
    private suspend fun walkImages(
        treeUri: Uri,
        startDocumentId: String,
        onImage: suspend (ImageItem) -> Unit,
    ) {
        val queue = ArrayDeque<String>()
        val seenDirectories = mutableSetOf(startDocumentId)
        queue.addLast(startDocumentId)

        while (queue.isNotEmpty()) {
            coroutineContext.ensureActive()
            val parentId = queue.removeFirst()
            val cursor = queryChildren(treeUri, parentId) ?: continue

            cursor.use { c ->
                val idIndex = c.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIndex = c.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIndex = c.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeIndex = c.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                val modifiedIndex = c.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                if (idIndex < 0 || nameIndex < 0 || mimeIndex < 0) {
                    Log.w(TAG, "Document provider omitted required columns for $parentId")
                    return@use
                }

                while (c.moveToNext()) {
                    coroutineContext.ensureActive()
                    val documentId = c.getStringOrNull(idIndex) ?: continue
                    val name = c.getStringOrNull(nameIndex) ?: continue
                    val mimeType = c.getStringOrNull(mimeIndex)

                    if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                        if (name.lowercase() in EXCLUDED_FOLDER_NAMES) {
                            Log.d(TAG, "Skipping excluded folder: $name")
                            continue
                        }
                        if (seenDirectories.add(documentId)) queue.addLast(documentId)
                        continue
                    }

                    if (name.startsWith(".")) continue
                    if (name.substringAfterLast('.', "").lowercase() !in SUPPORTED_EXTENSIONS) {
                        continue
                    }

                    onImage(
                        ImageItem(
                            uri = DocumentsContract
                                .buildDocumentUriUsingTree(treeUri, documentId)
                                .toString(),
                            fileName = name,
                            fileSize = if (sizeIndex >= 0) c.getLongOrZero(sizeIndex) else 0L,
                            lastModified = if (modifiedIndex >= 0) c.getLongOrZero(modifiedIndex) else 0L,
                            mimeType = mimeType,
                            imageWidth = 0,
                            imageHeight = 0,
                        )
                    )
                }
            }
        }
    }

    /** Children of [parentDocumentId] with everything needed for an [ImageItem]. */
    private fun queryChildren(treeUri: Uri, parentDocumentId: String): Cursor? {
        val childrenUri = try {
            DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocumentId)
        } catch (e: Exception) {
            Log.w(TAG, "Cannot build children URI for $parentDocumentId", e)
            return null
        }
        return try {
            context.contentResolver.query(childrenUri, CHILD_PROJECTION, null, null, null)
        } catch (e: Exception) {
            // A single unreadable directory must not abort the whole walk.
            Log.w(TAG, "Cannot list children of $parentDocumentId", e)
            null
        }
    }

    private fun Cursor.getStringOrNull(index: Int): String? =
        if (isNull(index)) null else getString(index)

    private fun Cursor.getLongOrZero(index: Int): Long =
        if (isNull(index)) 0L else getLong(index)

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
