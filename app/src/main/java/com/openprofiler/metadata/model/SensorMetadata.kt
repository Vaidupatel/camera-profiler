package com.openprofiler.metadata.model

import kotlinx.serialization.Serializable

/**
 * Physical image sensor dimensions and pixel array metadata.
 *
 * @property sensorWidthMm Physical sensor width in millimeters.
 * @property sensorHeightMm Physical sensor height in millimeters.
 * @property pixelArrayWidth Total pixel array width in pixels.
 * @property pixelArrayHeight Total pixel array height in pixels.
 * @property activeArrayWidth Active pixel array width in pixels.
 * @property activeArrayHeight Active pixel array height in pixels.
 * @property pixelPitchUm Calculated pixel pitch in micrometers (µm).
 * @property colorFilterArrangement Color filter arrangement string (e.g. "RGGB", "BGGR", "MONO").
 */
@Serializable
data class SensorMetadata(
    val sensorWidthMm: Double? = null,
    val sensorHeightMm: Double? = null,
    val pixelArrayWidth: Int? = null,
    val pixelArrayHeight: Int? = null,
    val activeArrayWidth: Int? = null,
    val activeArrayHeight: Int? = null,
    val pixelPitchUm: Double? = null,
    val colorFilterArrangement: String? = null,
)
