package com.openprofiler.ui.viewmodel

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openprofiler.camera.CameraIntrinsicsProvider
import com.openprofiler.camera.CameraState
import com.openprofiler.common.util.DispatcherProvider
import com.openprofiler.common.util.ImageDimensionUtils
import com.openprofiler.common.util.Logger
import com.openprofiler.domain.model.CoverageData
import com.openprofiler.domain.model.CoverageGuidance
import com.openprofiler.domain.model.DetectionResult
import com.openprofiler.domain.model.IntrinsicsSource
import com.openprofiler.domain.model.QualityMetricStatus
import com.openprofiler.domain.model.QualityResult
import com.openprofiler.domain.repository.CalibrationRepository
import com.openprofiler.domain.repository.CameraRepository
import com.openprofiler.domain.repository.CameraSessionToken
import com.openprofiler.domain.repository.CoverageRepository
import com.openprofiler.domain.repository.DetectionRepository
import com.openprofiler.domain.repository.QualityRepository
import com.openprofiler.domain.usecase.DetectBoardUseCase
import com.openprofiler.domain.usecase.EvaluateCoverageUseCase
import com.openprofiler.domain.usecase.EvaluateQualityUseCase
import com.openprofiler.domain.usecase.StartCalibrationUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject

private const val TAG = "CalibrationViewModel"

/**
 * Immutable UI state for the calibration session screen.
 */
data class CalibrationUiState(
    val cameraState: CameraState = CameraState(),
    val detectionResult: DetectionResult? = null,
    val qualityResult: QualityResult? = null,
    val coverageData: CoverageData = CoverageData(),
    val guidance: CoverageGuidance? = null,
    val minAcceptedFrames: Int = 0,
    val isDetecting: Boolean = false,
    val targetStatusText: String = "Searching...",
    val qualityStatusText: String = "Waiting for frames",
)

/**
 * Owns the Calibration Session camera pipeline:
 * PreviewView → CameraX Preview + ImageAnalysis → DetectBoard → EvaluateQuality → UI state.
 *
 * Compose never controls CameraX directly. Camera session ownership is via
 * [CameraSessionToken] so a previous screen's stale stop cannot tear this session down.
 */
