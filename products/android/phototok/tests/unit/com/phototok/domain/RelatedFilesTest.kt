package com.phototok.domain

import com.photoselector.core.Requirement
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
    @Requirement("REQ-TOK-ARCH.07")
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

    @Test
    fun `does not match files with same stem but identical extension`() {
        val jpg1 = img("photo.jpg")
        val jpg2 = ImageItem(
            uri = "content://photos/subfolder/photo.jpg",
            fileName = "photo.jpg",
            fileSize = 2L,
            lastModified = 2L,
            mimeType = null,
        )
        val all = listOf(jpg1, jpg2)

        assertTrue(RelatedFiles.siblings(all, jpg1).isEmpty())
    }

    @Test
    fun `does not match files from different parent directories`() {
        val jpg = ImageItem(
            uri = "content://photos/day1/shot.jpg",
            fileName = "shot.jpg",
            fileSize = 1L,
            lastModified = 1L,
            mimeType = null,
            parentUri = "content://tree/day1",
        )
        val rawDifferentFolder = ImageItem(
            uri = "content://photos/day2/shot.raw",
            fileName = "shot.raw",
            fileSize = 2L,
            lastModified = 2L,
            mimeType = null,
            parentUri = "content://tree/day2",
        )
        val rawSameFolder = ImageItem(
            uri = "content://photos/day1/shot.raw",
            fileName = "shot.raw",
            fileSize = 3L,
            lastModified = 3L,
            mimeType = null,
            parentUri = "content://tree/day1",
        )
        val all = listOf(jpg, rawDifferentFolder, rawSameFolder)

        val siblings = RelatedFiles.siblings(all, jpg).map { it.uri }
        assertEquals(listOf(rawSameFolder.uri), siblings)
    }

    @Test
    fun `hasRawJpegPairs detects matching RAW and JPEG files`() {
        val mixed = listOf(img("shot_01.jpg"), img("shot_01.arw"), img("shot_02.jpg"))
        assertTrue(RelatedFiles.hasRawJpegPairs(mixed))

        val jpegsOnly = listOf(img("shot_01.jpg"), img("shot_02.jpg"))
        org.junit.Assert.assertFalse(RelatedFiles.hasRawJpegPairs(jpegsOnly))

        val rawsOnly = listOf(img("shot_01.arw"), img("shot_02.cr2"))
        org.junit.Assert.assertFalse(RelatedFiles.hasRawJpegPairs(rawsOnly))

        val differentStems = listOf(img("shot_01.jpg"), img("shot_02.arw"))
        org.junit.Assert.assertFalse(RelatedFiles.hasRawJpegPairs(differentStems))
    }

    @Test
    fun `hasFilterMismatch is false for FileTypeFilter ALL or empty list`() {
        val images = listOf(img("shot_01.jpg"), img("shot_02.jpg"))
        org.junit.Assert.assertFalse(RelatedFiles.hasFilterMismatch(images, FileTypeFilter.ALL))
        org.junit.Assert.assertFalse(RelatedFiles.hasFilterMismatch(emptyList(), FileTypeFilter.RAW))
    }

    @Test
    fun `hasFilterMismatch is false when folder contains paired RAW and JPEG of same photos`() {
        // Shooting RAW+JPEG means every photo has a RAW counterpart, so filtering to RAW only
        // shows all photos without hiding unique shots — no hint required.
        val paired = listOf(
            img("shot_01.jpg"), img("shot_01.arw"),
            img("shot_02.jpg"), img("shot_02.arw"),
            img("shot_03.jpg"), img("shot_03.arw"),
        )
        org.junit.Assert.assertFalse(RelatedFiles.hasFilterMismatch(paired, FileTypeFilter.RAW))
        org.junit.Assert.assertFalse(RelatedFiles.hasFilterMismatch(paired, FileTypeFilter.JPG))
    }

    @Test
    fun `hasFilterMismatch is true when majority of unique photos do not match filter`() {
        // 1 photo is RAW (and paired with JPEG), 4 photos are JPEG only.
        // Total unique photos = 5. Matching RAW = 1. Unmatched = 4. 4 > 1 -> true.
        val majorityJpeg = listOf(
            img("shot_01.arw"), img("shot_01.jpg"),
            img("shot_02.jpg"),
            img("shot_03.jpg"),
            img("shot_04.jpg"),
            img("shot_05.jpg"),
        )
        assertTrue(RelatedFiles.hasFilterMismatch(majorityJpeg, FileTypeFilter.RAW))
        org.junit.Assert.assertFalse(RelatedFiles.hasFilterMismatch(majorityJpeg, FileTypeFilter.JPG))
    }

    @Test
    fun `hasFilterMismatch is true when zero photos match filter`() {
        val allJpeg = listOf(img("shot_01.jpg"), img("shot_02.jpg"), img("shot_03.jpg"))
        assertTrue(RelatedFiles.hasFilterMismatch(allJpeg, FileTypeFilter.RAW))

        val allRaw = listOf(img("shot_01.arw"), img("shot_02.cr2"))
        assertTrue(RelatedFiles.hasFilterMismatch(allRaw, FileTypeFilter.JPG))
    }

    @Test
    fun `hasFilterMismatch is false when matching photos outnumber unmatched photos`() {
        // 3 RAW photos, 1 JPEG photo (unpaired).
        // Total = 4. Matching RAW = 3. Unmatched = 1. 1 is not > 3 -> false.
        val mostlyRaw = listOf(
            img("shot_01.arw"),
            img("shot_02.arw"),
            img("shot_03.arw"),
            img("shot_04.jpg"),
        )
        org.junit.Assert.assertFalse(RelatedFiles.hasFilterMismatch(mostlyRaw, FileTypeFilter.RAW))
        assertTrue(RelatedFiles.hasFilterMismatch(mostlyRaw, FileTypeFilter.JPG))
    }
}
