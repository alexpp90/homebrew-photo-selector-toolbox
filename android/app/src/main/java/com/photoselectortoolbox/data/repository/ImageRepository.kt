package com.photoselectortoolbox.data.repository

import android.content.Context
import android.net.Uri
import com.photoselectortoolbox.data.model.ExifData
import com.photoselectortoolbox.data.model.ImageItem
import kotlinx.coroutines.flow.Flow

interface ImageRepository {
    fun discoverImages(folderUri: Uri): Flow<List<ImageItem>>
    suspend fun getExifData(context: Context, uri: Uri): ExifData?
    suspend fun deleteImage(context: Context, uri: Uri): Boolean
    suspend fun moveImage(context: Context, sourceUri: Uri, destFolderUri: Uri, sorting: Boolean): Boolean
    suspend fun copyImage(context: Context, sourceUri: Uri, destFolderUri: Uri, sorting: Boolean): Boolean
}
