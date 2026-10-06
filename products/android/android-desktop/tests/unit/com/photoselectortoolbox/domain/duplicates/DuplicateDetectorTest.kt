package com.photoselectortoolbox.domain.duplicates

import android.content.Context
import android.net.Uri
import io.mockk.every
import io.mockk.mockk
import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DuplicateDetectorTest {

    private val detector = DuplicateDetector()

    @Test
    fun `findDuplicates identifies duplicate content`() = runTest {
        val context = mockk<Context>()
        val uri1 = mockk<Uri>()
        val uri2 = mockk<Uri>()
        val uri3 = mockk<Uri>()

        every { uri1.toString() } returns "content://media/1"
        every { uri2.toString() } returns "content://media/2"
        every { uri3.toString() } returns "content://media/3"

        every { context.contentResolver.openInputStream(uri1) } answers {
            ByteArrayInputStream("hello world".toByteArray())
        }
        every { context.contentResolver.openInputStream(uri2) } answers {
            ByteArrayInputStream("hello world".toByteArray())
        }
        every { context.contentResolver.openInputStream(uri3) } answers {
            ByteArrayInputStream("different content".toByteArray())
        }

        val uris = listOf(
            uri1 to 11L,
            uri2 to 11L,
            uri3 to 17L
        )

        val duplicates = detector.findDuplicates(uris, context)

        assertEquals(1, duplicates.size)
        assertEquals(2, duplicates[0].files.size)
        assertTrue(duplicates[0].files.contains("content://media/1"))
        assertTrue(duplicates[0].files.contains("content://media/2"))
    }

    @Test
    fun `findDuplicates cancels promptly when coroutine cancelled`() = runTest {
        val context = mockk<Context>()
        val uri1 = mockk<Uri>()
        val uri2 = mockk<Uri>()

        every { uri1.toString() } returns "content://media/1"
        every { uri2.toString() } returns "content://media/2"

        // Infinite InputStream to simulate hashing a very large stream
        val infiniteInputStream = object : InputStream() {
            override fun read(): Int = 65
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                val readLen = Math.min(len, 1024)
                for (i in 0 until readLen) {
                    b[off + i] = 65
                }
                return readLen
            }
        }

        every { context.contentResolver.openInputStream(uri1) } returns infiniteInputStream
        every { context.contentResolver.openInputStream(uri2) } returns infiniteInputStream

        val job = launch(kotlinx.coroutines.Dispatchers.IO) {
            detector.findDuplicates(
                listOf(uri1 to 1000000L, uri2 to 1000000L),
                context
            )
        }

        // Wait until job is actively running on IO thread
        kotlinx.coroutines.delay(50)

        job.cancelAndJoin()

        assertTrue("Job should be cancelled promptly", job.isCancelled)
    }
}
