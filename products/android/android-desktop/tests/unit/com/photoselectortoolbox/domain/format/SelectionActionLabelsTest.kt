package com.photoselectortoolbox.domain.format

import com.photoselectortoolbox.domain.interaction.FilingAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The button says what it does.
 *
 * An earlier draft of the selector called the copy control "Keep" — a word that
 * describes how the photographer feels about the frame rather than what happens
 * to the file, and which is actively false under a "move" configuration, where
 * the file leaves the source folder. PhotoTok shipped the same mistake with a
 * "KEEP" swipe indicator (see the 2026-07-31 entry in `ai/memory/palette.md`),
 * so this is the second time; hence a test rather than a code review comment.
 */
class SelectionActionLabelsTest {

    @Test
    fun `the word is never a euphemism`() {
        val banned = listOf("keep", "save", "like", "favourite", "favorite", "star")

        FilingAction.entries.forEach { configured ->
            SelectionActionLabels.both(configured).forEach { label ->
                banned.forEach { word ->
                    assertFalse(
                        "filing control is labelled '${label.verb}'",
                        label.verb.lowercase().contains(word),
                    )
                    assertFalse(
                        "filing control is described as '${label.phrase}'",
                        label.phrase.lowercase().contains(word),
                    )
                }
            }
        }
    }

    @Test
    fun `the verb is copy or move and nothing else`() {
        FilingAction.entries.forEach { configured ->
            val verbs = SelectionActionLabels.both(configured).map { it.verb }.toSet()
            assertEquals(setOf("Copy", "Move"), verbs)
        }
    }

    @Test
    fun `the configured action is the primary one and comes first`() {
        FilingAction.entries.forEach { configured ->
            val both = SelectionActionLabels.both(configured)

            assertEquals(configured, both.first().action)
            assertTrue(both.first().isPrimary)
            assertFalse(both.last().isPrimary)
        }
    }

    @Test
    fun `both verbs are always offered whichever is configured`() {
        // The setting decides emphasis, not availability. A photographer who
        // normally moves must still be able to copy this one frame without
        // opening Settings.
        FilingAction.entries.forEach { configured ->
            val actions = SelectionActionLabels.both(configured).map { it.action }.toSet()
            assertEquals(FilingAction.entries.toSet(), actions)
        }
    }

    @Test
    fun `each control carries its own shortcut`() {
        val labels = SelectionActionLabels.both(FilingAction.COPY)

        assertEquals("C", labels.single { it.action == FilingAction.COPY }.shortcut)
        assertEquals("M", labels.single { it.action == FilingAction.MOVE }.shortcut)
    }

    @Test
    fun `accessibility label states the action and its shortcut`() {
        val label = SelectionActionLabels.primary(FilingAction.MOVE)

        assertEquals("Move to Selection, shortcut M", label.accessibilityLabel)
    }

    @Test
    fun `confirmation wording matches the verb that ran`() {
        assertEquals("Copied to Selection", SelectionActionLabels.confirmation(FilingAction.COPY))
        assertEquals("Moved to Selection", SelectionActionLabels.confirmation(FilingAction.MOVE))
    }

    @Test
    fun `move advances and copy does not`() {
        // The asymmetry is the culling loop: a moved frame is finished with, a
        // copied one may still be worth comparing against its neighbours.
        assertTrue(SelectionActionLabels.advancesAfter(FilingAction.MOVE))
        assertFalse(SelectionActionLabels.advancesAfter(FilingAction.COPY))
    }
}
