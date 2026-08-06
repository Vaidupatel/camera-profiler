package com.openprofiler.domain.repository

import com.openprofiler.domain.model.CoverageData
import com.openprofiler.domain.model.CoverageEvaluationResult
import com.openprofiler.domain.model.CoverageThresholds
import com.openprofiler.domain.model.DetectionResult

/**
 * Repository interface managing calibration pose diversity coverage analysis.
 */
interface CoverageRepository {
    /**
     * Initializes the coverage engine by loading threshold configuration assets.
     */
    suspend fun initialize(): Result<Unit>

    /**
     * Evaluates a target [detection] frame of dimensions [frameWidth] x [frameHeight] against
     * current session coverage, returning an acceptance decision, score delta, and guidance.
     */
    suspend fun evaluate(
        detection: DetectionResult,
        frameWidth: Int,
        frameHeight: Int
    ): CoverageEvaluationResult

    /**
     * Resets all accumulated session coverage state.
     */
    fun resetState()

    /**
     * Returns current accumulated coverage data breakdown.
     */
    fun getCurrentCoverage(): CoverageData

    /**
     * Returns currently active coverage thresholds.
     */
    fun getThresholds(): CoverageThresholds
}
