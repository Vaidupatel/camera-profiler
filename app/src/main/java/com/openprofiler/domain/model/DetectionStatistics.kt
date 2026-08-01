package com.openprofiler.domain.model

/**
 * Diagnostic statistics for a target detection run.
 *
 * @property totalTimeMs Overall execution time in milliseconds.
 * @property markerDetectionTimeMs Time taken for initial ArUco marker detection in ms.
 * @property charucoInterpolationTimeMs Time taken for ChArUco corner interpolation in ms.
 * @property subpixelRefinementTimeMs Time taken for subpixel refinement in ms.
 * @property poseEstimationTimeMs Time taken for board pose estimation in ms.
 */
data class DetectionStatistics(
    val totalTimeMs: Long = 0L,
    val markerDetectionTimeMs: Long = 0L,
    val charucoInterpolationTimeMs: Long = 0L,
    val subpixelRefinementTimeMs: Long = 0L,
    val poseEstimationTimeMs: Long = 0L
)
