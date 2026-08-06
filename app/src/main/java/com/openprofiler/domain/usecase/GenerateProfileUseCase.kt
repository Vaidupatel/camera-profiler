package com.openprofiler.domain.usecase

import com.openprofiler.domain.model.CameraProfile
import com.openprofiler.domain.model.IntrinsicsSource
import com.openprofiler.domain.repository.CalibrationRepository
import com.openprofiler.domain.repository.CoverageRepository
import com.openprofiler.domain.repository.MetadataRepository
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

/**
 * Use case for generating a complete camera profile.
 */
class GenerateProfileUseCase @Inject constructor(
    private val metadataRepository: MetadataRepository,
    private val calibrationRepository: CalibrationRepository,
    private val coverageRepository: CoverageRepository,
) {
    suspend operator fun invoke(): CameraProfile {
        val calibration = calibrationRepository.solve()
        val metadata = metadataRepository.collectMetadata()
        val coverage = coverageRepository.getCurrentCoverage()
        
        return CameraProfile(
            profileId = UUID.randomUUID().toString(),
            createdAt = Instant.now().toString(),
            calibrated = calibration,
            source = IntrinsicsSource.CALIBRATED,
            metadata = metadata,
            coverage = coverage
        )
    }
}
