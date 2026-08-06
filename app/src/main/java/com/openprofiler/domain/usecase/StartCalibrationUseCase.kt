package com.openprofiler.domain.usecase

import com.openprofiler.domain.repository.CalibrationRepository
import com.openprofiler.domain.repository.CoverageRepository
import javax.inject.Inject

/**
 * Use case for starting a calibration session.
 * Resets both coverage tracking and accumulated point correspondences.
 */
class StartCalibrationUseCase @Inject constructor(
    private val calibrationRepository: CalibrationRepository,
    private val coverageRepository: CoverageRepository,
) {
    operator fun invoke() {
        calibrationRepository.reset()
        coverageRepository.resetState()
    }
}
