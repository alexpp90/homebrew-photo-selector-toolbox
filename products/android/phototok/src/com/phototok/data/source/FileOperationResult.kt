package com.phototok.data.source

/**
 * Outcome of a single SAF file copy or move operation (`docs/shared/FILE_OPERATIONS.md`).
 *
 * - [success] is true when the transfer completed in full (copy verified; for a
 *   move, the source was also removed).
 * - [partial] is true when a move's destination copy was verified on disk, but
 *   removing the original source failed. The destination copy is preserved and
 *   the caller must surface a partial-outcome warning (`Copied to … — original
 *   could not be removed`) and restore the source item to the feed.
 */
data class FileOperationResult(
    val sourceUri: String,
    val destinationUri: String?,
    val success: Boolean,
    val error: String? = null,
    val partial: Boolean = false,
) {
    companion object {
        fun success(sourceUri: String, destinationUri: String?) =
            FileOperationResult(sourceUri, destinationUri, success = true)

        fun failure(sourceUri: String, error: String?) =
            FileOperationResult(sourceUri, null, success = false, error = error)

        fun partial(
            sourceUri: String,
            destinationUri: String,
            error: String = "Copied, but the original could not be removed",
        ) = FileOperationResult(
            sourceUri = sourceUri,
            destinationUri = destinationUri,
            success = false,
            error = error,
            partial = true,
        )
    }
}
