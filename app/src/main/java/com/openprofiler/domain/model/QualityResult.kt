package com.openprofiler.domain.model

/**
 * Complete immutable quality evaluation for a single frame.
 *
 * @property metrics All measured metrics (one entry per [QualityMetricId]).
 * @property summary Aggregate acceptance decision.
 * @property processingTimeMs Wall time spent evaluating this frame.
 * @property frameWidthPx Frame width used for measurements.
 * @property frameHeightPx Frame height used for measurements.
 */
data class QualityResult(
    val metrics: List<QualityMetric>,
    val summary: QualitySummary,
    val processingTimeMs: Long,
    val frameWidthPx: Int,
    val frameHeightPx: Int
) {
    fun metric(id: QualityMetricId): QualityMetric? = metrics.firstOrNull { it.id == id }
}
