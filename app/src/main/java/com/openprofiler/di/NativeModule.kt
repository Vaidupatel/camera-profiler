package com.openprofiler.di

import com.openprofiler.native_bridge.NativeBridge
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing native/JNI bindings.
 */
@Module
@InstallIn(SingletonComponent::class)
object NativeModule {

    @Provides
    @Singleton
    fun provideNativeBridge(): NativeBridge = NativeBridge()
}
