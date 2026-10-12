package com.photoselectortoolbox.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RatingFilterTest {

    @Test
    fun testAllMatchesAnyRating() {
        val filter = RatingFilter.All
        assertEquals("All", filter.label)
        assertTrue(filter.matches(-1))
        assertTrue(filter.matches(0))
        assertTrue(filter.matches(1))
        assertTrue(filter.matches(3))
        assertTrue(filter.matches(5))
        assertTrue(filter.matches(10))
    }

    @Test
    fun testMinRatingMatchesEqualOrGreater() {
        val filter = RatingFilter.MinRating(3)
        assertEquals("★ 3+", filter.label)
        assertFalse(filter.matches(-1))
        assertFalse(filter.matches(0))
        assertFalse(filter.matches(1))
        assertFalse(filter.matches(2))
        assertTrue(filter.matches(3))
        assertTrue(filter.matches(4))
        assertTrue(filter.matches(5))
    }

    @Test
    fun testMinRatingBoundaries() {
        val filter1 = RatingFilter.MinRating(1)
        assertEquals("★ 1+", filter1.label)
        assertFalse(filter1.matches(0))
        assertFalse(filter1.matches(-1))
        assertTrue(filter1.matches(1))
        assertTrue(filter1.matches(5))

        val filter5 = RatingFilter.MinRating(5)
        assertEquals("★ 5+", filter5.label)
        assertFalse(filter5.matches(4))
        assertTrue(filter5.matches(5))
        assertTrue(filter5.matches(6))
    }

    @Test(expected = IllegalArgumentException::class)
    fun testMinRatingInvalidBelowOne() {
        RatingFilter.MinRating(0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testMinRatingInvalidAboveFive() {
        RatingFilter.MinRating(6)
    }

    @Test
    fun testExactRatingMatchesExactOnly() {
        val filter = RatingFilter.ExactRating(4)
        assertEquals("★ 4", filter.label)
        assertFalse(filter.matches(-1))
        assertFalse(filter.matches(0))
        assertFalse(filter.matches(3))
        assertTrue(filter.matches(4))
        assertFalse(filter.matches(5))
    }

    @Test
    fun testExactRatingSpecialCases() {
        val filter0 = RatingFilter.ExactRating(0)
        assertEquals("Unrated", filter0.label)
        assertTrue(filter0.matches(0))
        assertFalse(filter0.matches(1))

        val filterReject = RatingFilter.ExactRating(-1)
        assertEquals("Rejected", filterReject.label)
        assertTrue(filterReject.matches(-1))
        assertFalse(filterReject.matches(0))
    }

    @Test
    fun testUnratedMatchesOnlyZero() {
        val filter = RatingFilter.Unrated
        assertEquals("Unrated", filter.label)
        assertTrue(filter.matches(0))
        assertFalse(filter.matches(-1))
        assertFalse(filter.matches(1))
        assertFalse(filter.matches(5))
    }

    @Test
    fun testRejectedMatchesOnlyMinusOne() {
        val filter = RatingFilter.Rejected
        assertEquals("Rejected", filter.label)
        assertTrue(filter.matches(-1))
        assertFalse(filter.matches(0))
        assertFalse(filter.matches(1))
        assertFalse(filter.matches(5))
    }

    @Test
    fun testPresetsContainsStandardFilters() {
        val presets = RatingFilter.PRESETS
        assertTrue(presets.contains(RatingFilter.All))
        assertTrue(presets.contains(RatingFilter.Unrated))
        assertTrue(presets.contains(RatingFilter.MinRating(3)))
        assertTrue(presets.contains(RatingFilter.MinRating(4)))
        assertTrue(presets.contains(RatingFilter.ExactRating(5)))
        assertTrue(presets.contains(RatingFilter.Rejected))
    }
}
