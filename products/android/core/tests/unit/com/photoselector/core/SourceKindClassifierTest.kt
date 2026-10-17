package com.photoselector.core

import android.net.Uri
import com.photoselector.core.storage.ExternalVolume
import com.photoselector.core.storage.SourceKind
import com.photoselector.core.storage.SourceKindClassifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SourceKindClassifierTest {

    @Test
    fun `classify primary and home externalstorage prefixes as PHONE_LIBRARY`() {
        val primaryUri = Uri.parse(
            "content://com.android.externalstorage.documents/tree/primary%3ADCIM%2FCamera"
        )
        val homeUri = Uri.parse(
            "content://com.android.externalstorage.documents/tree/home%3APictures"
        )

        assertEquals(SourceKind.PHONE_LIBRARY, SourceKindClassifier.classify(primaryUri))
        assertEquals(SourceKind.PHONE_LIBRARY, SourceKindClassifier.classify(homeUri))
        assertNull(SourceKindClassifier.extractRemovableUuid(primaryUri))
        assertNull(SourceKindClassifier.extractRemovableUuid(homeUri))
    }

    @Test
    fun `classify non-primary externalstorage prefix as REMOVABLE even when unmounted`() {
        val sdUri = Uri.parse(
            "content://com.android.externalstorage.documents/tree/1A2B-3C4D%3ADCIM%2F100MSDCF"
        )

        assertEquals(
            SourceKind.REMOVABLE,
            SourceKindClassifier.classify(sdUri, volumes = emptyList())
        )
        assertEquals(
            SourceKind.REMOVABLE,
            SourceKindClassifier.classify(
                sdUri,
                volumes = listOf(
                    ExternalVolume(
                        description = "SD Card",
                        path = "/storage/1A2B-3C4D",
                        isRemovable = true,
                        isPrimary = false,
                        uuid = "1A2B-3C4D"
                    )
                )
            )
        )
        assertEquals("1A2B-3C4D", SourceKindClassifier.extractRemovableUuid(sdUri))
    }

    @Test
    fun `classify MTP authority as CAMERA_MTP`() {
        val mtpUri = Uri.parse("content://com.android.mtp.documents/tree/123")
        assertEquals(SourceKind.CAMERA_MTP, SourceKindClassifier.classify(mtpUri))
        assertNull(SourceKindClassifier.extractRemovableUuid(mtpUri))
    }

    @Test
    fun `classify the device media and downloads providers as PHONE_LIBRARY`() {
        assertEquals(
            SourceKind.PHONE_LIBRARY,
            SourceKindClassifier.classify(
                authority = SourceKindClassifier.MEDIA_DOCUMENTS_AUTHORITY,
                documentId = "image:1"
            )
        )
        assertEquals(
            SourceKind.PHONE_LIBRARY,
            SourceKindClassifier.classify(
                authority = SourceKindClassifier.DOWNLOADS_DOCUMENTS_AUTHORITY,
                documentId = "raw:/storage/emulated/0/Download"
            )
        )
    }

    @Test
    fun `classify cloud and unknown authorities as CLOUD_OTHER`() {
        val driveUri = Uri.parse(
            "content://com.google.android.apps.docs.storage/tree/acc%3D1%3Bdoc%3Dfolder"
        )
        assertEquals(SourceKind.CLOUD_OTHER, SourceKindClassifier.classify(driveUri))
        assertEquals(
            SourceKind.CLOUD_OTHER,
            SourceKindClassifier.classify(authority = null, documentId = null)
        )
    }
}
