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
 *
 * Exactly one camera session may be active at a time. Ownership is explicit via
 * [CameraSessionToken]: [startCamera] issues a token to the caller; [stopCamera]
 * only unbinds when that token is still the current owner. Stale tokens are ignored.
 */
interface CameraRepository {

    /** Observable camera state flow. */
    val cameraState: StateFlow<CameraState>

    /**
     * Binds CameraX preview, capture, and analysis use cases to the provided lifecycle owner.
     *
     * Issues a new [CameraSessionToken] that becomes the sole session owner. Any previous
     * owner's token is immediately stale (including pending async binds from that owner).
     *
     * @param lifecycleOwner Android LifecycleOwner to bind camera session lifecycle.
     * @param surfaceProvider SurfaceProvider to render live camera preview.
     * @return Ownership token for this session; pass it to [stopCamera] when releasing.
     */
    fun startCamera(
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider,
    ): CameraSessionToken

    /** Toggles between front and rear camera lenses. */
    fun switchCamera()

    /**
     * Sets the frame analyzer callback for processing live camera frames.
     * Replaces any previous analyzer. Only one analyzer is active.
     *
     * @param analyzer CameraX ImageAnalysis.Analyzer implementation.
     */
    fun setFrameAnalyzer(analyzer: ImageAnalysis.Analyzer)

    /**
     * Clears the frame analyzer when [analyzer] is the currently registered instance.
     * Prevents a destroyed ViewModel from continuing to receive frames.
     */
    fun clearFrameAnalyzer(analyzer: ImageAnalysis.Analyzer)

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

    /**
     * Stops and unbinds camera use cases only if [session] is the current session owner.
     * Stale or [CameraSessionToken.None] tokens are logged and ignored (no-op).
     */
    fun stopCamera(session: CameraSessionToken)

    /** True when Preview + ImageAnalysis are currently bound. */
    fun isSessionActive(): Boolean

    /** Token of the current session owner, or [CameraSessionToken.None] if none. */
    fun currentSessionToken(): CameraSessionToken
}
