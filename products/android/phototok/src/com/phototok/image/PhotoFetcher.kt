package com.phototok.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.util.Log
import android.webkit.MimeTypeMap
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.Options
import coil.size.pxOrElse
import com.photoselector.core.preview.EmbeddedPreviewExtractor
import com.photoselector.core.preview.TiffBitmapDecoder
import com.phototok.domain.PhotoExtensions

/**
 * Coil fetcher for the photos Android's platform decoders cannot show: camera
 * RAWs (no platform codec reads CR3; others vary by device) and TIFF (Android
 * has no TIFF codec at all).
 *
 * - **RAW** → the camera's embedded JPEG preview via `:core`'s
 *   [EmbeddedPreviewExtractor] — the smallest preview that reaches the
 *   requested size, never a full RAW decode on the first-paint path. Only when
 *   the largest preview is below [MIN_DISPLAY_LONG_EDGE] (phone DNGs embed
 *   ~672 px) is the platform decoder tried, keeping the small preview as the
 *   last resort.
 * - **TIFF** → `:core`'s [TiffBitmapDecoder].
 *
 * Every other photo is declined, so JPEG/HEIF/PNG/WebP keep Coil's default
 * pipeline unchanged. [fetch] returning null also hands over to that pipeline.
 */
class PhotoFetcher(
    private val uri: Uri,
    private val kind: Kind,
    private val options: Options,
) : Fetcher {

    enum class Kind { RAW, TIFF, UNKNOWN }

    override suspend fun fetch(): FetchResult? {
        val context = options.context
        val kind = if (kind == Kind.UNKNOWN) kindFromMimeType(context, uri) ?: return null else kind
        val requested = maxOf(options.size.width.pxOrElse { 0 }, options.size.height.pxOrElse { 0 })
        val target = if (requested > 0) requested else UNSIZED_LONG_EDGE
        val bitmap = when (kind) {
            Kind.RAW -> decodeRaw(context, target)
            Kind.TIFF -> TiffBitmapDecoder.decode(context, uri, target)?.toBitmap()
            Kind.UNKNOWN -> null
        } ?: return null
        return DrawableResult(
            drawable = BitmapDrawable(context.resources, bitmap),
            isSampled = true,
            dataSource = DataSource.DISK,
        )
    }

    private fun decodeRaw(context: Context, target: Int): Bitmap? {
        val extracted = EmbeddedPreviewExtractor.extract(context, uri, minLongEdge = target)
        if (extracted != null && extracted.preview.longEdge >= minOf(target, MIN_DISPLAY_LONG_EDGE)) {
            extracted.decode(target)?.let { return it }
        }
        return decodePlatform(context, target) ?: extracted?.decode(target)
    }

    /** BitmapFactory, power-of-two subsampled so the long edge stays near [target]. */
    private fun decodePlatform(context: Context, target: Int): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val longEdge = maxOf(bounds.outWidth, bounds.outHeight)
        if (longEdge <= 0) {
            null
        } else {
            var sample = 1
            while (longEdge / (sample * 2) >= target) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        }
    } catch (e: Exception) {
        Log.d(TAG, "Platform decoder could not decode $uri: ${e.message}")
        null
    }

    class Factory : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            if (data.scheme != "content") return null
            // Local SAF document ids end in the display name, so no I/O is needed
            // to decide; opaque ids (cloud providers) are resolved by MIME type in fetch().
            val name = data.lastPathSegment ?: return null
            val kind = when {
                PhotoExtensions.isRaw(name) -> Kind.RAW
                PhotoExtensions.isTiff(name) -> Kind.TIFF
                PhotoExtensions.isSupported(name) -> return null
                else -> Kind.UNKNOWN
            }
            return PhotoFetcher(data, kind, options)
        }
    }

    companion object {
        private const val TAG = "PhotoFetcher"

        /**
         * Below this an embedded preview is too small to judge a photo by and a
         * platform decode is attempted. The cross-product display minimum
         * (`testdata/formats/manifest.json` → `display.min_long_edge_px`).
         */
        const val MIN_DISPLAY_LONG_EDGE = 1024

        /** Long edge used when a request carries no size. */
        const val UNSIZED_LONG_EDGE = 2048

        private fun kindFromMimeType(context: Context, uri: Uri): Kind? {
            val mime = try {
                context.contentResolver.getType(uri)
            } catch (e: Exception) {
                null
            } ?: return null
            val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: return null
            return when {
                PhotoExtensions.isRaw("x.$ext") -> Kind.RAW
                PhotoExtensions.isTiff("x.$ext") -> Kind.TIFF
                else -> null
            }
        }
    }
}
