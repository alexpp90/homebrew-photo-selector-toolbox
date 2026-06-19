package com.photoselectortoolbox.data.reader

import android.content.Context
import android.net.Uri
import com.photoselectortoolbox.data.model.ExifData

interface ExifReaderStrategy {
    suspend fun readExif(context: Context, uri: Uri): ExifData?
    fun supportedMimeTypes(): Set<String>
}
