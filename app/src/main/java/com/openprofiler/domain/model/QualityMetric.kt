package com.openprofiler.domain.model

import kotlinx.serialization.Serializable

/**
 * Immutable measurement of a single quality attribute.
 *
 * Every metric reports the measured [value], the governing [threshold],
 * a [status], and a human-readable [reason]. Secondary measurements
 * (ratios, counts) are optional and never fabricated.
 *
 * @property id Metric identity.
 * @property value Primary measured value.
 * @property threshold Threshold used for status decision.
 * @property status Pass / warning / fail.
 * @property reason Deterministic explanation of the status.
 * @property secondaryValues Optional named secondary measurements.
 */
@Serializable
data class QualityMetric(
    val id: QualityMetricId,
    val value: Double,
    val threshold: Double,
    val status: QualityMetricStatus,
    val reason: String,
    val secondaryValues: Map<String, Double> = emptyMap()
)
