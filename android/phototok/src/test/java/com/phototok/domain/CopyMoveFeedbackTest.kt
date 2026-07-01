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
    }
}
