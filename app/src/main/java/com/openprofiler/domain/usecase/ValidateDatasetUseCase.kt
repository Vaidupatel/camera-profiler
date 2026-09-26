package com.openprofiler.domain.usecase

import com.openprofiler.domain.model.CalibrationDataset
import com.openprofiler.domain.repository.CoverageRepository
import javax.inject.Inject

/**
 * Validates a calibration dataset against production-grade requirements.
 */
class ValidateDatasetUseCase @Inject constructor(
    private val coverageRepository: CoverageRepository
) {
    fun invoke(dataset: CalibrationDataset): DatasetValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        // 1. Minimum frame count
        if (dataset.observations.size < 10) {
            errors.add("Insufficient frames: ${dataset.observations.size} (minimum 10 required for production)")
        }

        // 2. Minimum corner count
        val totalCorners = dataset.observations.sumOf { it.frame.observation.charucoIds.size }
        if (totalCorners < 100) {
            errors.add("Insufficient total corners: $totalCorners (minimum 100 required)")
        }

        // 3. Board visibility check
        val avgVisibility = dataset.observations.map { 
            it.frame.observation.charucoIds.size.toDouble() / 
            ((dataset.boardConfig.squaresX - 1) * (dataset.boardConfig.squaresY - 1))
        }.average()
        if (avgVisibility < 0.3) {
            warnings.add("Low average board visibility: ${(avgVisibility * 100).toInt()}%")
        }

        // 4. Coverage check
        val coverage = coverageRepository.getCurrentCoverage()
        if (coverage.overallPercentage < 50.0) {
            errors.add("Insufficient coverage: ${coverage.overallPercentage}% (minimum 50% required)")
        }

        // 5. Pose diversity check (Rotation, Translation, Distance)
        if (dataset.observations.size >= 10) {
            val poses = dataset.observations.mapNotNull { it.frame.observation.pose }
            if (poses.size < 10) {
                errors.add("Too many frames missing pose estimation data")
            } else {
                val rvecStd = stdDev(poses.flatMap { it.rvec.toList() })
                val tvecStd = stdDev(poses.flatMap { it.tvec.toList() })
                
                if (rvecStd < 0.05) warnings.add("Low rotation diversity detected")
                if (tvecStd < 10.0) warnings.add("Low translation diversity detected")
            }
        }

        // 6. Duplicate / Identical pose rejection
        val uniquePoses = dataset.observations.distinctBy { 
            val pose = it.frame.observation.pose ?: return@distinctBy it.frame.id
            // Round to 2 decimal places for rotation (radians) and 1mm for translation
            val rx = (pose.rvec[0] * 100).toInt()
            val ry = (pose.rvec[1] * 100).toInt()
            val rz = (pose.rvec[2] * 100).toInt()
            val tx = pose.tvec[0].toInt()
            val ty = pose.tvec[1].toInt()
            val tz = pose.tvec[2].toInt()
            "${rx}_${ry}_${rz}_${tx}_${ty}_${tz}"
        }
        if (uniquePoses.size < dataset.observations.size) {
            warnings.add("Detected ${dataset.observations.size - uniquePoses.size} nearly identical poses")
        }

        return DatasetValidationResult(errors.isEmpty(), errors, warnings)
    }

    private fun stdDev(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val mean = values.average()
        return Math.sqrt(values.map { Math.pow(it - mean, 2.0) }.average())
    }
}

data class DatasetValidationResult(
    val isValid: Boolean,
    val errors: List<String>,
    val warnings: List<String>
)
