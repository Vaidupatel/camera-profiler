package com.openprofiler.camera

import androidx.camera.core.CameraSelector

/**
 * Immutable state model representing the current status of the CameraX pipeline.
 *
 * @property lensFacing Selected camera lens facing (front vs. rear).
 * @property isStreaming Whether the camera preview is currently active and streaming.
 * @property resolution Current camera preview resolution string.
 * @property fps Measured preview frame rate.
 * @property error Error message if camera initialization or streaming failed.
 */
data class CameraState(
    val lensFacing: Int = CameraSelector.LENS_FACING_BACK,
    val isStreaming: Boolean = false,
    val resolution: String = "1920x1080",
    val fps: Int = 60,
    val error: String? = null,
)
