package com.photoselectortoolbox.domain.format

import com.photoselectortoolbox.domain.interaction.FilingAction

/**
 * The words on the two controls that file a photograph into the Selection.
 *
 * There is one rule here and it is worth stating plainly: **the button says
 * what it does.** An earlier draft of this screen called the copy control
 * "Keep", which is a euphemism — it describes how the photographer feels about
 * the frame, not what happens to the file. Under a "move" configuration the
 * same euphemism is actively wrong, because the file leaves the source folder.
 * PhotoTok already learned this exact lesson (see the 2026-07-31 entry in
 * `ai/memory/palette.md`), and a unit test here asserts the word never comes
 * back.
 *
 * Both verbs are always on screen. The one named by the user's Filing Action
 * setting is the [primary] — first position, tonal fill — and the other is
 * [secondary]. Neither is hidden: a photographer who wants to copy this one
 * frame while normally moving must not have to open Settings to do it.
 *
 * Kept free of Compose and Android types so the wording is unit-testable.
 */
object SelectionActionLabels {

    /** The configured verb: the emphasised control. */
    fun primary(filingAction: FilingAction): ActionLabel = labelFor(filingAction, isPrimary = true)

    /** The other verb: present, labelled, visually quieter. */
    fun secondary(filingAction: FilingAction): ActionLabel =
        labelFor(filingAction.other, isPrimary = false)

    /**
     * Both controls in the order they are rendered, configured verb first.
     *
     * Callers iterate this rather than composing two buttons by hand, so the
     * ordering rule lives in one place and the control block cannot disagree
     * with the context menu.
     */
    fun both(filingAction: FilingAction): List<ActionLabel> =
        listOf(primary(filingAction), secondary(filingAction))

    /** The snackbar shown after the action succeeds. */
    fun confirmation(action: FilingAction): String = when (action) {
        FilingAction.COPY -> "Copied to Selection"
        FilingAction.MOVE -> "Moved to Selection"
    }

    /**
     * Whether the frame advances after this action.
     *
     * A moved frame is finished with; a copied one may still be worth comparing
     * against its neighbours. That asymmetry is the culling loop, so it is
     * stated here rather than rediscovered at each call site.
     */
    fun advancesAfter(action: FilingAction): Boolean = action == FilingAction.MOVE

    private fun labelFor(action: FilingAction, isPrimary: Boolean) = ActionLabel(
        action = action,
        verb = action.verb,
        phrase = action.phrase,
        shortcut = action.shortcut,
        isPrimary = isPrimary,
    )
}

/**
 * One filing control's wording.
 *
 * [verb] goes on the button, [phrase] into the accessibility description and
 * the context menu, [shortcut] onto the permanent key cap beside the verb.
 */
data class ActionLabel(
    val action: FilingAction,
    val verb: String,
    val phrase: String,
    val shortcut: String,
    val isPrimary: Boolean,
) {
    /** "Copy to Selection, shortcut C" — what a screen reader announces. */
    val accessibilityLabel: String get() = "$phrase, shortcut $shortcut"
}
