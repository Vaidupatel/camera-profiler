package com.openprofiler.domain.model

import com.openprofiler.metadata.model.FullMetadata
import kotlinx.serialization.Serializable

/**
 * Top-level camera profile model containing solved intrinsics, metadata, and coverage summary.
 *
 * @property schemaVersion The version of the profile schema.
 * @property profileVersion The version of this profile.
 * @property profileId Unique identifier for this profile.
 * @property createdAt ISO-8601 UTC timestamp of profile creation.
 * @property calibrated Intrinsics solved during the session.
 * @property source The origin of the intrinsics (must be CALIBRATED).
 * @property metadata aggregated device and camera hardware info.
 * @property coverage coverage data achieved during calibration.
 */
@Serializable
data class CameraProfile(
    val schemaVersion: Int = 1,
    val profileVersion: Int = 1,
    val profileId: String = "",
    val createdAt: String = "",
    val calibrated: CalibrationResult? = null,
    val source: IntrinsicsSource = IntrinsicsSource.CALIBRATED,
    val metadata: FullMetadata? = null,
    val coverage: CoverageData? = null,
)
