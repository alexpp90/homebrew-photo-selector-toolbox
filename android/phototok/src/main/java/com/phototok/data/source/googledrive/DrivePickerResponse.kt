package com.phototok.data.source.googledrive

import org.json.JSONObject

/** One document entry from a Google Picker `picked` callback. */
data class PickedDriveDoc(
    val id: String,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val lastEditedUtc: Long,
)

/**
 * Parses the JSON payload the Google Picker posts back through the WebView
 * JavaScript bridge. Kept free of WebView/UI dependencies so it is
 * unit-testable.
 *
 * Expected shape (subset):
 * `{"action":"picked","docs":[{"id":"...","name":"...","mimeType":"...",
 *   "sizeBytes":123,"lastEditedUtc":1690000000000}, ...]}`
 */
object DrivePickerResponse {

    fun parsePickedDocs(json: String): List<PickedDriveDoc> {
        return try {
            val root = JSONObject(json)
            val docs = root.optJSONArray("docs") ?: return emptyList()
            (0 until docs.length()).mapNotNull { i ->
                val doc = docs.optJSONObject(i) ?: return@mapNotNull null
                val id = doc.optString("id", "")
                if (id.isEmpty()) return@mapNotNull null
                PickedDriveDoc(
                    id = id,
                    name = doc.optString("name", id),
                    mimeType = doc.optString("mimeType", ""),
                    sizeBytes = doc.optLong("sizeBytes", 0),
                    lastEditedUtc = doc.optLong("lastEditedUtc", 0),
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
