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
}
