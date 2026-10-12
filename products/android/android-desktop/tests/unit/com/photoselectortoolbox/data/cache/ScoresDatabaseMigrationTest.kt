package com.photoselectortoolbox.data.cache

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ScoresDatabaseMigrationTest {

    @Test
    fun `migration 2 to 3 adds rating and compositionAnalysis columns with defaults`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "migration_test_2_3.db"
        context.deleteDatabase(dbName)

        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(2) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `scores_cache` (
                            `filePath` TEXT NOT NULL,
                            `fileSize` INTEGER NOT NULL,
                            `lastModified` INTEGER NOT NULL,
                            `sharpnessScore` REAL,
                            `noiseLevel` REAL,
                            `highlightClipping` REAL,
                            `shadowClipping` REAL,
                            `aestheticScore` REAL,
                            `lastAccessTime` INTEGER NOT NULL,
                            PRIMARY KEY(`filePath`)
                        )
                        """.trimIndent()
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        val db = helper.writableDatabase

        // Insert row into v2 database
        db.execSQL(
            """
            INSERT INTO scores_cache (filePath, fileSize, lastModified, sharpnessScore, noiseLevel, aestheticScore, lastAccessTime)
            VALUES ('content://photo/1', 1024, 2000, 42.0, 1.5, 7.8, 1000)
            """.trimIndent()
        )

        // Execute MIGRATION_2_3
        ScoresDatabase.MIGRATION_2_3.migrate(db)

        // Query the row after migration
        val cursor = db.query("SELECT filePath, sharpnessScore, aestheticScore, rating, compositionAnalysis FROM scores_cache WHERE filePath = 'content://photo/1'")
        assertTrue(cursor.moveToFirst())

        assertEquals("content://photo/1", cursor.getString(0))
        assertEquals(42.0, cursor.getDouble(1), 1e-6)
        assertEquals(7.8, cursor.getDouble(2), 1e-6)
        // rating must default to 0
        assertEquals(0, cursor.getInt(3))
        // compositionAnalysis must be null
        assertTrue(cursor.isNull(4))
        cursor.close()

        // Test updating new columns
        db.execSQL("UPDATE scores_cache SET rating = 4, compositionAnalysis = '{\"lines\":[]}' WHERE filePath = 'content://photo/1'")
        val updatedCursor = db.query("SELECT rating, compositionAnalysis FROM scores_cache WHERE filePath = 'content://photo/1'")
        assertTrue(updatedCursor.moveToFirst())
        assertEquals(4, updatedCursor.getInt(0))
        assertEquals("{\"lines\":[]}", updatedCursor.getString(1))
        updatedCursor.close()

        db.close()
    }

    @Test
    fun `migration 2 to 3 handles empty database with zero rows`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "migration_test_empty.db"
        context.deleteDatabase(dbName)

        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(2) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `scores_cache` (
                            `filePath` TEXT NOT NULL,
                            `fileSize` INTEGER NOT NULL,
                            `lastModified` INTEGER NOT NULL,
                            `sharpnessScore` REAL,
                            `noiseLevel` REAL,
                            `highlightClipping` REAL,
                            `shadowClipping` REAL,
                            `aestheticScore` REAL,
                            `lastAccessTime` INTEGER NOT NULL,
                            PRIMARY KEY(`filePath`)
                        )
                        """.trimIndent()
                    )
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        val db = helper.writableDatabase

        // Execute migration on empty database
        ScoresDatabase.MIGRATION_2_3.migrate(db)

        val cursor = db.query("SELECT count(*) FROM scores_cache")
        assertTrue(cursor.moveToFirst())
        assertEquals(0, cursor.getInt(0))
        cursor.close()

        // Insert row after migration
        db.execSQL(
            """
            INSERT INTO scores_cache (filePath, fileSize, lastModified, rating, lastAccessTime)
            VALUES ('content://photo/empty_test', 500, 1000, 3, 2000)
            """.trimIndent()
        )
        val checkCursor = db.query("SELECT filePath, rating, compositionAnalysis FROM scores_cache")
        assertTrue(checkCursor.moveToFirst())
        assertEquals("content://photo/empty_test", checkCursor.getString(0))
        assertEquals(3, checkCursor.getInt(1))
        assertTrue(checkCursor.isNull(2))
        checkCursor.close()

        db.close()
    }

    @Test
    fun `migration 2 to 3 preserves null scores, existing aestheticScore values, and mixed rows`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "migration_test_nulls_and_existing.db"
        context.deleteDatabase(dbName)

        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(2) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `scores_cache` (
                            `filePath` TEXT NOT NULL,
                            `fileSize` INTEGER NOT NULL,
                            `lastModified` INTEGER NOT NULL,
                            `sharpnessScore` REAL,
                            `noiseLevel` REAL,
                            `highlightClipping` REAL,
                            `shadowClipping` REAL,
                            `aestheticScore` REAL,
                            `lastAccessTime` INTEGER NOT NULL,
                            PRIMARY KEY(`filePath`)
                        )
                        """.trimIndent()
                    )
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        val db = helper.writableDatabase

        // Row 1: All scores null (e.g. freshly indexed or uncomputed)
        db.execSQL(
            """
            INSERT INTO scores_cache (filePath, fileSize, lastModified, sharpnessScore, noiseLevel, highlightClipping, shadowClipping, aestheticScore, lastAccessTime)
            VALUES ('photo_all_null', 100, 200, NULL, NULL, NULL, NULL, NULL, 500)
            """.trimIndent()
        )

        // Row 2: Only aestheticScore set, other scores null
        db.execSQL(
            """
            INSERT INTO scores_cache (filePath, fileSize, lastModified, sharpnessScore, noiseLevel, highlightClipping, shadowClipping, aestheticScore, lastAccessTime)
            VALUES ('photo_aesthetic_only', 100, 200, NULL, NULL, NULL, NULL, 9.4, 600)
            """.trimIndent()
        )

        // Row 3: All technical scores set, aestheticScore null (legacy v1 row migrated to v2)
        db.execSQL(
            """
            INSERT INTO scores_cache (filePath, fileSize, lastModified, sharpnessScore, noiseLevel, highlightClipping, shadowClipping, aestheticScore, lastAccessTime)
            VALUES ('photo_tech_only', 100, 200, 55.2, 0.8, 0.05, 0.02, NULL, 700)
            """.trimIndent()
        )

        // Row 4: Full data (both technical and aesthetic scores present)
        db.execSQL(
            """
            INSERT INTO scores_cache (filePath, fileSize, lastModified, sharpnessScore, noiseLevel, highlightClipping, shadowClipping, aestheticScore, lastAccessTime)
            VALUES ('photo_full', 100, 200, 88.0, 0.1, 0.01, 0.0, 8.9, 800)
            """.trimIndent()
        )

        // Execute MIGRATION_2_3
        ScoresDatabase.MIGRATION_2_3.migrate(db)

        // Verify Row 1: All scores still null, rating = 0, compositionAnalysis is null
        val c1 = db.query("SELECT sharpnessScore, noiseLevel, highlightClipping, shadowClipping, aestheticScore, rating, compositionAnalysis FROM scores_cache WHERE filePath = 'photo_all_null'")
        assertTrue(c1.moveToFirst())
        assertTrue(c1.isNull(0))
        assertTrue(c1.isNull(1))
        assertTrue(c1.isNull(2))
        assertTrue(c1.isNull(3))
        assertTrue(c1.isNull(4))
        assertEquals(0, c1.getInt(5))
        assertTrue(c1.isNull(6))
        c1.close()

        // Verify Row 2: aestheticScore preserved at 9.4, others null, rating = 0
        val c2 = db.query("SELECT sharpnessScore, aestheticScore, rating, compositionAnalysis FROM scores_cache WHERE filePath = 'photo_aesthetic_only'")
        assertTrue(c2.moveToFirst())
        assertTrue(c2.isNull(0))
        assertEquals(9.4, c2.getDouble(1), 1e-6)
        assertEquals(0, c2.getInt(2))
        assertTrue(c2.isNull(3))
        c2.close()

        // Verify Row 3: technical scores preserved, aestheticScore still null, rating = 0
        val c3 = db.query("SELECT sharpnessScore, noiseLevel, aestheticScore, rating FROM scores_cache WHERE filePath = 'photo_tech_only'")
        assertTrue(c3.moveToFirst())
        assertEquals(55.2, c3.getDouble(0), 1e-6)
        assertEquals(0.8, c3.getDouble(1), 1e-6)
        assertTrue(c3.isNull(2))
        assertEquals(0, c3.getInt(3))
        c3.close()

        // Verify Row 4: Full scores preserved intact
        val c4 = db.query("SELECT sharpnessScore, aestheticScore, rating FROM scores_cache WHERE filePath = 'photo_full'")
        assertTrue(c4.moveToFirst())
        assertEquals(88.0, c4.getDouble(0), 1e-6)
        assertEquals(8.9, c4.getDouble(1), 1e-6)
        assertEquals(0, c4.getInt(2))
        c4.close()

        db.close()
    }

    @Test
    fun `migration 2 to 3 opens cleanly with Room and verifies DAO rating queries`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "migration_test_room_dao.db"
        context.deleteDatabase(dbName)

        // 1. Create a schema v2 database using SupportSQLiteOpenHelper with Room master table
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(2) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `scores_cache` (
                            `filePath` TEXT NOT NULL,
                            `fileSize` INTEGER NOT NULL,
                            `lastModified` INTEGER NOT NULL,
                            `sharpnessScore` REAL,
                            `noiseLevel` REAL,
                            `highlightClipping` REAL,
                            `shadowClipping` REAL,
                            `aestheticScore` REAL,
                            `lastAccessTime` INTEGER NOT NULL,
                            PRIMARY KEY(`filePath`)
                        )
                        """.trimIndent()
                    )
                    db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
                    db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'f43c7924b06441a304ed9920fa73c02a')")
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        val rawDb = helper.writableDatabase

        // Seed 4 initial rows into v2 table
        rawDb.execSQL("INSERT INTO scores_cache (filePath, fileSize, lastModified, aestheticScore, lastAccessTime) VALUES ('photo_r1', 100, 200, 7.5, 1000)")
        rawDb.execSQL("INSERT INTO scores_cache (filePath, fileSize, lastModified, aestheticScore, lastAccessTime) VALUES ('photo_r2', 100, 200, 8.0, 1001)")
        rawDb.execSQL("INSERT INTO scores_cache (filePath, fileSize, lastModified, aestheticScore, lastAccessTime) VALUES ('photo_r3', 100, 200, NULL, 1002)")
        rawDb.execSQL("INSERT INTO scores_cache (filePath, fileSize, lastModified, aestheticScore, lastAccessTime) VALUES ('photo_r4', 100, 200, 9.1, 1003)")
        rawDb.close()

        // 2. Open via Room with MIGRATION_2_3 — this validates the full Room schema integrity!
        val roomDb = Room.databaseBuilder(context, ScoresDatabase::class.java, dbName)
            .addMigrations(ScoresDatabase.MIGRATION_2_3)
            .allowMainThreadQueries()
            .build()

        val dao = roomDb.scoreDao()

        // Verify all 4 rows exist and have rating = 0 (unrated default)
        assertEquals(4, dao.getCount())
        val r1 = dao.getScore("photo_r1")
        assertNotNull(r1)
        assertEquals(7.5, r1!!.aestheticScore!!, 1e-6)
        assertEquals(0, r1.rating)
        assertNull(r1.compositionAnalysis)

        val r3 = dao.getScore("photo_r3")
        assertNotNull(r3)
        assertNull(r3!!.aestheticScore)
        assertEquals(0, r3.rating)

        // 3. Test updateRating: set distinct ratings (-1 = reject, 0 = unrated, 3 = 3-star, 5 = 5-star)
        dao.updateRating("photo_r1", -1) // reject
        dao.updateRating("photo_r2", 3)  // 3 stars
        dao.updateRating("photo_r3", 5)  // 5 stars
        // photo_r4 stays at 0 (unrated)

        assertEquals(-1, dao.getScore("photo_r1")!!.rating)
        assertEquals(3, dao.getScore("photo_r2")!!.rating)
        assertEquals(5, dao.getScore("photo_r3")!!.rating)
        assertEquals(0, dao.getScore("photo_r4")!!.rating)

        // 4. Test updateRating on non-existent row does not crash
        dao.updateRating("non_existent_photo", 4)
        assertNull(dao.getScore("non_existent_photo"))

        // 5. Test updateCompositionAnalysis
        val compJson = """{"leadingLines":[],"subjectPlacement":"rule_of_thirds"}"""
        dao.updateCompositionAnalysis("photo_r3", compJson)
        assertEquals(compJson, dao.getScore("photo_r3")!!.compositionAnalysis)

        // 6. Test getScoresWithMinRating:
        // rating >= 3 should return photo_r2 (rating 3) and photo_r3 (rating 5)
        val min3 = dao.getScoresWithMinRating(3)
        assertEquals(2, min3.size)
        assertTrue(min3.any { it.filePath == "photo_r2" })
        assertTrue(min3.any { it.filePath == "photo_r3" })
        assertFalse(min3.any { it.filePath == "photo_r1" }) // -1
        assertFalse(min3.any { it.filePath == "photo_r4" }) // 0

        // rating >= 5 should return only photo_r3
        val min5 = dao.getScoresWithMinRating(5)
        assertEquals(1, min5.size)
        assertEquals("photo_r3", min5[0].filePath)

        // rating >= 0 should return photo_r2 (3), photo_r3 (5), photo_r4 (0) — excludes reject (-1)
        val min0 = dao.getScoresWithMinRating(0)
        assertEquals(3, min0.size)
        assertFalse(min0.any { it.filePath == "photo_r1" })

        // rating >= -1 should return all 4 photos
        val minMinus1 = dao.getScoresWithMinRating(-1)
        assertEquals(4, minMinus1.size)

        // rating >= 6 should return empty list
        val min6 = dao.getScoresWithMinRating(6)
        assertEquals(0, min6.size)

        // 7. Test getScoresWithRating (exact match):
        // rating = -1 (rejects)
        val rejects = dao.getScoresWithRating(-1)
        assertEquals(1, rejects.size)
        assertEquals("photo_r1", rejects[0].filePath)

        // rating = 0 (unrated)
        val unrated = dao.getScoresWithRating(0)
        assertEquals(1, unrated.size)
        assertEquals("photo_r4", unrated[0].filePath)

        // rating = 3 (3-star)
        val threeStars = dao.getScoresWithRating(3)
        assertEquals(1, threeStars.size)
        assertEquals("photo_r2", threeStars[0].filePath)

        // rating = 4 (no matches)
        val fourStars = dao.getScoresWithRating(4)
        assertEquals(0, fourStars.size)

        roomDb.close()
    }

    @Test
    fun `migration 1 to 3 executes both migrations sequentially and validates Room integrity`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "migration_test_1_to_3.db"
        context.deleteDatabase(dbName)

        // Create schema v1 database
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `scores_cache` (
                            `filePath` TEXT NOT NULL,
                            `fileSize` INTEGER NOT NULL,
                            `lastModified` INTEGER NOT NULL,
                            `sharpnessScore` REAL,
                            `noiseLevel` REAL,
                            `highlightClipping` REAL,
                            `shadowClipping` REAL,
                            `lastAccessTime` INTEGER NOT NULL,
                            PRIMARY KEY(`filePath`)
                        )
                        """.trimIndent()
                    )
                    db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
                    db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'cd6091cfecc83b34aff9776fc1c647b8')")
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        val rawDb = helper.writableDatabase

        // Seed legacy v1 row (has no aestheticScore, no rating, no compositionAnalysis)
        rawDb.execSQL("INSERT INTO scores_cache (filePath, fileSize, lastModified, sharpnessScore, noiseLevel, lastAccessTime) VALUES ('v1_legacy_photo', 1024, 2048, 65.0, 1.2, 5000)")
        rawDb.close()

        // Open directly with v3 Room DB using arrayOf(MIGRATION_1_2, MIGRATION_2_3)
        val roomDb = Room.databaseBuilder(context, ScoresDatabase::class.java, dbName)
            .addMigrations(*ScoresDatabase.MIGRATIONS)
            .allowMainThreadQueries()
            .build()

        val dao = roomDb.scoreDao()

        // Legacy row should have aestheticScore = null, rating = 0, compositionAnalysis = null
        val legacy = dao.getScore("v1_legacy_photo")
        assertNotNull(legacy)
        assertEquals(65.0, legacy!!.sharpnessScore!!, 1e-6)
        assertNull(legacy.aestheticScore)
        assertEquals(0, legacy.rating)
        assertNull(legacy.compositionAnalysis)

        // Rating update on legacy row
        dao.updateRating("v1_legacy_photo", 4)
        val updatedLegacy = dao.getScore("v1_legacy_photo")
        assertEquals(4, updatedLegacy!!.rating)

        // Insert new v3 entity
        dao.insertOrUpdate(
            ScoreEntity(
                filePath = "v3_new_photo",
                fileSize = 2048,
                lastModified = 4096,
                sharpnessScore = 70.0,
                aestheticScore = 9.2,
                rating = 5,
                compositionAnalysis = "{\"score\":0.9}"
            )
        )

        assertEquals(2, dao.getCount())
        val min4 = dao.getScoresWithMinRating(4)
        assertEquals(2, min4.size)

        // Pruning works seamlessly on migrated DB
        dao.pruneToMru(1)
        assertEquals(1, dao.getCount())

        roomDb.close()
    }
}

