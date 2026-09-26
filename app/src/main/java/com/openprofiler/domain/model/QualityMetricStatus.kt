package com.openprofiler.domain.model

import kotlinx.serialization.Serializable

/**
 * Pass / warning / fail outcome for a single measured quality metric.
 */
@Serializable
enum class QualityMetricStatus {
    PASS,
    WARNING,
    FAIL
}
