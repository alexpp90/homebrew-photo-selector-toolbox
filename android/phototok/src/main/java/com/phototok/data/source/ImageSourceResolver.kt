package com.phototok.data.source

import android.net.Uri
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves which [ImageSource] backend is responsible for a URI.
 * The single place in the app where URI schemes are dispatched on.
 *
 * Currently there is one backend: [LocalImageSource] (SAF/DocumentFile). SAF
 * document providers also cover cloud storage (e.g. Google Drive) exposed by
 * their apps, so no dedicated remote backend is needed. Adding a new backend
 * means one new [ImageSource] implementation plus registration here.
 */
@Singleton
class ImageSourceResolver @Inject constructor(
    private val localImageSource: LocalImageSource,
) {
    private val sources: List<ImageSource>
        get() = listOf(localImageSource)

    /** The source that owns [uri]; falls back to the local source. */
    fun sourceFor(uri: Uri): ImageSource =
        sources.firstOrNull { it.owns(uri) } ?: localImageSource

    /** True when both URIs are handled by the same backend. */
    fun sameSource(a: Uri, b: Uri): Boolean = sourceFor(a) === sourceFor(b)
}
