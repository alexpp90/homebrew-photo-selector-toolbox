package com.photoselectortoolbox.conformance

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract.Document
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileNotFoundException

/**
 * A SAF-shaped content provider over a real directory, for JVM tests.
 *
 * Answers exactly what the app asks a documents provider: one children query
 * per directory (`…/tree/<root>/document/<id>/children`) and `openFile` on a
 * document URI. Document ids are `root/<relative path>`, so — like the
 * external-storage provider — a document URI ends in the display name.
 */
class DirectoryDocumentsProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "com.photoselectortoolbox.test.documents"
        const val ROOT_ID = "root"

        @Volatile
        var root: File = File(".")
    }

    override fun onCreate(): Boolean = true

    private fun fileOf(documentId: String): File {
        require(documentId == ROOT_ID || documentId.startsWith("$ROOT_ID/")) { "bad id $documentId" }
        return if (documentId == ROOT_ID) root else File(root, documentId.removePrefix("$ROOT_ID/"))
    }

    private fun documentIdOf(uri: Uri): String {
        val s = uri.pathSegments
        val i = s.indexOf("document")
        return if (i >= 0 && i + 1 < s.size) s[i + 1] else s[1]
    }

    private fun mimeOf(file: File): String = when {
        file.isDirectory -> Document.MIME_TYPE_DIR
        else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase())
            ?: "application/octet-stream"
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val columns = projection ?: arrayOf(
            Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE,
            Document.COLUMN_SIZE, Document.COLUMN_LAST_MODIFIED,
        )
        val cursor = MatrixCursor(columns)
        val id = documentIdOf(uri)
        val targets = if (uri.pathSegments.lastOrNull() == "children") {
            fileOf(id).listFiles()?.sortedBy { it.name }?.map { "$id/${it.name}" to it }.orEmpty()
        } else {
            listOf(id to fileOf(id))
        }
        for ((childId, f) in targets) {
            cursor.addRow(columns.map { c ->
                when (c) {
                    Document.COLUMN_DOCUMENT_ID -> childId
                    Document.COLUMN_DISPLAY_NAME -> f.name
                    Document.COLUMN_MIME_TYPE -> mimeOf(f)
                    Document.COLUMN_SIZE -> f.length()
                    Document.COLUMN_LAST_MODIFIED -> f.lastModified()
                    else -> null
                }
            }.toTypedArray())
        }
        return cursor
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val f = fileOf(documentIdOf(uri))
        if (!f.isFile) throw FileNotFoundException(uri.toString())
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri): String = mimeOf(fileOf(documentIdOf(uri)))
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
