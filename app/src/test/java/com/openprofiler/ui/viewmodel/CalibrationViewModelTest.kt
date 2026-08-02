package com.openprofiler.ui.viewmodel

import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.lifecycle.LifecycleOwner
import com.google.common.truth.Truth.assertThat
import com.openprofiler.camera.CameraIntrinsicsProvider
import com.openprofiler.camera.CameraState
import com.openprofiler.common.util.DispatcherProvider
import com.openprofiler.domain.model.BoardConfig
import com.openprofiler.domain.model.DetectionResult
import com.openprofiler.domain.model.IntrinsicsSource
import com.openprofiler.domain.model.QualityMetric
import com.openprofiler.domain.model.QualityMetricId
import com.openprofiler.domain.model.QualityMetricStatus
import com.openprofiler.domain.model.QualityResult
import com.openprofiler.domain.model.QualitySummary
import com.openprofiler.domain.repository.CameraRepository
import com.openprofiler.domain.repository.CameraSessionToken
import com.openprofiler.domain.repository.DetectionRepository
import com.openprofiler.domain.repository.QualityRepository
import com.openprofiler.domain.usecase.DetectBoardUseCase
import com.openprofiler.domain.usecase.EvaluateQualityUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

private class CalibrationTestDispatcherProvider(
    private val testDispatcher: CoroutineDispatcher
) : DispatcherProvider {
    override val main: CoroutineDispatcher = testDispatcher
    override val io: CoroutineDispatcher = testDispatcher
    override val default: CoroutineDispatcher = testDispatcher
    override val unconfined: CoroutineDispatcher = testDispatcher
}

