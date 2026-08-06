package com.openprofiler.domain.model

/**
 * Result of evaluating frame coverage diversity against accumulated calibration dataset.
 *
 * @property isAccepted True if the frame contributes new calibration coverage information.
 * @property rejectReason Reason description if rejected (null when accepted).
 * @property coverageDelta Change in overall diversity score contributed by this frame [0.0, 100.0].
 * @property diversityScore Accumulated overall diversity score after processing this frame [0.0, 100.0].
 * @property guidance Real-time user positioning instruction.
 * @property remainingRequirements List of missing cells/bins/buckets needed to reach full coverage.
 * @property coverageData Comprehensive current coverage breakdown.
 */
data class CoverageEvaluationResult(
    val isAccepted: Boolean,
    val rejectReason: String?,
    val coverageDelta: Double,
    val diversityScore: Double,
    val guidance: CoverageGuidance,
    val remainingRequirements: List<String>,
    val coverageData: CoverageData
)
