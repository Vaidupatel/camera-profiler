package com.openprofiler.metadata.model

import kotlinx.serialization.Serializable

/**
 * Advanced lens intrinsics and Camera2 characteristics metadata.
 *
 * @property availableFocalLengths List of available physical focal lengths in millimeters (mm).
 * @property intrinsicCalibration Factory intrinsic calibration array [fx, fy, cx, cy, s] if exposed.
 * @property lensDistortion Lens distortion coefficients array if exposed.
 * @property minimumFocusDistance Minimum focus distance in diopters (1/m).
 * @property hyperfocalDistance Hyperfocal distance in diopters (1/m).
 * @property opticalStabilizationModes Supported optical image stabilization modes.
 */
@Serializable
data class CameraCharacteristicsMetadata(
    val availableFocalLengths: List<Float> = emptyList(),
    val intrinsicCalibration: List<Float>? = null,
    val lensDistortion: List<Float>? = null,
    val minimumFocusDistance: Float? = null,
    val hyperfocalDistance: Float? = null,
    val opticalStabilizationModes: List<String> = emptyList(),
)
