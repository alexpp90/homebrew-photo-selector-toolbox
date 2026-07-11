package com.phototok.data.source.googledrive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DrivePickerResponseTest {

    @Test
    fun `parses picked docs from picker payload`() {
        val json = """
            {"action":"picked","docs":[
              {"id":"file1","name":"IMG_001.JPG","mimeType":"image/jpeg",
               "sizeBytes":12345,"lastEditedUtc":1690000000000},
              {"id":"file2","name":"IMG_002.ARW","mimeType":"application/octet-stream",
               "sizeBytes":99,"lastEditedUtc":1690000000001}
            ]}
        """.trimIndent()

        val docs = DrivePickerResponse.parsePickedDocs(json)

        assertEquals(2, docs.size)
        assertEquals(
            PickedDriveDoc("file1", "IMG_001.JPG", "image/jpeg", 12345, 1690000000000),
            docs[0],
        )
        assertEquals("file2", docs[1].id)
    }

    @Test
    fun `doc without id is skipped and missing fields get defaults`() {
        val json = """{"action":"picked","docs":[{"name":"no-id"},{"id":"file1"}]}"""

        val docs = DrivePickerResponse.parsePickedDocs(json)

        assertEquals(1, docs.size)
        assertEquals("file1", docs[0].id)
        assertEquals("file1", docs[0].name) // falls back to the id
        assertEquals(0, docs[0].sizeBytes)
    }

    @Test
    fun `invalid or unexpected json yields empty list`() {
        assertTrue(DrivePickerResponse.parsePickedDocs("not json").isEmpty())
        assertTrue(DrivePickerResponse.parsePickedDocs("{}").isEmpty())
        assertTrue(DrivePickerResponse.parsePickedDocs("""{"docs":"nope"}""").isEmpty())
    }
}
