package com.photoselectortoolbox.data.cache

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ScoreEntity::class,
        UserRatingEntity::class,
        TechniqueFactEntity::class,
        LessonProgressEntity::class,
        PracticeGoalEntity::class,
    ],
    version = 7,
    exportSchema = true
)
abstract class ScoresDatabase : RoomDatabase() {
    abstract fun scoreDao(): ScoreDao

    companion object {
        /**
         * v1 → v2: add the nullable `aestheticScore` column for the on-device
         * AI aesthetic score. Existing cached rows keep their technical scores;
         * the new column defaults to NULL (recomputed on the next scan).
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE scores_cache ADD COLUMN aestheticScore REAL")
            }
        }

        /**
         * v2 → v3: add `rating` (INTEGER NOT NULL DEFAULT 0) and
         * `compositionAnalysis` (TEXT nullable).
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE scores_cache ADD COLUMN rating INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE scores_cache ADD COLUMN compositionAnalysis TEXT")
            }
        }

        /**
         * v3 → v4: create the dedicated `user_ratings` table so user ratings
         * are stored as durable user data (surviving cache clear / MRU pruning
         * and working on unscanned photos), and migrate existing non-zero
         * ratings from `scores_cache`.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `user_ratings` (`uri` TEXT NOT NULL, `rating` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`uri`))"
                )
                db.execSQL(
                    "INSERT OR REPLACE INTO `user_ratings` (`uri`, `rating`, `updatedAt`) SELECT `filePath`, `rating`, `lastAccessTime` FROM `scores_cache` WHERE `rating` != 0"
                )
            }
        }

        /**
         * v4 → v5: create the `technique_facts` table and indices for cached
         * photography technique facts (`U-22a`).
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `technique_facts` (`uri` TEXT NOT NULL, `kind` TEXT NOT NULL, `value` TEXT NOT NULL, `confidence` REAL NOT NULL, `geometryJson` TEXT, `fileSize` INTEGER NOT NULL, `lastModified` INTEGER NOT NULL, `detectorVersion` INTEGER NOT NULL, PRIMARY KEY(`uri`, `kind`))"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `idx_technique_facts_kind` ON `technique_facts` (`kind`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `idx_technique_facts_cache_key` ON `technique_facts` (`uri`, `fileSize`, `lastModified`, `detectorVersion`)"
                )
            }
        }

        /**
         * v5 → v6: create the `lesson_progress` table (`U-26`).
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `lesson_progress` (`lessonId` TEXT NOT NULL, `completedAt` INTEGER NOT NULL, `spotItCorrect` INTEGER NOT NULL, `spotItTotal` INTEGER NOT NULL, PRIMARY KEY(`lessonId`))"
                )
            }
        }

        /**
         * v6 → v7: create the `practice_goals` table (`U-28`).
         */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `practice_goals` (`lessonId` TEXT NOT NULL, `startedAtEpochMs` INTEGER NOT NULL, `baselineRate` REAL NOT NULL, `baselineSampleSize` INTEGER NOT NULL, PRIMARY KEY(`lessonId`))"
                )
            }
        }

        val MIGRATIONS = arrayOf(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6,
            MIGRATION_6_7,
        )
    }
}
