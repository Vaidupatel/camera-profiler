package com.openprofiler.metadata.model

import kotlinx.serialization.Serializable

/**
 * Metadata properties collected from Android Build.
 *
 * @property manufacturer Device manufacturer (e.g. "Samsung", "Google").
 * @property brand Commercial brand name.
 * @property model Model name or number.
 * @property device Industrial design / device code.
 * @property product Product name.
 * @property board Hardware board name.
 * @property hardware Hardware name from kernel or bootloader.
 * @property fingerprint Build fingerprint string.
 * @property supportedAbis Supported CPU ABIs.
 * @property sdk Android SDK API level.
 * @property securityPatch Security patch level date string.
 * @property buildId Build ID string.
 */
@Serializable
data class DeviceMetadata(
    val manufacturer: String? = null,
    val brand: String? = null,
    val model: String? = null,
    val device: String? = null,
    val product: String? = null,
    val board: String? = null,
    val hardware: String? = null,
    val fingerprint: String? = null,
    val supportedAbis: List<String> = emptyList(),
    val sdk: Int = 0,
    val securityPatch: String? = null,
    val buildId: String? = null,
)
