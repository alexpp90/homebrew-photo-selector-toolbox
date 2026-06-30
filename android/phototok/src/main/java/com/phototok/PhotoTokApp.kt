package com.phototok

import android.app.Application
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.phototok.data.source.googledrive.DriveCoilFetcher
import com.phototok.data.source.googledrive.GoogleDriveImageSource
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class PhotoTokApp : Application() {

    @Inject lateinit var driveImageSource: GoogleDriveImageSource

    override fun onCreate() {
        super.onCreate()
        initCoilWithDrive()
    }

    /**
     * Configure Coil with generous memory + disk caches and the Google Drive fetcher.
     * Larger caches keep already-seen frames warm so swiping back/forth — and loading
     * remote Drive images — stays snappy.
     */
    private fun initCoilWithDrive() {
        val imageLoader = ImageLoader.Builder(this)
            .components {
                add(DriveCoilFetcher.Factory(driveImageSource))
            }
            .memoryCache {
                MemoryCache.Builder(this)
                    // Use up to 30% of available app memory for decoded bitmaps.
                    .maxSizePercent(0.30)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    // 512 MB on-disk cache for downloaded/decoded source images.
                    .maxSizeBytes(512L * 1024 * 1024)
                    .build()
            }
            // Source images are immutable here; don't re-validate via HTTP headers.
            .respectCacheHeaders(false)
            .build()
        Coil.setImageLoader(imageLoader)
    }
}
