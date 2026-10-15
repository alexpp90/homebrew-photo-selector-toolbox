package com.photoselectortoolbox.domain.analysis

import org.junit.Assert.assertEquals
import org.junit.Test
import java.lang.reflect.Method

/**
 * Unit tests for NoiseAnalyzer pure math calculations (computeMAD and medianOfSorted).
 * The full OpenCV bitmap processing pipeline is exercised on-device or via instrumented tests;
 * here we verify the statistical MAD computation logic.
 */
class NoiseAnalyzerTest {

    private val analyzer = NoiseAnalyzer()

    private fun computeMAD(values: DoubleArray): Double {
        val method: Method = NoiseAnalyzer::class.java.getDeclaredMethod("computeMAD", DoubleArray::class.java)
        method.isAccessible = true
        return method.invoke(analyzer, values) as Double
    }

    private fun medianOfSorted(sorted: DoubleArray): Double {
        val method: Method = NoiseAnalyzer::class.java.getDeclaredMethod("medianOfSorted", DoubleArray::class.java)
        method.isAccessible = true
        return method.invoke(analyzer, sorted) as Double
    }

    @Test
    fun `empty array returns zero MAD`() {
        assertEquals(0.0, computeMAD(DoubleArray(0)), 1e-6)
    }

    @Test
    fun `zero deviation array produces zero MAD`() {
        val values = doubleArrayOf(5.0, 5.0, 5.0, 5.0, 5.0)
        assertEquals(0.0, computeMAD(values), 1e-6)
    }

    @Test
    fun `known values produce correct MAD noise estimate`() {
        // Values: -2, -1, 0, 1, 2 -> median = 0
        // Abs deviations: 2, 1, 0, 1, 2
        // Sorted abs deviations: 0, 1, 1, 2, 2 -> median abs deviation = 1.0
        // MAD noise estimate = 1.0 / 0.6745 = 1.4825796886582654
        val values = doubleArrayOf(-2.0, -1.0, 0.0, 1.0, 2.0)
        val expected = 1.0 / 0.6745
        assertEquals(expected, computeMAD(values), 1e-6)
    }

    @Test
    fun `medianOfSorted computes correct median for odd length`() {
        val sorted = doubleArrayOf(1.0, 3.0, 5.0)
        assertEquals(3.0, medianOfSorted(sorted), 1e-6)
    }

    @Test
    fun `medianOfSorted computes correct median for even length`() {
        val sorted = doubleArrayOf(1.0, 2.0, 4.0, 5.0)
        assertEquals(3.0, medianOfSorted(sorted), 1e-6)
    }

    @Test
    fun `unsorted array in computeMAD is sorted and analyzed correctly`() {
        val unsortedValues = doubleArrayOf(2.0, -2.0, 0.0, 1.0, -1.0)
        val expected = 1.0 / 0.6745
        assertEquals(expected, computeMAD(unsortedValues), 1e-6)
    }
}
