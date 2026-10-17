package com.photoselectortoolbox.data.cache

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ScoreEntity::class, UserRatingEntity::class],
    version = 4,
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

        val MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
    }
}
