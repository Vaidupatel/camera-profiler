package com.openprofiler.domain.model

/**
 * Quality assessment result for a single frame.
 * Phase 0: Placeholder.
 *
 * @property overallScore Overall quality score in [0.0, 1.0].
 * @property blur Blur metric.
 * @property exposure Exposure metric.
 * @property contrast Contrast metric.
 * @property isAccepted Whether the frame passes quality thresholds.
 * @property rejectReason Reason for rejection, null if accepted.
 */
data class QualityScore(
    val overallScore: Double = 0.0,
    val blur: Double = 0.0,
    val exposure: Double = 0.0,
    val contrast: Double = 0.0,
    val isAccepted: Boolean = false,
    val rejectReason: String? = null,
)
