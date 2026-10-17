package com.phototok.data.source

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.photoselector.core.model.ExifData
import com.photoselector.core.preview.EmbeddedPreviewExtractor
import com.photoselector.core.reader.AndroidExifReader
import com.photoselector.core.reader.MediaStoreReader
import com.phototok.data.model.ImageItem
import com.phototok.domain.PhotoExtensions
import com.phototok.domain.TransferResult
import com.phototok.domain.TransferStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.FileInputStream
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

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
        private const val EDIT_UNDERSCORE_SUFFIX = "_Edit"

        internal const val MAX_CONCURRENT_TRANSFERS = 2

        private val EXCLUDED_FOLDER_NAMES = setOf("selection", "selected", "phototok_selection", "phototok_leftswipe")

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

        private val DISPLAY_NAME_PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        )

        private val FLAGS_PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_FLAGS,
        )

        /**
         * Extract the canonical base stem of [fileName] (stripping extension,
         * double-extension `.xmp` sidecar parent extension, and `-Edit` / `_Edit`
         * suffixes) so bundle members can share a single `-N` collision suffix.
         */
        internal fun baseStemOf(fileName: String): String {
            val ext = PhotoExtensions.extensionOf(fileName)
            var stem = fileName.substringBeforeLast('.', fileName)
            if (ext == PhotoExtensions.XMP) {
                val dotIndex = stem.lastIndexOf('.')
                val parentExt = if (dotIndex > 0) stem.substring(dotIndex + 1).lowercase() else null
                if (parentExt != null && PhotoExtensions.isSupported(stem)) {
                    stem = stem.substring(0, dotIndex)
                }
            }
            if (stem.endsWith(EDIT_SUFFIX, ignoreCase = true) && stem.length > EDIT_SUFFIX.length) {
                stem = stem.dropLast(EDIT_SUFFIX.length)
            } else if (stem.endsWith(EDIT_UNDERSCORE_SUFFIX, ignoreCase = true) && stem.length > EDIT_UNDERSCORE_SUFFIX.length) {
                stem = stem.dropLast(EDIT_UNDERSCORE_SUFFIX.length)
            }
            return stem
        }

        /**
         * Insert `-$suffixNumber` directly after [primaryStem] in [fileName] so:
         * - `IMG_0001.CR3` -> `IMG_0001-2.CR3`
         * - `IMG_0001.CR3.xmp` -> `IMG_0001-2.CR3.xmp`
         * - `IMG_0001-Edit.tif` -> `IMG_0001-2-Edit.tif`
         */
        internal fun applyCollisionSuffix(
            fileName: String,
            primaryStem: String = baseStemOf(fileName),
            suffixNumber: Int,
        ): String {
            if (suffixNumber <= 1) return fileName
            val stemToUse = if (
                primaryStem.isNotEmpty() &&
                fileName.startsWith(primaryStem, ignoreCase = true)
            ) {
                fileName.substring(0, primaryStem.length)
            } else {
                val derived = baseStemOf(fileName)
                if (derived.isNotEmpty() && fileName.startsWith(derived, ignoreCase = true)) {
                    fileName.substring(0, derived.length)
                } else {
                    fileName.substringBeforeLast('.', fileName)
                }
            }
            val remainder = fileName.substring(stemToUse.length)
            return "$stemToUse-$suffixNumber$remainder"
        }

        /**
         * Resolve collision-free destination filenames for a bundle of [fileNames]
         * (`CR3 + JPG + CR3.xmp + -Edit.tif`) so that if any member collides in its
         * target folder, the entire bundle receives the same smallest `-N` ($N \ge 2$)
         * suffix that is free across all target folders.
         */
        internal fun resolveCollisionFreeBundleNames(
            fileNames: List<String>,
            primaryStem: String = fileNames.firstOrNull()?.let(::baseStemOf).orEmpty(),
            existingNamesByFolder: Map<String, Set<String>>,
            folderForFile: (String) -> String = { "" },
        ): Map<String, String> {
            if (fileNames.isEmpty()) return emptyMap()
            val lowerByFolder = existingNamesByFolder.mapValues { (_, names) ->
                names.map { it.lowercase() }.toSet()
            }

            fun isFree(candidate: String, originalFile: String): Boolean {
                val folderKey = folderForFile(originalFile)
                val existing = lowerByFolder[folderKey] ?: emptySet()
                return candidate.lowercase() !in existing
            }

            if (fileNames.all { isFree(it, it) }) {
                return fileNames.associateWith { it }
            }

            var n = 2
            while (true) {
                val candidateMap = fileNames.associateWith { name ->
                    applyCollisionSuffix(name, primaryStem, n)
                }
                if (candidateMap.all { (orig, candidate) -> isFree(candidate, orig) }) {
                    return candidateMap
                }
                n++
            }
        }
    }

    /** 2-permit semaphore bounding concurrent SAF file transfers. */
    internal val transferSemaphore = Semaphore(MAX_CONCURRENT_TRANSFERS)

    /** Observability counters for verifying concurrency bounds in unit tests. */
    internal val activeTransfers = AtomicInteger(0)
    internal val maxObservedConcurrentTransfers = AtomicInteger(0)

    /** Optional test hook invoked inside the semaphore permit during a transfer. */
    internal var onTransferInsidePermit: (suspend () -> Unit)? = null

    /** Overridable DocumentFile and DocumentsContract collaborators for unit tests. */
    internal var documentFileFromSingleUri: (Uri) -> DocumentFile? = { uri ->
        DocumentFile.fromSingleUri(context, uri)
    }
    internal var documentFileFromTreeUri: (Uri) -> DocumentFile? = { uri ->
        DocumentFile.fromTreeUri(context, uri)
    }
    internal var documentFlagsQuery: (Uri) -> Int = { uri ->
        queryDocumentFlags(uri)
    }
    internal var sourceParentResolver: (Uri, Uri) -> Uri? = { src, tree ->
        defaultSourceParentUri(src, tree)
    }
    internal var moveDocumentInvoker: (Uri, Uri, Uri) -> Uri? = { src, srcParent, targetParent ->
        DocumentsContract.moveDocument(context.contentResolver, src, srcParent, targetParent)
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Failed to persist URI permission for $folderUri", e)
            }
            val folderDoc = try {
                documentFileFromTreeUri(folderUri)?.takeIf { it.exists() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: SecurityException) {
                null
            }
            folderDoc?.let { it.name ?: "Photos" }
        }

    override suspend fun resolveFolderName(folderUri: Uri): String? =
        withContext(Dispatchers.IO) {
            try {
                documentFileFromTreeUri(folderUri)?.name
            } catch (e: CancellationException) {
                throw e
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
            val docFile = documentFileFromSingleUri(uri)
            docFile?.delete() ?: false
        } catch (e: CancellationException) {
            throw e
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
    ): Boolean = copyImageDetailed(
        sourceUri = sourceUri,
        destFolderUri = destFolderUri,
        sorting = sorting,
        subfolderName = subfolderName,
    ).isSuccess

    override suspend fun moveImage(
        sourceUri: Uri,
        destFolderUri: Uri,
        sorting: Boolean,
        subfolderName: String,
    ): Boolean = moveImageDetailed(
        sourceUri = sourceUri,
        destFolderUri = destFolderUri,
        sorting = sorting,
        subfolderName = subfolderName,
    ).isSuccess

    override suspend fun copyImageDetailed(
        sourceUri: Uri,
        destFolderUri: Uri,
        sorting: Boolean,
        subfolderName: String,
        desiredFileName: String?,
    ): TransferResult = withTransferPermit {
        try {
            copyImageInternal(sourceUri, destFolderUri, sorting, subfolderName, desiredFileName)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy image: $sourceUri", e)
            TransferResult(TransferStatus.FAILURE, error = e.message)
        }
    }

    override suspend fun moveImageDetailed(
        sourceUri: Uri,
        destFolderUri: Uri,
        sorting: Boolean,
        subfolderName: String,
        desiredFileName: String?,
    ): TransferResult = withTransferPermit {
        try {
            moveImageInternal(sourceUri, destFolderUri, sorting, subfolderName, desiredFileName)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to move image: $sourceUri", e)
            TransferResult(TransferStatus.FAILURE, error = e.message)
        }
    }

    override suspend fun listSelectionImages(
        folderUri: Uri,
        subfolderName: String,
    ): SelectionListing = withContext(Dispatchers.IO) {
        val selectionDir = try {
            documentFileFromTreeUri(folderUri)?.findFile(subfolderName)
        } catch (e: CancellationException) {
            throw e
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
            if (w > 0 && h > 0) Pair(w, h) else embeddedPreviewDimensions(uri)
        } catch (e: Exception) {
            Log.d(TAG, "Cannot read dimensions for $uri: ${e.message}")
            Pair(0, 0)
        }
    }

    /**
     * Dimensions of a RAW the platform cannot size (e.g. CR3), from its largest
     * embedded preview — header parsing only, the preview is not decoded.
     * Orientations 5–8 are rotated by 90°, so width/height are swapped.
     */
    private fun embeddedPreviewDimensions(uri: Uri): Pair<Int, Int> = try {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            // The stream is not closed on purpose: the descriptor belongs to `pfd`.
            val preview = EmbeddedPreviewExtractor.find(FileInputStream(pfd.fileDescriptor).channel, minLongEdge = Int.MAX_VALUE)
            when {
                preview == null -> Pair(0, 0)
                preview.orientation in 5..8 -> Pair(preview.height, preview.width)
                else -> Pair(preview.width, preview.height)
            }
        } ?: Pair(0, 0)
    } catch (e: Exception) {
        Pair(0, 0)
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
                    if (!PhotoExtensions.isSupported(name)) continue

                    val parentUriString = DocumentsContract
                        .buildDocumentUriUsingTree(treeUri, parentId)
                        .toString()

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
                            parentUri = parentUriString,
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
            val sortOrder = "${DocumentsContract.Document.COLUMN_LAST_MODIFIED} ASC"
            context.contentResolver.query(childrenUri, CHILD_PROJECTION, null, null, sortOrder)
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

    private suspend fun <T> withTransferPermit(block: suspend () -> T): T =
        transferSemaphore.withPermit {
            val current = activeTransfers.incrementAndGet()
            maxObservedConcurrentTransfers.updateAndGet { prev -> maxOf(prev, current) }
            try {
                onTransferInsidePermit?.invoke()
                withContext(Dispatchers.IO) {
                    block()
                }
            } finally {
                activeTransfers.decrementAndGet()
            }
        }

    private fun resolveTargetFolder(
        fileName: String,
        destFolderUri: Uri,
        sorting: Boolean,
        subfolderName: String,
    ): DocumentFile? {
        val destFolder = documentFileFromTreeUri(destFolderUri) ?: return null
        return if (sorting) {
            val selectionDir = destFolder.findFile(subfolderName)
                ?: destFolder.createDirectory(subfolderName)
                ?: return null
            determineTargetFolder(fileName, selectionDir)
        } else {
            destFolder
        }
    }

    /**
     * List existing child display names in [targetFolder] using a single cursor
     * query projecting [DocumentsContract.Document.COLUMN_DISPLAY_NAME], falling
     * back to [DocumentFile.listFiles] only when [treeUri] / [targetFolder] is
     * not backed by a tree document URI (e.g. unit-test mocks).
     */
    internal fun listExistingFileNames(treeUri: Uri, targetFolder: DocumentFile): Set<String> {
        val targetUri = try {
            targetFolder.uri
        } catch (_: Exception) {
            null
        }
        val parentDocId = targetUri?.let { documentIdOf(it) ?: treeDocumentIdOf(it) }
        val treeDocId = treeDocumentIdOf(treeUri)
        if (parentDocId != null && treeDocId != null) {
            val childrenUri = try {
                DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
            } catch (_: Exception) {
                null
            }
            if (childrenUri != null) {
                try {
                    val cursor = context.contentResolver.query(
                        childrenUri,
                        DISPLAY_NAME_PROJECTION,
                        null,
                        null,
                        null,
                    )
                    if (cursor != null) {
                        cursor.use { c ->
                            val nameIdx = c.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                            if (nameIdx >= 0) {
                                val names = mutableSetOf<String>()
                                while (c.moveToNext()) {
                                    c.getStringOrNull(nameIdx)?.let { names.add(it) }
                                }
                                return names
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Cursor listing failed for $targetUri; falling back to listFiles()", e)
                }
            }
        }
        return try {
            targetFolder.listFiles().mapNotNull { it.name }.toSet()
        } catch (_: Exception) {
            emptySet()
        }
    }

    private fun queryDocumentFlags(uri: Uri): Int = try {
        context.contentResolver.query(uri, FLAGS_PROJECTION, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(DocumentsContract.Document.COLUMN_FLAGS)
            if (idx >= 0 && c.moveToFirst() && !c.isNull(idx)) c.getInt(idx) else 0
        } ?: 0
    } catch (_: Exception) {
        0
    }

    private fun defaultSourceParentUri(sourceUri: Uri, fallbackTreeUri: Uri): Uri? {
        val treeId = treeDocumentIdOf(sourceUri) ?: treeDocumentIdOf(fallbackTreeUri) ?: return null
        val docId = documentIdOf(sourceUri)
        val parentDocId = if (docId != null && '/' in docId) {
            docId.substringBeforeLast('/')
        } else {
            treeId
        }
        return try {
            DocumentsContract.buildDocumentUriUsingTree(sourceUri, parentDocId)
        } catch (_: Exception) {
            try {
                DocumentsContract.buildDocumentUriUsingTree(fallbackTreeUri, parentDocId)
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun copyImageInternal(
        sourceUri: Uri,
        destFolderUri: Uri,
        sorting: Boolean,
        subfolderName: String,
        desiredFileName: String? = null,
    ): TransferResult {
        val sourceDoc = documentFileFromSingleUri(sourceUri)
            ?: return TransferResult(TransferStatus.FAILURE, error = "Source document not found")
        val fileName = sourceDoc.name
            ?: return TransferResult(TransferStatus.FAILURE, error = "Source filename missing")
        val mimeType = sourceDoc.type ?: "application/octet-stream"

        val targetFolder = resolveTargetFolder(fileName, destFolderUri, sorting, subfolderName)
            ?: return TransferResult(TransferStatus.FAILURE, error = "Target folder unavailable")

        val resolvedFileName = desiredFileName ?: run {
            val existing = listExistingFileNames(destFolderUri, targetFolder)
            resolveCollisionFreeBundleNames(
                fileNames = listOf(fileName),
                primaryStem = baseStemOf(fileName),
                existingNamesByFolder = mapOf("" to existing),
            ).getValue(fileName)
        }

        val copiedFile = copyFileVerified(
            sourceUri = sourceUri,
            sourceDoc = sourceDoc,
            targetFolder = targetFolder,
            mimeType = mimeType,
            desiredName = resolvedFileName,
        ) ?: return TransferResult(TransferStatus.FAILURE, error = "Copy or verification failed")

        return TransferResult(
            status = TransferStatus.SUCCESS,
            destinationUri = copiedFile.uri.toString(),
        )
    }

    private suspend fun moveImageInternal(
        sourceUri: Uri,
        destFolderUri: Uri,
        sorting: Boolean,
        subfolderName: String,
        desiredFileName: String? = null,
    ): TransferResult {
        val sourceDoc = documentFileFromSingleUri(sourceUri)
            ?: return TransferResult(TransferStatus.FAILURE, error = "Source document not found")
        val fileName = sourceDoc.name
            ?: return TransferResult(TransferStatus.FAILURE, error = "Source filename missing")
        val mimeType = sourceDoc.type ?: "application/octet-stream"

        val targetFolder = resolveTargetFolder(fileName, destFolderUri, sorting, subfolderName)
            ?: return TransferResult(TransferStatus.FAILURE, error = "Target folder unavailable")

        val resolvedFileName = desiredFileName ?: run {
            val existing = listExistingFileNames(destFolderUri, targetFolder)
            resolveCollisionFreeBundleNames(
                fileNames = listOf(fileName),
                primaryStem = baseStemOf(fileName),
                existingNamesByFolder = mapOf("" to existing),
            ).getValue(fileName)
        }

        // Fast path: atomic DocumentsContract.moveDocument when same authority,
        // no rename is needed, and the document advertises FLAG_SUPPORTS_MOVE.
        val targetFolderUri = try { targetFolder.uri } catch (_: Exception) { null }
        if (
            resolvedFileName.equals(fileName, ignoreCase = true) &&
            sourceUri.authority != null &&
            sourceUri.authority == targetFolderUri?.authority
        ) {
            val flags = documentFlagsQuery(sourceUri)
            if ((flags and DocumentsContract.Document.FLAG_SUPPORTS_MOVE) != 0) {
                val sourceParentUri = sourceParentResolver(sourceUri, destFolderUri)
                if (sourceParentUri != null && targetFolderUri != null) {
                    val movedUri = try {
                        moveDocumentInvoker(sourceUri, sourceParentUri, targetFolderUri)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "DocumentsContract.moveDocument failed for $sourceUri; falling back to copy+delete", e)
                        null
                    }
                    if (movedUri != null) {
                        return TransferResult(
                            status = TransferStatus.SUCCESS,
                            destinationUri = movedUri.toString(),
                        )
                    }
                }
            }
        }

        // Fallback path: stream-copy + byte-size & display-name verification, then delete source.
        val copiedFile = copyFileVerified(
            sourceUri = sourceUri,
            sourceDoc = sourceDoc,
            targetFolder = targetFolder,
            mimeType = mimeType,
            desiredName = resolvedFileName,
        ) ?: return TransferResult(TransferStatus.FAILURE, error = "Copy or verification failed")

        val deleted = deleteImage(sourceUri)
        return if (deleted) {
            TransferResult(
                status = TransferStatus.SUCCESS,
                destinationUri = copiedFile.uri.toString(),
            )
        } else {
            TransferResult(
                status = TransferStatus.PARTIAL,
                destinationUri = copiedFile.uri.toString(),
                error = "Copied, original could not be removed",
            )
        }
    }

    /**
     * Create [desiredName] inside [targetFolder], verify the provider did not
     * silently rename it, stream-copy [sourceUri], and verify the destination
     * byte size matches the source before returning the created [DocumentFile].
     *
     * Any failure cleans up the created destination file so no zero-byte,
     * truncated, or provider-renamed file is ever left behind.
     */
    private fun copyFileVerified(
        sourceUri: Uri,
        sourceDoc: DocumentFile,
        targetFolder: DocumentFile,
        mimeType: String,
        desiredName: String,
    ): DocumentFile? {
        val destFile = targetFolder.createFile(mimeType, desiredName) ?: return null

        // Verify created display name matches desiredName (guard against silent provider auto-rename).
        val actualName = destFile.name
        if (actualName != null && !actualName.equals(desiredName, ignoreCase = true)) {
            Log.w(TAG, "Provider renamed created file from $desiredName to $actualName; cleaning up")
            cleanUpFailedCopy(destFile)
            return null
        }

        var bytesCopied = -1L
        try {
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                context.contentResolver.openOutputStream(destFile.uri)?.use { output ->
                    bytesCopied = input.copyTo(output)
                    output.flush()
                }
            }
        } catch (e: Exception) {
            cleanUpFailedCopy(destFile)
            throw e
        }

        if (bytesCopied < 0L) {
            // Streams could not be opened — remove the empty placeholder file.
            cleanUpFailedCopy(destFile)
            return null
        }

        val sourceLength = sourceDoc.length()
        val destLength = destFile.length()
        val expectedLength = if (sourceLength > 0L) sourceLength else bytesCopied
        val sizeMatches = bytesCopied == expectedLength &&
            (destLength <= 0L || destLength == expectedLength) &&
            (sourceLength <= 0L || destLength <= 0L || destLength == sourceLength)

        if (!sizeMatches) {
            Log.w(
                TAG,
                "Size mismatch after copy for $desiredName: source=$sourceLength, copied=$bytesCopied, dest=$destLength",
            )
            cleanUpFailedCopy(destFile)
            return null
        }

        return destFile
    }

    internal fun cleanUpFailedCopy(destFile: DocumentFile) {
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
            stem.endsWith(EDIT_SUFFIX, ignoreCase = true) || stem.endsWith(EDIT_UNDERSCORE_SUFFIX, ignoreCase = true) ->
                rawDir ?: selectionDir
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
