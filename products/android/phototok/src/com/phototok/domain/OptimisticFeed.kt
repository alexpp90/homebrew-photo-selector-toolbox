package com.phototok.domain

import com.phototok.data.model.ImageItem

/**
 * Pure list transitions for actions that are applied to the feed **before** the
 * file system has confirmed them.
 *
 * A swipe must feel instant, so a move takes the photo out of the feed the
 * moment the gesture completes and the copy/move runs in the background. If the
 * transfer then fails, the photo has to come back — silently dropping it from
 * the feed would tell the user a file was filed away when it is still sitting
 * in the source folder.
 *
 * Kept Android-free so the index arithmetic is unit-testable.
 */
object OptimisticFeed {

    /** Where an image sat in both feed lists before it was optimistically removed. */
    data class Slot(
        val image: ImageItem,
        /** Index in the filtered feed, or -1 when the filter hid it. */
        val index: Int,
        /** Index in the unfiltered list, or -1 when absent. */
        val allImagesIndex: Int,
    )

    /** Record the positions of [targets] so they can be put back on failure. */
    fun slotsOf(
        images: List<ImageItem>,
        allImages: List<ImageItem>,
        targets: List<ImageItem>,
    ): List<Slot> = targets.map { target ->
        Slot(
            image = target,
            index = images.indexOfFirst { it.uri == target.uri },
            allImagesIndex = allImages.indexOfFirst { it.uri == target.uri },
        )
    }

    /**
     * The slots of the targets that were **not** removed for good, re-based to the
     * lists as they look once the [goneUris] are really gone. [restore] re-inserts
     * into those lists, so an index recorded before a sibling disappeared would
     * otherwise put the survivor too far down the feed.
     */
    fun slotsLeftBehind(slots: List<Slot>, goneUris: Set<String>): List<Slot> {
        val gone = slots.filter { it.image.uri in goneUris }
        return slots.filter { it.image.uri !in goneUris }.map { slot ->
            slot.copy(
                index = if (slot.index >= 0) slot.index - gone.count { it.index in 0 until slot.index } else -1,
                allImagesIndex = if (slot.allImagesIndex >= 0) {
                    slot.allImagesIndex - gone.count { it.allImagesIndex in 0 until slot.allImagesIndex }
                } else {
                    -1
                },
            )
        }
    }

    /**
     * Re-insert [slots] at their original positions in both lists.
     *
     * The active photo is tracked by URI rather than by index: re-inserting an
     * item in front of the user would otherwise silently shift them onto a
     * different photo than the one they are looking at.
     */
    fun restore(
        images: List<ImageItem>,
        allImages: List<ImageItem>,
        slots: List<Slot>,
        currentIndex: Int,
        sortByOrientation: Boolean,
    ): PendingDeleteLogic.ListsState {
        val activeUri = images.getOrNull(currentIndex)?.uri

        val restoredImages = images.toMutableList()
        // Ascending order so an earlier insertion cannot displace a later index.
        slots.filter { it.index >= 0 }
            .sortedBy { it.index }
            .forEach { slot ->
                if (restoredImages.none { it.uri == slot.image.uri }) {
                    restoredImages.add(slot.index.coerceIn(0, restoredImages.size), slot.image)
                }
            }

        val restoredAll = allImages.toMutableList()
        slots.filter { it.allImagesIndex >= 0 }
            .sortedBy { it.allImagesIndex }
            .forEach { slot ->
                if (restoredAll.none { it.uri == slot.image.uri }) {
                    restoredAll.add(slot.allImagesIndex.coerceIn(0, restoredAll.size), slot.image)
                }
            }

        val newIndex = activeUri
            ?.let { uri -> restoredImages.indexOfFirst { it.uri == uri } }
            ?.takeIf { it >= 0 }
            ?: currentIndex.coerceIn(0, (restoredImages.size - 1).coerceAtLeast(0))

        return PendingDeleteLogic.ListsState(
            images = restoredImages,
            allImages = restoredAll,
            currentIndex = newIndex,
            portraitSectionStart = PhoneFeedOrdering.portraitSplit(restoredImages, sortByOrientation),
        )
    }
}
