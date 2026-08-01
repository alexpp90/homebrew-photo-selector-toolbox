package com.phototok.domain

import com.phototok.data.model.ImageItem

/**
 * Pure ordering logic for the phone-mode feed, extracted from the ViewModel so it can
 * be unit-tested without Android dependencies.
 *
 * Rules:
 *  - Randomize wins over everything (uses the injected [shuffler] for determinism in tests).
 *  - Base order is by date, newest first (latest → oldest).
 *  - When sortByOrientation is on: landscape group first, then portrait, each kept in date order.
 *
 * Large folders are discovered progressively, so the feed is built up from
 * several batches instead of one complete list. [newItems] and [appendBatch]
 * exist for that case: they add newly discovered photos to the *end* of the
 * feed, never reordering what is already published, so a photo the user has
 * already swiped past can never jump back in front of them mid-session.
 */
object PhoneFeedOrdering {

    data class Result(
        val images: List<ImageItem>,
        /** Index where the portrait section starts, or -1 when there is no split. */
        val portraitSectionStart: Int,
    )

    fun order(
        images: List<ImageItem>,
        randomize: Boolean,
        sortByOrientation: Boolean,
        shuffler: (List<ImageItem>) -> List<ImageItem> = { it.shuffled() },
    ): Result {
        if (randomize) {
            return Result(shuffler(images), -1)
        }

        val byDate = images.sortedByDescending { it.lastModified }
        if (!sortByOrientation) {
            return Result(byDate, -1)
        }

        val landscape = byDate.filter { it.isLandscape }
        val portrait = byDate.filter { !it.isLandscape }
        val result = landscape + portrait
        val split = if (portrait.isEmpty()) -1 else landscape.size
        return Result(result, split)
    }

    /**
     * The subset of [incoming] that is not yet known, identified by URI.
     *
     * Progressive discovery re-emits a cumulative list on every batch, and the
     * user may have removed items (move / delete) from the feed in between, so
     * "not currently in the feed" is *not* the same as "new". Callers therefore
     * pass the set of URIs ever published for this folder.
     */
    fun newItems(incoming: List<ImageItem>, knownUris: Set<String>): List<ImageItem> =
        if (knownUris.isEmpty()) incoming else incoming.filter { it.uri !in knownUris }

    /**
     * Append a freshly discovered batch to an already published feed.
     *
     * [fresh] is ordered (or shuffled) on its own and appended after [current];
     * the order of [current] is never touched. This keeps the feed stable while
     * a large folder is still being enumerated in the background — the price is
     * that a randomized feed is shuffled per batch rather than globally, which
     * is invisible to the user and preferable to items moving under their thumb.
     *
     * When [current] is empty this is exactly [order].
     */
    fun appendBatch(
        current: List<ImageItem>,
        fresh: List<ImageItem>,
        randomize: Boolean,
        sortByOrientation: Boolean,
        shuffler: (List<ImageItem>) -> List<ImageItem> = { it.shuffled() },
    ): Result {
        if (fresh.isEmpty()) {
            return Result(current, portraitSplit(current, sortByOrientation))
        }
        if (current.isEmpty()) {
            return order(fresh, randomize, sortByOrientation, shuffler)
        }
        // Order within the batch only; the orientation split is recomputed over
        // the merged list (and re-grouped later, once dimensions are known).
        val ordered = order(fresh, randomize, sortByOrientation = false, shuffler = shuffler).images
        val merged = current + ordered
        return Result(merged, portraitSplit(merged, sortByOrientation))
    }

    /**
     * Recompute the portrait-section start for an already-ordered list, e.g. after an
     * item was removed or restored. Returns -1 when orientation sorting is off, the
     * list is empty, or there are no portrait images.
     */
    fun portraitSplit(images: List<ImageItem>, sortByOrientation: Boolean): Int {
        if (!sortByOrientation || images.isEmpty()) return -1
        val firstPortrait = images.indexOfFirst { !it.isLandscape }
        return if (firstPortrait >= 0) firstPortrait else -1
    }

    /** Apply the user's file-type filter to a list of images. */
    fun filterByType(images: List<ImageItem>, filter: FileTypeFilter): List<ImageItem> =
        when (filter) {
            FileTypeFilter.ALL -> images
            FileTypeFilter.RAW -> images.filter { PhotoExtensions.isRaw(it.fileName) }
            FileTypeFilter.JPG -> images.filter { PhotoExtensions.isJpeg(it.fileName) }
        }
}
