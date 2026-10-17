package com.phototok.domain

/**
 * Builds the user-facing feedback message for a copy/move action, including
 * partial-failure reporting when sibling (related) files are processed.
 * Pure logic so it is unit-testable.
 */
object CopyMoveFeedback {

    /**
     * @param isCopy true for copy, false for move
     * @param destinationNoun e.g. "collection" or "folder"
     * @param succeeded number of files processed successfully
     * @param failed number of files that failed
     * @param relatedCount number of sibling files included beyond the primary image
     * @param partial number of move operations where the copy succeeded on disk
     *   but removing the source failed (`FileOperationResult.partial`)
     */
    fun message(
        isCopy: Boolean,
        destinationNoun: String,
        succeeded: Int,
        failed: Int,
        relatedCount: Int,
        partial: Int = 0,
    ): String {
        if (!isCopy && partial > 0 && failed == 0) {
            return "Copied to $destinationNoun — original could not be removed"
        }
        val verb = if (isCopy) "Copied" else "Moved"
        val totalFailed = failed + partial
        return when {
            totalFailed == 0 -> {
                val suffix = if (relatedCount > 0) " (+$relatedCount related)" else ""
                "$verb to $destinationNoun$suffix"
            }
            succeeded == 0 -> "Failed to ${if (isCopy) "copy" else "move"} to $destinationNoun"
            else -> "$verb $succeeded of ${succeeded + totalFailed} to $destinationNoun, $totalFailed failed"
        }
    }

    /** True when the message reports an error (any file failed or only partially moved). */
    fun isError(failed: Int, partial: Int = 0): Boolean = failed > 0 || partial > 0
}
