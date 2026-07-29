package com.openprofiler.domain.usecase

import com.openprofiler.domain.repository.ValidationRepository
import javax.inject.Inject

/**
 * Use case for validating calibration results.
 * Phase 0: Placeholder with no business logic.
 */
class ValidateCalibrationUseCase @Inject constructor(
    private val validationRepository: ValidationRepository,
)
