package com.openprofiler.di

import com.openprofiler.coverage.CoverageRepositoryImpl
import com.openprofiler.domain.repository.CoverageRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt DI module binding [CoverageRepository] to [CoverageRepositoryImpl].
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class CoverageModule {

    @Binds
    @Singleton
    abstract fun bindCoverageRepository(
        impl: CoverageRepositoryImpl
    ): CoverageRepository
}