@OptIn(ExperimentalCoroutinesApi::class)
class CalibrationViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val dispatcherProvider = CalibrationTestDispatcherProvider(testDispatcher)

    private val cameraRepository: CameraRepository = mockk(relaxed = true)
    private val detectBoardUseCase: DetectBoardUseCase = mockk(relaxed = true)
    private val evaluateQualityUseCase: EvaluateQualityUseCase = mockk(relaxed = true)
    private val detectionRepository: DetectionRepository = mockk(relaxed = true)
    private val qualityRepository: QualityRepository = mockk(relaxed = true)
    private val cameraIntrinsicsProvider: CameraIntrinsicsProvider = mockk(relaxed = true)
    private val cameraStateFlow = MutableStateFlow(CameraState())

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { cameraRepository.cameraState } returns cameraStateFlow
        coEvery { detectionRepository.initialize() } returns Result.success(Unit)
        coEvery { qualityRepository.initialize() } returns Result.success(Unit)
        every { detectionRepository.getBoardConfig() } returns BoardConfig(
            dictionaryName = "DICT_5X5_1000",
            squaresX = 9,
            squaresY = 6,
            squareLengthMm = 30f,
            markerLengthMm = 22f
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = CalibrationViewModel(
        cameraRepository,
        detectBoardUseCase,
        evaluateQualityUseCase,
        detectionRepository,
        qualityRepository,
        cameraIntrinsicsProvider,
        dispatcherProvider
    )

    @Test
    fun startCamera_registersAnalyzerAndStartsRepositorySession() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        val lifecycle = mockk<androidx.lifecycle.Lifecycle>(relaxed = true)
        every { lifecycle.currentState } returns androidx.lifecycle.Lifecycle.State.STARTED
        val lifecycleOwner = mockk<LifecycleOwner>()
        every { lifecycleOwner.lifecycle } returns lifecycle
        val surfaceProvider = mockk<Preview.SurfaceProvider>()
        val issued = CameraSessionToken(3L)
        every { cameraRepository.startCamera(lifecycleOwner, surfaceProvider) } returns issued

        viewModel.startCamera(lifecycleOwner, surfaceProvider)

        verify { cameraRepository.setFrameAnalyzer(viewModel) }
        verify { cameraRepository.startCamera(lifecycleOwner, surfaceProvider) }

        viewModel.stopCamera()
        verify { cameraRepository.stopCamera(issued) }
    }

    @Test
    fun stopCamera_forwardsOwnedSessionTokenToRepository() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        viewModel.stopCamera()
        verify { cameraRepository.stopCamera(CameraSessionToken.None) }
    }

    @Test
    fun analyze_runsDetectionThenQualityAndUpdatesStatusTexts() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val imageProxy = mockk<ImageProxy>(relaxed = true)
        val detection = DetectionResult(
            boardDetected = true,
            dictionary = "DICT_5X5_1000",
            markerCount = 6,
            charucoCornerCount = 10,
            markerIds = listOf(0, 1, 2, 3, 4, 5),
            charucoIds = (0 until 10).toList(),
            cornerCoordinates = emptyList(),
            boardPose = null,
            detectionConfidence = 0.9f,
            processingTimeMs = 12L
        )
        val quality = QualityResult(
            metrics = listOf(
                QualityMetric(
                    QualityMetricId.BLUR,
                    200.0,
                    100.0,
                    QualityMetricStatus.PASS,
                    "ok"
                )
            ),
            summary = QualitySummary(
                isAccepted = true,
                overallScore = 1.0,
                passCount = 1,
                warningCount = 0,
                failCount = 0,
                primaryRejectReason = null
            ),
            processingTimeMs = 4L,
            frameWidthPx = 1920,
            frameHeightPx = 1080
        )

        every { cameraIntrinsicsProvider.getSeedIntrinsics(any(), any(), any()) } returns null
        coEvery {
            detectBoardUseCase(imageProxy, null, null, IntrinsicsSource.UNAVAILABLE)
        } returns detection
        coEvery {
            evaluateQualityUseCase(imageProxy, detection, any())
        } returns quality

        viewModel.analyze(imageProxy)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.isDetecting).isTrue()
        assertThat(state.detectionResult).isEqualTo(detection)
        assertThat(state.qualityResult).isEqualTo(quality)
        assertThat(state.targetStatusText).contains("Target locked")
        assertThat(state.qualityStatusText).contains("PASS")
        verify { imageProxy.close() }
        coVerify { detectBoardUseCase(imageProxy, null, null, IntrinsicsSource.UNAVAILABLE) }
        coVerify { evaluateQualityUseCase(imageProxy, detection, any()) }
    }

    @Test
    fun analyze_searchingStatusWhenBoardNotDetected() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val imageProxy = mockk<ImageProxy>(relaxed = true)
        val detection = DetectionResult(
            boardDetected = false,
            dictionary = "DICT_5X5_1000",
            markerCount = 0,
            charucoCornerCount = 0,
            markerIds = emptyList(),
            charucoIds = emptyList(),
            cornerCoordinates = emptyList(),
            boardPose = null,
            detectionConfidence = 0f,
            processingTimeMs = 5L,
            rejectedReason = "No markers"
        )
        val quality = QualityResult(
            metrics = emptyList(),
            summary = QualitySummary(
                isAccepted = false,
                overallScore = 0.0,
                passCount = 0,
                warningCount = 0,
                failCount = 1,
                primaryRejectReason = "Blur below minimum"
            ),
            processingTimeMs = 3L,
            frameWidthPx = 1920,
            frameHeightPx = 1080
        )

        every { cameraIntrinsicsProvider.getSeedIntrinsics(any(), any(), any()) } returns null
        coEvery {
            detectBoardUseCase(imageProxy, null, null, IntrinsicsSource.UNAVAILABLE)
        } returns detection
        coEvery { evaluateQualityUseCase(imageProxy, detection, any()) } returns quality

        viewModel.analyze(imageProxy)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.targetStatusText).contains("Searching")
        assertThat(viewModel.uiState.value.qualityStatusText).contains("FAIL")
    }

    @Test
    fun init_registersSingleAnalyzer() = runTest(testDispatcher) {
        createViewModel()
        verify(exactly = 1) { cameraRepository.setFrameAnalyzer(any()) }
    }
}
