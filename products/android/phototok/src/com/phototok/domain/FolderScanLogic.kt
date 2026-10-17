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
     * Where the viewer lands once enumeration is complete and the folder is NOT being
     * reloaded inside an active session:
     *
     * - The user already swiped: stay on [preservedUri] (fallback [currentIndex]).
     * - Randomized (`SHUFFLE`) feed (`U-16`): never jump to [findFirstNewImageIndex] —
     *   always stay on [preservedUri] (or `0` on fresh open) so completion does not teleport
     *   the user.
     * - `SHOOTING` feed (`U-12`): land on the first unseen photo (via [seenUris] from `SeenStore`),
     *   or `0` if none recorded / all seen.
     * - Otherwise (`NEWEST_FIRST` / `ORIENTATION_THEN_NEWEST`): any photo newer than
     *   [previousMaxLastModified] puts the user at the first new one; otherwise `0`.
     */
    fun resolveLandingIndex(
        images: List<ImageItem>,
        previousMaxLastModified: Long,
        userHasNavigated: Boolean,
        randomized: Boolean,
        preservedUri: String?,
        currentIndex: Int,
        mode: FeedOrderMode = if (randomized) FeedOrderMode.SHUFFLE else FeedOrderMode.NEWEST_FIRST,
        seenUris: Set<String> = emptySet(),
    ): Int {
        if (images.isEmpty()) return 0
        val preserved = preservedUri?.let { u -> images.indexOfFirst { it.uri == u } }?.takeIf { it >= 0 }
            ?: currentIndex.coerceIn(0, images.size - 1)
        if (userHasNavigated) return preserved
        if (randomized || mode == FeedOrderMode.SHUFFLE) {
            return preserved
        }
        if (mode == FeedOrderMode.SHOOTING) {
            if (seenUris.isNotEmpty()) {
                val firstUnseen = images.indexOfFirst { it.uri !in seenUris }
                if (firstUnseen >= 0) return firstUnseen
            }
            return 0
        }
        val firstNew = findFirstNewImageIndex(images, previousMaxLastModified)
        if (firstNew >= 0) return firstNew
        return 0
    }

    /**
     * Returns the 0-based index of the first image (in feed order; for a newest-first feed
     * this is where the run of new photos starts) in [images] whose [ImageItem.lastModified]
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

