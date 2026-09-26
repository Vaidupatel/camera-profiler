package com.openprofiler.native_bridge

/**
 * Flat JNI transfer object for native image-domain quality measurements.
 * Contains only measured primitives — no OpenCV types cross the boundary.
 */
data class NativeImageQualityMeasurements(
    val success: Boolean,
    val blurLaplacianVariance: Double,
    val sharpnessGradientMagnitude: Double,
    val meanBrightness: Double,
    val darkPixelRatio: Double,
    val brightPixelRatio: Double,
    val contrastScore: Double,
    val noiseScore: Double,
    val motionMad: Double,
    val hasPriorFrame: Boolean,
    val frameWidth: Int,
    val frameHeight: Int,
    val whiteMeanBrightness: Double = 0.0,
    val whiteSaturationRatio: Double = 0.0,
    val blackMeanBrightness: Double = 0.0,
    val blackClippingRatio: Double = 0.0,
    val targetContrast: Double = 0.0
)
