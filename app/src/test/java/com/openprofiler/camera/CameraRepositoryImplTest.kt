package com.openprofiler.camera

import android.content.Context
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.google.common.truth.Truth.assertThat
import com.openprofiler.domain.repository.CameraSessionToken
import io.mockk.every
import io.mockk.mockk
import org.junit.Before
import org.junit.Test
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Unit tests for [CameraRepositoryImpl] session ownership.
 *
 * Uses fake [CameraProviderClient] / [CameraUseCaseBinder] so ownership races are
 * exercised without constructing a real CameraX use-case graph.
 */
class CameraRepositoryImplTest {

    private val context: Context = mockk(relaxed = true)
    private val provider: ProcessCameraProvider = mockk(relaxed = true)

    private lateinit var recordingBinder: RecordingCameraUseCaseBinder
    private lateinit var repository: CameraRepositoryImpl

    private val lifecycle: Lifecycle = mockk(relaxed = true)
    private val lifecycleOwner: LifecycleOwner = mockk()
    private val surfaceProvider: Preview.SurfaceProvider = mockk(relaxed = true)

    @Before
    fun setUp() {
        every { lifecycleOwner.lifecycle } returns lifecycle
        every { lifecycle.currentState } returns Lifecycle.State.STARTED

        recordingBinder = RecordingCameraUseCaseBinder()
        val immediateClient = CameraProviderClient { onReady, _ -> onReady(provider) }
        repository = CameraRepositoryImpl(context, immediateClient, recordingBinder)
    }

    @Test
    fun startThenStop_withMatchingToken_unbindsAndClearsSession() {
        val token = repository.startCamera(lifecycleOwner, surfaceProvider)

        assertThat(token.isNone).isFalse()
        assertThat(repository.isSessionActive()).isTrue()
        assertThat(repository.currentSessionToken()).isEqualTo(token)
        assertThat(recordingBinder.bindCount.get()).isEqualTo(1)

        repository.stopCamera(token)

        assertThat(repository.isSessionActive()).isFalse()
        assertThat(repository.currentSessionToken()).isEqualTo(CameraSessionToken.None)
        assertThat(recordingBinder.explicitUnbindCount.get()).isEqualTo(1)
        assertThat(repository.cameraState.value.isStreaming).isFalse()
    }

    @Test
    fun staleStopCamera_afterNewerSession_isNoOp_sessionStaysBoundAndFramesFlow() {
        val lifecycleOwnerA: LifecycleOwner = mockk()
        val lifecycleOwnerB: LifecycleOwner = mockk()
        val surfaceA: Preview.SurfaceProvider = mockk(relaxed = true)
        val surfaceB: Preview.SurfaceProvider = mockk(relaxed = true)
        every { lifecycleOwnerA.lifecycle } returns lifecycle
        every { lifecycleOwnerB.lifecycle } returns lifecycle

        val liveAnalyzer = ImageAnalysis.Analyzer { imageProxy -> imageProxy.close() }
        repository.setFrameAnalyzer(liveAnalyzer)

        // Screen A owns session 1
        val tokenA = repository.startCamera(lifecycleOwnerA, surfaceA)
        assertThat(repository.isSessionActive()).isTrue()

        // Screen B takes ownership before A's Compose dispose runs
        repository.setFrameAnalyzer(liveAnalyzer)
        val tokenB = repository.startCamera(lifecycleOwnerB, surfaceB)
        assertThat(tokenB.id).isGreaterThan(tokenA.id)
        assertThat(repository.currentSessionToken()).isEqualTo(tokenB)
        assertThat(repository.isSessionActive()).isTrue()
        assertThat(recordingBinder.bindCount.get()).isEqualTo(2)

        val explicitUnbindsBeforeStaleStop = recordingBinder.explicitUnbindCount.get()
        assertThat(explicitUnbindsBeforeStaleStop).isEqualTo(0)

        // Async Compose dispose from screen A arrives with stale token
        repository.stopCamera(tokenA)

        assertThat(repository.isSessionActive()).isTrue()
        assertThat(repository.currentSessionToken()).isEqualTo(tokenB)
        assertThat(repository.cameraState.value.isStreaming).isTrue()
        assertThat(recordingBinder.explicitUnbindCount.get()).isEqualTo(0)

        // ImageAnalysis still delivers frames after the stale stop
        val analyzer = recordingBinder.lastAnalyzer.get()
        assertThat(analyzer).isNotNull()
        val frame = mockk<ImageProxy>(relaxed = true)
        every { frame.width } returns 1920
        every { frame.height } returns 1080
        analyzer!!.analyze(frame)
        assertThat(recordingBinder.framesDelivered.get()).isEqualTo(1)

        // Current owner B can still stop cleanly
        repository.stopCamera(tokenB)
        assertThat(repository.isSessionActive()).isFalse()
        assertThat(recordingBinder.explicitUnbindCount.get()).isEqualTo(1)
    }

