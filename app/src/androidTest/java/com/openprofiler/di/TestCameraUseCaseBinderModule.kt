package com.openprofiler.di

import com.openprofiler.camera.CameraUseCaseBinder
import com.openprofiler.camera.CountingCameraUseCaseBinder
import dagger.Binds
import dagger.Module
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [CameraUseCaseBinderModule::class],
)
abstract class TestCameraUseCaseBinderModule {

    @Binds
    @Singleton
    abstract fun bindCountingCameraUseCaseBinder(
        impl: CountingCameraUseCaseBinder,
    ): CameraUseCaseBinder
}
