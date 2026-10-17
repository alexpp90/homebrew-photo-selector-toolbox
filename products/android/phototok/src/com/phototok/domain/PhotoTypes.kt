package com.phototok.domain

/**
 * Typed replacements for the raw setting strings that used to be passed around
 * ("delete"/"copy"/"move", "all"/"raw"/"jpg"). Each enum keeps its DataStore
 * wire format in [key] so persisted preferences remain backward compatible.
 */

/** Action performed when the user swipes left on a photo. */
enum class SwipeAction(val key: String) {
    DELETE("delete"),
    COPY("copy"),
    MOVE("move");

    companion object {
        val DEFAULT = DELETE

        fun fromKey(key: String?): SwipeAction =
            entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}

/** Action performed when the user adds a photo to the collection (swipe right). */
enum class CollectionAction(val key: String) {
    COPY("copy"),
    MOVE("move");

    fun toSwipeRightVerb(): SwipeRightVerb = when (this) {
        COPY -> SwipeRightVerb.COPY
        MOVE -> SwipeRightVerb.MOVE
    }

    companion object {
        val DEFAULT = COPY

        fun fromKey(key: String?): CollectionAction =
            entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}

/**
 * Action performed when the user swipes right on a photo (v2 per-source verb).
 * Replaces binary [CollectionAction] when `FAVOURITE` is supported.
 */
enum class SwipeRightVerb(val key: String) {
    COPY("copy"),
    MOVE("move"),
    FAVOURITE("favourite");

    fun toCollectionAction(): CollectionAction = when (this) {
        COPY -> CollectionAction.COPY
        MOVE -> CollectionAction.MOVE
        FAVOURITE -> CollectionAction.COPY
    }

    fun toCollectionActionOrNull(): CollectionAction? = when (this) {
        COPY -> CollectionAction.COPY
        MOVE -> CollectionAction.MOVE
        FAVOURITE -> null
    }

    companion object {
        val DEFAULT = COPY

        fun fromKey(key: String?, default: SwipeRightVerb = DEFAULT): SwipeRightVerb =
            entries.firstOrNull { it.key == key } ?: default

        fun fromCollectionAction(action: CollectionAction): SwipeRightVerb =
            action.toSwipeRightVerb()
    }
}

/** Feed ordering mode per source kind. */
enum class FeedOrderMode(val key: String) {
    NEWEST_FIRST("newest_first"),
    SHOOTING("shooting"),
    SHUFFLE("shuffle"),
    ORIENTATION_THEN_NEWEST("orientation_then_newest");

    val isRandomized: Boolean
        get() = this == SHUFFLE

    val isSortByOrientation: Boolean
        get() = this == ORIENTATION_THEN_NEWEST

    companion object {
        val DEFAULT = NEWEST_FIRST

        fun fromKey(key: String?, default: FeedOrderMode = DEFAULT): FeedOrderMode =
            entries.firstOrNull { it.key == key } ?: default

        fun fromLegacy(randomize: Boolean, sortByOrientation: Boolean): FeedOrderMode = when {
            randomize -> SHUFFLE
            sortByOrientation -> ORIENTATION_THEN_NEWEST
            else -> NEWEST_FIRST
        }

        fun fromLegacyFlags(sortByOrientation: Boolean, randomize: Boolean): FeedOrderMode =
            fromLegacy(randomize = randomize, sortByOrientation = sortByOrientation)
    }
}

/** File-type filter applied to the feed. */
enum class FileTypeFilter(val key: String) {
    ALL("all"),
    RAW("raw"),
    JPG("jpg"),
    ONE_PER_SHOT("one_per_shot");

    companion object {
        val DEFAULT = ALL

        fun fromKey(key: String?, default: FileTypeFilter = DEFAULT): FileTypeFilter =
            entries.firstOrNull { it.key == key } ?: default
    }
}

