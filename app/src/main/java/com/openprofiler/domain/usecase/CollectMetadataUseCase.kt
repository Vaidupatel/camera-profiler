package com.openprofiler.domain.usecase

import com.openprofiler.domain.repository.MetadataRepository
import javax.inject.Inject

/**
 * Use case for collecting device and camera metadata.
 * Phase 0: Placeholder with no business logic.
 */
class CollectMetadataUseCase @Inject constructor(
    private val metadataRepository: MetadataRepository,
)
