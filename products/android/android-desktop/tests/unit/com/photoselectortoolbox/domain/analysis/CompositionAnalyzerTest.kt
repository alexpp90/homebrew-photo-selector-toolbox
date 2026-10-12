package com.photoselectortoolbox.domain.analysis

import com.photoselector.core.Requirement
import com.photoselectortoolbox.domain.model.DetectedLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [OpenCvCompositionAnalyzer] line filtering and collinear suppression logic.
 */
@Requirement("REQ-AND-COMPOSITION.01")
class CompositionAnalyzerTest {

    @Test
    fun `filterAndSuppressCollinear suppresses overlapping collinear segments`() {
        val lines = listOf(
            // Strong diagonal line
            DetectedLine(startX = 0.1f, startY = 0.1f, endX = 0.9f, endY = 0.9f, strength = 0.9f),
            // Weaker nearly identical line
            DetectedLine(startX = 0.12f, startY = 0.12f, endX = 0.88f, endY = 0.88f, strength = 0.6f),
            // Independent orthogonal line
            DetectedLine(startX = 0.1f, startY = 0.9f, endX = 0.9f, endY = 0.1f, strength = 0.8f)
        )

        val filtered = OpenCvCompositionAnalyzer.filterAndSuppressCollinear(lines, maxLines = 8)

        // The weaker overlapping line should be suppressed
        assertEquals(2, filtered.size)
        assertEquals(0.9f, filtered[0].strength, 1e-4f)
        assertEquals(0.8f, filtered[1].strength, 1e-4f)
    }

    @Test
    fun `filterAndSuppressCollinear preserves parallel lines with sufficient spatial separation`() {
        val lines = listOf(
            // Top horizontal line
            DetectedLine(startX = 0.1f, startY = 0.2f, endX = 0.9f, endY = 0.2f, strength = 0.8f),
            // Bottom horizontal line (parallel, but far apart)
            DetectedLine(startX = 0.1f, startY = 0.8f, endX = 0.9f, endY = 0.8f, strength = 0.7f)
        )

        val filtered = OpenCvCompositionAnalyzer.filterAndSuppressCollinear(lines, maxLines = 8)

        assertEquals(2, filtered.size)
    }

    @Test
    fun `filterAndSuppressCollinear respects maximum line limit`() {
        val lines = (1..15).map { i ->
            DetectedLine(
                startX = 0.05f * i,
                startY = 0.0f,
                endX = 0.05f * i,
                endY = 1.0f,
                strength = i / 15f
            )
        }

        val filtered = OpenCvCompositionAnalyzer.filterAndSuppressCollinear(lines, maxLines = 8)

        assertTrue(filtered.size <= 8)
        assertEquals(8, filtered.size)
        // Strongest lines are retained first
        assertTrue(filtered.first().strength > filtered.last().strength)
    }

    // ── Adversarial & Stress Collinear Suppression Tests ──────────────────────

    @Test
    fun `filterAndSuppressCollinear stress test with 1000 lines executes swiftly and limits output`() {
        val startTime = System.currentTimeMillis()
        val lines = (1..1000).map { i ->
            DetectedLine(
                startX = (i * 0.001f).coerceIn(0f, 1f),
                startY = (i * 0.001f).coerceIn(0f, 1f),
                endX = (1f - i * 0.001f).coerceIn(0f, 1f),
                endY = (i * 0.0005f).coerceIn(0f, 1f),
                strength = (i % 100) / 100f
            )
        }

        val filtered = OpenCvCompositionAnalyzer.filterAndSuppressCollinear(lines, maxLines = 8)
        val elapsed = System.currentTimeMillis() - startTime

        assertTrue("Execution must be sub-second, took ${elapsed}ms", elapsed < 1000)
        assertEquals(8, filtered.size)
        // Strictly descending strength
        for (i in 0 until filtered.size - 1) {
            assertTrue(
                "Must be sorted by strength descending",
                filtered[i].strength >= filtered[i + 1].strength
            )
        }
    }

    @Test
    fun `filterAndSuppressCollinear suppresses collinear lines with opposite direction vectors`() {
        val lines = listOf(
            // Vector from (0.1, 0.1) -> (0.9, 0.9)
            DetectedLine(startX = 0.1f, startY = 0.1f, endX = 0.9f, endY = 0.9f, strength = 0.95f),
            // Reverse vector from (0.88, 0.88) -> (0.12, 0.12)
            DetectedLine(startX = 0.88f, startY = 0.88f, endX = 0.12f, endY = 0.12f, strength = 0.70f)
        )

        val filtered = OpenCvCompositionAnalyzer.filterAndSuppressCollinear(lines, maxLines = 8)

        // Opposite direction collinear segment must be suppressed
        assertEquals(1, filtered.size)
        assertEquals(0.95f, filtered[0].strength, 1e-4f)
    }

