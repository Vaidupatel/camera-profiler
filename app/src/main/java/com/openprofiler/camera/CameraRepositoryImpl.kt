package com.openprofiler.camera

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.openprofiler.common.util.Logger
import com.openprofiler.domain.repository.CameraRepository
import com.openprofiler.domain.repository.CameraSessionToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "CameraRepository"

/**
 * CameraX-backed implementation of [CameraRepository].
 *
 * Session ownership:
 * Each [startCamera] call atomically installs a new [CameraSessionToken] as the sole owner.
 * [stopCamera] and async [bindCameraUseCases] only mutate the CameraX binding when the
 * caller's token still matches that owner. A disposed screen's late [stopCamera] with a
 * stale token is a deterministic no-op and cannot [ProcessCameraProvider.unbindAll] a
 * newer session (e.g. CalibrationScreen after CameraScreen dispose).
 *
 * Exactly one [ImageAnalysis.Analyzer] may be registered; [clearFrameAnalyzer] uses
 * identity equality, mirroring the session-token guard on start/stop.
 */
@Singleton
class CameraRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cameraProviderClient: CameraProviderClient,
    private val useCaseBinder: CameraUseCaseBinder,
) : CameraRepository {

    private val _cameraState = MutableStateFlow(CameraState())
    override val cameraState: StateFlow<CameraState> = _cameraState.asStateFlow()

    private val sessionLock = Any()
    private val sessionGeneration = AtomicLong(0)
    private var currentSession: CameraSessionToken = CameraSessionToken.None

    private var cameraProvider: ProcessCameraProvider? = null
    private var previewUseCase: Preview? = null
    private var imageCaptureUseCase: ImageCapture? = null
    private var imageAnalysisUseCase: ImageAnalysis? = null
    private var frameAnalyzer: ImageAnalysis.Analyzer? = null

    private var currentLifecycleOwner: LifecycleOwner? = null
    private var currentSurfaceProvider: Preview.SurfaceProvider? = null
    private var sessionActive: Boolean = false
    private var boundLensFacing: Int? = null
    private var boundLifecycleOwner: LifecycleOwner? = null
    private var useCaseGraphBound: Boolean = false

    private val analysisExecutor = Executors.newSingleThreadExecutor()
    private val framesReceived = AtomicLong(0)
    private val framesDeliveredToAnalyzer = AtomicLong(0)

    override fun startCamera(
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider,
    ): CameraSessionToken {
        val token: CameraSessionToken
        synchronized(sessionLock) {
            token = CameraSessionToken(sessionGeneration.incrementAndGet())
            currentSession = token
            currentLifecycleOwner = lifecycleOwner
            currentSurfaceProvider = surfaceProvider
            Logger.i(
                TAG,
                "startCamera issued session=${token.id} " +
                    "lifecycleState=${lifecycleOwner.lifecycle.currentState} " +
                    "sessionActive=$sessionActive"
            )
        }

        cameraProviderClient.get(
            onReady = { provider ->
                synchronized(sessionLock) {
                    if (token != currentSession) {
                        Logger.w(
                            TAG,
                            "Ignoring stale bind for session=${token.id} " +
                                "current=${currentSession.id}"
                        )
                        return@synchronized
                    }
                    cameraProvider = provider
                    bindCameraUseCasesLocked(expectedSession = token)
                }
            },
            onError = { e ->
                synchronized(sessionLock) {
                    if (token != currentSession) {
                        Logger.w(
                            TAG,
                            "Ignoring provider error for stale session=${token.id} " +
                                "current=${currentSession.id}"
                        )
                        return@synchronized
                    }
                    Logger.e(TAG, "Failed to obtain ProcessCameraProvider", e)
                    _cameraState.update {
                        it.copy(error = e.localizedMessage ?: "Camera initialization failed")
                    }
                }
            },
        )
        return token
    }

    /**
     * Must be called while holding [sessionLock].
     * Re-checks [expectedSession] so a late bind cannot overwrite a newer owner.
     *
     * When an identical use-case graph is already bound (same lens, same lifecycle owner,
     * Preview/ImageCapture/ImageAnalysis present), only the Preview surface and analyzer
     * are updated — no [ProcessCameraProvider.unbindAll].
     */
    private fun bindCameraUseCasesLocked(expectedSession: CameraSessionToken) {
        if (expectedSession != currentSession) {
            Logger.w(
                TAG,
                "Refusing bind: expected session=${expectedSession.id} " +
                    "current=${currentSession.id}"
            )
            return
        }

        val provider = cameraProvider ?: return
        val lifecycleOwner = currentLifecycleOwner ?: return
        val surfaceProvider = currentSurfaceProvider ?: return

        val lifecycleState = lifecycleOwner.lifecycle.currentState
        if (!lifecycleState.isAtLeast(Lifecycle.State.INITIALIZED)) {
            Logger.w(TAG, "Refusing bind: lifecycle state=$lifecycleState")
            return
        }

        val lensFacing = _cameraState.value.lensFacing

        if (canReuseBoundUseCases(lifecycleOwner, lensFacing)) {
            Logger.i(
                TAG,
                "Reusing bound use cases (lens=$lensFacing) — " +
                    "updating Preview surface / analyzer only, skip unbindAll"
            )
            previewUseCase?.setSurfaceProvider(surfaceProvider)
            frameAnalyzer?.let { analyzer ->
                imageAnalysisUseCase?.let { analysis ->
                    analysis.clearAnalyzer()
                    analysis.setAnalyzer(analysisExecutor, createLoggingAnalyzer(analyzer))
                }
            }
            sessionActive = true
            useCaseGraphBound = true
            _cameraState.update {
                it.copy(isStreaming = true, error = null)
            }
            return
        }

        try {
            Logger.i(TAG, "Camera unbound (preparing rebind) lifecycleState=$lifecycleState")
            sessionActive = false
            Logger.i(TAG, "Preview detached / ImageAnalysis stopped")

            val bound = useCaseBinder.bind(
                provider = provider,
                lifecycleOwner = lifecycleOwner,
                surfaceProvider = surfaceProvider,
                lensFacing = lensFacing,
                analyzer = frameAnalyzer?.let { createLoggingAnalyzer(it) },
                analysisExecutor = analysisExecutor,
            )

            previewUseCase = bound.preview
            imageCaptureUseCase = bound.imageCapture
            imageAnalysisUseCase = bound.imageAnalysis
            boundLensFacing = lensFacing
            boundLifecycleOwner = lifecycleOwner
            useCaseGraphBound = true

            if (frameAnalyzer != null) {
                Logger.i(TAG, "ImageAnalysis started with registered analyzer")
            } else {
                Logger.w(TAG, "ImageAnalysis bound without analyzer")
            }

            // Ownership may have changed while binding (another startCamera).
            if (expectedSession != currentSession) {
                Logger.w(
                    TAG,
                    "Session ${expectedSession.id} lost ownership during bind; " +
                        "unbinding stale graph (current=${currentSession.id})"
                )
                useCaseBinder.unbindAll(provider)
                previewUseCase = null
                imageCaptureUseCase = null
                imageAnalysisUseCase = null
                boundLensFacing = null
                boundLifecycleOwner = null
                useCaseGraphBound = false
                sessionActive = false
                return
            }

            sessionActive = true
            useCaseGraphBound = true
            _cameraState.update {
                it.copy(
                    isStreaming = true,
                    error = null,
                    resolution = _cameraState.value.resolution,
                )
            }
            Logger.i(
                TAG,
                "Camera bound successfully session=${expectedSession.id} " +
                    "lens=$lensFacing lifecycleState=$lifecycleState"
            )
        } catch (e: Exception) {
            sessionActive = false
            boundLensFacing = null
            boundLifecycleOwner = null
            useCaseGraphBound = false
            Logger.e(TAG, "Use case binding failed", e)
            _cameraState.update {
                it.copy(error = e.localizedMessage ?: "Binding camera use cases failed")
            }
        }
    }

    /**
     * True when Preview+Capture+Analysis are already bound for the same lens and lifecycle,
     * so only surface/analyzer updates are required.
     */
    private fun canReuseBoundUseCases(
        lifecycleOwner: LifecycleOwner,
        lensFacing: Int,
    ): Boolean {
        return sessionActive &&
            useCaseGraphBound &&
            boundLensFacing == lensFacing &&
            boundLifecycleOwner === lifecycleOwner
    }

    private fun createLoggingAnalyzer(
        analyzer: ImageAnalysis.Analyzer,
    ): ImageAnalysis.Analyzer = ImageAnalysis.Analyzer { imageProxy ->
        val received = framesReceived.incrementAndGet()
        if (received == 1L || received % 30L == 0L) {
            Logger.d(
                TAG,
                "Frame received count=$received ${imageProxy.width}x${imageProxy.height}"
            )
            _cameraState.update {
                it.copy(resolution = "${imageProxy.width}x${imageProxy.height}")
            }
        }
        framesDeliveredToAnalyzer.incrementAndGet()
        analyzer.analyze(imageProxy)
    }

    override fun switchCamera() {
        synchronized(sessionLock) {
            if (currentSession.isNone || !sessionActive) {
                Logger.w(TAG, "switchCamera ignored — no active owned session")
                return
            }
            val newLensFacing =
                if (_cameraState.value.lensFacing == CameraSelector.LENS_FACING_BACK) {
                    CameraSelector.LENS_FACING_FRONT
                } else {
                    CameraSelector.LENS_FACING_BACK
                }
            Logger.i(TAG, "switchCamera to lensFacing=$newLensFacing session=${currentSession.id}")
            _cameraState.update { it.copy(lensFacing = newLensFacing) }
            bindCameraUseCasesLocked(expectedSession = currentSession)
        }
    }

    override fun setFrameAnalyzer(analyzer: ImageAnalysis.Analyzer) {
        Logger.i(TAG, "setFrameAnalyzer analyzer=${analyzer.javaClass.simpleName}")
        synchronized(sessionLock) {
            frameAnalyzer = analyzer
            // Re-wrap so frame-received logging stays active when analyzer is set after bind.
            imageAnalysisUseCase?.let { analysis ->
                analysis.clearAnalyzer()
                analysis.setAnalyzer(analysisExecutor, createLoggingAnalyzer(analyzer))
                Logger.i(TAG, "ImageAnalysis analyzer updated on active session")
            }
        }
    }

    override fun clearFrameAnalyzer(analyzer: ImageAnalysis.Analyzer) {
        synchronized(sessionLock) {
            if (frameAnalyzer === analyzer) {
                Logger.i(TAG, "clearFrameAnalyzer removing ${analyzer.javaClass.simpleName}")
                frameAnalyzer = null
                imageAnalysisUseCase?.clearAnalyzer()
                Logger.i(TAG, "ImageAnalysis analyzer cleared")
            }
        }
    }

    override fun takePhoto(
        onImageCaptured: (ImageProxy) -> Unit,
        onError: (Exception) -> Unit,
    ) {
        val capture = imageCaptureUseCase ?: run {
            onError(IllegalStateException("ImageCapture use case not initialized"))
            return
        }

        capture.takePicture(
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    onImageCaptured(image)
                }

                override fun onError(exception: ImageCaptureException) {
                    Logger.e(TAG, "Image capture failed", exception)
                    onError(exception)
                }
            },
        )
    }

    override fun stopCamera(session: CameraSessionToken) {
        synchronized(sessionLock) {
            if (session.isNone || session != currentSession) {
                Logger.i(
                    TAG,
                    "Ignoring stopCamera for stale/foreign session=${session.id} " +
                        "current=${currentSession.id} sessionActive=$sessionActive"
                )
                return
            }

            Logger.i(TAG, "stopCamera session=${session.id} sessionActive=$sessionActive")
            cameraProvider?.let { useCaseBinder.unbindAll(it) }
            previewUseCase = null
            imageCaptureUseCase = null
            imageAnalysisUseCase = null
            currentSurfaceProvider = null
            currentLifecycleOwner = null
            boundLensFacing = null
            boundLifecycleOwner = null
            useCaseGraphBound = false
            sessionActive = false
            currentSession = CameraSessionToken.None
            _cameraState.update { it.copy(isStreaming = false) }
            Logger.i(TAG, "Camera unbound; Preview detached; ImageAnalysis stopped")
        }
    }

    override fun isSessionActive(): Boolean = synchronized(sessionLock) { sessionActive }

    override fun currentSessionToken(): CameraSessionToken =
        synchronized(sessionLock) { currentSession }
}
