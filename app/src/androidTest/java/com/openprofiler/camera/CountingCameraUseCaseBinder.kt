package com.openprofiler.camera

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Delegates to [DefaultCameraUseCaseBinder] while counting [bind] calls for
 * navigation/rebind instrumented tests.
 */
@Singleton
class CountingCameraUseCaseBinder @Inject constructor() : CameraUseCaseBinder {

    private val delegate = DefaultCameraUseCaseBinder()

    override fun bind(
        provider: ProcessCameraProvider,
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider,
        lensFacing: Int,
        analyzer: ImageAnalysis.Analyzer?,
        analysisExecutor: Executor,
    ): BoundCameraUseCases {
        bindCount.incrementAndGet()
        lastLifecycleOwnerName = lifecycleOwner.javaClass.name
        return delegate.bind(
            provider,
            lifecycleOwner,
            surfaceProvider,
            lensFacing,
            analyzer,
            analysisExecutor,
        )
    }

    override fun unbindAll(provider: ProcessCameraProvider) {
        unbindCount.incrementAndGet()
        delegate.unbindAll(provider)
    }

    companion object {
        val bindCount = AtomicInteger(0)
        val unbindCount = AtomicInteger(0)
        @Volatile
        var lastLifecycleOwnerName: String? = null

        fun reset() {
            bindCount.set(0)
            unbindCount.set(0)
            lastLifecycleOwnerName = null
        }
    }
}
