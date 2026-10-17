package com.photoselectortoolbox.data.cache

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for persisting user star ratings (`1..5`), rejects (`-1`), and
 * explicit unrated resets (`0`) as durable user data rather than evictable
 * scan cache state.
 *
 * Unlike [ScoreEntity] in `scores_cache`, rows in `user_ratings` are never
 * removed by cache clearing (`clearAll`) or MRU pruning (`pruneToMru`), and can
 * be written for photos that have never been scanned.
 */
@Entity(tableName = "user_ratings")
data class UserRatingEntity(
    @PrimaryKey val uri: String,
    val rating: Int,
    val updatedAt: Long = System.currentTimeMillis()
)
