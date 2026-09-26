package com.openprofiler.domain.usecase

import com.openprofiler.common.util.DispatcherProvider
import com.openprofiler.domain.model.*
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

/**
 * Handles the generation of human-readable reports and production JSON exports.
 */
class ExportCalibrationUseCase @Inject constructor(
    private val dispatcherProvider: DispatcherProvider
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    suspend fun exportJson(result: CalibrationResult): String = withContext(dispatcherProvider.io) {
        val export = ProductionCalibrationExport(
            timestamp = result.timestamp,
            device = DeviceInfo(android.os.Build.MODEL, result.dataset?.cameraName ?: "Unknown"),
            result = result,
            report = generateReport(result)
        )
        json.encodeToString(export)
    }

    fun generateReport(result: CalibrationResult): CalibrationReport {
        val stats = result.statistics ?: CalibrationStatistics(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
        
        val datasetSummary = "Calibration session using ${result.imageCount} frames on ${result.dataset?.deviceName ?: "Unknown"}"
        val validationSummary = buildString {
            append("RMS Error: ${"%.4f".format(result.rms)} px. ")
            append("Mean Error: ${"%.4f".format(stats.meanReprojectionError)} px. ")
            append("Max Error: ${"%.4f".format(stats.maxReprojectionError)} px. ")
            if (result.rejectedFrames.isNotEmpty()) {
                append("Rejected ${result.rejectedFrames.size} outlier frames.")
            }
        }

        return CalibrationReport(
            datasetSummary = datasetSummary,
            frameCount = result.imageCount,
            rejectedFrameCount = result.rejectedFrames.size,
            coveragePercent = stats.coveragePercent,
            cameraMatrix = result.cameraMatrix,
            distortionCoefficients = result.distortionCoefficients,
            overallRms = result.rms,
            confidence = stats.confidenceScore,
            validationSummary = validationSummary,
            timestamp = result.timestamp
        )
    }
}
