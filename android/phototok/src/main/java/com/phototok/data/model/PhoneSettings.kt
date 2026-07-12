package com.phototok.data.model

import com.phototok.domain.CollectionAction
import com.phototok.domain.FileTypeFilter
import com.phototok.domain.SwipeAction

/**
 * Typed snapshot of all simple phone-mode settings, produced as a single flow
 * by the settings repository (replaces a fragile 12-way positional combine).
 */
data class PhoneSettings(
    val collectionAction: CollectionAction = CollectionAction.DEFAULT,
    val trashConfirmEnabled: Boolean = true,
    val directDeleteConfirmEnabled: Boolean = true,
    val sortByOrientation: Boolean = false,
    val randomizeOrder: Boolean = false,
    val fileTypeFilter: FileTypeFilter = FileTypeFilter.DEFAULT,
    val leftSwipeAction: SwipeAction = SwipeAction.DEFAULT,
    val showExifOverlay: Boolean = false,
    val moveRelatedFiles: Boolean = false,
    val recentPathsEnabled: Boolean = true,
    val recentPathsCount: Int = 3,
    val recentPaths: List<RecentPath> = emptyList(),
)
