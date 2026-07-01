package com.phototok.data.repository

import android.content.Context
import android.net.Uri
import com.phototok.data.model.ExifData
import com.phototok.data.model.ImageItem
import kotlinx.coroutines.flow.Flow

interface ImageRepository {
    fun discoverImages(folderUri: Uri): Flow<List<ImageItem>>
    suspend fun getExifData(context: Context, uri: Uri): ExifData?
    suspend fun deleteImage(context: Context, uri: Uri): Boolean
    suspend fun moveImage(context: Context, sourceUri: Uri, destFolderUri: Uri, sorting: Boolean, subfolderName: String = "PhotoTok_Selection"): Boolean
    suspend fun copyImage(context: Context, sourceUri: Uri, destFolderUri: Uri, sorting: Boolean, subfolderName: String = "PhotoTok_Selection"): Boolean
    suspend fun getImageDimensions(context: Context, uri: Uri): Pair<Int, Int>
}
