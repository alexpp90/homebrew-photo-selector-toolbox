package com.photoselectortoolbox.data.repository

import com.photoselectortoolbox.data.cache.ScoreDao
import com.photoselectortoolbox.data.cache.ScoreEntity
import com.photoselectortoolbox.data.cache.UserRatingEntity
import javax.inject.Inject
import javax.inject.Singleton

interface CacheRepository {
    suspend fun getCachedScore(filePath: String): ScoreEntity?
    suspend fun cacheScore(score: ScoreEntity)
    suspend fun clearAll()
    suspend fun pruneToLimit(limit: Int = DEFAULT_CACHE_LIMIT)
    suspend fun updateRating(filePath: String, rating: Int)
    suspend fun getRating(uri: String): Int?
    suspend fun getRatings(uris: List<String>): Map<String, Int>
    suspend fun updateCompositionAnalysis(filePath: String, analysis: String?)
    suspend fun getScoresWithMinRating(minRating: Int): List<ScoreEntity>
    suspend fun getScoresWithRating(rating: Int): List<ScoreEntity>
}

private const val DEFAULT_CACHE_LIMIT = 10_000
private const val SQLITE_BIND_CHUNK_SIZE = 900

@Singleton
class CacheRepositoryImpl @Inject constructor(
    private val scoreDao: ScoreDao
) : CacheRepository {

    override suspend fun getCachedScore(filePath: String): ScoreEntity? {
        val entity = scoreDao.getScore(filePath)
        if (entity != null) {
            scoreDao.updateAccessTime(filePath, System.currentTimeMillis())
        }
        return entity
    }

    override suspend fun cacheScore(score: ScoreEntity) {
        scoreDao.insertOrUpdate(score)
    }

    override suspend fun clearAll() {
        scoreDao.deleteAll()
        scoreDao.deleteAllTechniqueFacts()
    }

    override suspend fun pruneToLimit(limit: Int) {
        val count = scoreDao.getCount()
        if (count > limit) {
            scoreDao.pruneToMru(limit)
        }
    }

    override suspend fun updateRating(filePath: String, rating: Int) {
        scoreDao.upsertUserRating(
            UserRatingEntity(
                uri = filePath,
                rating = rating,
                updatedAt = System.currentTimeMillis()
            )
        )
        scoreDao.updateRating(filePath, rating)
    }

    override suspend fun getRating(uri: String): Int? {
        return scoreDao.getUserRating(uri)?.rating
            ?: scoreDao.getScore(uri)?.rating?.takeIf { it != 0 }
    }

    override suspend fun getRatings(uris: List<String>): Map<String, Int> {
        if (uris.isEmpty()) return emptyMap()
        val result = mutableMapOf<String, Int>()
        uris.chunked(SQLITE_BIND_CHUNK_SIZE).forEach { chunk ->
            scoreDao.getUserRatings(chunk).forEach { entity ->
                result[entity.uri] = entity.rating
            }
        }
        return result
    }

    override suspend fun updateCompositionAnalysis(filePath: String, analysis: String?) {
        scoreDao.updateCompositionAnalysis(filePath, analysis)
    }

    override suspend fun getScoresWithMinRating(minRating: Int): List<ScoreEntity> {
        return scoreDao.getScoresWithMinRating(minRating)
    }

    override suspend fun getScoresWithRating(rating: Int): List<ScoreEntity> {
        return scoreDao.getScoresWithRating(rating)
    }
}
