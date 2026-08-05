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
     */
    fun message(
        isCopy: Boolean,
        destinationNoun: String,
        succeeded: Int,
        failed: Int,
        relatedCount: Int,
    ): String {
        val verb = if (isCopy) "Copied" else "Moved"
        return when {
            failed == 0 -> {
                val suffix = if (relatedCount > 0) " (+$relatedCount related)" else ""
                "$verb to $destinationNoun$suffix"
            }
            succeeded == 0 -> "Failed to ${if (isCopy) "copy" else "move"} to $destinationNoun"
            else -> "$verb $succeeded of ${succeeded + failed} to $destinationNoun, $failed failed"
        }
    }

    /** True when the message reports an error (any file failed). */
    fun isError(failed: Int): Boolean = failed > 0
}
