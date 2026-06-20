package com.phototok.data.reader

import android.content.Context
import android.net.Uri
import com.phototok.data.model.ExifData

interface ExifReaderStrategy {
    suspend fun readExif(context: Context, uri: Uri): ExifData?
    fun supportedMimeTypes(): Set<String>
}
