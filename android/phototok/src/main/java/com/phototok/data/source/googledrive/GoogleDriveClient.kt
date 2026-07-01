package com.phototok.data.source.googledrive

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/** Lightweight data class for a Drive file/folder entry. */
data class DriveFile(
    val id: String,
    val name: String,
    val mimeType: String,
    val size: Long = 0,
    val modifiedTime: Long = 0,
    val thumbnailLink: String? = null,
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
) {
    val isFolder: Boolean get() = mimeType == MIME_FOLDER

    companion object {
        const val MIME_FOLDER = "application/vnd.google-apps.folder"
    }
}

/**
 * Lightweight Google Drive REST API v3 client.
 * Uses raw HttpURLConnection — no extra library dependencies.
 *
 * Every request goes through a single authorized helper that refreshes the
 * cached OAuth token and retries once on HTTP 401, so all verbs (GET, POST,
 * PATCH, downloads, uploads) share the same token-expiry handling.
 */
@Singleton
class GoogleDriveClient @Inject constructor(
    private val auth: GoogleDriveAuth,
) {
    companion object {
        private const val TAG = "GoogleDriveClient"
        private const val BASE_URL = "https://www.googleapis.com/drive/v3"
        private const val UPLOAD_URL = "https://www.googleapis.com/upload/drive/v3"
        private const val PAGE_SIZE = 200

        /** Max folder listings in flight when recursing into a Drive tree. */
        private const val MAX_PARALLEL_LISTINGS = 4

        /** Image MIME types the app supports. */
        val IMAGE_MIME_TYPES = listOf(
            "image/jpeg", "image/png", "image/tiff", "image/bmp", "image/gif",
            "image/webp", "image/heif", "image/heic", "image/x-adobe-dng",
            "image/x-canon-cr2", "image/x-canon-cr3", "image/x-nikon-nef",
            "image/x-sony-arw", "image/x-olympus-orf", "image/x-panasonic-rw2",
            "image/x-pentax-pef", "image/x-samsung-srw", "image/x-fuji-raf",
        )
    }

    private val listingSemaphore = Semaphore(MAX_PARALLEL_LISTINGS)

    /**
     * List image files in a Drive folder. Returns all images recursively
     * if [recursive] is true, otherwise just direct children. Subfolders
     * are listed concurrently (bounded by [MAX_PARALLEL_LISTINGS]).
     */
    suspend fun listImages(
        folderId: String,
        recursive: Boolean = true,
    ): List<DriveFile> = withContext(Dispatchers.IO) {
        listImagesRecursive(folderId, recursive).sortedBy { it.name.lowercase() }
    }

    private suspend fun listImagesRecursive(
        folderId: String,
        recursive: Boolean,
    ): List<DriveFile> = coroutineScope {
        val images = mutableListOf<DriveFile>()
        val subfolderIds = mutableListOf<String>()
        var pageToken: String? = null
        do {
            // Query for images, generic octet-streams, and folders (to recurse into)
            val mimeFilter = (IMAGE_MIME_TYPES + "application/octet-stream")
                .joinToString(" or ") { "mimeType='$it'" }
            val query = "'$folderId' in parents and trashed=false and " +
                "($mimeFilter or mimeType='${DriveFile.MIME_FOLDER}')"
            val fields = "nextPageToken,files(id,name,mimeType,size,modifiedTime," +
                "imageMediaMetadata/width,imageMediaMetadata/height)"

            val url = buildString {
                append("$BASE_URL/files?")
                append("q=${URLEncoder.encode(query, "UTF-8")}")
                append("&fields=${URLEncoder.encode(fields, "UTF-8")}")
                append("&pageSize=$PAGE_SIZE")
                append("&orderBy=name")
                if (pageToken != null) append("&pageToken=$pageToken")
            }

            val json = listingSemaphore.withPermit { httpGet(url) } ?: break

            val files = json.optJSONArray("files") ?: break
            for (i in 0 until files.length()) {
                val f = files.getJSONObject(i)
                val mime = f.getString("mimeType")

                if (mime == DriveFile.MIME_FOLDER) {
                    // Skip "Selection" / "Selected" folders just like the local source
                    val folderName = f.getString("name").lowercase()
                    if (folderName == "selection" || folderName == "selected" ||
                        folderName == "phototok_selection"
                    ) continue

                    if (recursive) subfolderIds.add(f.getString("id"))
                } else {
                    val meta = f.optJSONObject("imageMediaMetadata")
                    images.add(
                        DriveFile(
                            id = f.getString("id"),
                            name = f.getString("name"),
                            mimeType = mime,
                            size = f.optLong("size", 0),
                            modifiedTime = parseRfc3339(f.optString("modifiedTime", "")),
                            imageWidth = meta?.optInt("width", 0) ?: 0,
                            imageHeight = meta?.optInt("height", 0) ?: 0,
                        )
                    )
                }
            }

            pageToken = json.optString("nextPageToken", null)
        } while (!pageToken.isNullOrEmpty())

        // Recurse into subfolders concurrently.
        val children = subfolderIds
            .map { id -> async { listImagesRecursive(id, true) } }
            .awaitAll()
        images + children.flatten()
    }

    /** List only folders inside a given parent (for the folder picker). */
    suspend fun listFolders(parentId: String): List<DriveFile> = withContext(Dispatchers.IO) {
        val results = mutableListOf<DriveFile>()
        var pageToken: String? = null
        do {
            val query = "'$parentId' in parents and mimeType='${DriveFile.MIME_FOLDER}' and trashed=false"
            val fields = "nextPageToken,files(id,name,mimeType)"

            val url = buildString {
                append("$BASE_URL/files?")
                append("q=${URLEncoder.encode(query, "UTF-8")}")
                append("&fields=${URLEncoder.encode(fields, "UTF-8")}")
                append("&pageSize=$PAGE_SIZE")
                append("&orderBy=name")
                if (pageToken != null) append("&pageToken=$pageToken")
            }

            val json = httpGet(url) ?: break
            val files = json.optJSONArray("files") ?: break
            for (i in 0 until files.length()) {
                val f = files.getJSONObject(i)
                results.add(
                    DriveFile(
                        id = f.getString("id"),
                        name = f.getString("name"),
                        mimeType = f.getString("mimeType"),
                    )
                )
            }
            pageToken = json.optString("nextPageToken", null)
        } while (!pageToken.isNullOrEmpty())
        results
    }

    /**
     * Download a Drive file to a local cache file. Returns true on success.
     * Retries once with a refreshed token on HTTP 401 like every other request.
     */
    suspend fun downloadFile(fileId: String, destFile: File): Boolean = withContext(Dispatchers.IO) {
        repeat(2) { attempt ->
            val token = auth.getAccessToken() ?: return@withContext false
            try {
                val url = URL("$BASE_URL/files/$fileId?alt=media")
                val conn = url.openConnection() as HttpURLConnection
                conn.setRequestProperty("Authorization", "Bearer $token")
                conn.connectTimeout = 30_000
                conn.readTimeout = 60_000

                when {
                    conn.responseCode == HttpURLConnection.HTTP_OK -> {
                        destFile.parentFile?.mkdirs()
                        conn.inputStream.use { input ->
                            destFile.outputStream().use { output ->
                                input.copyTo(output, bufferSize = 8192)
                            }
                        }
                        return@withContext true
                    }
                    conn.responseCode == HttpURLConnection.HTTP_UNAUTHORIZED && attempt == 0 -> {
                        Log.w(TAG, "Download HTTP 401 — refreshing access token and retrying")
                        auth.invalidateAccessToken(token)
                        // fall through to next repeat() iteration
                    }
                    else -> {
                        Log.e(TAG, "Download failed: ${conn.responseCode} ${conn.responseMessage}")
                        return@withContext false
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Download error for $fileId", e)
                return@withContext false
            }
        }
        false
    }

    /**
     * Upload a file to a Drive folder using a single multipart request
     * (metadata + content). Returns the new file's ID, or null on failure.
     */
    suspend fun uploadFile(
        parentFolderId: String,
        fileName: String,
        mimeType: String,
        inputFile: File,
    ): String? {
        val boundary = "====${System.currentTimeMillis()}===="
        val metadata = JSONObject().apply {
            put("name", fileName)
            put("parents", org.json.JSONArray().put(parentFolderId))
        }
        val response = request(
            method = "POST",
            urlString = "$UPLOAD_URL/files?uploadType=multipart",
            contentType = "multipart/related; boundary=$boundary",
        ) { out ->
            writeMultipartBody(out, boundary, metadata.toString(), mimeType, inputFile)
        } ?: return null
        return try {
            JSONObject(response).getString("id")
        } catch (e: Exception) {
            Log.e(TAG, "Upload response parse error", e)
            null
        }
    }

    /** Create a folder inside a parent folder. Returns the new folder's ID. */
    suspend fun createFolder(parentId: String, folderName: String): String? {
        val metadata = JSONObject().apply {
            put("name", folderName)
            put("mimeType", DriveFile.MIME_FOLDER)
            put("parents", org.json.JSONArray().put(parentId))
        }
        val response = requestJsonBody("POST", "$BASE_URL/files", metadata) ?: return null
        return try {
            JSONObject(response).getString("id")
        } catch (e: Exception) {
            Log.e(TAG, "Create folder response parse error", e)
            null
        }
    }

    /** Find or create a subfolder by name inside a parent. */
    suspend fun findOrCreateFolder(parentId: String, folderName: String): String? {
        // Search for existing
        val query = "'$parentId' in parents and mimeType='${DriveFile.MIME_FOLDER}' " +
            "and name='${escapeQueryValue(folderName)}' and trashed=false"
        val fields = "files(id)"
        val url = buildString {
            append("$BASE_URL/files?")
            append("q=${URLEncoder.encode(query, "UTF-8")}")
            append("&fields=${URLEncoder.encode(fields, "UTF-8")}")
            append("&pageSize=1")
        }
        val json = httpGet(url)
        val files = json?.optJSONArray("files")
        if (files != null && files.length() > 0) {
            return files.getJSONObject(0).getString("id")
        }
        // Create new
        return createFolder(parentId, folderName)
    }

    /** Delete a file from Drive (move to trash). */
    suspend fun trashFile(fileId: String): Boolean {
        val body = JSONObject().apply { put("trashed", true) }
        return requestJsonBody("PATCH", "$BASE_URL/files/$fileId", body) != null
    }

    /** Copy a file to a different folder on Drive. Returns the new file's ID. */
    suspend fun copyFile(fileId: String, destFolderId: String, newName: String? = null): String? {
        val metadata = JSONObject().apply {
            put("parents", org.json.JSONArray().put(destFolderId))
            if (newName != null) put("name", newName)
        }
        val response = requestJsonBody("POST", "$BASE_URL/files/$fileId/copy", metadata)
            ?: return null
        return try {
            JSONObject(response).getString("id")
        } catch (e: Exception) {
            Log.e(TAG, "Copy response parse error", e)
            null
        }
    }

    /** Move a file to a different folder (remove old parent, add new parent). */
    suspend fun moveFile(fileId: String, oldParentId: String, newParentId: String): Boolean {
        val url = "$BASE_URL/files/$fileId?addParents=$newParentId&removeParents=$oldParentId"
        // Empty JSON body required for PATCH.
        return requestJsonBody("PATCH", url, JSONObject()) != null
    }

    // ── Internal helpers ─────────────────────────────────────────────────

    /**
     * Escape a user-supplied value for use inside a Drive query string.
     * Drive queries use single-quoted strings where `\` and `'` must be escaped —
     * a folder named e.g. "Tom's Photos" would otherwise break the query.
     */
    private fun escapeQueryValue(value: String): String =
        value.replace("\\", "\\\\").replace("'", "\\'")

    /** GET returning parsed JSON (through the shared auth-retry helper). */
    private suspend fun httpGet(urlString: String): JSONObject? {
        val body = request("GET", urlString) ?: return null
        return try {
            JSONObject(body)
        } catch (e: Exception) {
            Log.e(TAG, "Response parse error for $urlString", e)
            null
        }
    }

    /** Request with a JSON body (through the shared auth-retry helper). */
    private suspend fun requestJsonBody(
        method: String,
        urlString: String,
        body: JSONObject,
    ): String? {
        val bytes = body.toString().toByteArray()
        return request(method, urlString, contentType = "application/json") { out ->
            out.write(bytes)
        }
    }

    /**
     * The single authorized-request helper. Applies the bearer token, executes
     * the request, and on HTTP 401 clears the cached token (GoogleAuthUtil caches
     * tokens) and retries exactly once with a fresh one. [writeBody] is invoked
     * per attempt so streamed bodies (uploads) are re-written on retry.
     *
     * Returns the response body on 2xx, or null on any failure.
     */
    private suspend fun request(
        method: String,
        urlString: String,
        contentType: String? = null,
        writeBody: ((OutputStream) -> Unit)? = null,
    ): String? = withContext(Dispatchers.IO) {
        repeat(2) { attempt ->
            val token = auth.getAccessToken() ?: return@withContext null
            try {
                val conn = URL(urlString).openConnection() as HttpURLConnection
                conn.requestMethod = method
                conn.setRequestProperty("Authorization", "Bearer $token")
                if (contentType != null) conn.setRequestProperty("Content-Type", contentType)
                conn.connectTimeout = 15_000
                conn.readTimeout = 30_000
                if (writeBody != null) {
                    conn.doOutput = true
                    conn.outputStream.use { writeBody(it) }
                }

                when {
                    conn.responseCode in 200..299 -> {
                        return@withContext conn.inputStream.bufferedReader().readText()
                    }
                    conn.responseCode == HttpURLConnection.HTTP_UNAUTHORIZED && attempt == 0 -> {
                        Log.w(TAG, "HTTP 401 — refreshing access token and retrying")
                        auth.invalidateAccessToken(token)
                        // fall through to next repeat() iteration
                    }
                    else -> {
                        val errorBody = conn.errorStream?.bufferedReader()?.use { it.readText() }
                        Log.e(
                            TAG,
                            "$method $urlString failed: HTTP ${conn.responseCode} " +
                                "${conn.responseMessage} - Body: $errorBody"
                        )
                        return@withContext null
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "$method $urlString failed", e)
                return@withContext null
            }
        }
        null
    }

    private fun writeMultipartBody(
        out: OutputStream,
        boundary: String,
        metadataJson: String,
        contentMimeType: String,
        file: File,
    ) {
        val writer = out.bufferedWriter()

        // Part 1: metadata
        writer.write("--$boundary\r\n")
        writer.write("Content-Type: application/json; charset=UTF-8\r\n\r\n")
        writer.write(metadataJson)
        writer.write("\r\n")

        // Part 2: file content
        writer.write("--$boundary\r\n")
        writer.write("Content-Type: $contentMimeType\r\n\r\n")
        writer.flush()
        file.inputStream().use { it.copyTo(out, bufferSize = 8192) }
        out.flush()
        writer.write("\r\n--$boundary--\r\n")
        writer.flush()
    }

    private fun parseRfc3339(dateString: String): Long {
        if (dateString.isEmpty()) return 0
        // Instant.parse handles RFC 3339 with or without fractional seconds
        // (minSdk 26, so java.time is available without desugaring).
        return try {
            java.time.Instant.parse(dateString).toEpochMilli()
        } catch (_: Exception) {
            0
        }
    }
}
