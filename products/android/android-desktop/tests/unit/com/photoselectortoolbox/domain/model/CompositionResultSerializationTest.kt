package com.photoselectortoolbox.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests verifying JSON serialization and deserialization for [CompositionResult] and [DetectedLine].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CompositionResultSerializationTest {

    @Test
    fun `roundtrip serialization preserves all fields accurately`() {
        val lines = listOf(
            DetectedLine(
                startX = 0.1f,
                startY = 0.2f,
                endX = 0.8f,
                endY = 0.9f,
                strength = 0.75f
            ),
            DetectedLine(
                startX = 0.35f,
                startY = 0.5f,
                endX = 0.65f,
                endY = 0.5f,
                strength = 0.42f
            )
        )
        val grid = listOf(
            0.05f, 0.10f, 0.05f,
            0.15f, 0.30f, 0.15f,
            0.05f, 0.10f, 0.05f
        )
        val original = CompositionResult(
            leadingLines = lines,
            massCentroidX = 0.55f,
            massCentroidY = 0.48f,
            gridMassDistribution = grid
        )

        val json = original.toJson()
        assertTrue(json.contains("\"leadingLines\""))
        assertTrue(json.contains("\"massCentroidX\""))
        assertTrue(json.contains("\"massCentroidY\""))
        assertTrue(json.contains("\"gridMassDistribution\""))

        val deserialized = CompositionResult.fromJson(json)

        assertEquals(original.massCentroidX, deserialized.massCentroidX, 1e-4f)
        assertEquals(original.massCentroidY, deserialized.massCentroidY, 1e-4f)
        assertEquals(original.leadingLines.size, deserialized.leadingLines.size)

        for (i in original.leadingLines.indices) {
            val origLine = original.leadingLines[i]
            val desLine = deserialized.leadingLines[i]
            assertEquals(origLine.startX, desLine.startX, 1e-4f)
            assertEquals(origLine.startY, desLine.startY, 1e-4f)
            assertEquals(origLine.endX, desLine.endX, 1e-4f)
            assertEquals(origLine.endY, desLine.endY, 1e-4f)
            assertEquals(origLine.strength, desLine.strength, 1e-4f)
        }

        assertEquals(9, deserialized.gridMassDistribution.size)
        for (i in 0..8) {
            assertEquals(original.gridMassDistribution[i], deserialized.gridMassDistribution[i], 1e-4f)
        }
    }

    @Test
    fun `roundtrip serialization handles empty lines list`() {
        val original = CompositionResult(
            leadingLines = emptyList(),
            massCentroidX = 0.5f,
            massCentroidY = 0.5f,
            gridMassDistribution = List(9) { 1f / 9f }
        )

        val json = original.toJson()
        val deserialized = CompositionResult.fromJson(json)

        assertTrue(deserialized.leadingLines.isEmpty())
        assertEquals(0.5f, deserialized.massCentroidX, 1e-4f)
        assertEquals(0.5f, deserialized.massCentroidY, 1e-4f)
        assertEquals(9, deserialized.gridMassDistribution.size)
    }

    @Test
    fun `fromJson handles minimal or fallback json gracefully`() {
        val minimalJson = "{}"
        val result = CompositionResult.fromJson(minimalJson)

        assertTrue(result.leadingLines.isEmpty())
        assertEquals(0.5f, result.massCentroidX, 1e-4f)
        assertEquals(0.5f, result.massCentroidY, 1e-4f)
        assertEquals(9, result.gridMassDistribution.size)
    }

    // ── Adversarial & Stress Serialization Tests ──────────────────────────────

    @Test
    fun `roundtrip serialization scales effortlessly to 100 plus lines without truncation`() {
        val lineCount = 150
        val lines = (1..lineCount).map { i ->
            DetectedLine(
                startX = (i * 0.005f).coerceIn(0f, 1f),
                startY = (i * 0.006f).coerceIn(0f, 1f),
                endX = (1f - i * 0.005f).coerceIn(0f, 1f),
                endY = (1f - i * 0.006f).coerceIn(0f, 1f),
                strength = (i.toFloat() / lineCount).coerceIn(0f, 1f)
            )
        }
        val grid = List(9) { 1f / 9f }
        val original = CompositionResult(
            leadingLines = lines,
            massCentroidX = 0.42f,
            massCentroidY = 0.58f,
            gridMassDistribution = grid
        )

        val json = original.toJson()
        val deserialized = CompositionResult.fromJson(json)

        assertEquals(lineCount, deserialized.leadingLines.size)
        for (i in 0 until lineCount) {
            assertEquals(original.leadingLines[i].startX, deserialized.leadingLines[i].startX, 1e-4f)
            assertEquals(original.leadingLines[i].startY, deserialized.leadingLines[i].startY, 1e-4f)
            assertEquals(original.leadingLines[i].endX, deserialized.leadingLines[i].endX, 1e-4f)
            assertEquals(original.leadingLines[i].endY, deserialized.leadingLines[i].endY, 1e-4f)
            assertEquals(original.leadingLines[i].strength, deserialized.leadingLines[i].strength, 1e-4f)
        }
    }

    @Test
    fun `fromJson throws JSONException on malformed or empty json strings`() {
        val malformedStrings = listOf(
            "",
            "   ",
            "not a json",
            "{unclosed",
            "[1, 2, 3]" // Top-level is array, not object
        )

        for (badJson in malformedStrings) {
            try {
                CompositionResult.fromJson(badJson)
                org.junit.Assert.fail("Should throw JSONException on malformed json: $badJson")
            } catch (e: org.json.JSONException) {
                // Expected
            }
        }
    }

    @Test
    fun `fromJson handles lenient unquoted json values by falling back to safe defaults`() {
        val lenientJson = "{leadingLines: not_quoted_string}"
        val result = CompositionResult.fromJson(lenientJson)
        assertTrue(result.leadingLines.isEmpty())
        assertEquals(0.5f, result.massCentroidX, 1e-4f)
        assertEquals(0.5f, result.massCentroidY, 1e-4f)
    }

    @Test
    fun `fromJson gracefully defaults missing or null properties`() {
        val partialJson = """
            {
                "massCentroidX": 0.25,
                "gridMassDistribution": [0.1, 0.1, 0.1, 0.1, 0.2, 0.1, 0.1, 0.1, 0.1]
            }
        """.trimIndent()

        val result = CompositionResult.fromJson(partialJson)

        assertTrue(result.leadingLines.isEmpty())
        assertEquals(0.25f, result.massCentroidX, 1e-4f)
        assertEquals(0.5f, result.massCentroidY, 1e-4f) // defaulted to 0.5
        assertEquals(9, result.gridMassDistribution.size)
        assertEquals(0.2f, result.gridMassDistribution[4], 1e-4f)
    }

    @Test
    fun `toJson throws JSONException when given NaN or Infinite floats`() {
        val nanResult = CompositionResult(
            leadingLines = emptyList(),
            massCentroidX = Float.NaN,
            massCentroidY = 0.5f,
            gridMassDistribution = List(9) { 1f / 9f }
        )

        try {
            nanResult.toJson()
            org.junit.Assert.fail("Should throw JSONException for NaN massCentroidX")
        } catch (e: org.json.JSONException) {
            // Expected: JSON does not support NaN or infinite numbers
        }

        val infResult = CompositionResult(
            leadingLines = emptyList(),
            massCentroidX = 0.5f,
            massCentroidY = Float.POSITIVE_INFINITY,
            gridMassDistribution = List(9) { 1f / 9f }
        )

        try {
            infResult.toJson()
            org.junit.Assert.fail("Should throw JSONException for POSITIVE_INFINITY massCentroidY")
        } catch (e: org.json.JSONException) {
            // Expected
        }
    }
}
