package com.openprofiler.domain.model

import kotlinx.serialization.Serializable

/**
 * Raw data captured from a single camera frame for calibration.
 */
@Serializable
data class FrameObservation(
    val timestamp: Long,
    val width: Int,
    val height: Int,
    val rotation: Int,
    val markerIds: List<Int>,
    val charucoIds: List<Int>,
    val subpixelCorners: List<Point2D>,
    val objectPoints: List<Point3D>,
    val imagePoints: List<Point2D>,
    val pose: BoardPose?,
    val coverageContribution: Double,
    val qualitySummary: QualitySummary?,
    val reprojectionPoints: List<Point2D>? = null,
    val metadata: Map<String, String> = emptyMap()
)

/**
 * A wrapper for a frame that has been processed by the calibration engine.
 */
@Serializable
data class CalibrationFrame(
    val id: String,
    val observation: FrameObservation,
    val diagnostics: FrameDiagnostics? = null
)

/**
 * Represents an observation that has been accepted into a calibration session.
 */
@Serializable
data class AcceptedObservation(
    val frame: CalibrationFrame
)

/**
 * A complete collection of frames used for a calibration run.
 */
@Serializable
data class CalibrationDataset(
    val observations: List<AcceptedObservation>,
    val boardConfig: BoardConfig,
    val deviceName: String,
    val cameraName: String
)

/**
 * Statistical analysis of the calibration residuals and coverage.
 */
@Serializable
data class CalibrationStatistics(
    val meanReprojectionError: Double,
    val medianReprojectionError: Double,
    val maxReprojectionError: Double,
    val p95ReprojectionError: Double,
    val cornerRms: Double,
    val frameRms: Double,
    val coveragePercent: Double,
    val confidenceScore: Double,
    val conditionNumber: Double? = null
)

/**
 * Estimated uncertainty for each calibrated parameter.
 */
@Serializable
data class CalibrationUncertainty(
    val cameraMatrixStdDev: List<Double>,
    val distortionStdDev: List<Double>,
    val intrinsicConfidence: Double,
    val distortionConfidence: Double,
    val coverageConfidence: Double,
    val datasetConfidence: Double,
    val overallConfidence: Double
)

/**
 * Quality metrics for a specific frame within the calibration solution.
 */
@Serializable
data class FrameDiagnostics(
    val rms: Double,
    val maxError: Double,
    val isOutlier: Boolean,
    val rejectionReason: String? = null
)

/**
 * Final human-readable and machine-parsable report of the calibration session.
 */
@Serializable
data class CalibrationReport(
    val datasetSummary: String,
    val frameCount: Int,
    val rejectedFrameCount: Int,
    val coveragePercent: Double,
    val cameraMatrix: List<List<Double>>,
    val distortionCoefficients: List<Double>,
    val overallRms: Double,
    val confidence: Double,
    val validationSummary: String,
    val timestamp: Long
)

/**
 * Top-level export schema for production calibration results.
 */
@Serializable
data class ProductionCalibrationExport(
    val schemaVersion: String = "1.0",
    val opencvVersion: String = "4.10.0",
    val timestamp: Long,
    val device: DeviceInfo,
    val result: CalibrationResult,
    val report: CalibrationReport
)

/**
 * Hardware device identification for the calibration report.
 */
@Serializable
data class DeviceInfo(
    val model: String,
    val camera: String
)
