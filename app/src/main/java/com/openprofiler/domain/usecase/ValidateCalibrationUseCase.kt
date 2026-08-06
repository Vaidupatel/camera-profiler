package com.openprofiler.domain.usecase

import com.openprofiler.domain.model.CalibrationResult
import javax.inject.Inject

/**
 * Use case for validating calibration results against scientific integrity thresholds.
 */
class ValidateCalibrationUseCase @Inject constructor() {
    /**
     * Validates [result] based on measured RMS error.
     * Returns true if RMS < 1.0 px (standard baseline for ChArUco stability).
     */
    operator fun invoke(result: CalibrationResult): Boolean {
        if (!result.success || result.imageCount == 0) return false
        
        // Strict measurement: never pass a result that hasn't been solved.
        return result.rms > 0.0 && result.rms < 1.0
    }
}
