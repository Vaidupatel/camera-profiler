package com.openprofiler.di

import com.openprofiler.camera.CameraUseCaseBinder
import com.openprofiler.camera.DefaultCameraUseCaseBinder
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Isolated binder module so androidTest can [dagger.hilt.testing.TestInstallIn]-replace
 * it with a counting fake without replacing the rest of [CameraModule].
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class CameraUseCaseBinderModule {

    @Binds
    @Singleton
    abstract fun bindCameraUseCaseBinder(impl: DefaultCameraUseCaseBinder): CameraUseCaseBinder
}
