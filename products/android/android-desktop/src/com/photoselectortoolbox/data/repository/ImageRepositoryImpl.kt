package com.photoselectortoolbox.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.lastOrNull
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * Collaborator for atomic SAF moves (`DocumentsContract.moveDocument`),
 * extracted so unit tests can inspect or override the provider move decision.
 */
internal interface SafDocumentMover {
    fun canMoveDocument(
        context: Context,
        sourceUri: Uri,
        sourceParentUri: Uri?,
        targetFolderUri: Uri,
    ): Boolean

    fun moveDocument(
        context: Context,
        sourceUri: Uri,
        sourceParentUri: Uri,
        targetFolderUri: Uri,
    ): Uri?
}

internal object DefaultSafDocumentMover : SafDocumentMover {
    private const val TAG = "DefaultSafDocumentMover"

    override fun canMoveDocument(
        context: Context,
        sourceUri: Uri,
        sourceParentUri: Uri?,
        targetFolderUri: Uri,
    ): Boolean {
        if (sourceParentUri == null) return false
        val authority = sourceUri.authority ?: return false
        if (authority != targetFolderUri.authority) return false

        val flags = try {
            context.contentResolver.query(
                sourceUri,
                arrayOf(DocumentsContract.Document.COLUMN_FLAGS),
                null,
                null,
                null,
            )?.use { cursor ->
                val idx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_FLAGS)
                if (idx >= 0 && cursor.moveToFirst() && !cursor.isNull(idx)) {
                    cursor.getInt(idx)
                } else {
                    0
                }
            } ?: 0
        } catch (_: Exception) {
            0
        }
        return (flags and DocumentsContract.Document.FLAG_SUPPORTS_MOVE) != 0
    }

    override fun moveDocument(
        context: Context,
        sourceUri: Uri,
        sourceParentUri: Uri,
        targetFolderUri: Uri,
    ): Uri? = try {
        DocumentsContract.moveDocument(
            context.contentResolver,
            sourceUri,
            sourceParentUri,
            targetFolderUri,
        )
    } catch (e: Exception) {
        Log.w(TAG, "DocumentsContract.moveDocument failed for $sourceUri, falling back to stream copy", e)
        null
    }
}

