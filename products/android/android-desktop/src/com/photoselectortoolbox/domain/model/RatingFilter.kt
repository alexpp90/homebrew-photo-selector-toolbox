package com.photoselectortoolbox.domain.model

/**
 * Filter algebra defining criteria for filtering photos by user/metadata star ratings.
 *
 * Ratings:
 * - `0`: Unrated
 * - `1..5`: Star ratings (1 to 5 stars)
 * - `-1`: Rejected
 */
sealed interface RatingFilter {
    val label: String

    fun matches(rating: Int): Boolean

    /** Passes all photographs regardless of rating. */
    data object All : RatingFilter {
        override val label: String get() = "All"
        override fun matches(rating: Int): Boolean = true
    }

    /** Matches photographs with rating >= [minStars]. */
    data class MinRating(val minStars: Int) : RatingFilter {
        init {
            require(minStars in 1..5) { "minStars must be between 1 and 5, got $minStars" }
        }
        override val label: String get() = "★ $minStars+"
        override fun matches(rating: Int): Boolean = rating >= minStars
    }

    /** Matches photographs with exactly [stars] rating. */
    data class ExactRating(val stars: Int) : RatingFilter {
        override val label: String get() = when (stars) {
            -1 -> "Rejected"
            0 -> "Unrated"
            else -> "★ $stars"
        }
        override fun matches(rating: Int): Boolean = rating == stars
    }

    /** Matches unrated photographs (rating == 0). */
    data object Unrated : RatingFilter {
        override val label: String get() = "Unrated"
        override fun matches(rating: Int): Boolean = rating == 0
    }

    /** Matches rejected photographs (rating == -1). */
    data object Rejected : RatingFilter {
        override val label: String get() = "Rejected"
        override fun matches(rating: Int): Boolean = rating == -1
    }

    companion object {
        /** Commonly used filter presets for UI chips and dropdowns. */
        val PRESETS: List<RatingFilter> = listOf(
            All,
            MinRating(3),
            MinRating(4),
            ExactRating(5),
            Unrated,
            Rejected,
        )
    }
}
