package com.phototok.data.source

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.phototok.domain.TransferStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for WP-03c safe SAF file transfer in [LocalImageSourceImpl]:
 * - Bundle-wide collision-free naming (`-2`, `-3`, …) across `CR3 + JPG + CR3.xmp + -Edit.tif`
 * - Byte-size verification after copy (cleaning up truncated destination files without touching source)
 * - Partial failure reporting when source deletion fails after verified copy
 * - Atomic [DocumentsContract.moveDocument] fast path when supported vs copy+delete fallback
 * - Provider silent auto-rename rejection (`IMG_0001 (1).CR3` != `IMG_0001.CR3`)
 * - 2-permit transfer semaphore and [CancellationException] rethrow hygiene
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocalImageSourceTransferTest {

    private lateinit var contentResolver: ContentResolver
    private lateinit var context: Context
    private lateinit var source: LocalImageSourceImpl

    private val sourceUri: Uri = Uri.parse("content://com.android.externalstorage.documents/tree/root/document/root%2FIMG_0001.CR3")
    private val destFolderUri: Uri = Uri.parse("content://com.android.externalstorage.documents/tree/root")
    private val targetFolderUri: Uri = Uri.parse("content://com.android.externalstorage.documents/tree/root/document/root%2FSelection%2FRAW")
    private val destFileUri: Uri = Uri.parse("content://com.android.externalstorage.documents/tree/root/document/root%2FSelection%2FRAW%2FIMG_0001.CR3")

    @Before
    fun setUp() {
        contentResolver = mockk(relaxed = true)
        context = mockk(relaxed = true) {
            every { this@mockk.contentResolver } returns this@LocalImageSourceTransferTest.contentResolver
        }
        source = LocalImageSourceImpl(
            context = context,
            androidExifReader = mockk(relaxed = true),
            mediaStoreReader = mockk(relaxed = true),
        )
    }

    @Test
    fun `collision naming for CR3 + JPG + CR3_xmp + Edit_tif bundle gives entire bundle -2 suffix`() {
        val bundle = listOf(
            "IMG_0001.CR3",
            "IMG_0001.JPG",
            "IMG_0001.CR3.xmp",
            "IMG_0001-Edit.tif",
        )
        // Suppose IMG_0001.JPG already exists in the JPEG folder.
        val existingByFolder = mapOf(
            "RAW" to emptySet(),
            "JPEG" to setOf("IMG_0001.JPG"),
        )
        val folderForFile: (String) -> String = { name ->
            if (name.endsWith(".JPG", ignoreCase = true)) "JPEG" else "RAW"
        }

        val resolved = LocalImageSourceImpl.resolveCollisionFreeBundleNames(
            fileNames = bundle,
            primaryStem = "IMG_0001",
            existingNamesByFolder = existingByFolder,
            folderForFile = folderForFile,
        )

        assertEquals("IMG_0001-2.CR3", resolved["IMG_0001.CR3"])
        assertEquals("IMG_0001-2.JPG", resolved["IMG_0001.JPG"])
        assertEquals("IMG_0001-2.CR3.xmp", resolved["IMG_0001.CR3.xmp"])
        assertEquals("IMG_0001-2-Edit.tif", resolved["IMG_0001-Edit.tif"])

        // If IMG_0001-2.CR3.xmp ALSO exists in RAW, the entire bundle advances together to -3.
        val existingWithTwo = mapOf(
            "RAW" to setOf("IMG_0001-2.CR3.xmp"),
            "JPEG" to setOf("IMG_0001.JPG"),
        )
        val resolvedThree = LocalImageSourceImpl.resolveCollisionFreeBundleNames(
            fileNames = bundle,
            primaryStem = "IMG_0001",
            existingNamesByFolder = existingWithTwo,
            folderForFile = folderForFile,
        )
        assertEquals("IMG_0001-3.CR3", resolvedThree["IMG_0001.CR3"])
        assertEquals("IMG_0001-3.JPG", resolvedThree["IMG_0001.JPG"])
        assertEquals("IMG_0001-3.CR3.xmp", resolvedThree["IMG_0001.CR3.xmp"])
        assertEquals("IMG_0001-3-Edit.tif", resolvedThree["IMG_0001-Edit.tif"])
    }

    @Test
    fun `size mismatch after copy cleans up destination file and keeps source untouched`() = runTest {
        val payload = ByteArray(1024) { 0x42 }
        val sourceDoc = mockk<DocumentFile>(relaxed = true) {
            every { name } returns "IMG_0001.CR3"
            every { type } returns "image/x-canon-cr3"
            every { length() } returns 2048L // Source claims 2048 bytes, but stream only yields 1024!
            every { delete() } returns true
        }
        val destDoc = mockk<DocumentFile>(relaxed = true) {
            every { uri } returns destFileUri
            every { name } returns "IMG_0001.CR3"
            every { length() } returns 1024L
            every { delete() } returns true
        }
        val targetFolder = mockk<DocumentFile>(relaxed = true) {
            every { uri } returns targetFolderUri
            every { listFiles() } returns emptyArray()
            every { createFile(any(), "IMG_0001.CR3") } returns destDoc
        }

        source.documentFileFromSingleUri = { sourceDoc }
        source.documentFileFromTreeUri = { targetFolder }
        source.documentFlagsQuery = { 0 } // Force stream copy+delete path
        every { contentResolver.query(any(), any(), any(), any(), any()) } returns null
        every { contentResolver.openInputStream(sourceUri) } answers { ByteArrayInputStream(payload) }
        every { contentResolver.openOutputStream(destFileUri) } answers { ByteArrayOutputStream() }

        val result = source.moveImageDetailed(
            sourceUri = sourceUri,
            destFolderUri = destFolderUri,
            sorting = false,
            subfolderName = "Selection",
        )

        assertEquals(TransferStatus.FAILURE, result.status)
        // Destination partial file was cleaned up:
        verify(exactly = 1) { destDoc.delete() }
        // Source file was never deleted:
        verify(exactly = 0) { sourceDoc.delete() }
    }

    @Test
    fun `source delete failure after verified copy keeps destination file and reports PARTIAL`() = runTest {
        val payload = ByteArray(1024) { 0x2A }
        val sourceDoc = mockk<DocumentFile>(relaxed = true) {
            every { name } returns "IMG_0001.CR3"
            every { type } returns "image/x-canon-cr3"
            every { length() } returns 1024L
            every { delete() } returns false // Source delete fails!
        }
        val destDoc = mockk<DocumentFile>(relaxed = true) {
            every { uri } returns destFileUri
            every { name } returns "IMG_0001.CR3"
            every { length() } returns 1024L
            every { delete() } returns true
        }
        val targetFolder = mockk<DocumentFile>(relaxed = true) {
            every { uri } returns targetFolderUri
            every { listFiles() } returns emptyArray()
            every { createFile(any(), "IMG_0001.CR3") } returns destDoc
        }

        source.documentFileFromSingleUri = { sourceDoc }
        source.documentFileFromTreeUri = { targetFolder }
        source.documentFlagsQuery = { 0 }
        every { contentResolver.query(any(), any(), any(), any(), any()) } returns null
        every { contentResolver.openInputStream(sourceUri) } answers { ByteArrayInputStream(payload) }
        every { contentResolver.openOutputStream(destFileUri) } answers { ByteArrayOutputStream() }

        val result = source.moveImageDetailed(
            sourceUri = sourceUri,
            destFolderUri = destFolderUri,
            sorting = false,
            subfolderName = "Selection",
        )

        assertEquals(TransferStatus.PARTIAL, result.status)
        assertEquals(destFileUri.toString(), result.destinationUri)
        // Destination file is kept (not cleaned up):
        verify(exactly = 0) { destDoc.delete() }
        // Source deletion was attempted:
        verify(exactly = 1) { sourceDoc.delete() }
    }

    @Test
    fun `DocumentsContract moveDocument fast path is chosen when same authority and FLAG_SUPPORTS_MOVE and no rename needed`() = runTest {
        val sourceDoc = mockk<DocumentFile>(relaxed = true) {
            every { name } returns "IMG_0001.CR3"
            every { type } returns "image/x-canon-cr3"
        }
        val targetFolder = mockk<DocumentFile>(relaxed = true) {
            every { uri } returns targetFolderUri
            every { listFiles() } returns emptyArray()
        }
        val sourceParentUri = Uri.parse("content://com.android.externalstorage.documents/tree/root/document/root")
        var moveInvokerCalled = false

        source.documentFileFromSingleUri = { sourceDoc }
        source.documentFileFromTreeUri = { targetFolder }
        source.documentFlagsQuery = { DocumentsContract.Document.FLAG_SUPPORTS_MOVE }
        source.sourceParentResolver = { _, _ -> sourceParentUri }
        source.moveDocumentInvoker = { src, srcParent, dstParent ->
            moveInvokerCalled = true
            assertEquals(sourceUri, src)
            assertEquals(sourceParentUri, srcParent)
            assertEquals(targetFolderUri, dstParent)
            destFileUri
        }
        every { contentResolver.query(any(), any(), any(), any(), any()) } returns null

        val result = source.moveImageDetailed(
            sourceUri = sourceUri,
            destFolderUri = destFolderUri,
            sorting = false,
            subfolderName = "Selection",
        )

        assertTrue(moveInvokerCalled)
        assertEquals(TransferStatus.SUCCESS, result.status)
        assertEquals(destFileUri.toString(), result.destinationUri)
        // Stream copy was never invoked:
        verify(exactly = 0) { targetFolder.createFile(any(), any()) }
    }

    @Test
    fun `provider silent auto-rename mismatch cleans up created file and fails`() = runTest {
        val payload = ByteArray(512) { 0x11 }
        val sourceDoc = mockk<DocumentFile>(relaxed = true) {
            every { name } returns "IMG_0001.CR3"
            every { type } returns "image/x-canon-cr3"
            every { length() } returns 512L
        }
        // Provider silently renamed the created file to "IMG_0001 (1).CR3"
        val renamedDestDoc = mockk<DocumentFile>(relaxed = true) {
            every { uri } returns destFileUri
            every { name } returns "IMG_0001 (1).CR3"
            every { length() } returns 512L
            every { delete() } returns true
        }
        val targetFolder = mockk<DocumentFile>(relaxed = true) {
            every { uri } returns targetFolderUri
            every { listFiles() } returns emptyArray()
            every { createFile(any(), "IMG_0001.CR3") } returns renamedDestDoc
        }

        source.documentFileFromSingleUri = { sourceDoc }
        source.documentFileFromTreeUri = { targetFolder }
        every { contentResolver.query(any(), any(), any(), any(), any()) } returns null
        every { contentResolver.openInputStream(sourceUri) } answers { ByteArrayInputStream(payload) }
        every { contentResolver.openOutputStream(destFileUri) } answers { ByteArrayOutputStream() }

        val result = source.copyImageDetailed(
            sourceUri = sourceUri,
            destFolderUri = destFolderUri,
            sorting = false,
            subfolderName = "Selection",
        )

        assertEquals(TransferStatus.FAILURE, result.status)
        verify(exactly = 1) { renamedDestDoc.delete() }
    }

    @Test
    fun `semaphore bounds concurrent transfers to 2 and CancellationException is rethrown`() = runTest {
        val payload = ByteArray(256) { 0x07 }
        val sourceDoc = mockk<DocumentFile>(relaxed = true) {
            every { name } returns "IMG_0001.CR3"
            every { type } returns "image/x-canon-cr3"
            every { length() } returns 256L
        }
        val destDoc = mockk<DocumentFile>(relaxed = true) {
            every { uri } returns destFileUri
            every { name } returns "IMG_0001.CR3"
            every { length() } returns 256L
        }
        val targetFolder = mockk<DocumentFile>(relaxed = true) {
            every { uri } returns targetFolderUri
            every { listFiles() } returns emptyArray()
            every { createFile(any(), any()) } returns destDoc
        }

        source.documentFileFromSingleUri = { sourceDoc }
        source.documentFileFromTreeUri = { targetFolder }
        every { contentResolver.query(any(), any(), any(), any(), any()) } returns null
        every { contentResolver.openInputStream(any()) } answers { ByteArrayInputStream(payload) }
        every { contentResolver.openOutputStream(any()) } answers { ByteArrayOutputStream() }

        val gate = CompletableDeferred<Unit>()
        source.onTransferInsidePermit = {
            delay(20)
            gate.await()
        }

        val deferreds = (1..5).map {
            async {
                source.copyImageDetailed(
                    sourceUri = sourceUri,
                    destFolderUri = destFolderUri,
                    sorting = false,
                    subfolderName = "Selection",
                )
            }
        }
        delay(50)
        assertTrue(
            "Semaphore must cap active transfers at 2, observed ${source.maxObservedConcurrentTransfers.get()}",
            source.maxObservedConcurrentTransfers.get() <= 2,
        )
        gate.complete(Unit)
        val results = deferreds.awaitAll()
        assertTrue(results.all { it.isSuccess })
        assertEquals(2, source.maxObservedConcurrentTransfers.get())

        // Verify CancellationException is rethrown and never swallowed into FAILURE/false.
        source.onTransferInsidePermit = {
            throw CancellationException("transfer cancelled")
        }
        try {
            source.copyImage(
                sourceUri = sourceUri,
                destFolderUri = destFolderUri,
                sorting = false,
                subfolderName = "Selection",
            )
            fail("Expected CancellationException to be rethrown")
        } catch (_: CancellationException) {
            // Expected
        }
    }
}
