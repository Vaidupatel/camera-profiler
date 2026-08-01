package com.openprofiler.camera

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.Executor

/**
 * Bound CameraX use cases for one session.
 * Fields may be null in unit-test fakes that only exercise session ownership.
 */
internal data class BoundCameraUseCases(
    val preview: Preview?,
    val imageCapture: ImageCapture?,
    val imageAnalysis: ImageAnalysis?,
)

/**
 * Creates and binds/unbinds CameraX use cases.
 * Separated from session-ownership logic so ownership races can be unit-tested
 * without constructing real CameraX use-case graphs.
 */
internal interface CameraUseCaseBinder {

    fun bind(
        provider: ProcessCameraProvider,
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider,
        lensFacing: Int,
        analyzer: ImageAnalysis.Analyzer?,
        analysisExecutor: Executor,
    ): BoundCameraUseCases

    fun unbindAll(provider: ProcessCameraProvider)
}

/**
 * Production binder that builds Preview / ImageCapture / ImageAnalysis and binds them.
 */
internal class DefaultCameraUseCaseBinder : CameraUseCaseBinder {

    override fun bind(
        provider: ProcessCameraProvider,
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider,
        lensFacing: Int,
        analyzer: ImageAnalysis.Analyzer?,
        analysisExecutor: Executor,
    ): BoundCameraUseCases {
        provider.unbindAll()

        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()

        val preview = Preview.Builder().build().also { it.setSurfaceProvider(surfaceProvider) }

        val imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()

        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .build()
            .also { analysis ->
                if (analyzer != null) {
                    analysis.setAnalyzer(analysisExecutor, analyzer)
                }
            }

        provider.bindToLifecycle(
            lifecycleOwner,
            cameraSelector,
            preview,
            imageCapture,
            imageAnalysis,
        )

        return BoundCameraUseCases(preview, imageCapture, imageAnalysis)
    }

    override fun unbindAll(provider: ProcessCameraProvider) {
        provider.unbindAll()
    }
}
