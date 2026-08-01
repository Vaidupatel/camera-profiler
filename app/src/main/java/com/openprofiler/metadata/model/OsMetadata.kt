package com.openprofiler.metadata.model

import kotlinx.serialization.Serializable

/**
 * Operating system metadata properties.
 *
 * @property name OS name ("Android").
 * @property version OS release version string.
 * @property apiLevel Android API level integer.
 * @property codename Development codename.
 * @property release OS release version.
 * @property incremental Incremental build version.
 */
@Serializable
data class OsMetadata(
    val name: String = "Android",
    val version: String = "",
    val apiLevel: Int = 0,
    val codename: String? = null,
    val release: String? = null,
    val incremental: String? = null,
)
