package com.openprofiler.domain.model

import kotlinx.serialization.Serializable

/**
 * Represents a detected ArUco marker.
 *
 * @property id The integer ID of the detected marker.
 * @property corners List of 4 2D corner coordinates [x, y] in image pixels, ordered clockwise starting from top-left.
 */
@Serializable
data class DetectedMarker(
    val id: Int,
    val corners: List<Point2D>
) {
    init {
        require(corners.size == 4) { "Marker must have exactly 4 corner points" }
    }
}

/**
 * 2D point representation in pixel space.
 */
@Serializable
data class Point2D(
    val x: Float,
    val y: Float
)
