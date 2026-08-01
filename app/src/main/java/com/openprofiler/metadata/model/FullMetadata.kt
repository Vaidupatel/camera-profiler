package com.openprofiler.metadata.model

import kotlinx.serialization.Serializable

/**
 * Top-level container aggregating all metadata collected from Camera2 API, Android Build, and runtime.
 *
 * @property device Device hardware properties.
 * @property operatingSystem Operating system details.
 * @property application Application build details.
 * @property camera Camera hardware info.
 * @property sensor Sensor physical and array metrics.
 * @property cameraCharacteristics Advanced camera characteristics and factory intrinsics.
 */
@Serializable
data class FullMetadata(
    val device: DeviceMetadata = DeviceMetadata(),
    val operatingSystem: OsMetadata = OsMetadata(),
    val application: AppMetadata = AppMetadata(),
    val camera: CameraInfoMetadata = CameraInfoMetadata(),
    val sensor: SensorMetadata = SensorMetadata(),
    val cameraCharacteristics: CameraCharacteristicsMetadata = CameraCharacteristicsMetadata(),
)
