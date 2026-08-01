package com.phototok.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The swipe indicators are the only thing telling a gesture-first user what is
 * about to happen, so the regression these tests exist for is a label that
 * describes a *default* rather than the user's configured action — the "KEEP"
 * label that appeared on a swipe right even when the photo was being moved.
 */
class SwipeLabelsTest {

    @Test
    fun `swipe right is labelled with the configured collection action`() {
        assertEquals("COPY", SwipeLabels.rightLabel(CollectionAction.COPY))
        assertEquals("MOVE", SwipeLabels.rightLabel(CollectionAction.MOVE))
    }

    @Test
    fun `swipe right is never labelled KEEP`() {
        CollectionAction.entries.forEach { action ->
            assertFalse(
                "swipe right must name the action, not imply keeping",
                SwipeLabels.rightLabel(action).equals("KEEP", ignoreCase = true),
            )
        }
    }

    @Test
    fun `swipe left is labelled with the configured left-swipe action`() {
        assertEquals("DELETE", SwipeLabels.leftLabel(SwipeAction.DELETE))
        assertEquals("COPY", SwipeLabels.leftLabel(SwipeAction.COPY))
        assertEquals("MOVE", SwipeLabels.leftLabel(SwipeAction.MOVE))
    }

    @Test
    fun `every action has a distinct label so the two swipes are never ambiguous`() {
        val labels = SwipeAction.entries.map { SwipeLabels.leftLabel(it) }
        assertEquals(labels.size, labels.toSet().size)
    }

    @Test
    fun `right description names the target folder`() {
        assertEquals(
            "Copy to Keepers",
            SwipeLabels.rightDescription(CollectionAction.COPY, "Keepers"),
        )
        assertEquals(
            "Move to Keepers",
            SwipeLabels.rightDescription(CollectionAction.MOVE, "Keepers"),
        )
    }

    @Test
    fun `right description falls back to a generic target when no folder is set`() {
        assertEquals(
            "Copy to ${SwipeLabels.DEFAULT_COLLECTION}",
            SwipeLabels.rightDescription(CollectionAction.COPY, ""),
        )
        assertEquals(
            "Copy to ${SwipeLabels.DEFAULT_COLLECTION}",
            SwipeLabels.rightDescription(CollectionAction.COPY, "   "),
        )
    }

    @Test
    fun `left description names the folder for copy and move but not for delete`() {
        assertEquals(
            "Copy to Rejects",
            SwipeLabels.leftDescription(SwipeAction.COPY, "Rejects"),
        )
        assertEquals(
            "Move to Rejects",
            SwipeLabels.leftDescription(SwipeAction.MOVE, "Rejects"),
        )
        // A delete has no destination folder to name; it points at Revert instead.
        assertTrue(SwipeLabels.leftDescription(SwipeAction.DELETE, "Rejects").contains("Revert"))
    }

    @Test
    fun `left description falls back to a generic folder when none is set`() {
        assertEquals(
            "Move to ${SwipeLabels.DEFAULT_LEFT_SWIPE}",
            SwipeLabels.leftDescription(SwipeAction.MOVE, ""),
        )
    }

    @Test
    fun `only delete is destructive`() {
        assertTrue(SwipeLabels.leftIsDestructive(SwipeAction.DELETE))
        assertFalse(SwipeLabels.leftIsDestructive(SwipeAction.COPY))
        assertFalse(SwipeLabels.leftIsDestructive(SwipeAction.MOVE))
    }

    @Test
    fun `verbs are sentence case for use in captions`() {
        assertEquals("Copy", SwipeLabels.rightVerb(CollectionAction.COPY))
        assertEquals("Move", SwipeLabels.rightVerb(CollectionAction.MOVE))
        assertEquals("Delete", SwipeLabels.leftVerb(SwipeAction.DELETE))
    }
}
