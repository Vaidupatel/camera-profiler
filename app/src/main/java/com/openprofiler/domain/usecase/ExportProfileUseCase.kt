package com.openprofiler.domain.usecase

import com.openprofiler.domain.repository.ExportRepository
import javax.inject.Inject

/**
 * Use case for exporting a camera profile to JSON.
 * Phase 0: Placeholder with no business logic.
 */
class ExportProfileUseCase @Inject constructor(
    private val exportRepository: ExportRepository,
)