    @Test
    fun `filterAndSuppressCollinear handles zero-length degenerate lines without division by zero`() {
        val lines = listOf(
            // Degenerate point-line (startX == endX, startY == endY)
            DetectedLine(startX = 0.5f, startY = 0.5f, endX = 0.5f, endY = 0.5f, strength = 0.1f),
            // Normal valid line
            DetectedLine(startX = 0.0f, startY = 0.0f, endX = 1.0f, endY = 1.0f, strength = 0.8f)
        )

        val filtered = OpenCvCompositionAnalyzer.filterAndSuppressCollinear(lines, maxLines = 8)

        assertEquals(2, filtered.size)
        assertEquals(0.8f, filtered[0].strength, 1e-4f)
        assertEquals(0.1f, filtered[1].strength, 1e-4f)
    }

    @Test
    fun `filterAndSuppressCollinear correctly evaluates distance tolerance boundaries`() {
        // Line along horizontal Y = 0.5
        val baseLine = DetectedLine(startX = 0.1f, startY = 0.5f, endX = 0.9f, endY = 0.5f, strength = 0.9f)

        // Line parallel at dist = 0.04 (< default distTolerance 0.05) -> must be suppressed
        val insideTol = DetectedLine(startX = 0.1f, startY = 0.54f, endX = 0.9f, endY = 0.54f, strength = 0.8f)

        val filteredSuppressed = OpenCvCompositionAnalyzer.filterAndSuppressCollinear(
            listOf(baseLine, insideTol),
            maxLines = 8,
            distTolerance = 0.05f
        )
        assertEquals(1, filteredSuppressed.size)

        // Line parallel at dist = 0.06 (> default distTolerance 0.05) -> must be retained
        val outsideTol = DetectedLine(startX = 0.1f, startY = 0.56f, endX = 0.9f, endY = 0.56f, strength = 0.8f)

        val filteredRetained = OpenCvCompositionAnalyzer.filterAndSuppressCollinear(
            listOf(baseLine, outsideTol),
            maxLines = 8,
            distTolerance = 0.05f
        )
        assertEquals(2, filteredRetained.size)
    }

    @Test
    fun `filterAndSuppressCollinear handles empty input and zero maxLines`() {
        val emptyResult = OpenCvCompositionAnalyzer.filterAndSuppressCollinear(emptyList())
        assertTrue(emptyResult.isEmpty())

        val lines = listOf(
            DetectedLine(startX = 0.0f, startY = 0.0f, endX = 1.0f, endY = 1.0f, strength = 0.9f)
        )
        val zeroLimit = OpenCvCompositionAnalyzer.filterAndSuppressCollinear(lines, maxLines = 0)
        assertTrue(zeroLimit.isEmpty())
    }

    @Test
    fun `filterAndSuppressCollinear boundary stress test for maxLines edge cases`() {
        val candidates = (1..20).map { i ->
            DetectedLine(
                startX = 0.04f * i,
                startY = 0.0f,
                endX = 0.04f * i,
                endY = 1.0f,
                strength = i / 20f
            )
        }

        // 1. Boundary: maxLines <= 0 (0, -1, Int.MIN_VALUE)
        val nonPositiveBoundaries = listOf(0, -1, Int.MIN_VALUE)
        for (boundary in nonPositiveBoundaries) {
            val resWithCandidates = OpenCvCompositionAnalyzer.filterAndSuppressCollinear(candidates, maxLines = boundary)
            assertEquals("Expected empty list for maxLines=$boundary with non-empty input", 0, resWithCandidates.size)
            assertTrue(resWithCandidates.isEmpty())

            val resWithEmpty = OpenCvCompositionAnalyzer.filterAndSuppressCollinear(emptyList(), maxLines = boundary)
            assertEquals("Expected empty list for maxLines=$boundary with empty input", 0, resWithEmpty.size)
            assertTrue(resWithEmpty.isEmpty())
        }

        // 2. Boundary: maxLines = 1
        val res1 = OpenCvCompositionAnalyzer.filterAndSuppressCollinear(candidates, maxLines = 1)
        assertEquals("Expected exactly 1 line for maxLines=1", 1, res1.size)
        assertEquals("Expected strongest candidate retained", 1.0f, res1.first().strength, 1e-4f)

        // 3. Boundary: maxLines = 8
        val res8 = OpenCvCompositionAnalyzer.filterAndSuppressCollinear(candidates, maxLines = 8)
        assertTrue("Expected size <= 8 for maxLines=8", res8.size <= 8)
        assertEquals(8, res8.size)

        // 4. Positive maxLines with empty candidates
        val resEmpty = OpenCvCompositionAnalyzer.filterAndSuppressCollinear(emptyList(), maxLines = 8)
        assertEquals(0, resEmpty.size)
    }
}

