package com.openprofiler.domain.model

/**
 * Projected 3D board axes onto the 2D image plane for UI overlay rendering.
 *
 * @property origin 2D pixel position of the board origin (0,0,0).
 * @property xAxisEnd 2D pixel position of the X-axis end point (red axis).
 * @property yAxisEnd 2D pixel position of the Y-axis end point (green axis).
 * @property zAxisEnd 2D pixel position of the Z-axis end point (blue axis).
 */
data class BoardAxesOverlay(
    val origin: Point2D,
    val xAxisEnd: Point2D,
    val yAxisEnd: Point2D,
    val zAxisEnd: Point2D
)

/**
 * Result of calibration target detection on a camera frame.
 *
 * @property boundingBox Pose-reprojected board outline (depends on K/D). For UI axes overlay only —
 *   never use for TARGET_COVERAGE gating.
 * @property observedBoundingBox Axis-aligned box from [cornerCoordinates] min/max in image space.
 *   Independent of intrinsics; used for TARGET_COVERAGE.
 */
data class DetectionResult(
    val boardDetected: Boolean,
    val dictionary: String,
    val markerCount: Int,
    val charucoCornerCount: Int,
    val markerIds: List<Int>,
    val charucoIds: List<Int>,
    val cornerCoordinates: List<DetectedCorner>,
    val boardPose: BoardPose?,
    val detectionConfidence: Float,
    val processingTimeMs: Long,
    val rejectedReason: String? = null,
    val detectedMarkers: List<DetectedMarker> = emptyList(),
    val boardAxes: BoardAxesOverlay? = null,
    val boundingBox: List<Point2D>? = null,
    val observedBoundingBox: List<Point2D>? = null,
    val statistics: DetectionStatistics = DetectionStatistics(totalTimeMs = processingTimeMs)
)
