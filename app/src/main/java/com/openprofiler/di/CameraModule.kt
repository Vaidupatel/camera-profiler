package com.openprofiler.di

import com.openprofiler.calibration.CalibrationRepositoryImpl
import com.openprofiler.camera.CameraProviderClient
import com.openprofiler.camera.CameraRepositoryImpl
import com.openprofiler.camera.ProcessCameraProviderClient
import com.openprofiler.domain.repository.CalibrationRepository
import com.openprofiler.domain.repository.CameraRepository
import com.openprofiler.domain.repository.ExportRepository
import com.openprofiler.domain.repository.MetadataRepository
import com.openprofiler.domain.repository.ValidationRepository
import com.openprofiler.export.ExportRepositoryImpl
import com.openprofiler.metadata.MetadataRepositoryImpl
import com.openprofiler.validation.ValidationRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing camera, metadata, and data layer bindings.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class CameraModule {

    @Binds
    @Singleton
    abstract fun bindCameraProviderClient(
        impl: ProcessCameraProviderClient,
    ): CameraProviderClient

    @Binds
    @Singleton
    abstract fun bindCameraRepository(impl: CameraRepositoryImpl): CameraRepository

    @Binds
    @Singleton
    abstract fun bindMetadataRepository(impl: MetadataRepositoryImpl): MetadataRepository

    @Binds
    @Singleton
    abstract fun bindCalibrationRepository(impl: CalibrationRepositoryImpl): CalibrationRepository

    @Binds
    @Singleton
    abstract fun bindValidationRepository(impl: ValidationRepositoryImpl): ValidationRepository

    @Binds
    @Singleton
    abstract fun bindExportRepository(impl: ExportRepositoryImpl): ExportRepository
}
