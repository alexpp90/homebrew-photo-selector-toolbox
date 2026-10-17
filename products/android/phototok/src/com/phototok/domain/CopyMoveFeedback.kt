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
     * @param renamedTo the name the primary file got when the transfer had to add a collision
     *   suffix (`-2`, `-3`, …), or null when it kept its name; only reported when everything
     *   succeeded ("Moved to Selection as IMG_0001-2.jpg (+1 related)")
     */
    fun message(
        isCopy: Boolean,
        destinationNoun: String,
        succeeded: Int,
        failed: Int,
        relatedCount: Int,
        partial: Int = 0,
        renamedTo: String? = null,
    ): String {
        if (renamedTo != null && failed == 0 && partial == 0) {
            val verb = if (isCopy) "Copied" else "Moved"
            val related = if (relatedCount > 0) " (+$relatedCount related)" else ""
            return "$verb to $destinationNoun as $renamedTo$related"
        }
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

    /**
     * File name of a document URI string: the last path segment of the (percent-decoded) document
     * id, so `…/document/primary%3ADCIM%2FIMG_1.JPG` gives `IMG_1.JPG`.
     */
    fun displayNameOf(uri: String): String {
        val last = uri.substringAfterLast('/')
        val decoded = try {
            java.net.URLDecoder.decode(last, "UTF-8")
        } catch (_: Exception) {
            last
        }
        return decoded.substringAfterLast('/').substringAfterLast(':')
    }

    /** True when the message reports an error (any file failed or only partially moved). */
    fun isError(failed: Int, partial: Int = 0): Boolean = failed > 0 || partial > 0
}
