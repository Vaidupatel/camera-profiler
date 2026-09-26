package com.openprofiler.domain.usecase

import com.openprofiler.domain.model.*
import com.openprofiler.domain.repository.CalibrationRepository
import com.openprofiler.domain.repository.CoverageRepository
import javax.inject.Inject

/**
 * Orchestrates the production calibration process.
 * Includes validation, solving, and report generation.
 */
class PerformCalibrationUseCase @Inject constructor(
    private val calibrationRepository: CalibrationRepository,
    private val coverageRepository: CoverageRepository,
    private val validateDatasetUseCase: ValidateDatasetUseCase
) {
    suspend operator fun invoke(boardConfig: BoardConfig): CalibrationResult {
        val dataset = calibrationRepository.getDataset(boardConfig)
        
        // Task 3: Dataset Validation
        val validation = validateDatasetUseCase.invoke(dataset)
        if (!validation.isValid) {
            com.openprofiler.common.util.Logger.e("PerformCalibration", "Dataset validation failed: ${validation.errors}")
            return CalibrationResult(success = false)
        }

        // Task 4 & 5: Production Calibration with Outlier Rejection
        val result = calibrationRepository.solve()
        if (!result.success) return result

        // Task 6: Calibration Statistics Enrichment
        val coverage = coverageRepository.getCurrentCoverage()
        val enrichedStats = result.statistics?.copy(
            coveragePercent = coverage.overallPercentage
        )

        // Task 10: Calibration Report Generation
        val finalResult = result.copy(
            statistics = enrichedStats,
            dataset = dataset
        )
        
        return finalResult
    }
}
