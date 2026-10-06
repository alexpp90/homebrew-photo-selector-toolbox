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
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Test

class ScanImagesUseCaseTest {

    @Before
    fun setUp() {
        mockkStatic(Uri::class)
        every { Uri.parse(any()) } returns mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        unmockkStatic(Uri::class)
    }

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
        val context = mockk<Context>()

        every { settingsRepository.analysisThreadCount } returns flowOf(2)
        every { aestheticAnalyzer.isAvailable() } returns true

        val images = listOf(
            ImageItem("content://media/external/images/media/1", "img1.jpg", 100L, 1000L, "image/jpeg"),
            ImageItem("content://media/external/images/media/2", "img2.jpg", 200L, 2000L, "image/jpeg")
        )

        val cachedScores = listOf(
            ScoreEntity("content://media/external/images/media/1", 100L, 1000L, 50.0, 10.0, 5.0, 5.0, 8.0, 0L),
            ScoreEntity("content://media/external/images/media/2", 200L, 2000L, 60.0, 10.0, 5.0, 5.0, null, 0L)
        )

        coEvery { scoreDao.getScores(listOf("content://media/external/images/media/1", "content://media/external/images/media/2")) } returns cachedScores
        coEvery { scoreDao.updateAccessTimes(any(), any()) } returns Unit

        val useCase = ScanImagesUseCase(
            sharpnessAnalyzer, noiseAnalyzer, clippingAnalyzer, aestheticAnalyzer,
            scoreDao, settingsRepository, context
        )

        useCase(images, aestheticEnabled = true).toList()

        verify(exactly = 1) { aestheticAnalyzer.isAvailable() }
    }
}
