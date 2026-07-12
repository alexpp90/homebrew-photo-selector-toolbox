package com.phototok

import android.app.Application
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class PhotoTokApp : Application() {

    override fun onCreate() {
        super.onCreate()
        initCoil()
    }

    /**
     * Configure Coil with generous memory + disk caches. Larger caches keep
     * already-seen frames warm so swiping back/forth stays snappy — including
     * for cloud-backed folders accessed through SAF document providers.
     */
    private fun initCoil() {
        val imageLoader = ImageLoader.Builder(this)
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
