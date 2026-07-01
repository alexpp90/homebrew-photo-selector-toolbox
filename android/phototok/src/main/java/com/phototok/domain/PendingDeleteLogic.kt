package com.phototok.domain

import com.phototok.data.model.ImageItem

/**
 * Pure state transitions for the swipe-to-delete undo window.
 *
 * A left-swipe delete is applied to the UI lists immediately but the file(s) are
 * only removed from disk once the deletion is "finalized" (next delete, folder
 * change, screen exit, or ViewModel clear). Until then the user can revert.
 *
 * Extracted from PhoneModeViewModel so the index math and sibling handling can be
 * unit-tested without Android dependencies.
 */
object PendingDeleteLogic {

    /** A deletion that has been applied to the UI but not yet to disk. */
    data class Pending(
        val image: ImageItem,
        /** Index the image had in the filtered list (for re-insertion on revert). */
        val index: Int,
        /** Index the image had in the unfiltered list (-1 if not present). */
        val allImagesIndex: Int,
        /** Sibling files removed together with the primary image. */
        val related: List<ImageItem>,
        /** Revert stays available while the image at this URI is the active one. */
        val revertAllowedUri: String?,
    )

    /** Snapshot of the list-related UI state after a transition. */
    data class ListsState(
        val images: List<ImageItem>,
        val allImages: List<ImageItem>,
        val currentIndex: Int,
        val portraitSectionStart: Int,
    )

    /**
     * Remove the image at [currentIndex] (and its [related] siblings) from both lists.
     * Returns null when [currentIndex] is out of bounds.
     */
    fun remove(
        images: List<ImageItem>,
        allImages: List<ImageItem>,
        currentIndex: Int,
        related: List<ImageItem>,
        sortByOrientation: Boolean,
    ): Pair<ListsState, Pending>? {
        val image = images.getOrNull(currentIndex) ?: return null
        val allImagesIndex = allImages.indexOfFirst { it.uri == image.uri }
        val removedUris = (listOf(image) + related).map { it.uri }.toSet()

        val updatedImages = images.toMutableList().apply { removeAt(currentIndex) }
        val updatedAll = allImages.filter { it.uri !in removedUris }
        val newIndex = currentIndex.coerceAtMost(updatedImages.size - 1).coerceAtLeast(0)

        val state = ListsState(
            images = updatedImages,
            allImages = updatedAll,
            currentIndex = newIndex,
            portraitSectionStart = PhoneFeedOrdering.portraitSplit(updatedImages, sortByOrientation),
        )
        val pending = Pending(
            image = image,
            index = currentIndex,
            allImagesIndex = allImagesIndex,
            related = related,
            revertAllowedUri = updatedImages.getOrNull(newIndex)?.uri,
        )
        return Pair(state, pending)
    }

    /** Re-insert a [pending] deletion (primary + siblings) into both lists. */
    fun restore(
        images: List<ImageItem>,
        allImages: List<ImageItem>,
        pending: Pending,
        sortByOrientation: Boolean,
    ): ListsState {
        val updatedImages = images.toMutableList().apply {
            add(pending.index.coerceIn(0, size), pending.image)
        }
        val updatedAll = allImages.toMutableList().apply {
            if (pending.allImagesIndex in 0..size) {
                add(pending.allImagesIndex, pending.image)
            } else {
                add(pending.image)
            }
            pending.related.forEach { sibling ->
                if (none { it.uri == sibling.uri }) add(sibling)
            }
        }
        return ListsState(
            images = updatedImages,
            allImages = updatedAll,
            currentIndex = pending.index.coerceIn(0, updatedImages.size - 1),
            portraitSectionStart = PhoneFeedOrdering.portraitSplit(updatedImages, sortByOrientation),
        )
    }
}
