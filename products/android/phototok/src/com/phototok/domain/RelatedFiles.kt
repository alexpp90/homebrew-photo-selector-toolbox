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

    private fun stemOf(fileName: String): String =
        fileName.substringBeforeLast('.').lowercase()
}
