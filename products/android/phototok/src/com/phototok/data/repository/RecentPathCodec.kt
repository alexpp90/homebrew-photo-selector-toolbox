package com.phototok.data.repository

import com.photoselector.core.storage.SourceKind
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
        paths.joinToString(ENTRY_SEP) { path ->
            listOf(
                path.uri,
                path.name,
                path.kind.name,
                path.volumeUuid.orEmpty(),
            ).joinToString(FIELD_SEP)
        }

    fun decode(raw: String?): List<RecentPath> {
        if (raw.isNullOrEmpty()) return emptyList()
        return raw.split(ENTRY_SEP).mapNotNull { entry ->
            val parts = entry.split(FIELD_SEP)
            when {
                parts.size < 2 || parts[0].isEmpty() -> null
                parts.size == 2 -> RecentPath(
                    uri = parts[0],
                    name = parts[1],
                    kind = SourceKind.PHONE_LIBRARY,
                    volumeUuid = null,
                )
                else -> {
                    val kind = SourceKind.entries.firstOrNull { it.name == parts[2] }
                        ?: SourceKind.PHONE_LIBRARY
                    val volumeUuid = parts.getOrNull(3)?.takeIf { it.isNotEmpty() }
                    RecentPath(
                        uri = parts[0],
                        name = parts[1],
                        kind = kind,
                        volumeUuid = volumeUuid,
                    )
                }
            }
        }
    }

    /** Add [uri]/[name] to the front, removing any existing entry with the same uri, capped at [max]. */
    fun add(
        current: List<RecentPath>,
        uri: String,
        name: String,
        max: Int = MAX_STORED,
        kind: SourceKind = SourceKind.PHONE_LIBRARY,
        volumeUuid: String? = null,
    ): List<RecentPath> =
        (listOf(RecentPath(uri, name, kind, volumeUuid)) + current.filter { it.uri != uri }).take(max)

    /** URIs present in [before] but no longer in [after] (evicted by the cap). */
    fun evictedUris(before: List<RecentPath>, after: List<RecentPath>): List<String> {
        val kept = after.mapTo(HashSet()) { it.uri }
        return before.map { it.uri }.filter { it !in kept }
    }
}

