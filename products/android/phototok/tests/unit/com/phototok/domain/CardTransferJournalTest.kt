package com.phototok.domain

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CardTransferJournalTest {

    @Test
    fun `records in-flight transfers, pops on interrupt, and tracks copied card URIs across reload`() {
        val tempDir = Files.createTempDirectory("card_journal_test").toFile()
        try {
            val journalFile = java.io.File(tempDir, "card_transfer_journal.json")
            val journal = CardTransferJournal(journalFile = journalFile)

            journal.recordTransferStart(
                CardTransferJournal.InFlightTransfer(
                    sourceUri = "content://card/1",
                    destFolderUri = "content://phone/selection",
                    targetFileName = "IMG_001.JPG",
                    volumeUuid = "1A2B-3C4D",
                )
            )
            journal.updateInFlightDestinationUri("content://card/1", "content://phone/selection/IMG_001.JPG")
            assertEquals(1, journal.inFlightTransfers().size)

            // Complete transfer 1
            journal.recordTransferComplete("content://card/1", cardSourceKey = "content://card/folder")
            assertTrue(journal.inFlightTransfers().isEmpty())
            assertEquals(1, journal.copiedCountForFolder("content://card/folder"))

            // Start transfer 2 and leave in-flight, then reload from disk
            journal.recordTransferStart(
                CardTransferJournal.InFlightTransfer(
                    sourceUri = "content://card/2",
                    destFolderUri = "content://phone/selection",
                    targetFileName = "IMG_002.JPG",
                    volumeUuid = "1A2B-3C4D",
                    destFileUri = "content://phone/selection/IMG_002.JPG",
                )
            )

            val reloaded = CardTransferJournal(journalFile = journalFile)
            assertEquals(setOf("content://card/1"), reloaded.copiedCardUris("content://card/folder"))
            val interrupted = reloaded.popInterruptedTransfers("1A2B-3C4D")
            assertEquals(1, interrupted.size)
            assertEquals("IMG_002.JPG", interrupted.first().targetFileName)
            assertTrue(reloaded.inFlightTransfers().isEmpty())
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
