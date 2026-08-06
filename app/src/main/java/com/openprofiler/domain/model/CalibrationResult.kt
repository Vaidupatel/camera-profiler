package com.openprofiler.domain.model

import kotlinx.serialization.Serializable

/**
 * Result of a camera calibration operation.
 *
 * @property rms Root mean square reprojection error.
 * @property cameraMatrix 3x3 camera intrinsic matrix (row-major).
 * @property distortionCoefficients Distortion coefficients.
 * @property imageCount Number of images used for calibration.
 * @property success Whether the calibration was successful.
 */
@Serializable
data class CalibrationResult(
    val rms: Double = 0.0,
    val cameraMatrix: List<List<Double>> = emptyList(),
    val distortionCoefficients: List<Double> = emptyList(),
    val imageCount: Int = 0,
    val success: Boolean = false,
)
