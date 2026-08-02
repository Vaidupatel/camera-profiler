package com.openprofiler.detection

import androidx.camera.core.ImageProxy
import com.openprofiler.domain.model.BoardAxesOverlay
import com.openprofiler.domain.model.BoardConfig
import com.openprofiler.domain.model.BoardPose
import com.openprofiler.domain.model.DetectedCorner
import com.openprofiler.domain.model.DetectedMarker
import com.openprofiler.domain.model.DetectionResult
import com.openprofiler.domain.model.DetectionStatistics
import com.openprofiler.domain.model.IntrinsicsSource
import com.openprofiler.domain.model.ObservedBoundingBox
import com.openprofiler.domain.model.Point2D
import com.openprofiler.domain.repository.DetectionRepository
import com.openprofiler.native_bridge.NativeDetectionEngine
import com.openprofiler.native_bridge.NativeDetectionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DetectionRepositoryImpl @Inject constructor(
    private val boardConfigLoader: BoardConfigLoader,
    private val nativeEngine: NativeDetectionEngine
) : DetectionRepository {

    private var currentConfig: BoardConfig? = null

    override suspend fun initialize(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val config = boardConfigLoader.loadBoardConfig().getOrThrow()
            val configJson = Json.encodeToString(config)
            val success = nativeEngine.initialize(configJson)
            if (!success) {
                error("Failed to initialize NativeDetectionEngine")
            }
            currentConfig = config
            Timber.d("NativeDetectionEngine initialized successfully with config: $config")
        }
    }

    override suspend fun detectBoard(
        imageProxy: ImageProxy,
        cameraMatrix: DoubleArray?,
        distCoeffs: DoubleArray?,
        intrinsicsSource: IntrinsicsSource,
    ): DetectionResult = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()

        if (!nativeEngine.isInitialized()) {
            val initResult = initialize()
            if (initResult.isFailure) {
                return@withContext DetectionResult(
                    boardDetected = false,
                    dictionary = currentConfig?.dictionaryName ?: "UNKNOWN",
                    markerCount = 0,
                    charucoCornerCount = 0,
                    markerIds = emptyList(),
                    charucoIds = emptyList(),
                    cornerCoordinates = emptyList(),
                    boardPose = null,
                    detectionConfidence = 0.0f,
                    processingTimeMs = System.currentTimeMillis() - startTime,
                    rejectedReason = "Native detection engine not initialized"
                )
            }
        }

        try {
            val planes = imageProxy.planes
            if (planes.isEmpty()) {
                return@withContext createFailureResult("ImageProxy planes empty", startTime)
            }

            val yPlane = planes[0]
            val uPlane = if (planes.size > 1) planes[1] else yPlane
            val vPlane = if (planes.size > 2) planes[2] else yPlane

            val nativeResult = nativeEngine.detectBoard(
                yBuffer = yPlane.buffer,
                uBuffer = uPlane.buffer,
                vBuffer = vPlane.buffer,
                yRowStride = yPlane.rowStride,
                uvRowStride = uPlane.rowStride,
                uvPixelStride = uPlane.pixelStride,
                width = imageProxy.width,
                height = imageProxy.height,
                rotationDegrees = imageProxy.imageInfo.rotationDegrees,
                cameraMatrix = cameraMatrix,
                distCoeffs = distCoeffs
            )

            if (nativeResult == null) {
                return@withContext createFailureResult("Native detection returned null", startTime)
            }

            mapNativeResultToDomain(
                nativeResult,
                System.currentTimeMillis() - startTime,
                intrinsicsSource,
            )
        } catch (e: Exception) {
            Timber.e(e, "Error executing detectBoard")
            createFailureResult(e.message ?: "Unknown detection error", startTime)
        }
    }

    override fun getBoardConfig(): BoardConfig? = currentConfig

    override fun release() {
        nativeEngine.release()
        currentConfig = null
    }

    private fun mapNativeResultToDomain(
        native: NativeDetectionResult,
        elapsedTimeMs: Long,
        intrinsicsSource: IntrinsicsSource,
    ): DetectionResult {
        val corners = mutableListOf<DetectedCorner>()
        for (i in native.charucoIds.indices) {
            if (i < native.cornerX.size && i < native.cornerY.size) {
                corners.add(
                    DetectedCorner(
                        id = native.charucoIds[i],
                        x = native.cornerX[i],
                        y = native.cornerY[i],
                        // Missing precision means unmeasured — never fabricate 1.0.
                        subpixelPrecision = if (i < native.cornerPrecision.size) {
                            native.cornerPrecision[i]
                        } else {
                            0.0f
                        }
                    )
                )
            }
        }

        val markers = mutableListOf<DetectedMarker>()
        native.markerOutlineCoords?.let { coords ->
            val pointsPerMarker = 8 // 4 x,y pairs
            for (m in native.markerIds.indices) {
                val offset = m * pointsPerMarker
                if (offset + 7 < coords.size) {
                    val pts = listOf(
                        Point2D(coords[offset], coords[offset + 1]),
                        Point2D(coords[offset + 2], coords[offset + 3]),
                        Point2D(coords[offset + 4], coords[offset + 5]),
                        Point2D(coords[offset + 6], coords[offset + 7])
                    )
                    markers.add(DetectedMarker(id = native.markerIds[m], corners = pts))
                }
            }
        }

        val pose = if (native.rvec != null && native.tvec != null) {
            BoardPose(
                rvec = native.rvec,
                tvec = native.tvec,
                intrinsicsSource = intrinsicsSource,
            )
        } else null

        val axes = native.boardAxesCoords?.let { coords ->
            if (coords.size >= 8) {
                BoardAxesOverlay(
                    origin = Point2D(coords[0], coords[1]),
                    xAxisEnd = Point2D(coords[2], coords[3]),
                    yAxisEnd = Point2D(coords[4], coords[5]),
                    zAxisEnd = Point2D(coords[6], coords[7])
                )
            } else null
        }

        val boundingBox = native.boundingBoxCoords?.let { coords ->
            if (coords.size >= 8) {
                listOf(
                    Point2D(coords[0], coords[1]),
                    Point2D(coords[2], coords[3]),
                    Point2D(coords[4], coords[5]),
                    Point2D(coords[6], coords[7])
                )
            } else null
        }

        val observedBoundingBox = ObservedBoundingBox.fromCorners(corners)

        val timings = native.stageTimingsMs
        val stats = if (timings != null && timings.size >= 5) {
            DetectionStatistics(
                totalTimeMs = timings[0],
                markerDetectionTimeMs = timings[1],
                charucoInterpolationTimeMs = timings[2],
                subpixelRefinementTimeMs = timings[3],
                poseEstimationTimeMs = timings[4]
            )
        } else {
            DetectionStatistics(totalTimeMs = elapsedTimeMs)
        }

        return DetectionResult(
            boardDetected = native.boardDetected,
            dictionary = native.dictionaryName,
            markerCount = native.markerCount,
            charucoCornerCount = native.charucoCornerCount,
            markerIds = native.markerIds.toList(),
            charucoIds = native.charucoIds.toList(),
            cornerCoordinates = corners,
            boardPose = pose,
            detectionConfidence = native.detectionConfidence,
            processingTimeMs = native.processingTimeMs.takeIf { it > 0 } ?: elapsedTimeMs,
            rejectedReason = native.rejectedReason,
            detectedMarkers = markers,
            boardAxes = axes,
            boundingBox = boundingBox,
            observedBoundingBox = observedBoundingBox,
            statistics = stats
        )
    }

    private fun createFailureResult(reason: String, startTimeMs: Long): DetectionResult {
        return DetectionResult(
            boardDetected = false,
            dictionary = currentConfig?.dictionaryName ?: "DICT_5X5_1000",
            markerCount = 0,
            charucoCornerCount = 0,
            markerIds = emptyList(),
            charucoIds = emptyList(),
            cornerCoordinates = emptyList(),
            boardPose = null,
            detectionConfidence = 0.0f,
            processingTimeMs = System.currentTimeMillis() - startTimeMs,
            rejectedReason = reason
        )
    }
}
