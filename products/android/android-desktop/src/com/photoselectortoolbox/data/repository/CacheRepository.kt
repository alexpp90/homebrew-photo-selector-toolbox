package com.photoselectortoolbox.data.repository

import com.photoselectortoolbox.data.cache.ScoreDao
import com.photoselectortoolbox.data.cache.ScoreEntity
import javax.inject.Inject
import javax.inject.Singleton

interface CacheRepository {
    suspend fun getCachedScore(filePath: String): ScoreEntity?
    suspend fun cacheScore(score: ScoreEntity)
    suspend fun clearAll()
    suspend fun pruneToLimit(limit: Int = DEFAULT_CACHE_LIMIT)
    suspend fun updateRating(filePath: String, rating: Int)
    suspend fun updateCompositionAnalysis(filePath: String, analysis: String?)
    suspend fun getScoresWithMinRating(minRating: Int): List<ScoreEntity>
    suspend fun getScoresWithRating(rating: Int): List<ScoreEntity>
}

private const val DEFAULT_CACHE_LIMIT = 10_000

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
    }

    override suspend fun pruneToLimit(limit: Int) {
        val count = scoreDao.getCount()
        if (count > limit) {
            scoreDao.pruneToMru(limit)
        }
    }

    override suspend fun updateRating(filePath: String, rating: Int) {
        scoreDao.updateRating(filePath, rating)
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
