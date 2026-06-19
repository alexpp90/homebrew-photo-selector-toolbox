package com.photoselectortoolbox.data.source

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.photoselectortoolbox.data.model.ImageItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

interface LocalImageSource {
    fun discoverImages(folderUri: Uri): Flow<List<ImageItem>>
}

@Singleton
class LocalImageSourceImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : LocalImageSource {

    companion object {
        private const val TAG = "LocalImageSource"

        val SUPPORTED_EXTENSIONS = setOf(
            "jpg", "jpeg", "png", "tiff", "tif", "bmp", "gif", "webp",
            "heif", "heic", "dng", "cr2", "cr3", "nef", "arw", "orf",
            "rw2", "pef", "srw", "raf", "nrw"
        )

        private val EXCLUDED_FOLDER_NAMES = setOf("selection", "selected")
    }

    override fun discoverImages(folderUri: Uri): Flow<List<ImageItem>> = flow {
        val folder = DocumentFile.fromTreeUri(context, folderUri)
        if (folder == null || !folder.isDirectory) {
            Log.w(TAG, "Invalid folder URI: $folderUri")
            emit(emptyList())
            return@flow
        }

        val images = mutableListOf<ImageItem>()
        enumerateImages(folder, images)

        val sorted = images.sortedBy { it.fileName.lowercase() }
        emit(sorted)
    }.flowOn(Dispatchers.IO)

    private fun enumerateImages(folder: DocumentFile, results: MutableList<ImageItem>) {
        val files = folder.listFiles()

        for (file in files) {
            if (file.isDirectory) {
                val folderName = file.name?.lowercase() ?: continue
                if (folderName in EXCLUDED_FOLDER_NAMES) {
                    Log.d(TAG, "Skipping excluded folder: ${file.name}")
                    continue
                }
                enumerateImages(file, results)
                continue
            }

            if (!file.isFile) continue

            val fileName = file.name ?: continue
            val extension = fileName.substringAfterLast('.', "").lowercase()

            if (extension !in SUPPORTED_EXTENSIONS) continue

            val imageItem = ImageItem(
                uri = file.uri.toString(),
                fileName = fileName,
                fileSize = file.length(),
                lastModified = file.lastModified(),
                mimeType = file.type
            )
            results.add(imageItem)
        }
    }
}
