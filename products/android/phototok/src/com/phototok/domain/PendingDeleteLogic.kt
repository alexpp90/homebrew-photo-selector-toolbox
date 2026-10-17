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
        /** Exact slots of the primary image and its siblings in both feed lists. */
        val slots: List<OptimisticFeed.Slot> =
            listOf(OptimisticFeed.Slot(image, index, allImagesIndex)) +
                related.map { OptimisticFeed.Slot(it, -1, -1) },
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
        val targets = listOf(image) + related
        val slots = OptimisticFeed.slotsOf(images, allImages, targets)
        val removedUris = targets.map { it.uri }.toSet()

        val updatedImages = images.filter { it.uri !in removedUris }
        val updatedAll = allImages.filter { it.uri !in removedUris }
        val removedBefore = (0 until currentIndex).count { images[it].uri in removedUris }
        val newIndex = if (updatedImages.isEmpty()) {
            0
        } else {
            (currentIndex - removedBefore).coerceIn(0, updatedImages.size - 1)
        }

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
            slots = slots,
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
        val updatedImages = images.toMutableList()
        pending.slots
            .filter { it.index >= 0 }
            .sortedBy { it.index }
            .forEach { slot ->
                if (updatedImages.none { it.uri == slot.image.uri }) {
                    updatedImages.add(slot.index.coerceIn(0, updatedImages.size), slot.image)
                }
            }

        val updatedAll = allImages.toMutableList()
        pending.slots
            .filter { it.allImagesIndex >= 0 }
            .sortedBy { it.allImagesIndex }
            .forEach { slot ->
                if (updatedAll.none { it.uri == slot.image.uri }) {
                    updatedAll.add(slot.allImagesIndex.coerceIn(0, updatedAll.size), slot.image)
                }
            }
        pending.slots
            .filter { it.allImagesIndex < 0 }
            .forEach { slot ->
                if (updatedAll.none { it.uri == slot.image.uri }) {
                    updatedAll.add(slot.image)
                }
            }

        val restoredIndex = updatedImages.indexOfFirst { it.uri == pending.image.uri }
            .takeIf { it >= 0 }
            ?: pending.index.coerceIn(0, (updatedImages.size - 1).coerceAtLeast(0))

        return ListsState(
            images = updatedImages,
            allImages = updatedAll,
            currentIndex = restoredIndex,
            portraitSectionStart = PhoneFeedOrdering.portraitSplit(updatedImages, sortByOrientation),
        )
    }
}
