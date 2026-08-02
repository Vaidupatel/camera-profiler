package com.openprofiler.domain.model

/**
 * Axis-aligned image-space bounding box helpers derived only from observed 2D points.
 * No camera matrix or pose reprojection is involved.
 */
object ObservedBoundingBox {

    /**
     * Builds a 4-point axis-aligned box (TL, TR, BR, BL) from detected ChArUco corners.
     * Returns null when fewer than 2 corners are available (box not measurable).
     */
    fun fromCorners(corners: List<DetectedCorner>): List<Point2D>? {
        if (corners.size < 2) return null
        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        for (c in corners) {
            if (c.x < minX) minX = c.x
            if (c.y < minY) minY = c.y
            if (c.x > maxX) maxX = c.x
            if (c.y > maxY) maxY = c.y
        }
        if (!minX.isFinite() || !maxX.isFinite() || !minY.isFinite() || !maxY.isFinite()) {
            return null
        }
        if (maxX <= minX || maxY <= minY) return null
        return listOf(
            Point2D(minX, minY),
            Point2D(maxX, minY),
            Point2D(maxX, maxY),
            Point2D(minX, maxY),
        )
    }
}
