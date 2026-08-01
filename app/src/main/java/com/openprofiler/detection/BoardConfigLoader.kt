package com.openprofiler.detection

import android.content.Context
import com.openprofiler.domain.model.BoardConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loads board configuration specifications from application assets.
 */
@Singleton
class BoardConfigLoader @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Loads target board configuration from asset file (default: "charuco_board.json").
     */
    fun loadBoardConfig(assetFileName: String = "charuco_board.json"): Result<BoardConfig> {
        return runCatching {
            val jsonString = context.assets.open(assetFileName).bufferedReader().use { it.readText() }
            json.decodeFromString<BoardConfig>(jsonString)
        }
    }
}
