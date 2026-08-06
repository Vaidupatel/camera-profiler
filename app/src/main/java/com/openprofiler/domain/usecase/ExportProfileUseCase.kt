package com.openprofiler.domain.usecase

import android.net.Uri
import com.openprofiler.domain.repository.ExportRepository
import com.openprofiler.storage.ProfileStorage
import javax.inject.Inject

/**
 * Use case for exporting a camera profile to JSON and saving to storage.
 */
class ExportProfileUseCase @Inject constructor(
    private val generateProfileUseCase: GenerateProfileUseCase,
    private val exportRepository: ExportRepository,
    private val profileStorage: ProfileStorage,
) {
    /**
     * Generates, serializes, and saves the profile.
     * @return true if successful.
     */
    suspend fun export(): Boolean {
        val profile = generateProfileUseCase()
        val json = exportRepository.serializeProfile(profile)
        return profileStorage.saveProfile(json)
    }

    /**
     * Returns a URI for sharing the previously saved profile.
     */
    fun getShareUri(): Uri? {
        return profileStorage.getProfileUri()
    }
}
