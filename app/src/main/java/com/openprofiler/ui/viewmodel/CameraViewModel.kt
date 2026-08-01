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
import com.openprofiler.domain.repository.CameraRepository
import com.openprofiler.domain.repository.CameraSessionToken
import com.openprofiler.domain.usecase.DetectBoardUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

private const val TAG = "CameraViewModel"

/**
 * Immutable UI state for the [com.openprofiler.ui.screens.CameraScreen].
 */
data class CameraUiState(
    val cameraState: CameraState = CameraState(),
    val detectionResult: DetectionResult? = null,
    val isDetecting: Boolean = false,
)

/**
 * ViewModel for the pre-session live camera screen.
 * Compose observes [uiState] only — CameraX is owned by [CameraRepository].
 *
 * Holds the [CameraSessionToken] issued by [startCamera] and releases only that
 * ownership in [stopCamera], so a late Compose dispose cannot unbind a newer session.
 */
@HiltViewModel
class CameraViewModel @Inject constructor(
    private val cameraRepository: CameraRepository,
    private val detectBoardUseCase: DetectBoardUseCase,
    private val dispatcherProvider: DispatcherProvider,
) : ViewModel(), ImageAnalysis.Analyzer {

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

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
        cameraRepository.setFrameAnalyzer(this)
        Logger.i(TAG, "Analyzer registered with CameraRepository")
    }

    override fun analyze(imageProxy: ImageProxy) {
        if (!isProcessingFrame.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }

        viewModelScope.launch(dispatcherProvider.default) {
            try {
                val result = detectBoardUseCase(imageProxy)
                val count = framesAnalyzed.incrementAndGet()
                if (count == 1L || count % 30L == 0L) {
                    Logger.d(
                        TAG,
                        "Frame analyzed count=$count boardDetected=${result.boardDetected} " +
                            "markers=${result.markerCount} timeMs=${result.processingTimeMs}"
                    )
                }
                Logger.d(TAG, "Detection result received boardDetected=${result.boardDetected}")
                _uiState.update {
                    it.copy(
                        detectionResult = result,
                        isDetecting = result.boardDetected,
                    )
                }
            } catch (e: Exception) {
                Logger.e(TAG, "Detection analysis failed", e)
            } finally {
                imageProxy.close()
                isProcessingFrame.set(false)
            }
        }
    }

    fun startCamera(lifecycleOwner: LifecycleOwner, surfaceProvider: Preview.SurfaceProvider) {
        Logger.i(TAG, "startCamera lifecycleState=${lifecycleOwner.lifecycle.currentState}")
        cameraRepository.setFrameAnalyzer(this)
        sessionToken = cameraRepository.startCamera(lifecycleOwner, surfaceProvider)
        Logger.i(TAG, "Acquired camera session=${sessionToken.id}")
    }

    fun switchCamera() {
        cameraRepository.switchCamera()
    }

    fun stopCamera() {
        val token = sessionToken
        Logger.i(TAG, "stopCamera session=${token.id}")
        cameraRepository.stopCamera(token)
        if (token == sessionToken) {
            sessionToken = CameraSessionToken.None
        }
    }

    override fun onCleared() {
        Logger.i(TAG, "onCleared — clearing analyzer ownership")
        cameraRepository.clearFrameAnalyzer(this)
        super.onCleared()
    }
}
