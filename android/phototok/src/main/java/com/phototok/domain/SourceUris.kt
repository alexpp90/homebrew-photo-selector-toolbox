package com.phototok.domain

/**
 * Scheme-level classification of source URIs, usable from any layer without
 * importing data-source classes. The single non-data-layer place where the
 * remote scheme string is known (must match GoogleDriveImageSource.SCHEME).
 */
object SourceUris {
    private const val GDRIVE_PREFIX = "gdrive://"

    /** True when the URI points at a remote (Google Drive) location. */
    fun isRemote(uri: String?): Boolean = uri?.startsWith(GDRIVE_PREFIX) == true
}
