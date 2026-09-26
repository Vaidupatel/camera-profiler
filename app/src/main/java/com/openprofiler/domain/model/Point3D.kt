package com.openprofiler.domain.model

import kotlinx.serialization.Serializable

/**
 * 3D point representation in object space (meters).
 */
@Serializable
data class Point3D(
    val x: Float,
    val y: Float,
    val z: Float
)
