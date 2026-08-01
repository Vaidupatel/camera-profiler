package com.openprofiler.domain.repository

import androidx.camera.core.ImageProxy
import com.openprofiler.domain.model.BoardConfig
import com.openprofiler.domain.model.DetectionResult
import com.openprofiler.domain.model.QualityResult
import com.openprofiler.domain.model.QualityThresholds

/**
 * Repository boundary for frame quality evaluation.
 */
interface QualityRepository {
    /**
     * Loads thresholds and initializes the native quality evaluator.
     */
    suspend fun initialize(): Result<Unit>

    /**
     * Evaluates whether [imageProxy] is suitable for calibration, using
     * optional [detection] and [boardConfig] for board-derived metrics.
     */
    suspend fun evaluate(
        imageProxy: ImageProxy,
        detection: DetectionResult?,
        boardConfig: BoardConfig?
    ): QualityResult

    /**
     * Clears inter-frame motion state.
     */
    fun resetMotionState()

    /**
     * Releases native quality resources.
     */
    fun release()

    /**
     * Returns currently loaded thresholds.
     */
    fun getThresholds(): QualityThresholds
}
