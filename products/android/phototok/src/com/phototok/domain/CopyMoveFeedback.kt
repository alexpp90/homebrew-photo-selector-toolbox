package com.phototok.domain

/** Outcome of a single SAF file copy or move operation. */
enum class TransferStatus {
    /** Destination verified (or atomic move succeeded) and source removed (for move). */
    SUCCESS,

    /** Destination copy succeeded and verified, but source removal failed during a move. */
    PARTIAL,

    /** Transfer did not complete; any partial destination file was cleaned up and source is intact. */
    FAILURE,
}

/** Detailed result of a file transfer operation. */
data class TransferResult(
    val status: TransferStatus,
    val destinationUri: String? = null,
    val error: String? = null,
) {
    val isSuccess: Boolean get() = status == TransferStatus.SUCCESS
    val isPartial: Boolean get() = status == TransferStatus.PARTIAL
}

/**
 * Builds the user-facing feedback message for a copy/move action, including
 * partial-failure reporting when sibling (related) files are processed or when
 * a move copies the file but cannot remove the original.
 * Pure logic so it is unit-testable.
 */
object CopyMoveFeedback {

    /**
     * @param isCopy true for copy, false for move
     * @param destinationNoun e.g. "collection" or "folder"
     * @param succeeded number of files processed completely
     * @param failed number of files that failed completely
     * @param relatedCount number of sibling files included beyond the primary image
     * @param partial number of move operations where the copy succeeded and verified,
     *   but the original file could not be deleted
     */
    fun message(
        isCopy: Boolean,
        destinationNoun: String,
        succeeded: Int,
        failed: Int,
        relatedCount: Int,
        partial: Int = 0,
    ): String {
        val verb = if (isCopy) "Copied" else "Moved"
        return when {
            partial > 0 && failed == 0 ->
                "Copied to $destinationNoun — original could not be removed"
            partial > 0 ->
                "Copied to $destinationNoun — original could not be removed ($failed failed)"
            failed == 0 -> {
                val suffix = if (relatedCount > 0) " (+$relatedCount related)" else ""
                "$verb to $destinationNoun$suffix"
            }
            succeeded == 0 -> "Failed to ${if (isCopy) "copy" else "move"} to $destinationNoun"
            else -> "$verb $succeeded of ${succeeded + failed} to $destinationNoun, $failed failed"
        }
    }

    /** True when the message reports an error (any file failed or only partially moved). */
    fun isError(failed: Int, partial: Int = 0): Boolean = failed > 0 || partial > 0
}
