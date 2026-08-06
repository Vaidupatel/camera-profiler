package com.openprofiler.domain.model

import kotlinx.serialization.Serializable

/**
 * Calibration coverage tracking data across image position, scale, pose, and distance dimensions.
 *
 * @property overallPercentage Total normalized diversity score in [0.0, 100.0].
 * @property center Coverage at image center region [0.0, 100.0].
 * @property top Coverage at top region [0.0, 100.0].
 * @property bottom Coverage at bottom region [0.0, 100.0].
 * @property left Coverage at left region [0.0, 100.0].
 * @property right Coverage at right region [0.0, 100.0].
 * @property gridCoveragePercent Image position grid cell coverage [0.0, 100.0].
 * @property scaleCoveragePercent Board scale bucket coverage [0.0, 100.0].
 * @property poseCoveragePercent Target pose angle bin coverage [0.0, 100.0].
 * @property distanceCoveragePercent Relative distance bin coverage [0.0, 100.0].
 * @property visitedGridCells Count of visited grid cells.
 * @property totalGridCells Total grid cells available (e.g. 9 for 3x3).
 * @property visitedScaleBuckets Count of visited scale buckets.
 * @property totalScaleBuckets Total scale buckets (5).
 * @property visitedPoseBins Count of visited pose angle bins.
 * @property totalPoseBins Total pose angle bins.
 * @property visitedDistanceBins Count of visited distance bins.
 * @property totalDistanceBins Total distance bins.
 * @property acceptedFrameCount Total accepted frames that contributed new coverage information.
 */
@Serializable
data class CoverageData(
    val overallPercentage: Double = 0.0,
    val center: Double = 0.0,
    val top: Double = 0.0,
    val bottom: Double = 0.0,
    val left: Double = 0.0,
    val right: Double = 0.0,
    val gridCoveragePercent: Double = 0.0,
    val scaleCoveragePercent: Double = 0.0,
    val poseCoveragePercent: Double = 0.0,
    val distanceCoveragePercent: Double = 0.0,
    val visitedGridCells: Int = 0,
    val totalGridCells: Int = 9,
    val visitedScaleBuckets: Int = 0,
    val totalScaleBuckets: Int = 5,
    val visitedPoseBins: Int = 0,
    val totalPoseBins: Int = 0,
    val visitedDistanceBins: Int = 0,
    val totalDistanceBins: Int = 0,
    val acceptedFrameCount: Int = 0
)
