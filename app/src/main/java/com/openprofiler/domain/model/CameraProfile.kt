package com.openprofiler.domain.model

import kotlinx.serialization.Serializable

/**
 * Top-level camera profile model.
 * Phase 0: Minimal placeholder. Full schema will be implemented in Phase 10.
 *
 * @property schemaVersion The version of the profile schema.
 * @property profileVersion The version of this profile.
 * @property profileId Unique identifier for this profile.
 * @property createdAt ISO-8601 UTC timestamp of profile creation.
 */
@Serializable
data class CameraProfile(
    val schemaVersion: Int = 1,
    val profileVersion: Int = 1,
    val profileId: String = "",
    val createdAt: String = "",
)
