package com.phototok.data.repository

import android.net.Uri
import android.util.Log
import com.phototok.data.source.ImageSourceResolver
import com.phototok.data.source.LocalImageSource
import com.phototok.data.source.SelectionListing
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Routing tests for [ImageRepositoryImpl]: the repository must dispatch each
 * operation through the resolver to the source that owns the URI (currently
 * the single SAF-backed local source, which also covers cloud document
 * providers such as Google Drive).
 */
class ImageRepositoryImplTest {

    private val localUri = mockk<Uri>(relaxed = true)
    private val unknownUri = mockk<Uri>(relaxed = true)

    private val local = mockk<LocalImageSource>(relaxed = true) {
        every { owns(localUri) } returns true
        every { owns(unknownUri) } returns false
    }

    private val repository = ImageRepositoryImpl(ImageSourceResolver(local))

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

        repository.discoverImages(localUri)

        coVerify(exactly = 1) { local.discoverImages(localUri) }
    }

    @Test
    fun `unowned URIs fall back to the local source`() {
        every { local.discoverImages(unknownUri) } returns flowOf(emptyList())

        repository.discoverImages(unknownUri)

        coVerify(exactly = 1) { local.discoverImages(unknownUri) }
    }

    @Test
    fun `deleteImage routes to the owning source`() = runTest {
        coEvery { local.deleteImage(localUri) } returns true

        assertTrue(repository.deleteImage(localUri))

        coVerify(exactly = 1) { local.deleteImage(localUri) }
    }

    @Test
    fun `copy within one source delegates to that source`() = runTest {
        coEvery { local.copyImage(localUri, localUri, true, "Sub") } returns true

        assertTrue(repository.copyImage(localUri, localUri, sorting = true, subfolderName = "Sub"))
    }

    @Test
    fun `listSelectionImages routes to the owning source`() = runTest {
        coEvery { local.listSelectionImages(localUri, any()) } returns SelectionListing.Missing

        assertEquals(SelectionListing.Missing, repository.listSelectionImages(localUri))
    }
}
