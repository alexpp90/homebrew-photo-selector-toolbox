package com.phototok.data.model

import com.photoselector.core.storage.SourceKind

/**
 * A recently-used source folder, shown on the landing screen for quick re-selection.
 *
 * @param uri        The persisted SAF tree URI of the folder.
 * @param name       Human-readable folder name to display.
 * @param kind       Classified storage kind of this source folder.
 * @param volumeUuid Removable volume UUID when [kind] is [SourceKind.REMOVABLE], or null.
 */
data class RecentPath(
    val uri: String,
    val name: String,
    val kind: SourceKind = SourceKind.PHONE_LIBRARY,
    val volumeUuid: String? = null,
) {
    val displayName: String
        get() = name.ifEmpty { "Folder" }
}

