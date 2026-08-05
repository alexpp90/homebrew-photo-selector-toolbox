package com.phototok.data.model

/**
 * A recently-used source folder, shown on the landing screen for quick re-selection.
 *
 * @param uri  The persisted SAF tree URI of the folder.
 * @param name Human-readable folder name to display.
 */
data class RecentPath(
    val uri: String,
    val name: String,
)
