package com.photoselectortoolbox.data.cache

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "lesson_progress")
data class LessonProgressEntity(
    @PrimaryKey
    val lessonId: String,
    val completedAt: Long,
    val spotItCorrect: Int,
    val spotItTotal: Int,
)
