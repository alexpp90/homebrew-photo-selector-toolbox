package com.phototok.data.repository

import androidx.documentfile.provider.DocumentFile
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Tests for the RAW/JPEG sorting rules in [ImageRepositoryImpl.determineTargetFolder].
 */
class ImageRepositoryImplTest {

    private val repository = ImageRepositoryImpl(
        localImageSource = mockk(relaxed = true),
        androidExifReader = mockk(relaxed = true),
        mediaStoreReader = mockk(relaxed = true),
        driveImageSource = mockk(relaxed = true),
        driveClient = mockk(relaxed = true),
    )

    private val rawDir = mockk<DocumentFile>()
    private val jpegDir = mockk<DocumentFile>()
    private val selectionDir = mockk<DocumentFile> {
        every { findFile("RAW") } returns rawDir
        every { findFile("JPEG") } returns jpegDir
    }

    @Test
    fun `raw files go to RAW subfolder`() {
        assertSame(rawDir, repository.determineTargetFolder("IMG_001.ARW", selectionDir))
        assertSame(rawDir, repository.determineTargetFolder("img_002.dng", selectionDir))
    }

    @Test
    fun `jpeg files go to JPEG subfolder`() {
        assertSame(jpegDir, repository.determineTargetFolder("IMG_001.JPG", selectionDir))
        assertSame(jpegDir, repository.determineTargetFolder("img_002.jpeg", selectionDir))
    }

    @Test
    fun `lightroom edits go to RAW subfolder`() {
        assertSame(rawDir, repository.determineTargetFolder("IMG_001-Edit.tif", selectionDir))
    }

    @Test
    fun `xmp sidecar follows raw parent`() {
        assertSame(rawDir, repository.determineTargetFolder("IMG_001.arw.xmp", selectionDir))
    }

    @Test
    fun `xmp sidecar without raw parent stays in selection folder`() {
        assertSame(selectionDir, repository.determineTargetFolder("IMG_001.xmp", selectionDir))
    }

    @Test
    fun `unknown extensions stay in selection folder`() {
        assertSame(selectionDir, repository.determineTargetFolder("video.mp4", selectionDir))
        assertSame(selectionDir, repository.determineTargetFolder("notes.txt", selectionDir))
    }

    @Test
    fun `falls back to selection folder when subfolder cannot be created`() {
        val brokenDir = mockk<DocumentFile> {
            every { findFile("RAW") } returns null
            every { createDirectory("RAW") } returns null
        }
        assertSame(brokenDir, repository.determineTargetFolder("IMG_001.ARW", brokenDir))
    }
}
