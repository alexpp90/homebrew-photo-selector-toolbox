package com.phototok.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The one-time explanations must describe what actually happened — the wrong
 * verb or the wrong folder is worse than no hint at all.
 */
class FirstRunHintTextTest {

    @Test
    fun `hint keys round-trip and are unique`() {
        val keys = FirstRunHint.entries.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
        FirstRunHint.entries.forEach { hint ->
            assertEquals(hint, FirstRunHint.fromKey(hint.key))
        }
        assertNull(FirstRunHint.fromKey("not_a_hint"))
        assertNull(FirstRunHint.fromKey(null))
    }

    @Test
    fun `swipe right uses copy wording and the collection folder name`() {
        val message = FirstRunHintText.message(
            hint = FirstRunHint.SWIPE_RIGHT,
            collectionAction = CollectionAction.COPY,
            collectionFolderName = "Keepers",
        )

        assertTrue(message, message.contains("copied"))
        assertTrue(message, message.contains("Keepers"))
        assertTrue(message, message.contains("Settings"))
    }

    @Test
    fun `swipe right uses move wording when configured to move`() {
        val message = FirstRunHintText.message(
            hint = FirstRunHint.SWIPE_RIGHT,
            collectionAction = CollectionAction.MOVE,
            collectionFolderName = "Keepers",
        )

        assertTrue(message, message.contains("moved"))
        assertTrue(message, !message.contains("copied"))
    }

    @Test
    fun `swipe right falls back to generic wording without a folder name`() {
        val message = FirstRunHintText.message(
            hint = FirstRunHint.SWIPE_RIGHT,
            collectionAction = CollectionAction.COPY,
            collectionFolderName = "",
        )

        assertTrue(message, message.contains("your collection folder"))
    }

    @Test
    fun `left swipe folder hint reflects copy versus move`() {
        val copy = FirstRunHintText.message(
            hint = FirstRunHint.SWIPE_LEFT_FOLDER,
            leftSwipeAction = SwipeAction.COPY,
            leftSwipeFolderName = "Rejects",
        )
        val move = FirstRunHintText.message(
            hint = FirstRunHint.SWIPE_LEFT_FOLDER,
            leftSwipeAction = SwipeAction.MOVE,
            leftSwipeFolderName = "Rejects",
        )

        assertTrue(copy, copy.contains("copied") && copy.contains("Rejects"))
        assertTrue(move, move.contains("moved") && move.contains("Rejects"))
    }

    @Test
    fun `delete hint points at revert`() {
        val message = FirstRunHintText.message(FirstRunHint.SWIPE_LEFT_DELETE)

        assertTrue(message, message.contains("Revert"))
        assertTrue(message, message.contains("trash"))
    }

    @Test
    fun `every hint has a non-empty title and message`() {
        FirstRunHint.entries.forEach { hint ->
            assertTrue(hint.name, FirstRunHintText.title(hint).isNotBlank())
            assertTrue(hint.name, FirstRunHintText.message(hint).isNotBlank())
        }
    }
}
