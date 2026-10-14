package com.photoselector.core

import android.content.ContentResolver
import android.content.Context
import android.database.MatrixCursor
import android.net.Uri
import android.provider.MediaStore
import com.photoselector.core.reader.MediaStoreReader
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MediaStoreReaderTest {

    private val reader = MediaStoreReader()

    @Test
    fun `supportedMimeTypes includes standard raster formats`() {
        val types = reader.supportedMimeTypes()
        assertTrue(types.contains("image/jpeg"))
        assertTrue(types.contains("image/png"))
        assertTrue(types.contains("image/webp"))
        assertTrue(types.contains("image/heic"))
        assertTrue(types.contains("image/heif"))
        assertFalse(types.contains("image/x-canon-cr3"))
    }

    @Test
    fun `readExif returns fallback ExifData when contentResolver query returns populated cursor`() = runTest {
        val context = mockk<Context>()
        val contentResolver = mockk<ContentResolver>()
        val uri = Uri.parse("content://media/external/images/media/42")

        val cursor = MatrixCursor(
            arrayOf(
                MediaStore.Images.Media.DATA,
                MediaStore.Images.Media.SIZE,
                MediaStore.Images.Media.DATE_MODIFIED
            )
        ).apply {
            addRow(arrayOf("/storage/emulated/0/DCIM/IMG_0001.JPG", 4096L, 1700000000L))
        }

        every { context.contentResolver } returns contentResolver
        every { contentResolver.query(uri, any(), null, null, null) } returns cursor

        val result = reader.readExif(context, uri)
        assertNotNull(result)
        assertEquals(true, result?.isFallback)
        assertEquals("Unknown", result?.lens)
        assertNull(result?.shutterSpeed)
        assertNull(result?.aperture)
        assertNull(result?.iso)
    }

    @Test
    fun `readExif returns null when contentResolver query returns empty cursor or null`() = runTest {
        val context = mockk<Context>()
        val contentResolver = mockk<ContentResolver>()
        val uriEmpty = Uri.parse("content://media/external/images/media/99")
        val uriNull = Uri.parse("content://media/external/images/media/100")

        val emptyCursor = MatrixCursor(
            arrayOf(
                MediaStore.Images.Media.DATA,
                MediaStore.Images.Media.SIZE,
                MediaStore.Images.Media.DATE_MODIFIED
            )
        )

        every { context.contentResolver } returns contentResolver
        every { contentResolver.query(uriEmpty, any(), null, null, null) } returns emptyCursor
        every { contentResolver.query(uriNull, any(), null, null, null) } returns null

        assertNull(reader.readExif(context, uriEmpty))
        assertNull(reader.readExif(context, uriNull))
    }
}
