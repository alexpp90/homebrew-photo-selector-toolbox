package com.photoselectortoolbox.data.cache

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ScoreEntity::class],
    version = 2,
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
    }
}
