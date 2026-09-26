package com.openprofiler.calibration

import com.openprofiler.domain.model.*
import com.openprofiler.domain.repository.CalibrationRepository
import com.openprofiler.native_bridge.NativeCalibrationEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CalibrationRepositoryImpl @Inject constructor(
    private val session: CalibrationSession,
    private val nativeEngine: NativeCalibrationEngine
) : CalibrationRepository {

    private var lastWidth: Int = 0
    private var lastHeight: Int = 0

    override fun addFrame(
        detection: DetectionResult,
        quality: QualityResult,
        boardConfig: BoardConfig,
        coverageContribution: Double,
        metadata: Map<String, String>
    ) {
        val width = detection.frameWidth
        val height = detection.frameHeight

        if (session.getAcceptedFrameCount() > 0 && (width != lastWidth || height != lastHeight)) {
            com.openprofiler.common.util.Logger.w(
                "CalibrationRepository",
                "Rejecting frame with inconsistent dimensions: ${width}x${height} " +
                    "(session expected ${lastWidth}x${lastHeight})"
            )
            return
        }

        val observation = FrameObservation(
            timestamp = System.currentTimeMillis(),
            width = width,
            height = height,
            rotation = 0, // TODO: Get actual rotation if needed
            markerIds = detection.markerIds,
            charucoIds = detection.charucoIds,
            subpixelCorners = detection.cornerCoordinates.map { Point2D(it.x, it.y) },
            objectPoints = computeObjectPoints(detection.charucoIds, boardConfig),
            imagePoints = detection.cornerCoordinates.map { Point2D(it.x, it.y) },
            pose = detection.boardPose,
            coverageContribution = coverageContribution,
            qualitySummary = quality.summary,
            metadata = metadata
        )

        session.addObservation(observation)
        lastWidth = width
        lastHeight = height
    }

    override fun addFrame(
        corners: List<DetectedCorner>,
        boardConfig: BoardConfig,
        width: Int,
        height: Int
    ) {
        // Legacy support - create a minimal observation
        val charucoIds = corners.map { it.id }
        val observation = FrameObservation(
            timestamp = System.currentTimeMillis(),
            width = width,
            height = height,
            rotation = 0,
            markerIds = emptyList(),
            charucoIds = charucoIds,
            subpixelCorners = corners.map { Point2D(it.x, it.y) },
            objectPoints = computeObjectPoints(charucoIds, boardConfig),
            imagePoints = corners.map { Point2D(it.x, it.y) },
            pose = null,
            coverageContribution = 0.0,
            qualitySummary = null
        )
        session.addObservation(observation)
        lastWidth = width
        lastHeight = height
    }

    private fun computeObjectPoints(charucoIds: List<Int>, boardConfig: BoardConfig): List<Point3D> {
        val squareSize = boardConfig.squareLengthMm / 1000f
        val cornersX = boardConfig.squaresX - 1
        return charucoIds.map { id ->
            val row = id / cornersX
            val col = id % cornersX
            Point3D(col * squareSize, row * squareSize, 0f)
        }
    }

    override fun reset() {
        session.reset()
    }

    override fun getAcceptedFrameCount(): Int {
        return session.getAcceptedFrameCount()
    }

    override fun getDataset(boardConfig: BoardConfig): CalibrationDataset {
        return CalibrationDataset(
            observations = session.getObservations(),
            boardConfig = boardConfig,
            deviceName = android.os.Build.MODEL,
            cameraName = if (lastWidth > 0) "Primary Camera" else "Unknown"
        )
    }

    override suspend fun solve(): CalibrationResult = withContext(Dispatchers.Default) {
        val observations = session.getObservations()
        if (observations.isEmpty() || lastWidth <= 0 || lastHeight <= 0) {
            return@withContext CalibrationResult(success = false)
        }

        val objPoints = session.getObjectPoints()
        val imgPoints = session.getImagePoints()
        
        val nativeResult = nativeEngine.calibrate(
            objPoints,
            imgPoints,
            lastWidth,
            lastHeight
        ) ?: return@withContext CalibrationResult(success = false)
        
        val stats = computeStatistics(nativeResult, observations)
        val uncertainty = computeUncertainty(nativeResult)

        CalibrationResult(
            rms = nativeResult.rms,
            cameraMatrix = listOf(
                nativeResult.cameraMatrix.slice(0..2).toList(),
                nativeResult.cameraMatrix.slice(3..5).toList(),
                nativeResult.cameraMatrix.slice(6..8).toList()
            ),
            distortionCoefficients = nativeResult.distCoeffs.toList(),
            imageCount = observations.size - nativeResult.rejectedFrames.size,
            success = nativeResult.success,
            perViewErrors = nativeResult.perViewErrors.toList(),
            rejectedFrames = nativeResult.rejectedFrames.toList(),
            residuals = nativeResult.residuals.toList(),
            statistics = stats,
            uncertainty = uncertainty,
            dataset = getDataset(BoardConfig("", 0, 0, 0f, 0f)), // Dummy, will be replaced by caller
            timestamp = System.currentTimeMillis()
        )
    }

    private fun computeStatistics(
        native: com.openprofiler.native_bridge.NativeCalibrationResult,
        observations: List<AcceptedObservation>
    ): CalibrationStatistics {
        val errors = native.perViewErrors.sorted()
        return CalibrationStatistics(
            meanReprojectionError = errors.average(),
            medianReprojectionError = if (errors.isNotEmpty()) errors[errors.size / 2] else 0.0,
            maxReprojectionError = errors.lastOrNull() ?: 0.0,
            p95ReprojectionError = if (errors.isNotEmpty()) errors[(errors.size * 0.95).toInt().coerceAtMost(errors.size - 1)] else 0.0,
            cornerRms = native.rms, // OpenCV RMS is usually per-corner
            frameRms = native.rms, // Simplified
            coveragePercent = 0.0, // Should be set by caller from CoverageRepository
            confidenceScore = 1.0 - (native.rms / 2.0).coerceIn(0.0, 1.0) // Heuristic
        )
    }

    private fun computeUncertainty(
        native: com.openprofiler.native_bridge.NativeCalibrationResult
    ): CalibrationUncertainty {
        return CalibrationUncertainty(
            cameraMatrixStdDev = native.stdDevIntrinsics.sliceArray(0..3).toList(),
            distortionStdDev = native.stdDevIntrinsics.sliceArray(4 until native.stdDevIntrinsics.size).toList(),
            intrinsicConfidence = 0.9,
            distortionConfidence = 0.9,
            coverageConfidence = 0.9,
            datasetConfidence = 0.9,
            overallConfidence = 0.9
        )
    }
}
