package com.photoselectortoolbox.domain.interaction

/**
 * The one place a gesture's meaning is written down.
 *
 * The shipped viewer claimed "swipe ← → navigate" on its hint card while a
 * leftward swipe **deleted the photograph**, a rightward swipe dismissed the
 * viewer, and navigation was a vertical pager. Three of the five lines on that
 * card were false, and the single destructive action in the viewer was bound to
 * the gesture the card described as harmless.
 *
 * That was not a copy bug. It was a card written by hand next to bindings
 * written somewhere else, with nothing forcing them to agree. So the bindings
 * and the wording are now the same object: the viewer reads [FullscreenGesture]
 * to decide what a gesture does, the hint card reads it to decide what to say,
 * and a hint row that does not correspond to a binding cannot be expressed.
 *
 * Kept free of Compose and Android types so every rule here is unit-testable.
 */
object SelectorGestures {

    /**
     * The rows the fullscreen hint card renders, in order.
     *
     * Derived from the bindings, never written out. No gesture files a
     * photograph any more, so no row here depends on the filing setting — the
     * filing *button*'s wording comes from
     * [com.photoselectortoolbox.domain.format.SelectionActionLabels] instead.
     */
    fun fullscreenHintRows(): List<GestureRow> =
        FullscreenGesture.entries.map { gesture ->
            GestureRow(input = gesture.input, effect = gesture.effect)
        }

    /**
     * The rows the selector's shortcut sheet renders, in order.
     *
     * Same contract as above: the sheet is generated from the bindings, so a
     * shortcut that stops working stops being advertised in the same commit.
     */
    fun selectorShortcutRows(filingAction: FilingAction): List<GestureRow> =
        SelectorShortcut.entries.map { shortcut ->
            GestureRow(input = shortcut.input, effect = shortcut.describeEffect(filingAction))
        }
}

/** One line of a hint card: what you do, and what happens. */
data class GestureRow(val input: String, val effect: String)

/**
 * Which verb the configured filing control performs.
 *
 * Replaces the free-form `"copy"` / `"move"` strings that were compared by hand
 * at four call sites. The persisted value is unchanged, so no migration is
 * needed; only the in-memory type is now closed.
 */
enum class FilingAction(
    /** The persisted DataStore value. Do not change — it is on users' devices. */
    val storedValue: String,
    /** The word on the button. Never "Keep": the button says what it does. */
    val verb: String,
    /** The full phrase for accessibility labels and menus. */
    val phrase: String,
    /** The keyboard shortcut that performs it. */
    val shortcut: String,
) {
    COPY(
        storedValue = "copy",
        verb = "Copy",
        phrase = "Copy to Selection",
        shortcut = "C",
    ),
    MOVE(
        storedValue = "move",
        verb = "Move",
        phrase = "Move to Selection",
        shortcut = "M",
    );

    /** The other verb — the one that is available but not configured as primary. */
    val other: FilingAction get() = if (this == COPY) MOVE else COPY

    companion object {
        val DEFAULT = COPY

        /**
         * Parse a persisted value, falling back rather than throwing.
         *
         * A settings string that has been on disk across an upgrade is not
         * trustworthy input, and a crash on launch is a worse outcome than a
         * default.
         */
        fun fromStored(value: String?): FilingAction =
            entries.firstOrNull { it.storedValue == value } ?: DEFAULT
    }
}

/**
 * Every gesture the fullscreen viewer binds, with the words for it.
 *
 * [destructive] exists so the product rule — *no destructive action is ever a
 * bare gesture* — is data a test can assert, rather than a sentence in a design
 * document that the next refactor will not read.
 */
enum class FullscreenGesture(
    val input: String,
    val effect: String,
    val destructive: Boolean = false,
) {
    PINCH("pinch", "zoom"),
    DOUBLE_TAP("double-tap", "fit ↔ 100%"),
    HORIZONTAL_SWIPE("swipe ← →", "previous / next"),
    SWIPE_DOWN("swipe down", "dismiss"),
    ESCAPE("Esc", "exit"),
}

/**
 * Every keyboard shortcut the selector binds, with the words for it.
 *
 * The `?` sheet renders these; [com.photoselectortoolbox.ui.selector.handleSelectorKey]
 * implements them. A test asserts the two agree.
 */
enum class SelectorShortcut(val input: String) {
    PREVIOUS("←"),
    NEXT("→"),
    FILE_PRIMARY("C / M"),
    DELETE("Del"),
    FULLSCREEN("F"),
    MAXIMISE("1 / 2 / 3"),
    ESCAPE("Esc"),
    SHORTCUTS("?");

    fun describeEffect(filingAction: FilingAction): String = when (this) {
        PREVIOUS -> "previous image"
        NEXT -> "next image"
        FILE_PRIMARY -> "${filingAction.phrase} · ${filingAction.other.phrase}"
        DELETE -> "delete, with confirmation"
        FULLSCREEN -> "open fullscreen"
        MAXIMISE -> "maximise previous / current / next"
        ESCAPE -> "leave fullscreen or maximised, close a sheet"
        SHORTCUTS -> "show this list"
    }
}
