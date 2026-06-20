package com.photoselectortoolbox.data.repository

import android.content.Context
import android.net.Uri
import com.photoselectortoolbox.data.model.ExifData
import com.photoselectortoolbox.data.model.ImageItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FakeImageRepository @Inject constructor() : ImageRepository {

    val imagesFlow = MutableStateFlow<List<ImageItem>>(emptyList())

    override fun discoverImages(folderUri: Uri): Flow<List<ImageItem>> {
        return imagesFlow
    }

    override suspend fun getExifData(context: Context, uri: Uri): ExifData? {
        return imagesFlow.value.find { it.uri == uri.toString() }?.exifData
    }

    override suspend fun deleteImage(context: Context, uri: Uri): Boolean {
        val current = imagesFlow.value
        imagesFlow.value = current.filter { it.uri != uri.toString() }
        return true
    }

    var canTrashResult: Boolean = false

    override fun canTrash(uri: Uri): Boolean = canTrashResult

    override suspend fun moveImage(
        context: Context,
        sourceUri: Uri,
        destFolderUri: Uri,
        sorting: Boolean
    ): Boolean {
        deleteImage(context, sourceUri)
        return true
    }

    override suspend fun copyImage(
        context: Context,
        sourceUri: Uri,
        destFolderUri: Uri,
        sorting: Boolean
    ): Boolean {
        return true
    }

    override suspend fun getImageDimensions(context: Context, uri: Uri): Pair<Int, Int> {
        val img = imagesFlow.value.find { it.uri == uri.toString() }
        return if (img != null) Pair(img.imageWidth, img.imageHeight) else Pair(0, 0)
    }
}
