package com.photoselectortoolbox.data.cache

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scores_cache")
data class ScoreEntity(
    @PrimaryKey
    val filePath: String,
    val fileSize: Long,
    val lastModified: Long,
    val sharpnessScore: Double? = null,
    val noiseLevel: Double? = null,
    val highlightClipping: Double? = null,
    val shadowClipping: Double? = null,
    /** On-device AI aesthetic score (1.0–10.0), added in DB schema v2. */
    val aestheticScore: Double? = null,
    /** 1–5 star rating, 0 = unrated, -1 = reject. Added in DB schema v3. */
    val rating: Int = 0,
    /** JSON-serialized OpenCV composition analysis. Added in DB schema v3. */
    val compositionAnalysis: String? = null,
    val lastAccessTime: Long = System.currentTimeMillis()
)
