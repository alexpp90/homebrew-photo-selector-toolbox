package com.phototok.di

import com.phototok.data.repository.ImageRepository
import com.phototok.data.repository.ImageRepositoryImpl
import com.phototok.data.source.LocalImageSource
import com.phototok.data.source.LocalImageSourceImpl
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
    abstract fun bindLocalImageSource(
        impl: LocalImageSourceImpl
    ): LocalImageSource
}
