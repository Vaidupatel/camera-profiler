package com.openprofiler.quality

import android.content.Context
import com.openprofiler.domain.model.QualityThresholds
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loads [QualityThresholds] from application assets.
 */
@Singleton
class QualityThresholdsLoader @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Loads thresholds from [assetFileName] (default: quality.json).
     */
    fun load(assetFileName: String = "quality.json"): Result<QualityThresholds> {
        return runCatching {
            val text = context.assets.open(assetFileName).bufferedReader().use { it.readText() }
            json.decodeFromString<QualityThresholds>(text).also {
                Timber.d("Loaded quality thresholds from %s", assetFileName)
            }
        }
    }
}
