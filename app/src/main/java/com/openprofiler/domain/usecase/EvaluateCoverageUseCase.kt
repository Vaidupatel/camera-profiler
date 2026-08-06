package com.openprofiler.domain.usecase

import com.openprofiler.domain.model.CoverageEvaluationResult
import com.openprofiler.domain.model.DetectionResult
import com.openprofiler.domain.repository.CoverageRepository
import javax.inject.Inject

/**
 * Use case for evaluating calibration pose diversity coverage on incoming frame detections.
 */
class EvaluateCoverageUseCase @Inject constructor(
    private val coverageRepository: CoverageRepository
) {
    suspend operator fun invoke(
        detection: DetectionResult,
        frameWidth: Int,
        frameHeight: Int
    ): CoverageEvaluationResult {
        return coverageRepository.evaluate(detection, frameWidth, frameHeight)
    }
}
