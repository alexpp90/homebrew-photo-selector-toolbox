package com.phototok.domain

/**
 * One-time explanations shown the first time the user performs an action.
 *
 * Photo-Tok is gesture-first, so an action's *effect* (a file was copied, a
 * file was trashed) is invisible unless we say so. Each hint fires exactly
 * once, is persisted by its [key] in DataStore, and can be brought back via
 * "Reset tutorials" in Settings.
 */
enum class FirstRunHint(val key: String) {
    /** First swipe-right (add to collection). */
    SWIPE_RIGHT("swipe_right"),

    /** First swipe-left when it copies or moves to the custom folder. */
    SWIPE_LEFT_FOLDER("swipe_left_folder"),

    /** First swipe-left when it deletes. */
    SWIPE_LEFT_DELETE("swipe_left_delete"),

    /** First vertical swipe (browsing the feed). */
    SWIPE_VERTICAL("swipe_vertical"),

    /** First single tap (HUD toggle). */
    TAP_HUD("tap_hud"),

    /** First double tap (zoom). */
    DOUBLE_TAP_ZOOM("double_tap_zoom"),

    /** First use of Revert. */
    REVERT("revert");

    companion object {
        fun fromKey(key: String?): FirstRunHint? = entries.firstOrNull { it.key == key }
    }
}

/**
 * Builds the wording for each [FirstRunHint].
 *
 * Kept as a pure function of the user's current settings so the copy always
 * describes what *actually* happened (copy vs. move, which folder) rather than
 * a generic default — and so it is unit-testable without Android.
 */
object FirstRunHintText {

    private const val SETTINGS_SUFFIX = "You can change this in Settings."

    /** Fallback wording when no custom folder name is known yet. */
    private const val DEFAULT_COLLECTION = "your collection folder"
    private const val DEFAULT_LEFT_SWIPE = "your chosen folder"

    fun title(hint: FirstRunHint): String = when (hint) {
        FirstRunHint.SWIPE_RIGHT -> "Added to collection"
        FirstRunHint.SWIPE_LEFT_FOLDER -> "Sent to folder"
        FirstRunHint.SWIPE_LEFT_DELETE -> "Moved to trash"
        FirstRunHint.SWIPE_VERTICAL -> "Browsing photos"
        FirstRunHint.TAP_HUD -> "Distraction-free view"
        FirstRunHint.DOUBLE_TAP_ZOOM -> "Zoom"
        FirstRunHint.REVERT -> "Undo"
    }

    fun message(
        hint: FirstRunHint,
        collectionAction: CollectionAction = CollectionAction.DEFAULT,
        leftSwipeAction: SwipeAction = SwipeAction.DEFAULT,
        collectionFolderName: String = "",
        leftSwipeFolderName: String = "",
    ): String = when (hint) {
        FirstRunHint.SWIPE_RIGHT -> {
            val verb = if (collectionAction == CollectionAction.COPY) "copied" else "moved"
            val where = collectionFolderName.ifBlank { DEFAULT_COLLECTION }
            "You $verb this photo to $where. Every swipe right does the same. $SETTINGS_SUFFIX"
        }

        FirstRunHint.SWIPE_LEFT_FOLDER -> {
            val verb = if (leftSwipeAction == SwipeAction.COPY) "copied" else "moved"
            val where = leftSwipeFolderName.ifBlank { DEFAULT_LEFT_SWIPE }
            "You $verb this photo to $where. Every swipe left does the same. $SETTINGS_SUFFIX"
        }

        FirstRunHint.SWIPE_LEFT_DELETE ->
            "This photo is on its way to the trash. Tap Revert at the bottom to bring it " +
                "back. Swipe left can copy or move instead — $SETTINGS_SUFFIX"

        FirstRunHint.SWIPE_VERTICAL ->
            "Swipe up and down to move through your photos. Your place is remembered per folder."

        FirstRunHint.TAP_HUD ->
            "A single tap hides the overlays so you see the photo alone. Tap again to bring them back."

        FirstRunHint.DOUBLE_TAP_ZOOM ->
            "Double-tap to zoom in and check the detail, then drag to pan. Double-tap again to reset."

        FirstRunHint.REVERT ->
            "The photo is back in your feed. Revert only undoes the most recent deletion."
    }
}
