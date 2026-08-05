package com.photoselector.core.reader

import android.content.Context
import android.net.Uri
import com.photoselector.core.model.ExifData

interface ExifReaderStrategy {
    suspend fun readExif(context: Context, uri: Uri): ExifData?
    fun supportedMimeTypes(): Set<String>
}
