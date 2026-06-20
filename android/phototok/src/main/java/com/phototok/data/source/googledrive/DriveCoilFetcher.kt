package com.phototok.data.source.googledrive

import android.net.Uri
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path.Companion.toOkioPath

class DriveCoilFetcher(
    private val uri: Uri,
    private val driveImageSource: GoogleDriveImageSource,
) : Fetcher {

    override suspend fun fetch(): FetchResult? = withContext(Dispatchers.IO) {
        val fileId = GoogleDriveImageSource.extractId(uri) ?: return@withContext null
        val fileName = uri.lastPathSegment ?: fileId
        val localFile = driveImageSource.ensureCached(fileId, fileName)
            ?: return@withContext null

        SourceResult(
            source = ImageSource(
                file = localFile.toOkioPath(),
                fileSystem = FileSystem.SYSTEM,
            ),
            mimeType = null,
            dataSource = DataSource.DISK,
        )
    }

    class Factory(
        private val driveImageSource: GoogleDriveImageSource,
    ) : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            if (!GoogleDriveImageSource.isDriveUri(data)) return null
            return DriveCoilFetcher(data, driveImageSource)
        }
    }
}
