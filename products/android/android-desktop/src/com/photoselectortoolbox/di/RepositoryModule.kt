package com.photoselectortoolbox.di

import com.photoselectortoolbox.data.repository.CacheRepository
import com.photoselectortoolbox.data.repository.CacheRepositoryImpl
import com.photoselectortoolbox.data.repository.ImageRepository
import com.photoselectortoolbox.data.repository.ImageRepositoryImpl
import com.photoselectortoolbox.data.source.LocalImageSource
import com.photoselectortoolbox.data.source.LocalImageSourceImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindImageRepository(
        impl: ImageRepositoryImpl
    ): ImageRepository

    @Binds
    @Singleton
    abstract fun bindCacheRepository(
        impl: CacheRepositoryImpl
    ): CacheRepository

    @Binds
    @Singleton
    abstract fun bindLocalImageSource(
        impl: LocalImageSourceImpl
    ): LocalImageSource
}
