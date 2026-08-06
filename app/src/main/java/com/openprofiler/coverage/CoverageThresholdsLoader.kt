package com.openprofiler.coverage

import android.content.Context
import com.openprofiler.domain.model.CoverageThresholds
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loads [CoverageThresholds] from application assets.
 */
@Singleton
class CoverageThresholdsLoader @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Loads thresholds from [assetFileName] (default: coverage.json).
     */
    fun load(assetFileName: String = "coverage.json"): Result<CoverageThresholds> {
        return runCatching {
            val text = context.assets.open(assetFileName).bufferedReader().use { it.readText() }
            json.decodeFromString<CoverageThresholds>(text).also {
                Timber.d("Loaded coverage thresholds from %s", assetFileName)
            }
        }
    }
}
