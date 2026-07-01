package com.phototok.data.repository

import android.net.Uri
import android.util.Log
import com.phototok.data.model.ExifData
import com.phototok.data.model.ImageItem
import com.phototok.data.source.ImageSourceResolver
import com.phototok.data.source.SelectionListing
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin router over the [com.phototok.data.source.ImageSource] backends.
 * All backend-specific behavior lives in the source implementations;
 * this class only resolves which backend owns a URI and delegates.
 */
@Singleton
class ImageRepositoryImpl @Inject constructor(
    private val resolver: ImageSourceResolver,
) : ImageRepository {

    companion object {
        private const val TAG = "ImageRepositoryImpl"
    }

    override fun discoverImages(folderUri: Uri): Flow<List<ImageItem>> =
        resolver.sourceFor(folderUri).discoverImages(folderUri)

    override suspend fun prepareSourceFolder(folderUri: Uri): String? =
        resolver.sourceFor(folderUri).prepareSourceFolder(folderUri)

    override suspend fun resolveFolderName(folderUri: Uri): String? =
        resolver.sourceFor(folderUri).resolveFolderName(folderUri)

    override suspend fun getExifData(uri: Uri): ExifData? =
        resolver.sourceFor(uri).getExifData(uri)

    override suspend fun deleteImage(uri: Uri): Boolean =
        resolver.sourceFor(uri).deleteImage(uri)

    override suspend fun moveImage(
        sourceUri: Uri,
        destFolderUri: Uri,
        sorting: Boolean,
        subfolderName: String,
    ): Boolean {
        if (!resolver.sameSource(sourceUri, destFolderUri)) {
            Log.w(TAG, "Cross-source move is not supported: $sourceUri -> $destFolderUri")
            return false
        }
        return resolver.sourceFor(sourceUri)
            .moveImage(sourceUri, destFolderUri, sorting, subfolderName)
    }

    override suspend fun copyImage(
        sourceUri: Uri,
        destFolderUri: Uri,
        sorting: Boolean,
        subfolderName: String,
    ): Boolean {
        if (!resolver.sameSource(sourceUri, destFolderUri)) {
            Log.w(TAG, "Cross-source copy is not supported: $sourceUri -> $destFolderUri")
            return false
        }
        return resolver.sourceFor(sourceUri)
            .copyImage(sourceUri, destFolderUri, sorting, subfolderName)
    }

    override suspend fun getImageDimensions(uri: Uri): Pair<Int, Int> =
        resolver.sourceFor(uri).getImageDimensions(uri)

    override suspend fun listSelectionImages(
        folderUri: Uri,
        subfolderName: String,
    ): SelectionListing =
        resolver.sourceFor(folderUri).listSelectionImages(folderUri, subfolderName)
}
