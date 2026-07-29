package com.openprofiler.domain.usecase

import com.openprofiler.domain.repository.CalibrationRepository
import javax.inject.Inject

/**
 * Use case for starting a calibration session.
 * Phase 0: Placeholder with no business logic.
 */
class StartCalibrationUseCase @Inject constructor(
    private val calibrationRepository: CalibrationRepository,
)
