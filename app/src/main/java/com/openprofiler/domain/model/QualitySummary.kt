package com.openprofiler.domain.model

/**
 * Rollup of a frame quality evaluation.
 *
 * @property isAccepted True only when no metric has [QualityMetricStatus.FAIL].
 * @property overallScore Score in [0.0, 1.0] derived from measured metric statuses.
 * @property passCount Number of PASS metrics.
 * @property warningCount Number of WARNING metrics.
 * @property failCount Number of FAIL metrics.
 * @property primaryRejectReason First FAIL reason, or null when accepted.
 */
data class QualitySummary(
    val isAccepted: Boolean,
    val overallScore: Double,
    val passCount: Int,
    val warningCount: Int,
    val failCount: Int,
    val primaryRejectReason: String?
)
