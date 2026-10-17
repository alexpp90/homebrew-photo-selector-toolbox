package com.phototok.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CopyMoveFeedbackTest {

    @Test
    fun `all succeeded without related files`() {
        assertEquals(
            "Copied to collection",
            CopyMoveFeedback.message(isCopy = true, destinationNoun = "collection", succeeded = 1, failed = 0, relatedCount = 0),
        )
        assertEquals(
            "Moved to folder",
            CopyMoveFeedback.message(isCopy = false, destinationNoun = "folder", succeeded = 1, failed = 0, relatedCount = 0),
        )
    }

    @Test
    fun `all succeeded with related files reports sibling count`() {
        assertEquals(
            "Moved to collection (+2 related)",
            CopyMoveFeedback.message(isCopy = false, destinationNoun = "collection", succeeded = 3, failed = 0, relatedCount = 2),
        )
    }

    @Test
    fun `total failure`() {
        assertEquals(
            "Failed to copy to collection",
            CopyMoveFeedback.message(isCopy = true, destinationNoun = "collection", succeeded = 0, failed = 1, relatedCount = 0),
        )
        assertEquals(
            "Failed to move to folder",
            CopyMoveFeedback.message(isCopy = false, destinationNoun = "folder", succeeded = 0, failed = 2, relatedCount = 1),
        )
    }

    @Test
    fun `partial failure reports both counts`() {
        assertEquals(
            "Moved 2 of 3 to collection, 1 failed",
            CopyMoveFeedback.message(isCopy = false, destinationNoun = "collection", succeeded = 2, failed = 1, relatedCount = 2),
        )
        assertEquals(
            "Copied 1 of 2 to folder, 1 failed",
            CopyMoveFeedback.message(isCopy = true, destinationNoun = "folder", succeeded = 1, failed = 1, relatedCount = 1),
        )
    }

    @Test
    fun `isError only when something failed`() {
        assertFalse(CopyMoveFeedback.isError(0))
        assertTrue(CopyMoveFeedback.isError(1))
        assertTrue(CopyMoveFeedback.isError(failed = 0, partial = 1))
    }

    // REQ-TOK-ARCH.16
    @Test
    fun `partial source-delete failure reports copied with original not removed`() {
        assertEquals(
            "Copied to collection — original could not be removed",
            CopyMoveFeedback.message(
                isCopy = false,
                destinationNoun = "collection",
                succeeded = 0,
                failed = 0,
                relatedCount = 0,
                partial = 1,
            ),
        )
    }

    @Test
    fun `a collision-suffixed move names the file it became`() {
        assertEquals(
            "Moved to Selection as IMG_0001-2.jpg (+1 related)",
            CopyMoveFeedback.message(
                isCopy = false,
                destinationNoun = "Selection",
                succeeded = 2,
                failed = 0,
                relatedCount = 1,
                renamedTo = "IMG_0001-2.jpg",
            ),
        )
    }

    @Test
    fun `a collision-suffixed copy without companions has no related count`() {
        assertEquals(
            "Copied to Selection as IMG_0001-3.jpg",
            CopyMoveFeedback.message(
                isCopy = true,
                destinationNoun = "Selection",
                succeeded = 1,
                failed = 0,
                relatedCount = 0,
                renamedTo = "IMG_0001-3.jpg",
            ),
        )
    }

    @Test
    fun `the rename is only reported when everything succeeded`() {
        assertEquals(
            "Moved 1 of 2 to Selection, 1 failed",
            CopyMoveFeedback.message(
                isCopy = false,
                destinationNoun = "Selection",
                succeeded = 1,
                failed = 1,
                relatedCount = 1,
                renamedTo = "IMG_0001-2.jpg",
            ),
        )
    }

    @Test
    fun `display name of a document uri is the last path segment of the decoded document id`() {
        assertEquals("a-2.jpg", CopyMoveFeedback.displayNameOf("content://x/tree/root/document/root%2FSelection%2Fa-2.jpg"))
        assertEquals("IMG_1.JPG", CopyMoveFeedback.displayNameOf("content://x/tree/primary%3ADCIM/document/primary%3ADCIM%2FIMG_1.JPG"))
    }
}
