package com.photoselectortoolbox.domain.usecase

import android.net.Uri
import com.photoselectortoolbox.data.cache.ScoreDao
import com.photoselectortoolbox.data.cache.ScoreEntity
import com.photoselectortoolbox.data.model.ImageItem
import com.photoselectortoolbox.data.model.ScanResult
import com.photoselectortoolbox.data.repository.SettingsRepository
import com.photoselectortoolbox.data.source.PhotoBitmapDecoder
import com.photoselectortoolbox.domain.analysis.AestheticAnalyzer
import com.photoselectortoolbox.domain.analysis.ClippingAnalyzer
import com.photoselectortoolbox.domain.analysis.NoiseAnalyzer
import com.photoselectortoolbox.domain.analysis.SharpnessAnalyzer
import javax.inject.Inject
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
    private val aestheticAnalyzer: AestheticAnalyzer,
    private val scoreDao: ScoreDao,
    private val settingsRepository: SettingsRepository,
    private val bitmapDecoder: PhotoBitmapDecoder,
    private val techniqueFactDetector: com.photoselectortoolbox.domain.learn.TechniqueFactDetector,
) {
    constructor(
        sharpnessAnalyzer: SharpnessAnalyzer,
        noiseAnalyzer: NoiseAnalyzer,
        clippingAnalyzer: ClippingAnalyzer,
        aestheticAnalyzer: AestheticAnalyzer,
        scoreDao: ScoreDao,
        settingsRepository: SettingsRepository,
        bitmapDecoder: PhotoBitmapDecoder,
    ) : this(
        sharpnessAnalyzer,
        noiseAnalyzer,
        clippingAnalyzer,
        aestheticAnalyzer,
        scoreDao,
        settingsRepository,
        bitmapDecoder,
        com.photoselectortoolbox.domain.learn.TechniqueFactDetector(),
    )

    companion object {
        /**
         * Minimum sharpness (Laplacian variance) below which an image is
         * considered too blurry to be worth AI aesthetic scoring — the cheap
         * OpenCV gate that keeps the expensive model off obvious rejects.
         * Baseline empirical threshold for Laplacian variance sharpness filtering.
         */
        internal const val MIN_SHARPNESS_FOR_AESTHETIC = 40.0
    }

    /**
     * Scan a list of images, emitting progress updates as a Flow.
     * Results are cached and retrieved from cache when available.
     * Multiple images are analyzed concurrently.
     *
     * @param images The images to scan.
     * @return Flow emitting scan progress with accumulated results.
     */    operator fun invoke(
        images: List<ImageItem>,
        aestheticEnabled: Boolean = false,
    ): Flow<ScanProgress> = channelFlow {
        val results = mutableMapOf<String, ScanResult>()

        send(ScanProgress(0, images.size, "", results.toMap()))

        // Read thread count setting (default: min(4, availableProcessors))
        val threadCount = settingsRepository.analysisThreadCount.first()
        val semaphore = Semaphore(threadCount)

        // Bulk load cache
        val imageUris = images.map { it.uri }
        val cachedScores = scoreDao.getScores(imageUris).associateBy { it.filePath }

        // Find which images have valid cache hits and update their access times
        val currentTime = System.currentTimeMillis()
        val validCacheHits = mutableMapOf<String, ScanResult>()
        val urisToUpdate = mutableListOf<String>()
        val needsAestheticRecompute = aestheticEnabled && aestheticAnalyzer.isAvailable()

        for (image in images) {
            val cached = cachedScores[image.uri] ?: continue

            // Validate cache entry matches current file
            if (cached.fileSize != image.fileSize || cached.lastModified != image.lastModified) {
                continue
            }

            // If the user now wants an aesthetic score but the cached row predates
            // it (and a model is available), recompute rather than serving a stale
            // hit with no aesthetic value.
            if (needsAestheticRecompute && cached.aestheticScore == null) {
                continue
            }

            validCacheHits[image.uri] = ScanResult(
                filePath = image.uri,
                sharpnessScore = cached.sharpnessScore,
                noiseLevel = cached.noiseLevel,
                highlightClipping = cached.highlightClipping,
                shadowClipping = cached.shadowClipping,
                aestheticScore = cached.aestheticScore
            )
            urisToUpdate.add(image.uri)
        }

        // Bulk update access times in background
        if (urisToUpdate.isNotEmpty()) {
            launch(Dispatchers.IO) {
                // SQLite has a limit of 999 variables per query (SQLITE_MAX_VARIABLE_NUMBER)
                // Chunk the updates to avoid SQLiteException: too many SQL variables
                urisToUpdate.chunked(900).forEach { chunk ->
                    scoreDao.updateAccessTimes(chunk, currentTime)
                }
            }
        }

        // Channel for progress updates from concurrent workers
        val progressChannel = Channel<Pair<Int, Pair<String, ScanResult?>>>(Channel.BUFFERED)

        // Launch concurrent image analysis jobs
        for ((index, image) in images.withIndex()) {
            launch(Dispatchers.IO) {
                semaphore.withPermit {
                    ensureActive()

                    // Check cache first (now from pre-computed map)
                    val cached = validCacheHits[image.uri]
                    if (cached != null) {
                        progressChannel.send(index to (image.fileName to cached))
                        return@withPermit
                    }

                    // Compute scores
                    val scanResult = analyzeImage(image, aestheticEnabled)
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
     * Run all analysis tools on an image.
     * Uses combined clipping analysis to avoid duplicate conversions.
     * Sharpness and noise run in parallel; clipping runs in a third coroutine.
     * Returns null if the image cannot be decoded.
     */
    private suspend fun analyzeImage(
        image: ImageItem,
        aestheticEnabled: Boolean,
    ): ScanResult? = coroutineScope {
        val uri = Uri.parse(image.uri)

        // Decode the bitmap once for all analyzers (≤ 2048 px, software ARGB_8888).
        // RAWs go through their embedded camera JPEG — the platform has no
        // decoder for most of them (CR3 at all) — see PhotoBitmapDecoder.
        val bitmap = bitmapDecoder.decodeForAnalysis(uri, image.fileName)
            ?: return@coroutineScope null

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
            val sharpness = sharpnessDeferred.await()

            // AI aesthetic scoring is gated: only when the user enabled it, a
            // model is available, and the image passes the cheap sharpness floor.
            val aesthetic = if (
                aestheticEnabled &&
                aestheticAnalyzer.isAvailable() &&
                passesSharpnessGate(sharpness)
            ) {
                try {
                    aestheticAnalyzer.analyze(bitmap)
                } catch (e: Exception) {
                    null
                }
            } else {
                null
            }

            val noise = noiseDeferred.await()

            try {
                val sampleW = bitmap.width.coerceIn(1, 64)
                val sampleH = bitmap.height.coerceIn(1, 64)
                val (luma, rgb) = if (bitmap.width >= 8 && bitmap.height >= 8) {
                    val pixels = IntArray(sampleW * sampleH)
                    val lumaArr = FloatArray(sampleW * sampleH)
                    val stepX = (bitmap.width.toFloat() / sampleW).coerceAtLeast(1f)
                    val stepY = (bitmap.height.toFloat() / sampleH).coerceAtLeast(1f)
                    for (sy in 0 until sampleH) {
                        val py = (sy * stepY).toInt().coerceIn(0, bitmap.height - 1)
                        for (sx in 0 until sampleW) {
                            val px = (sx * stepX).toInt().coerceIn(0, bitmap.width - 1)
                            val argb = bitmap.getPixel(px, py)
                            val idx = sy * sampleW + sx
                            pixels[idx] = argb
                            val r = (argb shr 16) and 0xFF
                            val g = (argb shr 8) and 0xFF
                            val b = argb and 0xFF
                            lumaArr[idx] = 0.299f * r + 0.587f * g + 0.114f * b
                        }
                    }
                    lumaArr to pixels
                } else {
                    null to null
                }

                val isRaw = image.hasRawCompanion ||
                    com.photoselectortoolbox.domain.format.ImageFormats.isRaw(image.fileName)
                val facts = techniqueFactDetector.detectAll(
                    uri = image.uri,
                    fileSize = image.fileSize,
                    lastModified = image.lastModified,
                    exif = image.exifData,
                    luma = luma,
                    rgbPixels = rgb,
                    width = if (luma != null) sampleW else (image.imageWidth ?: 0),
                    height = if (luma != null) sampleH else (image.imageHeight ?: 0),
                    sharpnessScore = sharpness,
                    noiseLevel = noise,
                    highlightClipping = clippingResult?.first,
                    shadowClipping = clippingResult?.second,
                    isRawOrHasRawCompanion = isRaw,
                )
                if (facts.isNotEmpty()) {
                    scoreDao.upsertTechniqueFacts(facts)
                }
            } catch (_: Exception) {
                // Non-critical: technique facts can be recomputed on demand
            }

            ScanResult(
                filePath = image.uri,
                sharpnessScore = sharpness,
                noiseLevel = noise,
                highlightClipping = clippingResult?.first,
                shadowClipping = clippingResult?.second,
                aestheticScore = aesthetic
            )
        } finally {
            bitmap.recycle()
        }
    }

    /** The cheap OpenCV gate: skip AI scoring on clearly blurry images. */
    private fun passesSharpnessGate(sharpness: Double?): Boolean =
        sharpness == null || sharpness >= MIN_SHARPNESS_FOR_AESTHETIC

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
                    shadowClipping = result.shadowClipping,
                    aestheticScore = result.aestheticScore,
                    rating = image.rating
                )
            )
        } catch (e: Exception) {
            // Cache failures are non-critical; swallow silently
        }
    }
}
