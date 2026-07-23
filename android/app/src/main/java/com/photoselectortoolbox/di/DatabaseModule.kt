package com.photoselectortoolbox.di

import android.content.Context
import androidx.room.Room
import com.photoselectortoolbox.data.cache.ScoreDao
import com.photoselectortoolbox.data.cache.ScoresDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideScoresDatabase(
        @ApplicationContext context: Context
    ): ScoresDatabase {
        return Room.databaseBuilder(
            context,
            ScoresDatabase::class.java,
            "scores_cache.db"
        )
            .addMigrations(ScoresDatabase.MIGRATION_1_2)
            .build()
    }

    @Provides
    @Singleton
    fun provideScoreDao(database: ScoresDatabase): ScoreDao {
        return database.scoreDao()
    }
}
