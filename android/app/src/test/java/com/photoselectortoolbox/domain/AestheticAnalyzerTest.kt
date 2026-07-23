package com.photoselectortoolbox.domain

import android.content.Context
import com.photoselectortoolbox.domain.analysis.AestheticAnalyzer
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the pure NIMA distribution → score reduction. The TFLite model
 * path is exercised on-device; here we only verify the deterministic math,
 * which does not touch the Android context.
 */
class AestheticAnalyzerTest {

    private fun analyzer(): AestheticAnalyzer = AestheticAnalyzer(mockk<Context>(relaxed = true))

    @Test
    fun `distribution peaked at 10 scores 10`() {
        val probs = FloatArray(10) { if (it == 9) 1.0f else 0.0f }
        val score = analyzer().distributionToScore(probs)
        assertEquals(10.0, score!!, 1e-6)
    }

    @Test
    fun `uniform distribution scores mid-scale`() {
        val probs = FloatArray(10) { 0.1f }
        val score = analyzer().distributionToScore(probs)
        assertEquals(5.5, score!!, 1e-6)
    }

    @Test
    fun `unnormalised weights still reduce to expected value`() {
        val probs = FloatArray(10) { if (it == 9) 2.0f else 0.0f }
        assertEquals(10.0, analyzer().distributionToScore(probs)!!, 1e-6)
    }

    @Test
    fun `empty or degenerate distributions return null`() {
        assertNull(analyzer().distributionToScore(FloatArray(0)))
        assertNull(analyzer().distributionToScore(FloatArray(10) { 0.0f }))
    }

    @Test
    fun `score is clamped into 1 to 10`() {
        val probs = FloatArray(10) { if (it == 0) 1.0f else 0.0f }
        val score = analyzer().distributionToScore(probs)!!
        assertTrue(score in 1.0..10.0)
    }
}
