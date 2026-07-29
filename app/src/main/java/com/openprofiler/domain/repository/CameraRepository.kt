package com.openprofiler.domain.repository

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.lifecycle.LifecycleOwner
import com.openprofiler.camera.CameraState
import kotlinx.coroutines.flow.StateFlow

/**
 * Repository interface for CameraX operations.
 * Manages camera lifecycle, preview binding, lens selection, and frame analysis.
 */
interface CameraRepository {

    /** Observable camera state flow. */
    val cameraState: StateFlow<CameraState>

    /**
     * Binds CameraX preview, capture, and analysis use cases to the provided lifecycle owner.
     *
     * @param lifecycleOwner Android LifecycleOwner to bind camera session lifecycle.
     * @param surfaceProvider SurfaceProvider to render live camera preview.
     */
    fun startCamera(
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider,
    )

    /** Toggles between front and rear camera lenses. */
    fun switchCamera()

    /**
     * Sets the frame analyzer callback for processing live camera frames.
     *
     * @param analyzer CameraX ImageAnalysis.Analyzer implementation.
     */
    fun setFrameAnalyzer(analyzer: ImageAnalysis.Analyzer)

    /**
     * Captures a still image.
     *
     * @param onImageCaptured Callback invoked when image frame is captured successfully.
     * @param onError Callback invoked if image capture fails.
     */
    fun takePhoto(
        onImageCaptured: (ImageProxy) -> Unit,
        onError: (Exception) -> Unit,
    )

    /** Stops and unbinds all active camera use cases. */
    fun stopCamera()
}
