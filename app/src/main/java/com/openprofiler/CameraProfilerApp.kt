package com.openprofiler

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

/**
 * Application class for Open Camera Profiler.
 * Initializes dependency injection and logging.
 */
@HiltAndroidApp
class CameraProfilerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
