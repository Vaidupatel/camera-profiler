package com.openprofiler.ui.viewmodel

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.lifecycle.LifecycleOwner
import com.google.common.truth.Truth.assertThat
import com.openprofiler.camera.CameraState
import com.openprofiler.common.util.DispatcherProvider
import com.openprofiler.domain.model.BoardAxesOverlay
import com.openprofiler.domain.model.DetectedCorner
import com.openprofiler.domain.model.DetectedMarker
import com.openprofiler.domain.model.DetectionResult
import com.openprofiler.domain.model.Point2D
import com.openprofiler.domain.repository.CameraRepository
import com.openprofiler.domain.repository.CameraSessionToken
import com.openprofiler.domain.usecase.DetectBoardUseCase
import io.mockk.coEvery
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

private class TestDispatcherProvider(
    private val testDispatcher: CoroutineDispatcher
) : DispatcherProvider {
    override val main: CoroutineDispatcher = testDispatcher
    override val io: CoroutineDispatcher = testDispatcher
    override val default: CoroutineDispatcher = testDispatcher
    override val unconfined: CoroutineDispatcher = testDispatcher
}

@OptIn(ExperimentalCoroutinesApi::class)
class CameraViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val dispatcherProvider = TestDispatcherProvider(testDispatcher)

    private val cameraRepository: CameraRepository = mockk(relaxed = true)
    private val detectBoardUseCase: DetectBoardUseCase = mockk(relaxed = true)

    private val cameraStateFlow = MutableStateFlow(CameraState())

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { cameraRepository.cameraState } returns cameraStateFlow
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state reflects default CameraUiState`() = runTest(testDispatcher) {
        val viewModel = CameraViewModel(cameraRepository, detectBoardUseCase, dispatcherProvider)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.isDetecting).isFalse()
        assertThat(state.detectionResult).isNull()
        assertThat(state.cameraState).isEqualTo(CameraState())
    }

    @Test
    fun `camera state updates in repository update CameraUiState`() = runTest(testDispatcher) {
        val viewModel = CameraViewModel(cameraRepository, detectBoardUseCase, dispatcherProvider)
        val newCamState = CameraState(isStreaming = true, resolution = "1920x1080")

        cameraStateFlow.value = newCamState
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.cameraState).isEqualTo(newCamState)
    }

    @Test
    fun `switchCamera forwards call to CameraRepository`() = runTest(testDispatcher) {
        val viewModel = CameraViewModel(cameraRepository, detectBoardUseCase, dispatcherProvider)
        viewModel.switchCamera()

        verify { cameraRepository.switchCamera() }
    }

    @Test
    fun `startCamera forwards call to CameraRepository and retains session token`() = runTest(testDispatcher) {
        val viewModel = CameraViewModel(cameraRepository, detectBoardUseCase, dispatcherProvider)
        val lifecycle = mockk<androidx.lifecycle.Lifecycle>(relaxed = true)
        every { lifecycle.currentState } returns androidx.lifecycle.Lifecycle.State.STARTED
        val lifecycleOwner = mockk<LifecycleOwner>()
        every { lifecycleOwner.lifecycle } returns lifecycle
        val surfaceProvider = mockk<Preview.SurfaceProvider>()
        val issued = CameraSessionToken(7L)
        every { cameraRepository.startCamera(lifecycleOwner, surfaceProvider) } returns issued

        viewModel.startCamera(lifecycleOwner, surfaceProvider)

        verify { cameraRepository.setFrameAnalyzer(viewModel) }
        verify { cameraRepository.startCamera(lifecycleOwner, surfaceProvider) }

        viewModel.stopCamera()
        verify { cameraRepository.stopCamera(issued) }
    }

    @Test
    fun `stopCamera forwards owned session token to CameraRepository`() = runTest(testDispatcher) {
        val viewModel = CameraViewModel(cameraRepository, detectBoardUseCase, dispatcherProvider)
        viewModel.stopCamera()

        verify { cameraRepository.stopCamera(CameraSessionToken.None) }
    }

    @Test
    fun `analyze processes frame via DetectBoardUseCase and updates uiState with detection result`() = runTest(testDispatcher) {
        val viewModel = CameraViewModel(cameraRepository, detectBoardUseCase, dispatcherProvider)
        val mockImageProxy = mockk<ImageProxy>(relaxed = true)

        val detectionResult = DetectionResult(
            boardDetected = true,
            dictionary = "DICT_5X5_1000",
            markerCount = 4,
            charucoCornerCount = 8,
            markerIds = listOf(0, 1, 2, 3),
            charucoIds = listOf(0, 1, 2, 3, 4, 5, 6, 7),
            cornerCoordinates = listOf(DetectedCorner(id = 0, x = 100f, y = 100f)),
            boardPose = null,
            detectionConfidence = 0.95f,
            processingTimeMs = 15L,
            detectedMarkers = listOf(
                DetectedMarker(
                    id = 0,
                    corners = listOf(
                        Point2D(10f, 10f),
                        Point2D(20f, 10f),
                        Point2D(20f, 20f),
                        Point2D(10f, 20f)
                    )
                )
            ),
            boardAxes = BoardAxesOverlay(
                origin = Point2D(50f, 50f),
                xAxisEnd = Point2D(80f, 50f),
                yAxisEnd = Point2D(50f, 80f),
                zAxisEnd = Point2D(50f, 50f)
            )
        )

        coEvery { detectBoardUseCase(mockImageProxy) } returns detectionResult

        viewModel.analyze(mockImageProxy)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.isDetecting).isTrue()
        assertThat(state.detectionResult).isEqualTo(detectionResult)
        verify { mockImageProxy.close() }
    }

    @Test
    fun `detection state transition updates isDetecting from true to false when target lost`() = runTest(testDispatcher) {
        val viewModel = CameraViewModel(cameraRepository, detectBoardUseCase, dispatcherProvider)
        val imageProxy1 = mockk<ImageProxy>(relaxed = true)
        val imageProxy2 = mockk<ImageProxy>(relaxed = true)

        val detectedResult = DetectionResult(
            boardDetected = true,
            dictionary = "DICT_5X5_1000",
            markerCount = 2,
            charucoCornerCount = 4,
            markerIds = listOf(0, 1),
            charucoIds = listOf(0, 1, 2, 3),
            cornerCoordinates = emptyList(),
            boardPose = null,
            detectionConfidence = 0.9f,
            processingTimeMs = 10L
        )

        val lostResult = DetectionResult(
            boardDetected = false,
            dictionary = "DICT_5X5_1000",
            markerCount = 0,
            charucoCornerCount = 0,
            markerIds = emptyList(),
            charucoIds = emptyList(),
            cornerCoordinates = emptyList(),
            boardPose = null,
            detectionConfidence = 0.0f,
            processingTimeMs = 5L,
            rejectedReason = "No target detected"
        )

        coEvery { detectBoardUseCase(imageProxy1) } returns detectedResult
        coEvery { detectBoardUseCase(imageProxy2) } returns lostResult

        viewModel.analyze(imageProxy1)
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.isDetecting).isTrue()

        viewModel.analyze(imageProxy2)
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.isDetecting).isFalse()
        assertThat(viewModel.uiState.value.detectionResult?.rejectedReason).isEqualTo("No target detected")
    }
}
