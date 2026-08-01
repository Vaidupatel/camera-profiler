package com.openprofiler.ui.viewmodel

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openprofiler.camera.CameraState
import com.openprofiler.common.util.DispatcherProvider
import com.openprofiler.common.util.Logger
import com.openprofiler.domain.model.DetectionResult
import com.openprofiler.domain.model.QualityResult
import com.openprofiler.domain.repository.CameraRepository
import com.openprofiler.domain.repository.CameraSessionToken
import com.openprofiler.domain.repository.DetectionRepository
import com.openprofiler.domain.repository.QualityRepository
import com.openprofiler.domain.usecase.DetectBoardUseCase
import com.openprofiler.domain.usecase.EvaluateQualityUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

private const val TAG = "CalibrationViewModel"

/**
 * Immutable UI state for the calibration session screen.
 *
 * Coverage counters remain placeholders until Phase 6 — this ViewModel only
 * owns camera session, detection, and quality evaluation (Phase 4/5 integration).
 */
data class CalibrationUiState(
    val cameraState: CameraState = CameraState(),
    val detectionResult: DetectionResult? = null,
    val qualityResult: QualityResult? = null,
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
    private val detectionRepository: DetectionRepository,
    private val qualityRepository: QualityRepository,
    private val dispatcherProvider: DispatcherProvider,
) : ViewModel(), ImageAnalysis.Analyzer {

    private val _uiState = MutableStateFlow(CalibrationUiState())
    val uiState: StateFlow<CalibrationUiState> = _uiState.asStateFlow()

    private val isProcessingFrame = AtomicBoolean(false)
    private val framesAnalyzed = AtomicLong(0)

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
            Logger.i(TAG, "Detection and Quality engines initialized for calibration session")
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
                val detection = detectBoardUseCase(imageProxy)
                val boardConfig = detectionRepository.getBoardConfig()
                val quality = evaluateQualityUseCase(
                    imageProxy = imageProxy,
                    detection = detection,
                    boardConfig = boardConfig
                )

                val count = framesAnalyzed.incrementAndGet()
                if (count == 1L || count % 30L == 0L) {
                    Logger.d(
                        TAG,
                        "Frame analyzed count=$count detected=${detection.boardDetected} " +
                            "qualityAccepted=${quality.summary.isAccepted} " +
                            "detMs=${detection.processingTimeMs} qMs=${quality.processingTimeMs}"
                    )
                }
                Logger.d(
                    TAG,
                    "Detection result received boardDetected=${detection.boardDetected}; " +
                        "quality accepted=${quality.summary.isAccepted}"
                )

                _uiState.update {
                    it.copy(
                        detectionResult = detection,
                        qualityResult = quality,
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
