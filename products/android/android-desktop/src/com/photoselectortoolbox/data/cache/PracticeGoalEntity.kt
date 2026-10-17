package com.photoselectortoolbox.data.cache

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "practice_goals")
data class PracticeGoalEntity(
    @PrimaryKey
    val lessonId: String,
    val startedAtEpochMs: Long,
    val baselineRate: Double,
    val baselineSampleSize: Int,
)
