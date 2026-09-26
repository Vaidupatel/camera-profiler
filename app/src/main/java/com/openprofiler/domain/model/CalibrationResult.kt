package com.openprofiler.domain.model

import kotlinx.serialization.Serializable

/**
 * Result of a camera calibration operation.
 */
@Serializable
data class CalibrationResult(
    val rms: Double = 0.0,
    val cameraMatrix: List<List<Double>> = emptyList(),
    val distortionCoefficients: List<Double> = emptyList(),
    val imageCount: Int = 0,
    val success: Boolean = false,
    val perViewErrors: List<Double> = emptyList(),
    val rejectedFrames: List<Int> = emptyList(),
    val residuals: List<Double> = emptyList(),
    val statistics: CalibrationStatistics? = null,
    val uncertainty: CalibrationUncertainty? = null,
    val dataset: CalibrationDataset? = null,
    val timestamp: Long = System.currentTimeMillis()
)
