package com.phototok.domain

import com.phototok.data.model.ImageItem

/**
 * Finds "related" files: same base name (stem) but different extension, e.g.
 * IMG_001.JPG and IMG_001.ARW. Pure logic so it is independent of the user's
 * file-type filter and easily unit-tested.
 */
object RelatedFiles {

    /**
     * Sibling images of [target] within [all] (excludes [target] itself). Case-insensitive.
     * Matches only files in the same directory (when parent URIs are known) with matching stems
     * and differing, complementary extensions.
     */
    fun siblings(all: List<ImageItem>, target: ImageItem): List<ImageItem> {
        val stem = stemOf(target.fileName)
        val targetExt = PhotoExtensions.extensionOf(target.fileName)
        return all.filter { candidate ->
            candidate.uri != target.uri &&
                stemOf(candidate.fileName) == stem &&
                PhotoExtensions.extensionOf(candidate.fileName) != targetExt &&
                (target.parentUri == null || candidate.parentUri == null || candidate.parentUri == target.parentUri)
        }
    }

    /**
     * Checks if [images] contains both RAW and JPEG formats of the same photo
     * (matching stem with at least one RAW and at least one JPEG extension).
     */
    fun hasRawJpegPairs(images: List<ImageItem>): Boolean {
        val byStem = images.groupBy { stemOf(it.fileName) }
        return byStem.values.any { group ->
            val hasRaw = group.any { PhotoExtensions.isRaw(it.fileName) }
            val hasJpeg = group.any { PhotoExtensions.isJpeg(it.fileName) }
            hasRaw && hasJpeg
        }
    }

    /**
     * Checks if the majority of unique photos (stems) in [images] do NOT match [filter].
     *
     * Photos with matching RAW and JPEG pairs of the same shot are considered matching
     * if either format satisfies the filter (e.g. shooting RAW+JPEG does not trigger a warning
     * when filtered to RAW only or JPEG only).
     */
    fun hasFilterMismatch(images: List<ImageItem>, filter: FileTypeFilter): Boolean {
        if (filter == FileTypeFilter.ALL || images.isEmpty()) return false
        val byStem = images.groupBy { stemOf(it.fileName) }
        val matchingCount = byStem.values.count { group ->
            when (filter) {
                FileTypeFilter.ALL -> true
                FileTypeFilter.RAW -> group.any { PhotoExtensions.isRaw(it.fileName) }
                FileTypeFilter.JPG -> group.any { PhotoExtensions.isJpeg(it.fileName) }
            }
        }
        val unmatchedCount = byStem.size - matchingCount
        return unmatchedCount > matchingCount
    }

    private fun stemOf(fileName: String): String =
        fileName.substringBeforeLast('.').lowercase()
}
