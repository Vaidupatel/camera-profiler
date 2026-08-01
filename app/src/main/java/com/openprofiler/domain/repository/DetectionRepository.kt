package com.openprofiler.domain.repository

import androidx.camera.core.ImageProxy
import com.openprofiler.domain.model.BoardConfig
import com.openprofiler.domain.model.DetectionResult

/**
 * Repository for calibration target board detection.
 */
interface DetectionRepository {
    /**
     * Initializes the detector with board configuration loaded from assets.
     */
    suspend fun initialize(): Result<Unit>

    /**
     * Analyzes an incoming camera frame for calibration targets.
     *
     * @param imageProxy Frame input from CameraX.
     * @param cameraMatrix Optional 3x3 intrinsics matrix for pose estimation (9 doubles).
     * @param distCoeffs Optional distortion coefficients for pose estimation (5+ doubles).
     * @return DetectionResult containing detected markers, corners, pose, and overlay data.
     */
    suspend fun detectBoard(
        imageProxy: ImageProxy,
        cameraMatrix: DoubleArray? = null,
        distCoeffs: DoubleArray? = null
    ): DetectionResult

    /**
     * Returns the current board configuration.
     */
    fun getBoardConfig(): BoardConfig?

    /**
     * Releases native detector resources.
     */
    fun release()
}
