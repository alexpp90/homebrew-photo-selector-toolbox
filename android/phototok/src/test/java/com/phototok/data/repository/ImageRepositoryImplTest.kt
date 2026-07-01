package com.phototok.data.repository

import android.net.Uri
import android.util.Log
import com.phototok.data.source.ImageSourceResolver
import com.phototok.data.source.LocalImageSource
import com.phototok.data.source.SelectionListing
import com.phototok.data.source.googledrive.GoogleDriveImageSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Routing tests for [ImageRepositoryImpl]: the repository must dispatch each
 * operation to the source that owns the URI and refuse cross-source transfers.
 */
class ImageRepositoryImplTest {

    private val localUri = mockk<Uri>(relaxed = true)
    private val driveUri = mockk<Uri>(relaxed = true)

    private val local = mockk<LocalImageSource>(relaxed = true) {
        every { owns(localUri) } returns true
        every { owns(driveUri) } returns false
    }
    private val drive = mockk<GoogleDriveImageSource>(relaxed = true) {
        every { owns(driveUri) } returns true
        every { owns(localUri) } returns false
    }

    private val repository = ImageRepositoryImpl(ImageSourceResolver(local, drive))

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.w(any(), any<String>()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun `discoverImages routes to the owning source`() {
        every { local.discoverImages(localUri) } returns flowOf(emptyList())
        every { drive.discoverImages(driveUri) } returns flowOf(emptyList())

        repository.discoverImages(localUri)
        repository.discoverImages(driveUri)

        coVerify(exactly = 1) { local.discoverImages(localUri) }
        coVerify(exactly = 1) { drive.discoverImages(driveUri) }
    }

    @Test
    fun `deleteImage routes to the owning source`() = runTest {
        coEvery { drive.deleteImage(driveUri) } returns true

        assertTrue(repository.deleteImage(driveUri))

        coVerify(exactly = 1) { drive.deleteImage(driveUri) }
        coVerify(exactly = 0) { local.deleteImage(any()) }
    }

    @Test
    fun `copy within one source delegates to that source`() = runTest {
        coEvery { local.copyImage(localUri, localUri, true, "Sub") } returns true

        assertTrue(repository.copyImage(localUri, localUri, sorting = true, subfolderName = "Sub"))
    }

    @Test
    fun `cross-source copy and move are rejected without touching the sources`() = runTest {
        assertFalse(repository.copyImage(localUri, driveUri, sorting = true, subfolderName = "Sub"))
        assertFalse(repository.moveImage(driveUri, localUri, sorting = true, subfolderName = "Sub"))

        coVerify(exactly = 0) { local.copyImage(any(), any(), any(), any()) }
        coVerify(exactly = 0) { local.moveImage(any(), any(), any(), any()) }
        coVerify(exactly = 0) { drive.copyImage(any(), any(), any(), any()) }
        coVerify(exactly = 0) { drive.moveImage(any(), any(), any(), any()) }
    }

    @Test
    fun `listSelectionImages routes to the owning source`() = runTest {
        coEvery { drive.listSelectionImages(driveUri, any()) } returns SelectionListing.NotSupported
        coEvery { local.listSelectionImages(localUri, any()) } returns SelectionListing.Missing

        assertEquals(SelectionListing.NotSupported, repository.listSelectionImages(driveUri))
        assertEquals(SelectionListing.Missing, repository.listSelectionImages(localUri))
    }
}