    @Test
    fun lateProviderCallback_fromPreviousOwner_doesNotRebindOverNewerSession() {
        val pending = mutableListOf<(ProcessCameraProvider) -> Unit>()
        val deferredClient = CameraProviderClient { onReady, _ -> pending.add(onReady) }
        val binder = RecordingCameraUseCaseBinder()
        val repo = CameraRepositoryImpl(context, deferredClient, binder)

        val ownerA: LifecycleOwner = mockk()
        val ownerB: LifecycleOwner = mockk()
        every { ownerA.lifecycle } returns lifecycle
        every { ownerB.lifecycle } returns lifecycle
        val surfaceA: Preview.SurfaceProvider = mockk(relaxed = true)
        val surfaceB: Preview.SurfaceProvider = mockk(relaxed = true)

        val tokenA = repo.startCamera(ownerA, surfaceA)
        val tokenB = repo.startCamera(ownerB, surfaceB)
        assertThat(pending).hasSize(2)
        assertThat(tokenB.id).isGreaterThan(tokenA.id)

        // Newer session's provider callback binds first
        pending[1].invoke(provider)
        assertThat(repo.isSessionActive()).isTrue()
        assertThat(repo.currentSessionToken()).isEqualTo(tokenB)
        assertThat(binder.bindCount.get()).isEqualTo(1)

        // Stale callback from screen A must not rebind
        pending[0].invoke(provider)
        assertThat(binder.bindCount.get()).isEqualTo(1)
        assertThat(repo.currentSessionToken()).isEqualTo(tokenB)
        assertThat(repo.isSessionActive()).isTrue()
    }

    @Test
    fun secondStartCamera_sameLifecycleAndLens_skipsFullRebind() {
        val token1 = repository.startCamera(lifecycleOwner, surfaceProvider)
        assertThat(recordingBinder.bindCount.get()).isEqualTo(1)

        val surface2: Preview.SurfaceProvider = mockk(relaxed = true)
        val token2 = repository.startCamera(lifecycleOwner, surface2)

        assertThat(token2.id).isGreaterThan(token1.id)
        // Same lens + same LifecycleOwner ⇒ reuse graph (analyzer/surface only).
        assertThat(recordingBinder.bindCount.get()).isEqualTo(1)
        assertThat(repository.isSessionActive()).isTrue()
        repository.stopCamera(token2)
    }

    @Test
    fun switchCamera_forcesFullRebind() {
        repository.startCamera(lifecycleOwner, surfaceProvider)
        assertThat(recordingBinder.bindCount.get()).isEqualTo(1)

        repository.switchCamera()
        assertThat(recordingBinder.bindCount.get()).isEqualTo(2)
        repository.stopCamera(repository.currentSessionToken())
    }

    @Test
    fun stopCamera_withNoneToken_isNoOp() {
        repository.startCamera(lifecycleOwner, surfaceProvider)
        assertThat(repository.isSessionActive()).isTrue()

        repository.stopCamera(CameraSessionToken.None)

        assertThat(repository.isSessionActive()).isTrue()
        assertThat(recordingBinder.explicitUnbindCount.get()).isEqualTo(0)
    }

    @Test
    fun clearFrameAnalyzer_onlyClearsWhenIdentityMatches() {
        val analyzerA = ImageAnalysis.Analyzer { it.close() }
        val analyzerB = ImageAnalysis.Analyzer { it.close() }

        repository.setFrameAnalyzer(analyzerA)
        val token = repository.startCamera(lifecycleOwner, surfaceProvider)
        repository.setFrameAnalyzer(analyzerB)

        // Stale clear from previous analyzer identity must not clear B
        repository.clearFrameAnalyzer(analyzerA)

        // Deliver a frame through the binder's last analyzer — still present after stale clear
        val active = recordingBinder.lastAnalyzer.get()
        assertThat(active).isNotNull()

        repository.clearFrameAnalyzer(analyzerB)
        repository.stopCamera(token)
        assertThat(repository.isSessionActive()).isFalse()
    }

    /**
     * Fake binder that records bind/unbind and delivers frames through the registered analyzer.
     *
     * [explicitUnbindCount] counts only [unbindAll] calls from [CameraRepositoryImpl.stopCamera],
     * not the unbind that happens inside a rebind.
     */
    private class RecordingCameraUseCaseBinder : CameraUseCaseBinder {
        val bindCount = AtomicInteger(0)
        val explicitUnbindCount = AtomicInteger(0)
        val framesDelivered = AtomicInteger(0)
        val lastAnalyzer = AtomicReference<ImageAnalysis.Analyzer?>(null)

        override fun bind(
            provider: ProcessCameraProvider,
            lifecycleOwner: LifecycleOwner,
            surfaceProvider: Preview.SurfaceProvider,
            lensFacing: Int,
            analyzer: ImageAnalysis.Analyzer?,
            analysisExecutor: Executor,
        ): BoundCameraUseCases {
            // Production binder unbinds before rebinding; do not count that as stopCamera.
            provider.unbindAll()

            if (analyzer != null) {
                lastAnalyzer.set(
                    ImageAnalysis.Analyzer { imageProxy ->
                        framesDelivered.incrementAndGet()
                        analyzer.analyze(imageProxy)
                    }
                )
            } else {
                lastAnalyzer.set(null)
            }

            bindCount.incrementAndGet()
            // Avoid mocking CameraX Preview/ImageAnalysis (static init needs Android APIs).
            return BoundCameraUseCases(
                preview = null,
                imageCapture = null,
                imageAnalysis = null,
            )
        }

        override fun unbindAll(provider: ProcessCameraProvider) {
            explicitUnbindCount.incrementAndGet()
            provider.unbindAll()
        }
    }
}
