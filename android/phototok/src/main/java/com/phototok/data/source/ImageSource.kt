package com.phototok.data.source

import android.net.Uri
import com.phototok.data.model.ExifData
import com.phototok.data.model.ImageItem
import kotlinx.coroutines.flow.Flow

/** Result of listing the app-managed selection subfolder of a source folder. */
sealed interface SelectionListing {
    /** The selection folder exists; [images] are sorted newest-first. */
    data class Available(val images: List<ImageItem>, val folderName: String) : SelectionListing

    /** This source backend does not support browsing the selection folder. */
    data object NotSupported : SelectionListing

    /** The selection folder does not exist yet (nothing collected). */
    data object Missing : SelectionListing
}

/**
 * A backend that can enumerate and manipulate images addressed by URI.
 *
 * Implementation: [LocalImageSourceImpl] (SAF/DocumentFile trees). SAF also
 * covers cloud storage exposed through document providers (e.g. Google Drive).
 *
 * All URI-scheme dispatch lives in [ImageSourceResolver]; code outside the
 * data layer must not branch on URI scheme for per-image operations.
 */
interface ImageSource {

    /** True when this source is responsible for [uri]. */
    fun owns(uri: Uri): Boolean

    /** Discover all images under the given folder (recursively). */
    fun discoverImages(folderUri: Uri): Flow<List<ImageItem>>

    /**
     * Prepare a folder for use as the swipe source: persist access permissions
     * where applicable and verify it exists. Returns the display name, or null
     * when the folder cannot be accessed.
     */
    suspend fun prepareSourceFolder(folderUri: Uri): String?

    /** Resolve a folder's display name, or null when unavailable. */
    suspend fun resolveFolderName(folderUri: Uri): String?

    suspend fun getExifData(uri: Uri): ExifData?

    /** Header-only (width, height), or (0, 0) when undeterminable. */
    suspend fun getImageDimensions(uri: Uri): Pair<Int, Int>

    suspend fun deleteImage(uri: Uri): Boolean

    suspend fun copyImage(
        sourceUri: Uri,
        destFolderUri: Uri,
        sorting: Boolean,
        subfolderName: String,
    ): Boolean

    suspend fun moveImage(
        sourceUri: Uri,
        destFolderUri: Uri,
        sorting: Boolean,
        subfolderName: String,
    ): Boolean

    /** List images inside the [subfolderName] child of [folderUri]. */
    suspend fun listSelectionImages(folderUri: Uri, subfolderName: String): SelectionListing
}
