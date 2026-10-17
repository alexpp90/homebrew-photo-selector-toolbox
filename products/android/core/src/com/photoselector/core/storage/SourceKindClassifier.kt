package com.photoselector.core.storage

import android.net.Uri
import android.provider.DocumentsContract
import java.net.URLDecoder

/**
 * Canonical classification of a SAF document tree source (U-09).
 */
enum class SourceKind {
    PHONE_LIBRARY,
    REMOVABLE,
    CAMERA_MTP,
    CLOUD_OTHER,
}

/**
 * Classifies a SAF document tree URI into a [SourceKind] with pure parsing separated
 * from [android.os.storage.StorageManager] volume lists.
 *
 * Rules (U-09):
 * - Authority `com.android.externalstorage.documents` + document-ID prefix `primary` or `home`
 *   -> [SourceKind.PHONE_LIBRARY].
 * - Authority `com.android.externalstorage.documents` + any other prefix (e.g. `1A2B-3C4D`)
 *   -> [SourceKind.REMOVABLE] whether or not the volume is currently mounted in `volumes`.
 * - Authority `com.android.mtp.documents` -> [SourceKind.CAMERA_MTP].
 * - Authorities `com.android.providers.media.documents` and `com.android.providers.downloads.documents`
 *   (the device's own media and Downloads) -> [SourceKind.PHONE_LIBRARY].
 * - Anything else -> [SourceKind.CLOUD_OTHER].
 */
object SourceKindClassifier {

    const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"
    const val MTP_DOCUMENTS_AUTHORITY = "com.android.mtp.documents"
    const val MEDIA_DOCUMENTS_AUTHORITY = "com.android.providers.media.documents"
    const val DOWNLOADS_DOCUMENTS_AUTHORITY = "com.android.providers.downloads.documents"

    private val PHONE_PREFIXES = setOf("primary", "home")

    fun classify(treeUri: Uri, volumes: List<ExternalVolume> = emptyList()): SourceKind {
        val authority = treeUri.authority
        val docId = extractTreeDocumentId(treeUri)
        return classify(authority = authority, documentId = docId, volumes = volumes)
    }

    fun classify(
        authority: String?,
        documentId: String?,
        volumes: List<ExternalVolume> = emptyList(),
    ): SourceKind {
        if (authority.isNullOrBlank()) return SourceKind.CLOUD_OTHER
        return when (authority) {
            EXTERNAL_STORAGE_AUTHORITY -> {
                val prefix = extractVolumePrefix(documentId)
                when {
                    prefix == null -> SourceKind.PHONE_LIBRARY
                    prefix.lowercase() in PHONE_PREFIXES -> SourceKind.PHONE_LIBRARY
                    else -> SourceKind.REMOVABLE
                }
            }
            MTP_DOCUMENTS_AUTHORITY -> SourceKind.CAMERA_MTP
            MEDIA_DOCUMENTS_AUTHORITY, DOWNLOADS_DOCUMENTS_AUTHORITY -> SourceKind.PHONE_LIBRARY
            else -> SourceKind.CLOUD_OTHER
        }
    }

    /**
     * Extracts the volume prefix before `:` from a SAF document ID (e.g. `"primary:DCIM"` -> `"primary"`,
     * `"1A2B-3C4D:DCIM/100MSDCF"` -> `"1A2B-3C4D"`).
     */
    fun extractVolumePrefix(documentId: String?): String? {
        if (documentId.isNullOrBlank()) return null
        val prefix = documentId.substringBefore(':').trim()
        return prefix.ifEmpty { null }
    }

    /**
     * Extracts the removable volume UUID from an external-storage tree URI, or null if the URI
     * is not on an external-storage removable volume.
     */
    fun extractRemovableUuid(treeUri: Uri): String? {
        if (treeUri.authority != EXTERNAL_STORAGE_AUTHORITY) return null
        val prefix = extractVolumePrefix(extractTreeDocumentId(treeUri)) ?: return null
        return if (prefix.lowercase() in PHONE_PREFIXES) null else prefix
    }

    /**
     * Extracts the tree document ID from [treeUri], falling back to path parsing when
     * [DocumentsContract.getTreeDocumentId] is stubbed or throws.
     */
    fun extractTreeDocumentId(treeUri: Uri): String? {
        try {
            val id = DocumentsContract.getTreeDocumentId(treeUri)
            if (!id.isNullOrBlank()) return id
        } catch (_: Throwable) {
            // Fall through to pure URI path parsing
        }
        val rawPath = treeUri.encodedPath ?: treeUri.path ?: return null
        val treeMarker = "/tree/"
        val idx = rawPath.indexOf(treeMarker)
        if (idx >= 0) {
            val afterTree = rawPath.substring(idx + treeMarker.length)
            val rawTreeId = afterTree.substringBefore("/document/")
            return try {
                URLDecoder.decode(rawTreeId, "UTF-8")
            } catch (_: Throwable) {
                rawTreeId
            }
        }
        val docMarker = "/document/"
        val docIdx = rawPath.indexOf(docMarker)
        if (docIdx >= 0) {
            val rawDocId = rawPath.substring(docIdx + docMarker.length)
            return try {
                URLDecoder.decode(rawDocId, "UTF-8")
            } catch (_: Throwable) {
                rawDocId
            }
        }
        return null
    }
}
