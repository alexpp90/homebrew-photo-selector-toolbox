package com.photoselectortoolbox.domain.duplicates

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import io.mockk.every
import io.mockk.mockk
import java.io.ByteArrayInputStream
import java.io.IOException
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DuplicateDetectorTest {

    private lateinit var context: Context
    private lateinit var contentResolver: ContentResolver
    private lateinit var detector: DuplicateDetector

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        contentResolver = mockk(relaxed = true)
        every { context.contentResolver } returns contentResolver
        detector = DuplicateDetector()
    }

    @Test
    fun `findDuplicates returns empty list when given empty input`() = runTest {
        val result = detector.findDuplicates(emptyList(), context)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `findDuplicates returns empty when all files have unique sizes`() = runTest {
        val uris = listOf(
            Uri.parse("content://media/1") to 100L,
            Uri.parse("content://media/2") to 200L,
            Uri.parse("content://media/3") to 300L
        )

        val result = detector.findDuplicates(uris, context)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `findDuplicates returns empty when same-size files have different content`() = runTest {
        val uri1 = Uri.parse("content://media/1")
        val uri2 = Uri.parse("content://media/2")
        val uris = listOf(uri1 to 500L, uri2 to 500L)

        every { contentResolver.openInputStream(uri1) } answers {
            ByteArrayInputStream("Content A".toByteArray())
        }
        every { contentResolver.openInputStream(uri2) } answers {
            ByteArrayInputStream("Content B".toByteArray())
        }

        val result = detector.findDuplicates(uris, context)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `findDuplicates returns group when same-size files have identical content`() = runTest {
        val uri1 = Uri.parse("content://media/1")
        val uri2 = Uri.parse("content://media/2")
        val uris = listOf(uri1 to 500L, uri2 to 500L)

        val content = "Identical Image Content".toByteArray()
        every { contentResolver.openInputStream(uri1) } answers { ByteArrayInputStream(content) }
        every { contentResolver.openInputStream(uri2) } answers { ByteArrayInputStream(content) }

        val result = detector.findDuplicates(uris, context)

        assertEquals(1, result.size)
        val group = result[0]
        assertEquals(2, group.files.size)
        assertTrue(group.files.contains(uri1.toString()))
        assertTrue(group.files.contains(uri2.toString()))
        assertTrue(group.hash.isNotEmpty())
    }

    @Test
    fun `findDuplicates handles null input stream gracefully`() = runTest {
        val uri1 = Uri.parse("content://media/1")
        val uri2 = Uri.parse("content://media/2")
        val uris = listOf(uri1 to 500L, uri2 to 500L)

        every { contentResolver.openInputStream(uri1) } returns null
        every { contentResolver.openInputStream(uri2) } answers {
            ByteArrayInputStream("Some content".toByteArray())
        }

        val result = detector.findDuplicates(uris, context)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `findDuplicates handles openInputStream exception gracefully`() = runTest {
        val uri1 = Uri.parse("content://media/1")
        val uri2 = Uri.parse("content://media/2")
        val uris = listOf(uri1 to 500L, uri2 to 500L)

        every { contentResolver.openInputStream(uri1) } throws IOException("Permission denied")
        every { contentResolver.openInputStream(uri2) } answers {
            ByteArrayInputStream("Some content".toByteArray())
        }

        val result = detector.findDuplicates(uris, context)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `findDuplicatesWithProgress emits progress updates and final duplicate groups`() = runTest {
        val uri1 = Uri.parse("content://media/1")
        val uri2 = Uri.parse("content://media/2")
        val uri3 = Uri.parse("content://media/3")
        val uris = listOf(
            uri1 to 1000L,
            uri2 to 1000L,
            uri3 to 2000L // Unique size, excluded from hashing
        )

        val content = "Same Content".toByteArray()
        every { contentResolver.openInputStream(uri1) } answers { ByteArrayInputStream(content) }
        every { contentResolver.openInputStream(uri2) } answers { ByteArrayInputStream(content) }

        val progressList = detector.findDuplicatesWithProgress(uris, context).toList()

        // Emissions:
        // 1. Initial emission: processed=0, total=2, groups=[]
        // 2. Emission after processing group: processed=2, total=2, groups=[DuplicateGroup]
        // 3. Final emission: processed=2, total=2, groups=[DuplicateGroup]
        assertTrue(progressList.size >= 2)

        val initial = progressList.first()
        assertEquals(0, initial.processed)
        assertEquals(2, initial.total)
        assertTrue(initial.groups.isEmpty())

        val finalState = progressList.last()
        assertEquals(2, finalState.processed)
        assertEquals(2, finalState.total)
        assertEquals(1, finalState.groups.size)
        assertEquals(2, finalState.groups[0].files.size)
    }
}
