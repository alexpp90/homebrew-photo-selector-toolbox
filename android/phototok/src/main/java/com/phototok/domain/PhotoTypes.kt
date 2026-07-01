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

    companion object {
        val DEFAULT = COPY

        fun fromKey(key: String?): CollectionAction =
            entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}

/** File-type filter applied to the feed. */
enum class FileTypeFilter(val key: String) {
    ALL("all"),
    RAW("raw"),
    JPG("jpg");

    companion object {
        val DEFAULT = ALL

        fun fromKey(key: String?): FileTypeFilter =
            entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}
