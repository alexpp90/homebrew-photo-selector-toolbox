package com.phototok.conformance

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileNotFoundException

/**
 * A minimal SAF document provider over one flat directory ([folder]), so the
 * conformance test drives PhotoTok's real tree walk, Coil content-URI loading
 * and EXIF reading with genuine `content://` document URIs and seekable file
 * descriptors. The root's document id is [ROOT]; a file's id is its name.
 */
class FolderDocumentsProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "com.phototok.test.folderdocuments"
        const val ROOT = "root"

        /** The directory currently exposed. */
        @Volatile
        lateinit var folder: File

        val treeUri: Uri get() = DocumentsContract.buildTreeDocumentUri(AUTHORITY, ROOT)
    }

    override fun onCreate(): Boolean = true

    /** `…/tree/<root>/document/<id>[/children]` → `<id>`. */
    private fun idOf(uri: Uri): String {
        val segments = uri.pathSegments
        val i = segments.indexOf("document")
        return if (i >= 0 && i + 1 < segments.size) segments[i + 1] else segments.getOrElse(1) { ROOT }
    }

    private fun fileOf(id: String): File = if (id == ROOT) folder else File(folder, id)

    private fun mimeOf(file: File): String =
        if (file.isDirectory) {
            Document.MIME_TYPE_DIR
        } else {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase())
                ?: "application/octet-stream"
        }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val columns = projection ?: arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME)
        val id = idOf(uri)
        val rows = if (uri.pathSegments.lastOrNull() == "children") {
            folder.listFiles().orEmpty().sortedBy { it.name }.map { it.name to it }
        } else {
            listOf(id to fileOf(id))
        }
        return MatrixCursor(columns).apply {
            rows.forEach { (rowId, file) ->
                addRow(
                    columns.map { column ->
                        when (column) {
                            Document.COLUMN_DOCUMENT_ID -> rowId
                            Document.COLUMN_DISPLAY_NAME -> file.name
                            Document.COLUMN_MIME_TYPE -> mimeOf(file)
                            Document.COLUMN_SIZE -> file.length()
                            Document.COLUMN_LAST_MODIFIED -> file.lastModified()
                            else -> null
                        }
                    }.toTypedArray(),
                )
            }
        }
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val file = fileOf(idOf(uri))
        if (!file.isFile) throw FileNotFoundException(uri.toString())
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri): String = mimeOf(fileOf(idOf(uri)))

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
