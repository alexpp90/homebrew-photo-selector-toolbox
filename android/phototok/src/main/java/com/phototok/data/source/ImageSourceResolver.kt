package com.phototok.data.source

import android.net.Uri
import com.phototok.data.source.googledrive.GoogleDriveImageSource
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves which [ImageSource] backend is responsible for a URI.
 * The single place in the app where URI schemes are dispatched on.
 */
@Singleton
class ImageSourceResolver @Inject constructor(
    private val localImageSource: LocalImageSource,
    private val driveImageSource: GoogleDriveImageSource,
) {
    private val sources: List<ImageSource>
        get() = listOf(driveImageSource, localImageSource)

    /** The source that owns [uri]; falls back to the local source. */
    fun sourceFor(uri: Uri): ImageSource =
        sources.firstOrNull { it.owns(uri) } ?: localImageSource

    /** True when both URIs are handled by the same backend. */
    fun sameSource(a: Uri, b: Uri): Boolean = sourceFor(a) === sourceFor(b)
}
