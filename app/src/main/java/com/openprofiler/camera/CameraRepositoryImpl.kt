package com.openprofiler.camera

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.openprofiler.common.util.Logger
import com.openprofiler.domain.repository.CameraRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "CameraRepository"

/**
 * CameraX-backed implementation of [CameraRepository].
 * Handles ProcessCameraProvider initialization, lifecycle binding, Preview, ImageCapture, and ImageAnalysis.
 */
@Singleton
class CameraRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : CameraRepository {

    private val _cameraState = MutableStateFlow(CameraState())
    override val cameraState: StateFlow<CameraState> = _cameraState.asStateFlow()

    private var cameraProvider: ProcessCameraProvider? = null
    private var previewUseCase: Preview? = null
    private var imageCaptureUseCase: ImageCapture? = null
    private var imageAnalysisUseCase: ImageAnalysis? = null
    private var frameAnalyzer: ImageAnalysis.Analyzer? = null

    private var currentLifecycleOwner: LifecycleOwner? = null
    private var currentSurfaceProvider: Preview.SurfaceProvider? = null

    private val analysisExecutor = Executors.newSingleThreadExecutor()

    override fun startCamera(
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider,
    ) {
        currentLifecycleOwner = lifecycleOwner
        currentSurfaceProvider = surfaceProvider

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases()
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to obtain ProcessCameraProvider", e)
                _cameraState.update { it.copy(error = e.localizedMessage ?: "Camera initialization failed") }
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun bindCameraUseCases() {
        val provider = cameraProvider ?: return
        val lifecycleOwner = currentLifecycleOwner ?: return
        val surfaceProvider = currentSurfaceProvider ?: return

        try {
            provider.unbindAll()

            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(_cameraState.value.lensFacing)
                .build()

            previewUseCase = Preview.Builder().build().also {
                it.setSurfaceProvider(surfaceProvider)
            }

            imageCaptureUseCase = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            imageAnalysisUseCase = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .build()

            frameAnalyzer?.let { analyzer ->
                imageAnalysisUseCase?.setAnalyzer(analysisExecutor, analyzer)
            }

            provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                previewUseCase,
                imageCaptureUseCase,
                imageAnalysisUseCase,
            )

            _cameraState.update {
                it.copy(
                    isStreaming = true,
                    error = null,
                )
            }
            Logger.i(TAG, "Camera use cases bound successfully")
        } catch (e: Exception) {
            Logger.e(TAG, "Use case binding failed", e)
            _cameraState.update { it.copy(error = e.localizedMessage ?: "Binding camera use cases failed") }
        }
    }

    override fun switchCamera() {
        val newLensFacing = if (_cameraState.value.lensFacing == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
        _cameraState.update { it.copy(lensFacing = newLensFacing) }
        bindCameraUseCases()
    }

    override fun setFrameAnalyzer(analyzer: ImageAnalysis.Analyzer) {
        frameAnalyzer = analyzer
        imageAnalysisUseCase?.setAnalyzer(analysisExecutor, analyzer)
    }

    override fun takePhoto(
        onImageCaptured: (ImageProxy) -> Unit,
        onError: (Exception) -> Unit,
    ) {
        val capture = imageCaptureUseCase ?: run {
            onError(IllegalStateException("ImageCapture use case not initialized"))
            return
        }

        capture.takePicture(
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    onImageCaptured(image)
                }

                override fun onError(exception: ImageCaptureException) {
                    Logger.e(TAG, "Image capture failed", exception)
                    onError(exception)
                }
            },
        )
    }

    override fun stopCamera() {
        cameraProvider?.unbindAll()
        _cameraState.update { it.copy(isStreaming = false) }
        Logger.i(TAG, "Camera stopped and unbound")
    }
}
