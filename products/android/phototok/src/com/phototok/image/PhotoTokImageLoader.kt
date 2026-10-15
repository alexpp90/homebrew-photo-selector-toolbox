package com.phototok.image

import android.content.Context
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache

/**
 * Builds the one Coil [ImageLoader] behind every photo the feed shows: the
 * pager's `SubcomposeAsyncImage` and the neighbour prefetch in `PhoneModeViewer`
 * both go through `Coil.imageLoader`, which [com.phototok.PhotoTokApp] sets to
 * the loader built here. The model is always the photo's SAF document `Uri`;
 * RAW and TIFF documents are served by [PhotoFetcher].
 *
 * Kept outside the Application so conformance tests decode through exactly the
 * same loader configuration the app uses.
 */
object PhotoTokImageLoader {

    /**
     * Generous memory + disk caches keep already-seen frames warm so swiping
     * back/forth stays snappy — including for cloud-backed folders accessed
     * through SAF document providers. Both caches are bounded.
     */
    fun create(context: Context): ImageLoader {
        val appContext = context.applicationContext
        return ImageLoader.Builder(appContext)
            // RAW (embedded preview) and TIFF; everything else uses Coil's defaults.
            .components { add(PhotoFetcher.Factory()) }
            .memoryCache {
                MemoryCache.Builder(appContext)
                    // Use up to 30% of available app memory for decoded bitmaps.
                    .maxSizePercent(0.30)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(appContext.cacheDir.resolve("image_cache"))
                    // 512 MB on-disk cache for downloaded/decoded source images.
                    .maxSizeBytes(512L * 1024 * 1024)
                    .build()
            }
            // Source images are immutable here; don't re-validate via HTTP headers.
            .respectCacheHeaders(false)
            .build()
    }
}
