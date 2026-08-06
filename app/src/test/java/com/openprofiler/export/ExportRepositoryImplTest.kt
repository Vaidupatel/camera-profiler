package com.openprofiler.export

import com.google.common.truth.Truth.assertThat
import com.openprofiler.domain.model.CameraProfile
import com.openprofiler.domain.model.IntrinsicsSource
import org.junit.Test

class ExportRepositoryImplTest {

    private val repository = ExportRepositoryImpl()

    @Test
    fun `serializeProfile keeps nulls as nulls in JSON`() {
        val profile = CameraProfile(
            profileId = "test-id",
            createdAt = "2026-08-02T12:00:00Z",
            calibrated = null, // Explicitly null
            metadata = null,
            coverage = null
        )

        val json = repository.serializeProfile(profile)
        
        assertThat(json).contains("\"calibrated\": null")
        assertThat(json).contains("\"metadata\": null")
        assertThat(json).contains("\"coverage\": null")
    }

    @Test
    fun `serializeProfile encodes calibrated source`() {
        val profile = CameraProfile(
            source = IntrinsicsSource.CALIBRATED
        )

        val json = repository.serializeProfile(profile)
        
        assertThat(json).contains("\"source\": \"CALIBRATED\"")
    }
}
