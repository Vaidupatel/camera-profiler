package com.openprofiler.di

import com.openprofiler.domain.repository.QualityRepository
import com.openprofiler.quality.QualityRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class QualityModule {

    @Binds
    @Singleton
    abstract fun bindQualityRepository(
        impl: QualityRepositoryImpl
    ): QualityRepository
}
