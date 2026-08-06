package com.openprofiler.domain.repository

import com.openprofiler.domain.model.CameraProfile

/**
 * Repository interface for profile export operations.
 */
interface ExportRepository {
    /**
     * Serializes a [CameraProfile] to a JSON string.
     */
    fun serializeProfile(profile: CameraProfile): String
}
