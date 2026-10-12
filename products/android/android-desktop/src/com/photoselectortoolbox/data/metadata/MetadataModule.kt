package com.photoselectortoolbox.data.metadata

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class MetadataModule {

    @Binds
    @Singleton
    abstract fun bindMetadataWriter(
        impl: MetadataWriterImpl
    ): MetadataWriter
}
