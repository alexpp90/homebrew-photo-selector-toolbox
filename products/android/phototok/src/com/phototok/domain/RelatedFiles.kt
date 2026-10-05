package com.phototok.domain

import com.phototok.data.model.ImageItem

/**
 * Finds "related" files: same base name (stem) but different extension, e.g.
 * IMG_001.JPG and IMG_001.ARW. Pure logic so it is independent of the user's
 * file-type filter and easily unit-tested.
 */
object RelatedFiles {

    /** Sibling images of [target] within [all] (excludes [target] itself). Case-insensitive. */
    fun siblings(all: List<ImageItem>, target: ImageItem): List<ImageItem> {
        val stem = stemOf(target.fileName)
        return all.filter { it.uri != target.uri && stemOf(it.fileName) == stem }
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

    private fun stemOf(fileName: String): String =
        fileName.substringBeforeLast('.').lowercase()
}
