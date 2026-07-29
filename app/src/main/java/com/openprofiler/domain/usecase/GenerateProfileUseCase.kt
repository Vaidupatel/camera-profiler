package com.openprofiler.domain.usecase

import com.openprofiler.domain.repository.CalibrationRepository
import com.openprofiler.domain.repository.MetadataRepository
import javax.inject.Inject

/**
 * Use case for generating a complete camera profile.
 * Phase 0: Placeholder with no business logic.
 */
class GenerateProfileUseCase @Inject constructor(
    private val metadataRepository: MetadataRepository,
    private val calibrationRepository: CalibrationRepository,
)
