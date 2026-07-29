package com.openprofiler.domain.model

/**
 * Calibration coverage tracking data.
 * Phase 0: Placeholder.
 *
 * @property overallPercentage Total coverage percentage in [0.0, 100.0].
 * @property center Coverage at image center.
 * @property top Coverage at top region.
 * @property bottom Coverage at bottom region.
 * @property left Coverage at left region.
 * @property right Coverage at right region.
 */
data class CoverageData(
    val overallPercentage: Double = 0.0,
    val center: Double = 0.0,
    val top: Double = 0.0,
    val bottom: Double = 0.0,
    val left: Double = 0.0,
    val right: Double = 0.0,
)
