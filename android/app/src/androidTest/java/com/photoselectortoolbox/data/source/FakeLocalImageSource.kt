package com.photoselectortoolbox.data.source

import android.net.Uri
import com.photoselectortoolbox.data.model.ImageItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FakeLocalImageSource @Inject constructor() : LocalImageSource {
    override fun discoverImages(folderUri: Uri): Flow<List<ImageItem>> {
        return flowOf(emptyList())
    }

    override suspend fun getImageDimensions(uri: Uri): Pair<Int, Int> {
        return Pair(0, 0)
    }
}
