package com.photoselectortoolbox.domain.usecase

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import com.photoselectortoolbox.data.cache.ScoreDao
import com.photoselectortoolbox.data.cache.ScoreEntity
import com.photoselectortoolbox.data.model.ImageItem
import com.photoselectortoolbox.data.model.ScanResult
import com.photoselectortoolbox.data.repository.SettingsRepository
import com.photoselectortoolbox.domain.analysis.ClippingAnalyzer
import com.photoselectortoolbox.domain.analysis.NoiseAnalyzer
import com.photoselectortoolbox.domain.analysis.SharpnessAnalyzer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject

/**
 * Progress state emitted during image scanning.
 */
data class ScanProgress(
    val processed: Int,
    val total: Int,
    val currentFile: String,
    val results: Map<String, ScanResult>
)

/**
 * Orchestrates running all analysis tools (sharpness, noise, clipping)
 * on a list of images. Checks cache before computing, caches results after,
 * and runs analysis in parallel using coroutines with semaphore-gated concurrency.
 *
 * Optimizations applied:
 * - Multiple images are analyzed concurrently (gated by thread count setting).
 * - Combined clipping analysis avoids duplicate bitmap→Mat/grayscale conversions.
 * - Sharpness and noise analysis run in parallel within each image.
 */
class ScanImagesUseCase @Inject constructor(
    private val sharpnessAnalyzer: SharpnessAnalyzer,
    private val noiseAnalyzer: NoiseAnalyzer,
    private val clippingAnalyzer: ClippingAnalyzer,
    private val scoreDao: ScoreDao,
    private val settingsRepository: SettingsRepository,
    @ApplicationContext private val context: Context
) {

    /**
     * Scan a list of images, emitting progress updates as a Flow.
     * Results are cached and retrieved from cache when available.
     * Multiple images are analyzed concurrently.
     *
     * @param images The images to scan.
     * @return Flow emitting scan progress with accumulated results.
     */
    operator fun invoke(images: List<ImageItem>): Flow<ScanProgress> = channelFlow {
        val results = mutableMapOf<String, ScanResult>()

        send(ScanProgress(0, images.size, "", results.toMap()))

        // Read thread count setting (default: min(4, availableProcessors))
        val threadCount = settingsRepository.analysisThreadCount.first()
        val semaphore = Semaphore(threadCount)

        // Channel for progress updates from concurrent workers
        val progressChannel = Channel<Pair<Int, Pair<String, ScanResult?>>>(Channel.BUFFERED)

        // Launch concurrent image analysis jobs
        for ((index, image) in images.withIndex()) {
            launch(Dispatchers.IO) {
                semaphore.withPermit {
                    ensureActive()

                    // Check cache first
                    val cached = checkCache(image)
                    if (cached != null) {
                        progressChannel.send(index to (image.fileName to cached))
                        return@withPermit
                    }

                    // Compute scores
                    val scanResult = analyzeImage(image)
                    if (scanResult != null) {
                        cacheResult(image, scanResult)
                    }
                    progressChannel.send(index to (image.fileName to scanResult))
                }
            }
        }

        // Collect progress from workers and emit to downstream
        launch {
            var received = 0
            while (received < images.size) {
                val (_, pair) = progressChannel.receive()
                val (fileName, scanResult) = pair
                received++

                if (scanResult != null) {
                    results[scanResult.filePath] = scanResult
                }

                send(
                    ScanProgress(
                        processed = received,
                        total = images.size,
                        currentFile = fileName,
                        results = results.toMap()
                    )
                )
            }
            progressChannel.close()
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Check if a valid cached result exists for this image.
     * Cache is valid if the file path, size, and modification time match.
     */
    private suspend fun checkCache(image: ImageItem): ScanResult? {
        val cached = scoreDao.getScore(image.uri) ?: return null

        // Validate cache entry matches current file
        if (cached.fileSize != image.fileSize || cached.lastModified != image.lastModified) {
            return null
        }

        // Update access time for LRU tracking
        scoreDao.updateAccessTime(image.uri, System.currentTimeMillis())

        return ScanResult(
            filePath = image.uri,
            sharpnessScore = cached.sharpnessScore,
            noiseLevel = cached.noiseLevel,
            highlightClipping = cached.highlightClipping,
            shadowClipping = cached.shadowClipping
        )
    }

    /**
     * Run all analysis tools on an image.
     * Uses combined clipping analysis to avoid duplicate conversions.
     * Sharpness and noise run in parallel; clipping runs in a third coroutine.
     * Returns null if the image cannot be decoded.
     */
    private suspend fun analyzeImage(image: ImageItem): ScanResult? = coroutineScope {
        val uri = Uri.parse(image.uri)

        // Decode the bitmap once for all analyzers with memory-efficient downsampling
        val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    // Set allocator to SOFTWARE because OpenCV bitmapToMat requires a software bitmap (not HARDWARE)
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE

                    // Downsample if the image is too large (max 2048px on longest edge)
                    val longestEdge = maxOf(info.size.width, info.size.height)
                    val maxDimension = 2048
                    if (longestEdge > maxDimension) {
                        val sampleSize = longestEdge / maxDimension
                        if (sampleSize > 1) {
                            decoder.setTargetSampleSize(sampleSize)
                        }
                    }
                }
            } catch (e: Exception) {
                null
            }
        } else {
            // Fallback for API < 28: use BitmapFactory with calculated inSampleSize and ARGB_8888 config
            try {
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, options)
                }

                val width = options.outWidth
                val height = options.outHeight
                if (width > 0 && height > 0) {
                    val longestEdge = maxOf(width, height)
                    val maxDimension = 2048
                    var sampleSize = 1
                    while (longestEdge / sampleSize > maxDimension) {
                        sampleSize *= 2
                    }

                    val decodeOptions = BitmapFactory.Options().apply {
                        inSampleSize = sampleSize
                        inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
                    }
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        BitmapFactory.decodeStream(stream, null, decodeOptions)
                    }
                } else null
            } catch (e: Exception) {
                null
            }
        } ?: return@coroutineScope null

        try {
            // Run sharpness and noise in parallel
            val sharpnessDeferred = async(Dispatchers.Default) {
                try {
                    sharpnessAnalyzer.analyze(bitmap)
                } catch (e: Exception) {
                    null
                }
            }

            val noiseDeferred = async(Dispatchers.Default) {
                try {
                    noiseAnalyzer.analyze(bitmap)
                } catch (e: Exception) {
                    null
                }
            }

            // Combined clipping analysis: single bitmap→Mat + grayscale conversion
            val clippingDeferred = async(Dispatchers.Default) {
                try {
                    clippingAnalyzer.analyzeClipping(bitmap)
                } catch (e: Exception) {
                    null
                }
            }

            val clippingResult = clippingDeferred.await()

            ScanResult(
                filePath = image.uri,
                sharpnessScore = sharpnessDeferred.await(),
                noiseLevel = noiseDeferred.await(),
                highlightClipping = clippingResult?.first,
                shadowClipping = clippingResult?.second
            )
        } finally {
            bitmap.recycle()
        }
    }

    /**
     * Cache the scan result for future lookups.
     */
    private suspend fun cacheResult(image: ImageItem, result: ScanResult) {
        try {
            scoreDao.insertOrUpdate(
                ScoreEntity(
                    filePath = image.uri,
                    fileSize = image.fileSize,
                    lastModified = image.lastModified,
                    sharpnessScore = result.sharpnessScore,
                    noiseLevel = result.noiseLevel,
                    highlightClipping = result.highlightClipping,
                    shadowClipping = result.shadowClipping
                )
            )
        } catch (e: Exception) {
            // Cache failures are non-critical; swallow silently
        }
    }
}
