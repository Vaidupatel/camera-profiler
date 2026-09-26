package com.openprofiler.domain.model

import kotlinx.serialization.Serializable

/**
 * Identifiers for every measured quality metric.
 */
@Serializable
enum class QualityMetricId {
    BLUR,
    SHARPNESS,
    EXPOSURE,
    BLACK_LEVEL,
    CONTRAST,
    NOISE,
    TARGET_COVERAGE,
    CORNER_QUALITY,
    BOARD_VISIBILITY,
    POSE_QUALITY,
    MOTION,
    IMAGE_RESOLUTION,
    ASPECT_RATIO
}
