package com.openprofiler.domain.model

/**
 * Represents a subpixel-refined ChArUco corner.
 *
 * @property id The index/ID of the ChArUco corner on the calibration target.
 * @property x Pixel coordinate along horizontal axis.
 * @property y Pixel coordinate along vertical axis.
 * @property subpixelPrecision Subpixel refinement precision / quality metric.
 */
data class DetectedCorner(
    val id: Int,
    val x: Float,
    val y: Float,
    val subpixelPrecision: Float = 1.0f
)
