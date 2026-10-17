package com.photoselectortoolbox.data.cache

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ScoreDao {
    @Query("SELECT * FROM scores_cache WHERE filePath = :filePath LIMIT 1")
    suspend fun getScore(filePath: String): ScoreEntity?

    @Query("SELECT * FROM scores_cache WHERE filePath IN (:filePaths)")
    suspend fun getScores(filePaths: List<String>): List<ScoreEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(score: ScoreEntity)

    @Query("DELETE FROM scores_cache")
    suspend fun deleteAll()

    @Query(
        """
        DELETE FROM scores_cache WHERE filePath NOT IN (
            SELECT filePath FROM scores_cache ORDER BY lastAccessTime DESC LIMIT :limit
        )
        """
    )
    suspend fun pruneToMru(limit: Int)
    @Query("UPDATE scores_cache SET lastAccessTime = :time WHERE filePath = :filePath")
    suspend fun updateAccessTime(filePath: String, time: Long)

    @Query("UPDATE scores_cache SET lastAccessTime = :time WHERE filePath IN (:filePaths)")
    suspend fun updateAccessTimes(filePaths: List<String>, time: Long)

    @Query("UPDATE scores_cache SET rating = :rating WHERE filePath = :filePath")
    suspend fun updateRating(filePath: String, rating: Int)

    @Query("UPDATE scores_cache SET compositionAnalysis = :analysis WHERE filePath = :filePath")
    suspend fun updateCompositionAnalysis(filePath: String, analysis: String?)

    @Query("SELECT * FROM scores_cache WHERE rating >= :minRating")
    suspend fun getScoresWithMinRating(minRating: Int): List<ScoreEntity>

    @Query("SELECT * FROM scores_cache WHERE rating = :rating")
    suspend fun getScoresWithRating(rating: Int): List<ScoreEntity>

    @Query("SELECT COUNT(*) FROM scores_cache")
    suspend fun getCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertUserRating(entity: UserRatingEntity)

    @Query("SELECT * FROM user_ratings WHERE uri = :uri LIMIT 1")
    suspend fun getUserRating(uri: String): UserRatingEntity?

    @Query("SELECT * FROM user_ratings WHERE uri IN (:uris)")
    suspend fun getUserRatings(uris: List<String>): List<UserRatingEntity>

    @Query("SELECT * FROM user_ratings WHERE rating >= :minRating")
    suspend fun getUserRatingsWithMinRating(minRating: Int): List<UserRatingEntity>

    @Query("SELECT * FROM user_ratings WHERE rating = :rating")
    suspend fun getUserRatingsWithRating(rating: Int): List<UserRatingEntity>

    /**
     * Chunked wrapper for [getScores] so no single SQLite `IN (:filePaths)`
     * statement binds more than [chunkSize] variables (`SQLITE_MAX_VARIABLE_NUMBER = 999`
     * on older Android builds).
     */
    suspend fun getScoresChunked(
        filePaths: List<String>,
        chunkSize: Int = SQLITE_MAX_BIND_PARAMS,
    ): List<ScoreEntity> {
        if (filePaths.isEmpty()) return emptyList()
        return filePaths.chunked(chunkSize).flatMap { chunk -> getScores(chunk) }
    }

    /**
     * Chunked wrapper for [updateAccessTimes] so no single `IN (:filePaths)`
     * update binds more than [chunkSize] variables (`time` takes 1 variable).
     */
    suspend fun updateAccessTimesChunked(
        filePaths: List<String>,
        time: Long,
        chunkSize: Int = SQLITE_MAX_BIND_PARAMS,
    ) {
        if (filePaths.isEmpty()) return
        filePaths.chunked(chunkSize).forEach { chunk ->
            updateAccessTimes(chunk, time)
        }
    }

    /**
     * Chunked wrapper for [getUserRatings] so no single `IN (:uris)` query
     * binds more than [chunkSize] variables.
     */
    suspend fun getUserRatingsChunked(
        uris: List<String>,
        chunkSize: Int = SQLITE_MAX_BIND_PARAMS,
    ): List<UserRatingEntity> {
        if (uris.isEmpty()) return emptyList()
        return uris.chunked(chunkSize).flatMap { chunk -> getUserRatings(chunk) }
    }

    companion object {
        const val SQLITE_MAX_BIND_PARAMS = 900
    }
}
