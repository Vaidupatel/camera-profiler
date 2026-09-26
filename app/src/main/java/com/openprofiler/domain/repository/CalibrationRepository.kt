package com.openprofiler.domain.repository

import com.openprofiler.domain.model.*

/**
 * Repository interface for calibration operations and session data accumulation.
 */
interface CalibrationRepository {
    /**
     * Records a frame's complete observation for the calibration corpus.
     * Only called for frames accepted by the quality and coverage engines.
     */
    fun addFrame(
        detection: DetectionResult,
        quality: QualityResult,
        boardConfig: BoardConfig,
        coverageContribution: Double,
        metadata: Map<String, String> = emptyMap()
    )

    /**
     * Legacy method for simple frame addition (backward compatibility).
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

    /**
     * Returns the complete dataset for the current session.
     */
    fun getDataset(boardConfig: BoardConfig): CalibrationDataset
}