@HiltViewModel
class CalibrationViewModel @Inject constructor(
    private val cameraRepository: CameraRepository,
    private val detectBoardUseCase: DetectBoardUseCase,
    private val evaluateQualityUseCase: EvaluateQualityUseCase,
    private val evaluateCoverageUseCase: EvaluateCoverageUseCase,
    private val startCalibrationUseCase: StartCalibrationUseCase,
    private val detectionRepository: DetectionRepository,
    private val qualityRepository: QualityRepository,
    private val coverageRepository: CoverageRepository,
    private val calibrationRepository: CalibrationRepository,
    private val cameraIntrinsicsProvider: CameraIntrinsicsProvider,
    private val dispatcherProvider: DispatcherProvider,
) : ViewModel(), ImageAnalysis.Analyzer {

    private val _uiState = MutableStateFlow(CalibrationUiState())
    val uiState: StateFlow<CalibrationUiState> = _uiState.asStateFlow()

    private val isProcessingFrame = AtomicBoolean(false)
    private val framesAnalyzed = AtomicLong(0)
    private val seedIntrinsics =
        AtomicReference<Pair<Triple<Int, Int, Int>, com.openprofiler.domain.model.CameraIntrinsics?>?>(null)

    @Volatile
    private var sessionToken: CameraSessionToken = CameraSessionToken.None

    init {
        viewModelScope.launch(dispatcherProvider.main) {
            cameraRepository.cameraState.collect { camState ->
                _uiState.update { it.copy(cameraState = camState) }
            }
        }
        viewModelScope.launch(dispatcherProvider.io) {
            detectionRepository.initialize()
            qualityRepository.initialize()
            coverageRepository.initialize()
            startCalibrationUseCase()
            val thresholds = coverageRepository.getThresholds()
            _uiState.update { it.copy(minAcceptedFrames = thresholds.minAcceptedFrames) }
            Logger.i(TAG, "Detection, Quality, and Coverage engines initialized for calibration session")
        }
        cameraRepository.setFrameAnalyzer(this)
        Logger.i(TAG, "Calibration session ViewModel ready; analyzer registered")
    }

    override fun analyze(imageProxy: ImageProxy) {
        if (!isProcessingFrame.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }

        viewModelScope.launch(dispatcherProvider.default) {
            try {
                val rotationDegrees = imageProxy.imageInfo.rotationDegrees
                val rotatedWidth = ImageDimensionUtils.rotatedWidth(imageProxy.width, imageProxy.height, rotationDegrees)
                val rotatedHeight = ImageDimensionUtils.rotatedHeight(imageProxy.width, imageProxy.height, rotationDegrees)

                val seed = resolveSeedIntrinsics(rotatedWidth, rotatedHeight, rotationDegrees)
                val detection = detectBoardUseCase(
                    imageProxy = imageProxy,
                    cameraMatrix = seed?.cameraMatrix,
                    distCoeffs = seed?.distCoeffs,
                    intrinsicsSource = seed?.source ?: IntrinsicsSource.UNAVAILABLE,
                )
                val boardConfig = detectionRepository.getBoardConfig()
                val quality = evaluateQualityUseCase(
                    imageProxy = imageProxy,
                    detection = detection,
                    boardConfig = boardConfig
                )

                var coverageResult = coverageRepository.getCurrentCoverage()
                var currentGuidance: CoverageGuidance? = null

                if (quality.summary.isAccepted) {
                    val coverageEval = evaluateCoverageUseCase(
                        detection = detection,
                        frameWidth = rotatedWidth,
                        frameHeight = rotatedHeight
                    )
                    coverageResult = coverageEval.coverageData
                    currentGuidance = coverageEval.guidance

                    if (coverageEval.isAccepted) {
                        // Accepted frame! Record complete observation in the calibration corpus.
                        boardConfig?.let { config ->
                            calibrationRepository.addFrame(
                                detection = detection,
                                quality = quality,
                                boardConfig = config,
                                coverageContribution = coverageEval.coverageDelta
                            )
                        }

                        Logger.i(
                            TAG,
                            "FRAME_ACCEPTED frame=${framesAnalyzed.get()} delta=${coverageEval.coverageDelta} " +
                                "totalScore=${coverageEval.diversityScore} acceptedCount=${coverageResult.acceptedFrameCount}"
                        )
                    } else {
                        Logger.d(TAG, "FRAME_REJECTED_BY_COVERAGE: ${coverageEval.rejectReason}")
                    }
                }

                val count = framesAnalyzed.incrementAndGet()
                if (count == 1L || count % 30L == 0L) {
                    Logger.d(
                        TAG,
                        "Frame analyzed count=$count detected=${detection.boardDetected} " +
                            "markers=${detection.markerCount} corners=${detection.charucoCornerCount} " +
                            "qualityAccepted=${quality.summary.isAccepted} fails=${quality.summary.failCount} " +
                            "detMs=${detection.processingTimeMs} qMs=${quality.processingTimeMs} " +
                            "seed=${seed?.seedMethod ?: "none"} " +
                            "observedBBox=${detection.observedBoundingBox?.size ?: 0}"
                    )
                    if (!quality.summary.isAccepted) {
                        logFailingQualityMetrics(quality, detection, count)
                    }
                }

                _uiState.update {
                    it.copy(
                        detectionResult = detection,
                        qualityResult = quality,
                        coverageData = coverageResult,
                        guidance = currentGuidance,
                        isDetecting = detection.boardDetected,
                        targetStatusText = buildTargetStatus(detection),
                        qualityStatusText = buildQualityStatus(quality),
                    )
                }
            } catch (e: Exception) {
                Logger.e(TAG, "Calibration frame pipeline failed", e)
            } finally {
                imageProxy.close()
                isProcessingFrame.set(false)
            }
        }
    }

    fun startCamera(lifecycleOwner: LifecycleOwner, surfaceProvider: Preview.SurfaceProvider) {
        Logger.i(
            TAG,
            "Calibration startCamera lifecycleState=${lifecycleOwner.lifecycle.currentState}"
        )
        cameraRepository.setFrameAnalyzer(this)
        sessionToken = cameraRepository.startCamera(lifecycleOwner, surfaceProvider)
        Logger.i(TAG, "Acquired camera session=${sessionToken.id}")
    }

    fun stopCamera() {
        val token = sessionToken
        Logger.i(TAG, "Calibration stopCamera session=${token.id}")
        cameraRepository.stopCamera(token)
        if (token == sessionToken) {
            sessionToken = CameraSessionToken.None
        }
    }

    override fun onCleared() {
        Logger.i(TAG, "onCleared — stopping session and clearing analyzer")
        cameraRepository.clearFrameAnalyzer(this)
        stopCamera()
        qualityRepository.resetMotionState()
        super.onCleared()
    }

    /**
     * Resolves factory seed K/D for this analysis resolution.
     * Never invents a heuristic when the provider returns null.
     */
    private fun resolveSeedIntrinsics(
        width: Int,
        height: Int,
        rotationDegrees: Int,
    ): com.openprofiler.domain.model.CameraIntrinsics? {
        val sizeKey = Triple(width, height, rotationDegrees)
        seedIntrinsics.get()?.let { (cachedKey, cachedIntrinsics) ->
            if (cachedKey == sizeKey) return cachedIntrinsics
        }
        val resolved = cameraIntrinsicsProvider.getSeedIntrinsics(
            cameraId = null,
            imageWidthPx = width,
            imageHeightPx = height,
            rotationDegrees = rotationDegrees,
        )
        seedIntrinsics.set(sizeKey to resolved)
        if (resolved == null) {
            Logger.w(
                TAG,
                "No device-reported seed intrinsics for ${width}x${height}; " +
                    "pose estimation will be skipped (null over guess)"
            )
        } else {
            Logger.i(
                TAG,
                "Using seed intrinsics source=${resolved.source} method=${resolved.seedMethod}"
            )
        }
        return resolved
    }

    private fun logFailingQualityMetrics(
        quality: QualityResult,
        detection: DetectionResult,
        frameCount: Long,
    ) {
        val fails = quality.metrics.filter { it.status == QualityMetricStatus.FAIL }
        for (m in fails) {
            Logger.d(
                TAG,
                "QUALITY_FAIL frame=$frameCount id=${m.id} value=${m.value} " +
                    "threshold=${m.threshold} reason=${m.reason} secondary=${m.secondaryValues}"
            )
        }
        // Coverage / corner distribution for root-cause without guessing thresholds.
        val box = detection.observedBoundingBox
        if (box != null && box.size >= 4 && quality.frameWidthPx > 0 && quality.frameHeightPx > 0) {
            val minX = box.minOf { it.x }
            val maxX = box.maxOf { it.x }
            val minY = box.minOf { it.y }
            val maxY = box.maxOf { it.y }
            val wPct = (maxX - minX) / quality.frameWidthPx * 100.0
            val hPct = (maxY - minY) / quality.frameHeightPx * 100.0
            val aPct = (maxX - minX) * (maxY - minY) /
                (quality.frameWidthPx.toDouble() * quality.frameHeightPx) * 100.0
            Logger.d(
                TAG,
                "QUALITY_DIAG coverage frame=${quality.frameWidthPx}x${quality.frameHeightPx} " +
                    "observedBox=[${minX},${minY}]-[${maxX},${maxY}] " +
                    "widthPct=$wPct heightPct=$hPct areaPct=$aPct"
            )
        } else {
            Logger.d(
                TAG,
                "QUALITY_DIAG coverage observedBoundingBox=" +
                    "${box?.size ?: "null"} (unavailable for TARGET_COVERAGE)"
            )
        }
        val precisions = detection.cornerCoordinates.map { it.subpixelPrecision }
        if (precisions.isNotEmpty()) {
            val sorted = precisions.sorted()
            val avg = precisions.average()
            val p50 = sorted[sorted.size / 2]
            val p10 = sorted[(sorted.size * 0.1).toInt().coerceAtMost(sorted.lastIndex)]
            Logger.d(
                TAG,
                "QUALITY_DIAG cornerPrecision n=${precisions.size} avg=$avg " +
                    "min=${sorted.first()} p10=$p10 p50=$p50 max=${sorted.last()} " +
                    "poseSource=${detection.boardPose?.intrinsicsSource}"
            )
        } else {
            Logger.d(TAG, "QUALITY_DIAG cornerPrecision empty")
        }
    }

    private fun buildTargetStatus(detection: DetectionResult): String {
        return when {
            detection.boardDetected ->
                "Target locked · markers=${detection.markerCount} corners=${detection.charucoCornerCount}"
            !detection.rejectedReason.isNullOrBlank() ->
                "Searching... (${detection.rejectedReason})"
            else -> "Searching..."
        }
    }

    private fun buildQualityStatus(quality: QualityResult): String {
        val summary = quality.summary
        return if (summary.isAccepted) {
            "Quality: PASS (score=${"%.2f".format(summary.overallScore)})"
        } else {
            "Quality: FAIL — ${summary.primaryRejectReason ?: "thresholds not met"}"
        }
    }
}
