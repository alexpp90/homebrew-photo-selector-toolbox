package com.phototok.domain

import com.phototok.data.model.ImageItem

/**
 * Scan state metadata for a folder, tracking the maximum timestamp and file count
 * observed during the user's previous review session.
 */
data class FolderScanInfo(
    val maxLastModified: Long = 0L,
    val fileCount: Int = 0,
)

/**
 * Pure domain logic for detecting new unscanned photos and resolving target
 * feed positions on folder open and active session reload.
 */
object FolderScanLogic {

    /**
     * Returns the 0-based index of the first image in [images] whose [ImageItem.lastModified]
     * is strictly greater than [previousMaxLastModified].
     *
     * Returns -1 if [previousMaxLastModified] is <= 0 (e.g. initial folder load without
     * prior scan record) or if no images are newer than [previousMaxLastModified].
     */
    fun findFirstNewImageIndex(
        images: List<ImageItem>,
        previousMaxLastModified: Long,
    ): Int {
        if (previousMaxLastModified <= 0L || images.isEmpty()) return -1
        return images.indexOfFirst { it.lastModified > previousMaxLastModified }
    }

    /**
     * Counts how many images in [images] have [ImageItem.lastModified] strictly greater
     * than [previousMaxLastModified].
     */
    fun countNewImages(
        images: List<ImageItem>,
        previousMaxLastModified: Long,
    ): Int {
        if (previousMaxLastModified <= 0L || images.isEmpty()) return 0
        return images.count { it.lastModified > previousMaxLastModified }
    }

    /**
     * Computes the updated scan metadata from the current list of images.
     */
    fun computeUpdatedScanInfo(images: List<ImageItem>): FolderScanInfo {
        if (images.isEmpty()) return FolderScanInfo(0L, 0)
        val maxTime = images.maxOfOrNull { it.lastModified } ?: 0L
        return FolderScanInfo(maxLastModified = maxTime, fileCount = images.size)
    }
}
