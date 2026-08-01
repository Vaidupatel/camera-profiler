package com.openprofiler.metadata.model

import kotlinx.serialization.Serializable

/**
 * Camera hardware info collected from Camera2 API.
 *
 * @property cameraId Camera identifier string.
 * @property logicalCamera Whether this is a logical multi-camera device.
 * @property physicalCameraIds Physical camera IDs associated with this logical camera.
 * @property lensFacing Lens facing direction string ("FRONT", "BACK", "EXTERNAL").
 * @property sensorOrientation Clockwise rotation angle required to align sensor to device natural orientation.
 * @property flashAvailable Whether flash hardware is available.
 * @property hardwareLevel Hardware support level ("LIMITED", "FULL", "LEVEL_3", "LEGACY").
 * @property timestampSource Timestamp source ("UNKNOWN", "REALTIME").
 * @property requestCapabilities List of supported Camera2 request capability strings.
 */
@Serializable
data class CameraInfoMetadata(
    val cameraId: String = "0",
    val logicalCamera: Boolean = false,
    val physicalCameraIds: List<String> = emptyList(),
    val lensFacing: String = "BACK",
    val sensorOrientation: Int = 0,
    val flashAvailable: Boolean = false,
    val hardwareLevel: String = "UNKNOWN",
    val timestampSource: String = "UNKNOWN",
    val requestCapabilities: List<String> = emptyList(),
)
