package com.openprofiler.domain.model

import kotlinx.serialization.Serializable

/**
 * Configurable quality thresholds loaded from assets/quality.json.
 * No magic numbers in engine code — all limits live here.
 */
@Serializable
data class QualityThresholds(
    val minBlurLaplacianVariance: Double = 100.0,
    val warningBlurLaplacianVariance: Double = 150.0,
    val minSharpnessGradientMagnitude: Double = 20.0,
    val warningSharpnessGradientMagnitude: Double = 35.0,
    val minMeanBrightness: Double = 40.0,
    val maxMeanBrightness: Double = 220.0,
    val warningMeanBrightnessLow: Double = 60.0,
    val warningMeanBrightnessHigh: Double = 200.0,
    val maxDarkPixelRatio: Double = 0.35,
    val maxBrightPixelRatio: Double = 0.35,
    val darkPixelThreshold: Double = 30.0,
    val brightPixelThreshold: Double = 225.0,
    val minContrastScore: Double = 0.35,
    val warningContrastScore: Double = 0.50,
    val maxNoiseScore: Double = 25.0,
    val warningNoiseScore: Double = 15.0,
    val minBoardWidthPercent: Double = 15.0,
    val minBoardHeightPercent: Double = 15.0,
    val minImageCoveragePercent: Double = 5.0,
    val warningImageCoveragePercent: Double = 10.0,
    val minAverageCornerResponse: Double = 0.40,
    val minMinimumCornerResponse: Double = 0.20,
    val minCornerCount: Int = 4,
    val minSubpixelSuccessRatio: Double = 0.80,
    val minBoardVisibilityPercent: Double = 25.0,
    val warningBoardVisibilityPercent: Double = 50.0,
    val maxAbsPitchDegrees: Double = 60.0,
    val maxAbsYawDegrees: Double = 60.0,
    val maxAbsRollDegrees: Double = 45.0,
    val minFrontalScore: Double = 0.35,
    val warningFrontalScore: Double = 0.55,
    val minDistanceMm: Double = 80.0,
    val maxDistanceMm: Double = 2500.0,
    val maxMotionMad: Double = 18.0,
    val warningMotionMad: Double = 10.0,
    val minWidthPx: Int = 640,
    val minHeightPx: Int = 480,
    val supportedAspectRatios: List<Double> = listOf(4.0 / 3.0, 16.0 / 9.0, 1.0, 3.0 / 2.0),
    val aspectRatioTolerance: Double = 0.08
)
