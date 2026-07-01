package com.phototok.data.source

import androidx.documentfile.provider.DocumentFile
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Tests for the RAW/JPEG sorting rules in [LocalImageSourceImpl.determineTargetFolder]
 * (moved here from the repository together with the copy/move implementation).
 */
class LocalImageSourceImplTest {

    private val source = LocalImageSourceImpl(
        context = mockk(relaxed = true),
        androidExifReader = mockk(relaxed = true),
        mediaStoreReader = mockk(relaxed = true),
    )

    private val rawDir = mockk<DocumentFile>()
    private val jpegDir = mockk<DocumentFile>()
    private val selectionDir = mockk<DocumentFile> {
        every { findFile("RAW") } returns rawDir
        every { findFile("JPEG") } returns jpegDir
    }

    @Test
    fun `raw files go to RAW subfolder`() {
        assertSame(rawDir, source.determineTargetFolder("IMG_001.ARW", selectionDir))
        assertSame(rawDir, source.determineTargetFolder("img_002.dng", selectionDir))
    }

    @Test
    fun `jpeg files go to JPEG subfolder`() {
        assertSame(jpegDir, source.determineTargetFolder("IMG_001.JPG", selectionDir))
        assertSame(jpegDir, source.determineTargetFolder("img_002.jpeg", selectionDir))
    }

    @Test
    fun `lightroom edits go to RAW subfolder`() {
        assertSame(rawDir, source.determineTargetFolder("IMG_001-Edit.tif", selectionDir))
    }

    @Test
    fun `xmp sidecar follows raw parent`() {
        assertSame(rawDir, source.determineTargetFolder("IMG_001.arw.xmp", selectionDir))
    }

    @Test
    fun `xmp sidecar without raw parent stays in selection folder`() {
        assertSame(selectionDir, source.determineTargetFolder("IMG_001.xmp", selectionDir))
    }

    @Test
    fun `unknown extensions stay in selection folder`() {
        assertSame(selectionDir, source.determineTargetFolder("video.mp4", selectionDir))
        assertSame(selectionDir, source.determineTargetFolder("notes.txt", selectionDir))
    }

    @Test
    fun `falls back to selection folder when subfolder cannot be created`() {
        val brokenDir = mockk<DocumentFile> {
            every { findFile("RAW") } returns null
            every { createDirectory("RAW") } returns null
        }
        assertSame(brokenDir, source.determineTargetFolder("IMG_001.ARW", brokenDir))
    }
}
