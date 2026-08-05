package com.phototok.domain

import com.phototok.data.model.ImageItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RelatedFilesTest {

    private fun img(name: String) = ImageItem(
        uri = "content://photos/$name",
        fileName = name,
        fileSize = 1L,
        lastModified = 1L,
        mimeType = null,
    )

    @Test
    fun `matches same stem different extension and excludes self`() {
        val jpg = img("IMG_001.JPG")
        val all = listOf(jpg, img("IMG_001.ARW"), img("IMG_002.JPG"))

        val siblings = RelatedFiles.siblings(all, jpg).map { it.fileName }

        assertEquals(listOf("IMG_001.ARW"), siblings)
    }

    @Test
    fun `stem match is case insensitive`() {
        val raw = img("img_001.arw")
        val all = listOf(raw, img("IMG_001.JPG"))

        val siblings = RelatedFiles.siblings(all, raw).map { it.fileName }

        assertEquals(listOf("IMG_001.JPG"), siblings)
    }

    @Test
    fun `no siblings when names differ`() {
        val a = img("A.JPG")
        val all = listOf(a, img("B.ARW"), img("C.JPG"))

        assertTrue(RelatedFiles.siblings(all, a).isEmpty())
    }

    @Test
    fun `multiple siblings of same stem are all returned`() {
        val jpg = img("shot.jpg")
        val all = listOf(jpg, img("shot.arw"), img("shot.dng"), img("other.jpg"))

        val siblings = RelatedFiles.siblings(all, jpg).map { it.fileName }.toSet()

        assertEquals(setOf("shot.arw", "shot.dng"), siblings)
    }
}
