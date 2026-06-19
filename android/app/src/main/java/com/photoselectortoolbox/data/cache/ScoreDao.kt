package com.photoselectortoolbox.data.cache

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ScoreDao {

    @Query("SELECT * FROM scores_cache WHERE filePath = :filePath LIMIT 1")
    suspend fun getScore(filePath: String): ScoreEntity?

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

    @Query("SELECT COUNT(*) FROM scores_cache")
    suspend fun getCount(): Int
}
