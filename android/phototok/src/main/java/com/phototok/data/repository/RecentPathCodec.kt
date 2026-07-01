package com.phototok.data.repository

import com.phototok.data.model.RecentPath

/**
 * Serializes the recent-folder list to/from a single preference string, and applies the
 * newest-first + de-duplication + cap policy. Pure logic so it is unit-testable.
 */
object RecentPathCodec {

    /** Max number of recent paths persisted (display count is capped separately in the UI). */
    const val MAX_STORED = 10

    private const val ENTRY_SEP = "\u001E" // record separator
    private const val FIELD_SEP = "\u001F" // unit separator

    fun encode(paths: List<RecentPath>): String =
        paths.joinToString(ENTRY_SEP) { "${it.uri}$FIELD_SEP${it.name}" }

    fun decode(raw: String?): List<RecentPath> {
        if (raw.isNullOrEmpty()) return emptyList()
        return raw.split(ENTRY_SEP).mapNotNull { entry ->
            val parts = entry.split(FIELD_SEP)
            if (parts.size == 2 && parts[0].isNotEmpty()) RecentPath(parts[0], parts[1]) else null
        }
    }

    /** Add [uri]/[name] to the front, removing any existing entry with the same uri, capped at [max]. */
    fun add(
        current: List<RecentPath>,
        uri: String,
        name: String,
        max: Int = MAX_STORED,
    ): List<RecentPath> =
        (listOf(RecentPath(uri, name)) + current.filter { it.uri != uri }).take(max)

    /** URIs present in [before] but no longer in [after] (evicted by the cap). */
    fun evictedUris(before: List<RecentPath>, after: List<RecentPath>): List<String> {
        val kept = after.mapTo(HashSet()) { it.uri }
        return before.map { it.uri }.filter { it !in kept }
    }
}
