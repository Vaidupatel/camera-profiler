package com.openprofiler.domain.model

import kotlinx.serialization.Serializable

/**
 * Thresholds and weights for the Coverage Engine, loaded from assets/coverage.json.
 */
@Serializable
data class CoverageThresholds(
    val gridRows: Int = 3,
    val gridCols: Int = 3,
    val minAcceptedFrames: Int = 15,
    val minGridCoveragePercent: Double = 70.0,
    val minScaleBuckets: Int = 3,
    val minPoseBins: Int = 4,
    val minDistanceBins: Int = 3,
    val targetDiversityScore: Double = 80.0,
    val minDiversityDeltaToAccept: Double = 0.1,
    val scaleThresholdsPercent: List<Double> = listOf(10.0, 25.0, 40.0, 60.0),
    val pitchBinsDegrees: List<Double> = listOf(-45.0, -15.0, 15.0, 45.0),
    val yawBinsDegrees: List<Double> = listOf(-45.0, -15.0, 15.0, 45.0),
    val rollBinsDegrees: List<Double> = listOf(-30.0, -10.0, 10.0, 30.0),
    val distanceBinsMm: List<Double> = listOf(500.0, 1000.0, 1500.0, 2500.0),
    val gridWeight: Double = 0.35,
    val scaleWeight: Double = 0.20,
    val poseWeight: Double = 0.25,
    val distanceWeight: Double = 0.20
)
