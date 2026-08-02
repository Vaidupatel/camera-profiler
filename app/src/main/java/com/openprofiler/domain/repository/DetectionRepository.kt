package com.openprofiler.domain.repository

import androidx.camera.core.ImageProxy
import com.openprofiler.domain.model.BoardConfig
import com.openprofiler.domain.model.DetectionResult
import com.openprofiler.domain.model.IntrinsicsSource

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
     *   When null, native pose estimation fails rather than guessing K.
     * @param distCoeffs Optional distortion coefficients for pose estimation (5+ doubles).
     * @param intrinsicsSource Provenance of [cameraMatrix]/[distCoeffs], stamped onto [BoardPose].
     * @return DetectionResult containing detected markers, corners, pose, and overlay data.
     */
    suspend fun detectBoard(
        imageProxy: ImageProxy,
        cameraMatrix: DoubleArray? = null,
        distCoeffs: DoubleArray? = null,
        intrinsicsSource: IntrinsicsSource = IntrinsicsSource.UNAVAILABLE,
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
