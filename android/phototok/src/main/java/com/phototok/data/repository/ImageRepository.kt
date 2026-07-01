package com.phototok.data.repository

import android.net.Uri
import com.phototok.data.model.ExifData
import com.phototok.data.model.ImageItem
import com.phototok.data.source.SelectionListing
import com.phototok.domain.PhotoFolders
import kotlinx.coroutines.flow.Flow

interface ImageRepository {
    fun discoverImages(folderUri: Uri): Flow<List<ImageItem>>

    /**
     * Prepare a folder for use as the swipe source (persist permission, verify
     * access). Returns the display name, or null when the folder is inaccessible.
     */
    suspend fun prepareSourceFolder(folderUri: Uri): String?

    /** Resolve a folder's display name, or null when unavailable. */
    suspend fun resolveFolderName(folderUri: Uri): String?

    suspend fun getExifData(uri: Uri): ExifData?

    suspend fun deleteImage(uri: Uri): Boolean

    suspend fun moveImage(
        sourceUri: Uri,
        destFolderUri: Uri,
        sorting: Boolean,
        subfolderName: String = PhotoFolders.SELECTION,
    ): Boolean

    suspend fun copyImage(
        sourceUri: Uri,
        destFolderUri: Uri,
        sorting: Boolean,
        subfolderName: String = PhotoFolders.SELECTION,
    ): Boolean

    suspend fun getImageDimensions(uri: Uri): Pair<Int, Int>

    /** List the images collected in the selection subfolder of [folderUri]. */
    suspend fun listSelectionImages(
        folderUri: Uri,
        subfolderName: String = PhotoFolders.SELECTION,
    ): SelectionListing
}
