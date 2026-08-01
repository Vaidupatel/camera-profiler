package com.openprofiler.domain.repository

import com.openprofiler.metadata.model.FullMetadata

/**
 * Repository interface for device and camera metadata collection.
 */
interface MetadataRepository {

    /**
     * Collects all available device, OS, camera hardware, sensor, and characteristics metadata.
     *
     * @param cameraId Camera ID to collect metadata for. If null, default camera ID ("0") is used.
     * @return Complete [FullMetadata] aggregated container.
     */
    fun collectMetadata(cameraId: String? = null): FullMetadata

    /**
     * Queries all available camera IDs on the device.
     *
     * @return List of camera ID strings exposed by [CameraManager].
     */
    fun getAvailableCameraIds(): List<String>
}
