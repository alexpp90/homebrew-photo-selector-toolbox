package com.photoselectortoolbox.domain.usecase

import android.content.Context
import android.net.Uri
import com.photoselectortoolbox.data.cache.ScoreDao
import com.photoselectortoolbox.data.cache.ScoreEntity
import com.photoselectortoolbox.data.model.ImageItem
import com.photoselectortoolbox.data.repository.SettingsRepository
import com.photoselectortoolbox.domain.analysis.AestheticAnalyzer
import com.photoselectortoolbox.domain.analysis.ClippingAnalyzer
import com.photoselectortoolbox.domain.analysis.NoiseAnalyzer
import com.photoselectortoolbox.domain.analysis.SharpnessAnalyzer
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class ScanImagesUseCaseTest {

    @Test
    fun minSharpnessThresholdConstantIs40() {
        assertEquals(40.0, ScanImagesUseCase.MIN_SHARPNESS_FOR_AESTHETIC, 1e-9)
    }

    @Test
    fun cacheHitCheckingHoistsAestheticAvailabilityCheck() = runBlocking {
        val sharpnessAnalyzer = mockk<SharpnessAnalyzer>()
        val noiseAnalyzer = mockk<NoiseAnalyzer>()
        val clippingAnalyzer = mockk<ClippingAnalyzer>()
        val aestheticAnalyzer = mockk<AestheticAnalyzer>()
        val scoreDao = mockk<ScoreDao>()
        val settingsRepository = mockk<SettingsRepository>()
        val bitmapDecoder = mockk<com.photoselectortoolbox.data.source.PhotoBitmapDecoder>()
        val mockBitmap = mockk<android.graphics.Bitmap>(relaxed = true)

        every { bitmapDecoder.decodeForAnalysis(any(), any()) } returns mockBitmap
        every { sharpnessAnalyzer.analyze(any(), any()) } returns 50.0
        every { noiseAnalyzer.analyze(any()) } returns 10.0
        every { clippingAnalyzer.analyzeClipping(any()) } returns Pair(0.0, 0.0)
        every { aestheticAnalyzer.analyze(any()) } returns 7.5
        every { settingsRepository.analysisThreadCount } returns flowOf(2)
        every { aestheticAnalyzer.isAvailable() } returns true

        val images = listOf(
            ImageItem("content://media/external/images/media/1", "img1.jpg", 100L, 1000L, "image/jpeg"),
            ImageItem("content://media/external/images/media/2", "img2.jpg", 200L, 2000L, "image/jpeg")
        )

        val cachedScores = listOf(
            ScoreEntity(
                filePath = "content://media/external/images/media/1",
                fileSize = 100L,
                lastModified = 1000L,
                sharpnessScore = 50.0,
                noiseLevel = 10.0,
                highlightClipping = 5.0,
                shadowClipping = 5.0,
                aestheticScore = 8.0
            ),
            ScoreEntity(
                filePath = "content://media/external/images/media/2",
                fileSize = 200L,
                lastModified = 2000L,
                sharpnessScore = 60.0,
                noiseLevel = 10.0,
                highlightClipping = 5.0,
                shadowClipping = 5.0,
                aestheticScore = null
            )
        )

        coEvery { scoreDao.getScores(listOf("content://media/external/images/media/1", "content://media/external/images/media/2")) } returns cachedScores
        coEvery { scoreDao.updateAccessTimes(any(), any()) } returns Unit

        val useCase = ScanImagesUseCase(
            sharpnessAnalyzer, noiseAnalyzer, clippingAnalyzer, aestheticAnalyzer,
            scoreDao, settingsRepository, bitmapDecoder
        )

        useCase(images, aestheticEnabled = true).toList()

        verify(exactly = 2) { aestheticAnalyzer.isAvailable() }
    }

    // REQ-AND-CACHE.03, REQ-AND-CACHE.06
    @Test
    fun `scan with 2500 cached images chunks getScores and updateAccessTimes at 900`() = runBlocking {
        val sharpnessAnalyzer = mockk<SharpnessAnalyzer>(relaxed = true)
        val noiseAnalyzer = mockk<NoiseAnalyzer>(relaxed = true)
        val clippingAnalyzer = mockk<ClippingAnalyzer>(relaxed = true)
        val aestheticAnalyzer = mockk<AestheticAnalyzer>(relaxed = true)
        val scoreDao = mockk<ScoreDao>()
        val settingsRepository = mockk<SettingsRepository> {
            every { analysisThreadCount } returns flowOf(4)
        }
        val bitmapDecoder = mockk<com.photoselectortoolbox.data.source.PhotoBitmapDecoder>(relaxed = true)

        val images = (1..2500).map { idx ->
            ImageItem("content://test/img_$idx.jpg", "img_$idx.jpg", 100L, 1000L, "image/jpeg")
        }
        val getScoresChunkSizes = java.util.Collections.synchronizedList(mutableListOf<Int>())
        val updateAccessChunkSizes = java.util.Collections.synchronizedList(mutableListOf<Int>())

        coEvery { scoreDao.getScores(any()) } answers {
            val chunk = firstArg<List<String>>()
            getScoresChunkSizes.add(chunk.size)
            chunk.map { uri ->
                ScoreEntity(
                    filePath = uri,
                    fileSize = 100L,
                    lastModified = 1000L,
                    sharpnessScore = 80.0,
                    noiseLevel = 5.0,
                    highlightClipping = 0.0,
                    shadowClipping = 0.0,
                )
            }
        }
        coEvery { scoreDao.updateAccessTimes(any(), any()) } answers {
            val chunk = firstArg<List<String>>()
            updateAccessChunkSizes.add(chunk.size)
        }

        val useCase = ScanImagesUseCase(
            sharpnessAnalyzer, noiseAnalyzer, clippingAnalyzer, aestheticAnalyzer,
            scoreDao, settingsRepository, bitmapDecoder
        )

        val progress = useCase(images, aestheticEnabled = false).toList().last()
        assertEquals(2500, progress.results.size)
        assertEquals(listOf(900, 900, 700), getScoresChunkSizes)
        assertEquals(listOf(900, 900, 700), updateAccessChunkSizes)
    }
}