@Singleton
class ImageRepositoryImpl @Inject constructor(
    private val localImageSource: LocalImageSource,
    private val androidExifReader: AndroidExifReader,
    private val mediaStoreReader: MediaStoreReader,
    private val settingsRepository: SettingsRepository,
    private val folderAccessManager: FolderAccessManager,
) : ImageRepository {

    constructor(
        localImageSource: LocalImageSource,
        androidExifReader: AndroidExifReader,
        mediaStoreReader: MediaStoreReader,
        settingsRepository: SettingsRepository,
    ) : this(
        localImageSource = localImageSource,
        androidExifReader = androidExifReader,
        mediaStoreReader = mediaStoreReader,
        settingsRepository = settingsRepository,
        folderAccessManager = FolderAccessManager(),
    )

    companion object {
        private const val TAG = "ImageRepositoryImpl"
        private const val SELECTION_FOLDER_NAME = "Selection"
        private const val RAW_SUBFOLDER = "RAW"
        private const val JPEG_SUBFOLDER = "JPEG"
        private const val SINGLE_FOLDER_KEY = "TARGET"
        internal const val MAX_CONCURRENT_TRANSFERS = 2

        private val DERIVATIVE_SUFFIXES = listOf("-Edit", "_Edit", "-Enhanced-NR", "_Enhanced-NR")

        /**
         * Extract the shared base stem of a photo, sidecar or derivative so a
         * collision suffix (`-2`, `-3`, …) is inserted before `.ext`, `.RAW.xmp`
         * or `-Edit` / `_Edit` / `-Enhanced-NR`.
         */
        internal fun extractPrimaryStem(fileName: String): String {
            val name = fileName.substringAfterLast('/')
            val withoutXmp = if (name.endsWith(".xmp", ignoreCase = true) && name.length > 4) {
                name.dropLast(4)
            } else {
                name
            }
            val withoutExt = if (withoutXmp.contains('.')) {
                withoutXmp.substringBeforeLast('.')
            } else {
                withoutXmp
            }
            for (suffix in DERIVATIVE_SUFFIXES) {
                if (withoutExt.endsWith(suffix, ignoreCase = true) && withoutExt.length > suffix.length) {
                    return withoutExt.dropLast(suffix.length)
                }
            }
            return withoutExt
        }

        /**
         * Insert `-[suffixNumber]` into [fileName] after [primaryStem] so that:
         * - `IMG_0001.CR3` → `IMG_0001-2.CR3`
         * - `IMG_0001.JPG` → `IMG_0001-2.JPG`
         * - `IMG_0001.xmp` → `IMG_0001-2.xmp`
         * - `IMG_0001.CR3.xmp` → `IMG_0001-2.CR3.xmp`
         * - `IMG_0001-Edit.tif` → `IMG_0001-2-Edit.tif`
         * - `IMG_0001_Edit.tif` → `IMG_0001-2_Edit.tif`
         */
        internal fun suffixFileName(
            fileName: String,
            suffixNumber: Int,
            primaryStem: String = extractPrimaryStem(fileName),
        ): String {
            val name = fileName.substringAfterLast('/')
            val stemLen = if (
                primaryStem.isNotEmpty() &&
                name.startsWith(primaryStem, ignoreCase = true) &&
                (name.length == primaryStem.length || name[primaryStem.length] in charArrayOf('.', '-', '_'))
            ) {
                primaryStem.length
            } else {
                extractPrimaryStem(name).length
            }
            return name.substring(0, stemLen) + "-$suffixNumber" + name.substring(stemLen)
        }

        /**
         * Compute bundle-wide collision-free destination filenames (`-2`, `-3`, …).
         *
         * If any file in [fileNames] collides case-insensitively with an existing
         * name in its target folder ([existingNamesByFolder]), the entire bundle
         * receives the lowest integer $N \ge 2$ for which **every** member is
         * collision-free in its target folder.
         */
        internal fun resolveCollisionFreeBundleNames(
            fileNames: List<String>,
            primaryStem: String = fileNames.firstOrNull()?.let(::extractPrimaryStem).orEmpty(),
            existingNamesByFolder: Map<String, Set<String>>,
            folderForFile: (String) -> String = { ImageFormats.selectionSubfolder(it).name },
        ): Map<String, String> {
            if (fileNames.isEmpty()) return emptyMap()

            val lowercaseByFolder: Map<String, Set<String>> = existingNamesByFolder.mapValues { (_, names) ->
                names.mapTo(HashSet(names.size)) { it.lowercase() }
            }

            val anyCollision = fileNames.any { name ->
                val folder = folderForFile(name)
                name.lowercase() in lowercaseByFolder[folder].orEmpty()
            }
            if (!anyCollision) {
                return fileNames.associateWith { it }
            }

            var n = 2
            while (true) {
                val candidateMap = LinkedHashMap<String, String>(fileNames.size)
                var allFree = true
                for (name in fileNames) {
                    val candidate = suffixFileName(name, n, primaryStem)
                    val folder = folderForFile(name)
                    if (candidate.lowercase() in lowercaseByFolder[folder].orEmpty()) {
                        allFree = false
                        break
                    }
                    candidateMap[name] = candidate
                }
                if (allFree) {
                    return candidateMap
                }
                n++
            }
        }
    }

    private val transferSemaphore = Semaphore(MAX_CONCURRENT_TRANSFERS)

    internal var ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    internal var documentMover: SafDocumentMover = DefaultSafDocumentMover

    private sealed class CopyStepResult {
        data class Success(val destinationUri: String) : CopyStepResult()
        data class Failure(val reason: String) : CopyStepResult()
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
        withContext(ioDispatcher) {
            folderAccessManager.takeGrant(
                uri = folderUri,
                purpose = FolderAccessPurpose.SOURCE,
                fallbackContext = context,
            )

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
        withContext(ioDispatcher) { discoverImages(folderUri).lastOrNull().orEmpty() }

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
        withContext(ioDispatcher) {
            try {
                val docFile = DocumentFile.fromSingleUri(context, uri)
                docFile?.delete() ?: false
            } catch (e: CancellationException) {
                throw e
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
    ): FileOperationResult = transferSemaphore.withPermit {
        withContext(ioDispatcher) {
            val source = sourceUri.toString()
            try {
                val folderName = settingsRepository.selectionFolderName.first()
                    .trim().ifEmpty { SELECTION_FOLDER_NAME }
                moveOrCopySingle(
                    context = context,
                    sourceUri = sourceUri,
                    destFolderUri = destFolderUri,
                    sorting = sorting,
                    selectionFolderName = folderName,
                    isMove = true,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to move image: $sourceUri", e)
                FileOperationResult.failure(source, e.message)
            }
        }
    }

    override suspend fun copyImage(
        context: Context,
        sourceUri: Uri,
        destFolderUri: Uri,
        sorting: Boolean
    ): FileOperationResult = transferSemaphore.withPermit {
        withContext(ioDispatcher) {
            val source = sourceUri.toString()
            try {
                val folderName = settingsRepository.selectionFolderName.first()
                    .trim().ifEmpty { SELECTION_FOLDER_NAME }
                moveOrCopySingle(
                    context = context,
                    sourceUri = sourceUri,
                    destFolderUri = destFolderUri,
                    sorting = sorting,
                    selectionFolderName = folderName,
                    isMove = false,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to copy image: $sourceUri", e)
                FileOperationResult.failure(source, e.message)
            }
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

    /**
     * Transfer a companion bundle (primary + RAW/JPEG twin + XMP sidecars +
     * `-Edit` derivatives) into [destFolderUri] using a single cursor query per
     * target folder and a shared collision suffix (`-2`, `-3`, …) across the
     * entire bundle so pairing is never broken.
     */
    internal suspend fun transferBundle(
        context: Context,
        sourceUris: List<Uri>,
        destFolderUri: Uri,
        sorting: Boolean,
        isMove: Boolean,
    ): List<FileOperationResult> = transferSemaphore.withPermit {
        withContext(ioDispatcher) {
            if (sourceUris.isEmpty()) return@withContext emptyList()
            val folderName = settingsRepository.selectionFolderName.first()
                .trim().ifEmpty { SELECTION_FOLDER_NAME }

            val destFolder = DocumentFile.fromTreeUri(context, destFolderUri)
                ?: return@withContext sourceUris.map { uri ->
                    FileOperationResult.failure(uri.toString(), "Could not write to the $folderName folder")
                }

            val selectionDir = if (sorting) {
                destFolder.findFile(folderName)
                    ?: destFolder.createDirectory(folderName)
                    ?: return@withContext sourceUris.map { uri ->
                        FileOperationResult.failure(uri.toString(), "Could not write to the $folderName folder")
                    }
            } else {
                destFolder
            }

            data class ResolvedSource(
                val uri: Uri,
                val doc: DocumentFile?,
                val fileName: String?,
                val folderKey: String,
                val targetFolder: DocumentFile?,
            )

            val targetFoldersByKey = LinkedHashMap<String, DocumentFile>()
            val resolvedSources = sourceUris.map { uri ->
                val doc = DocumentFile.fromSingleUri(context, uri)
                val name = doc?.name
                if (name == null) {
                    ResolvedSource(uri, doc, null, SINGLE_FOLDER_KEY, null)
                } else if (sorting) {
                    val key = ImageFormats.selectionSubfolder(name).name
                    val target = targetFoldersByKey.getOrPut(key) {
                        determineTargetFolder(name, selectionDir)
                    }
                    ResolvedSource(uri, doc, name, key, target)
                } else {
                    targetFoldersByKey.getOrPut(SINGLE_FOLDER_KEY) { destFolder }
                    ResolvedSource(uri, doc, name, SINGLE_FOLDER_KEY, destFolder)
                }
            }

            val existingNamesByFolder = targetFoldersByKey.mapValues { (_, folder) ->
                queryExistingDisplayNames(context, folder)
            }
            val validNames = resolvedSources.mapNotNull { it.fileName }
            val primaryStem = validNames.firstOrNull()?.let(::extractPrimaryStem).orEmpty()
            val folderKeyByName = resolvedSources
                .filter { it.fileName != null }
                .associate { it.fileName!! to it.folderKey }

            val desiredNames = resolveCollisionFreeBundleNames(
                fileNames = validNames,
                primaryStem = primaryStem,
                existingNamesByFolder = existingNamesByFolder,
                folderForFile = { folderKeyByName[it] ?: SINGLE_FOLDER_KEY },
            )

            resolvedSources.map { src ->
                val sourceStr = src.uri.toString()
                val doc = src.doc
                val name = src.fileName
                val targetFolder = src.targetFolder
                if (doc == null || name == null || targetFolder == null) {
                    FileOperationResult.failure(sourceStr, "Could not write to the $folderName folder")
                } else {
                    val desiredName = desiredNames[name] ?: name
                    executeSingleTransfer(
                        context = context,
                        sourceUri = src.uri,
                        sourceDoc = doc,
                        fileName = name,
                        desiredName = desiredName,
                        destFolderUri = destFolderUri,
                        targetFolder = targetFolder,
                        selectionFolderName = folderName,
                        isMove = isMove,
                    )
                }
            }
        }
    }

    private suspend fun moveOrCopySingle(
        context: Context,
        sourceUri: Uri,
        destFolderUri: Uri,
        sorting: Boolean,
        selectionFolderName: String,
        isMove: Boolean,
    ): FileOperationResult {
        val source = sourceUri.toString()
        val sourceDoc = DocumentFile.fromSingleUri(context, sourceUri)
            ?: return FileOperationResult.failure(source, if (isMove) "Copy step failed" else "Could not write to the $selectionFolderName folder")
        val fileName = sourceDoc.name
            ?: return FileOperationResult.failure(source, if (isMove) "Copy step failed" else "Could not write to the $selectionFolderName folder")

        val destFolder = DocumentFile.fromTreeUri(context, destFolderUri)
            ?: return FileOperationResult.failure(source, if (isMove) "Copy step failed" else "Could not write to the $selectionFolderName folder")

        val targetFolder = if (sorting) {
            val selectionDir = destFolder.findFile(selectionFolderName)
                ?: destFolder.createDirectory(selectionFolderName)
                ?: return FileOperationResult.failure(source, if (isMove) "Copy step failed" else "Could not write to the $selectionFolderName folder")
            determineTargetFolder(fileName, selectionDir)
        } else {
            destFolder
        }

        // Single cursor query to find existing display names in targetFolder.
        val existingNames = queryExistingDisplayNames(context, targetFolder)
        val desiredName = resolveCollisionFreeBundleNames(
            fileNames = listOf(fileName),
            primaryStem = extractPrimaryStem(fileName),
            existingNamesByFolder = mapOf(SINGLE_FOLDER_KEY to existingNames),
            folderForFile = { SINGLE_FOLDER_KEY },
        ).getValue(fileName)

        return executeSingleTransfer(
            context = context,
            sourceUri = sourceUri,
            sourceDoc = sourceDoc,
            fileName = fileName,
            desiredName = desiredName,
            destFolderUri = destFolderUri,
            targetFolder = targetFolder,
            selectionFolderName = selectionFolderName,
            isMove = isMove,
        )
    }

    private suspend fun executeSingleTransfer(
        context: Context,
        sourceUri: Uri,
        sourceDoc: DocumentFile,
        fileName: String,
        desiredName: String,
        destFolderUri: Uri,
        targetFolder: DocumentFile,
        selectionFolderName: String,
        isMove: Boolean,
    ): FileOperationResult {
        val source = sourceUri.toString()
        if (sourceUri == targetFolder.uri) {
            return FileOperationResult.success(source, source)
        }

        // Attempt atomic DocumentsContract.moveDocument when moving without a
        // collision rename and the provider supports FLAG_SUPPORTS_MOVE.
        if (isMove && desiredName == fileName) {
            val sourceParentUri = resolveSourceParentUri(sourceUri, destFolderUri)
            if (documentMover.canMoveDocument(context, sourceUri, sourceParentUri, targetFolder.uri) &&
                sourceParentUri != null
            ) {
                val movedUri = documentMover.moveDocument(
                    context = context,
                    sourceUri = sourceUri,
                    sourceParentUri = sourceParentUri,
                    targetFolderUri = targetFolder.uri,
                )
                if (movedUri != null) {
                    return FileOperationResult.success(source, movedUri.toString())
                }
            }
        }

        val mimeType = sourceDoc.type ?: "application/octet-stream"
        return when (
            val copyResult = copyToTargetFolder(
                context = context,
                sourceUri = sourceUri,
                sourceDoc = sourceDoc,
                targetFolder = targetFolder,
                mimeType = mimeType,
                desiredName = desiredName,
                selectionFolderName = selectionFolderName,
            )
        ) {
            is CopyStepResult.Failure -> FileOperationResult.failure(source, copyResult.reason)
            is CopyStepResult.Success -> {
                if (!isMove) {
                    FileOperationResult.success(source, copyResult.destinationUri)
                } else {
                    val deleted = try {
                        deleteImage(context, sourceUri)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to delete source after verified copy: $sourceUri", e)
                        false
                    }
                    if (deleted) {
                        FileOperationResult.success(source, copyResult.destinationUri)
                    } else {
                        // The copy is verified on disk, so never delete it when source
                        // removal fails; report a partial outcome instead.
                        FileOperationResult.partial(
                            sourceUri = source,
                            destinationUri = copyResult.destinationUri,
                            error = "Copied to $selectionFolderName — original could not be removed",
                        )
                    }
                }
            }
        }
    }

    /**
     * Stream-copy [sourceUri] into [targetFolder] as [desiredName], verifying
     * both the created display name and the destination byte size before
     * reporting success.
     */
    private fun copyToTargetFolder(
        context: Context,
        sourceUri: Uri,
        sourceDoc: DocumentFile,
        targetFolder: DocumentFile,
        mimeType: String,
        desiredName: String,
        selectionFolderName: String,
    ): CopyStepResult {
        val destFile = targetFolder.createFile(mimeType, desiredName)
            ?: return CopyStepResult.Failure("Could not write to the $selectionFolderName folder")

        // Guard against a SAF provider silently auto-renaming the created file
        // (e.g. "IMG_0001 (1).CR3") on a race, which would break companion pairing.
        val actualName = destFile.name
        if (!actualName.isNullOrEmpty() && !actualName.equals(desiredName, ignoreCase = true)) {
            cleanUpFailedCopy(destFile)
            return CopyStepResult.Failure(
                "Provider renamed destination file to '$actualName' instead of '$desiredName'"
            )
        }

        val sourceSize = sourceDoc.length()
        var bytesCopied = -1L

        try {
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                context.contentResolver.openOutputStream(destFile.uri)?.use { output ->
                    bytesCopied = input.copyTo(output)
                    output.flush()
                }
            }
        } catch (e: CancellationException) {
            cleanUpFailedCopy(destFile)
            throw e
        } catch (e: Exception) {
            // Never leave a half-written file in the Selection: a truncated
            // photograph that looks filed is worse than a failed move.
            cleanUpFailedCopy(destFile)
            throw e
        }

        if (bytesCopied < 0L) {
            // Streams could not be opened — remove the empty placeholder file.
            cleanUpFailedCopy(destFile)
            return CopyStepResult.Failure("Could not write to the $selectionFolderName folder")
        }

        val expectedSize = if (sourceSize > 0L) sourceSize else bytesCopied
        val destSize = destFile.length()
        if ((sourceSize > 0L && bytesCopied != sourceSize) ||
            (expectedSize > 0L && destSize != expectedSize)
        ) {
            cleanUpFailedCopy(destFile)
            return CopyStepResult.Failure("Copied file size mismatch")
        }

        return CopyStepResult.Success(destFile.uri.toString())
    }

    /**
     * List existing child display names in [targetFolder] using a **single**
     * `ContentResolver` cursor query (`COLUMN_DISPLAY_NAME`), falling back to
     * [DocumentFile.listFiles] only when [targetFolder] is not backed by a tree
     * document URI in unit-test mocks.
     */
    internal fun queryExistingDisplayNames(
        context: Context,
        targetFolder: DocumentFile,
    ): Set<String> {
        val folderUri = targetFolder.uri
        val childrenUri = try {
            val docId = DocumentsContract.getDocumentId(folderUri)
            DocumentsContract.buildChildDocumentsUriUsingTree(folderUri, docId)
        } catch (_: Exception) {
            try {
                val treeDocId = DocumentsContract.getTreeDocumentId(folderUri)
                DocumentsContract.buildChildDocumentsUriUsingTree(folderUri, treeDocId)
            } catch (_: Exception) {
                null
            }
        }

        if (childrenUri != null) {
            try {
                context.contentResolver.query(
                    childrenUri,
                    arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                    null,
                    null,
                    null,
                )?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    if (nameIndex >= 0) {
                        val names = LinkedHashSet<String>(cursor.count.coerceAtLeast(0))
                        while (cursor.moveToNext()) {
                            if (!cursor.isNull(nameIndex)) {
                                cursor.getString(nameIndex)?.let { names.add(it) }
                            }
                        }
                        return names
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Cursor query for existing names failed in $folderUri, falling back to listFiles", e)
            }
        }

        return try {
            targetFolder.listFiles().mapNotNull { it.name }.toSet()
        } catch (e: Exception) {
            Log.w(TAG, "Fallback listFiles failed in $folderUri", e)
            emptySet()
        }
    }

    /**
     * Derive the parent document URI of [sourceUri] within [treeFolderUri] when
     * the document ID encodes a slash-separated relative path or sits at the
     * root of the opened tree.
     */
    internal fun resolveSourceParentUri(sourceUri: Uri, treeFolderUri: Uri): Uri? {
        val authority = sourceUri.authority ?: return null
        if (authority != treeFolderUri.authority) return null
        val sourceDocId = try {
            DocumentsContract.getDocumentId(sourceUri)
        } catch (_: Exception) {
            null
        } ?: return null

        val parentDocId = if (sourceDocId.contains('/')) {
            sourceDocId.substringBeforeLast('/')
        } else {
            try {
                DocumentsContract.getTreeDocumentId(treeFolderUri)
            } catch (_: Exception) {
                null
            }
        } ?: return null

        return try {
            DocumentsContract.buildDocumentUriUsingTree(treeFolderUri, parentDocId)
        } catch (_: Exception) {
            try {
                DocumentsContract.buildDocumentUri(authority, parentDocId)
            } catch (_: Exception) {
                null
            }
        }
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
     * lives in [ImageFormats.selectionSubfolder].
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
    ): Map<String, ImageDimensions> = withContext(ioDispatcher) {
        localImageSource.resolveDimensions(uris)
    }
}
