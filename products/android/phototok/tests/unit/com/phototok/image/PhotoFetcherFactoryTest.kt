package com.phototok.image

import android.net.Uri
import coil.ImageLoader
import coil.request.Options
import io.mockk.mockk
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Which documents leave Coil's default pipeline for [PhotoFetcher]. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class PhotoFetcherFactoryTest {

    private val factory = PhotoFetcher.Factory()
    private val options = Options(RuntimeEnvironment.getApplication())
    private val loader: ImageLoader = mockk(relaxed = true)

    private fun doc(id: String): Uri = Uri.parse(
        "content://com.android.externalstorage.documents/tree/primary%3ADCIM/document/" + Uri.encode(id),
    )

    private fun create(uri: Uri) = factory.create(uri, options, loader)

    @Test
    fun `RAW and TIFF documents are claimed in any case`() {
        listOf("primary:DCIM/DSC00087.ARW", "primary:DCIM/IMG_1.cr3", "primary:DCIM/x.Dng", "primary:DCIM/scan.TIF")
            .forEach { assertNotNull(it, create(doc(it))) }
    }

    @Test
    fun `platform-decodable bitmaps keep the default pipeline`() {
        listOf("primary:DCIM/a.jpg", "primary:DCIM/b.HEIC", "primary:DCIM/c.png", "primary:DCIM/d.webp")
            .forEach { assertNull(it, create(doc(it))) }
    }

    @Test
    fun `opaque document ids are resolved by MIME type at fetch time`() {
        assertNotNull(create(doc("1AbCdEfGhIjK")))
    }

    @Test
    fun `non-content schemes are declined`() {
        assertNull(create(Uri.parse("https://example.com/a.arw")))
    }
}
