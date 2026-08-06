package com.openprofiler.export

import com.openprofiler.domain.model.CameraProfile
import com.openprofiler.domain.repository.ExportRepository
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * kotlinx.serialization-backed implementation of [ExportRepository].
 */
@Singleton
class ExportRepositoryImpl @Inject constructor() : ExportRepository {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = true // Ensure nulls stay null in JSON
    }

    override fun serializeProfile(profile: CameraProfile): String {
        return json.encodeToString(profile)
    }
}
