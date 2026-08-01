package com.openprofiler.metadata.model

import kotlinx.serialization.Serializable

/**
 * Application metadata properties.
 *
 * @property appName Name of the application.
 * @property appVersion Version name of the application.
 * @property opencvVersion OpenCV native C++ engine version.
 * @property serializationVersion kotlinx.serialization version.
 */
@Serializable
data class AppMetadata(
    val appName: String = "Open Camera Profiler",
    val appVersion: String = "1.0.0",
    val opencvVersion: String = "4.10.0",
    val serializationVersion: String = "1.7.3",
)
