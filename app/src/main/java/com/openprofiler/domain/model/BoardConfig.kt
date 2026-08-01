package com.openprofiler.domain.model

import kotlinx.serialization.Serializable

/**
 * Configuration for a ChArUco calibration board target loaded from assets.
 */
@Serializable
data class BoardConfig(
    val dictionaryName: String,
    val squaresX: Int,
    val squaresY: Int,
    val squareLengthMm: Float,
    val markerLengthMm: Float,
    val minMarkers: Int = 4,
    val minCorners: Int = 4,
    val minConfidence: Float = 0.5f
)
