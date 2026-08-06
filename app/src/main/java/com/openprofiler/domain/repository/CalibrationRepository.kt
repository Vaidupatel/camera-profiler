package com.openprofiler.domain.repository

import com.openprofiler.domain.model.BoardConfig
import com.openprofiler.domain.model.CalibrationResult
import com.openprofiler.domain.model.DetectedCorner

/**
 * Repository interface for calibration operations and session data accumulation.
 */
interface CalibrationRepository {
    /**
     * Records a frame's point correspondences for the calibration corpus.
     * Only called for frames accepted by the coverage engine.
     */
    fun addFrame(
        corners: List<DetectedCorner>,
        boardConfig: BoardConfig,
        width: Int,
        height: Int
    )

    /**
     * Resets all accumulated session correspondences.
     */
    fun reset()

    /**
     * Solves the calibration using all accumulated frames.
     * Returns a [CalibrationResult] with measured intrinsics and RMS error.
     */
    suspend fun solve(): CalibrationResult

    /**
     * Returns the count of frames currently in the calibration corpus.
     */
    fun getAcceptedFrameCount(): Int
}
