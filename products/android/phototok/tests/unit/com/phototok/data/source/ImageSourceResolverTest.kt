package com.phototok.data.source

import android.net.Uri
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageSourceResolverTest {

    private val localSource = mockk<LocalImageSource>()
    private val resolver = ImageSourceResolver(localSource)

    private val localUri = mockk<Uri>()
    private val foreignUri = mockk<Uri>()

    @Test
    fun `sourceFor returns local source when it owns the URI`() {
        every { localSource.owns(localUri) } returns true

        val result = resolver.sourceFor(localUri)

        assertEquals(localSource, result)
    }

    @Test
    fun `sourceFor falls back to local source when unowned`() {
        every { localSource.owns(foreignUri) } returns false

        val result = resolver.sourceFor(foreignUri)

        assertEquals(localSource, result)
    }

    @Test
    fun `sameSource returns true when both URIs resolve to same source`() {
        every { localSource.owns(localUri) } returns true
        every { localSource.owns(foreignUri) } returns false

        // Both resolve to localSource (foreign falls back to local)
        assertTrue(resolver.sameSource(localUri, foreignUri))
    }
}
