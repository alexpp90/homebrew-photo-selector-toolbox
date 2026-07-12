package com.phototok.data.repository

/**
 * A set of Google Drive files the user granted the app access to via the
 * Google Picker (the only way the app can reach pre-existing Drive content
 * under the `drive.file` scope).
 *
 * @param key stable identifier used in `gdrive-picked://<key>` source URIs
 * @param name display name shown on the landing screen / recents
 * @param fileIds Drive file IDs in the order the picker returned them
 */
data class DrivePickedSelection(
    val key: String,
    val name: String,
    val fileIds: List<String>,
)

/**
 * Serializes picked-file selections to/from a single preference string,
 * newest-first and capped. Pure logic so it is unit-testable (mirrors
 * [RecentPathCodec]).
 */
object DrivePickedCodec {

    /** Max number of picked selections persisted (matches [RecentPathCodec.MAX_STORED]). */
    const val MAX_STORED = 10

    private const val ENTRY_SEP = "\u001E" // record separator
    private const val FIELD_SEP = "\u001F" // unit separator
    private const val ID_SEP = ","

    fun encode(selections: List<DrivePickedSelection>): String =
        selections.joinToString(ENTRY_SEP) {
            "${it.key}$FIELD_SEP${it.name}$FIELD_SEP${it.fileIds.joinToString(ID_SEP)}"
        }

    fun decode(raw: String?): List<DrivePickedSelection> {
        if (raw.isNullOrEmpty()) return emptyList()
        return raw.split(ENTRY_SEP).mapNotNull { entry ->
            val parts = entry.split(FIELD_SEP)
            if (parts.size == 3 && parts[0].isNotEmpty()) {
                DrivePickedSelection(
                    key = parts[0],
                    name = parts[1],
                    fileIds = parts[2].split(ID_SEP).filter { it.isNotEmpty() },
                )
            } else {
                null
            }
        }
    }

    /** Add [selection] to the front, replacing any entry with the same key, capped at [max]. */
    fun add(
        current: List<DrivePickedSelection>,
        selection: DrivePickedSelection,
        max: Int = MAX_STORED,
    ): List<DrivePickedSelection> =
        (listOf(selection) + current.filter { it.key != selection.key }).take(max)

    fun find(selections: List<DrivePickedSelection>, key: String): DrivePickedSelection? =
        selections.firstOrNull { it.key == key }
}
