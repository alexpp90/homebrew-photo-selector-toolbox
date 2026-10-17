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

    // ── Technique Facts (U-22a) ───────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTechniqueFacts(facts: List<TechniqueFactEntity>)

    @Query("SELECT * FROM technique_facts WHERE uri = :uri")
    suspend fun getTechniqueFactsForUri(uri: String): List<TechniqueFactEntity>

    @Query("SELECT * FROM technique_facts WHERE uri IN (:uris)")
    suspend fun getTechniqueFactsForUris(uris: List<String>): List<TechniqueFactEntity>

    @Query("SELECT * FROM technique_facts WHERE kind = :kind AND confidence >= :minConfidence")
    suspend fun getTechniqueFactsByKind(kind: String, minConfidence: Double = 0.0): List<TechniqueFactEntity>

    @Query("DELETE FROM technique_facts WHERE uri = :uri")
    suspend fun deleteTechniqueFactsForUri(uri: String)

    @Query("DELETE FROM technique_facts")
    suspend fun deleteAllTechniqueFacts()

    // ── Lesson Progress (U-26) ────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLessonProgress(progress: LessonProgressEntity)

    @Query("SELECT * FROM lesson_progress WHERE lessonId = :lessonId LIMIT 1")
    suspend fun getLessonProgress(lessonId: String): LessonProgressEntity?

    @Query("SELECT * FROM lesson_progress")
    suspend fun getAllLessonProgress(): List<LessonProgressEntity>

    // ── Practice Goals (U-28) ─────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPracticeGoal(goal: PracticeGoalEntity)

    @Query("SELECT * FROM practice_goals WHERE lessonId = :lessonId LIMIT 1")
    suspend fun getPracticeGoal(lessonId: String): PracticeGoalEntity?

    @Query("SELECT * FROM practice_goals")
    suspend fun getAllPracticeGoals(): List<PracticeGoalEntity>

    @Query("DELETE FROM practice_goals WHERE lessonId = :lessonId")
    suspend fun deletePracticeGoal(lessonId: String)
}
