package com.openprofiler.domain.usecase

import androidx.camera.core.ImageProxy
import com.google.common.truth.Truth.assertThat
import com.openprofiler.domain.model.DetectionResult
import com.openprofiler.domain.repository.DetectionRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DetectBoardUseCaseTest {

    private val detectionRepository: DetectionRepository = mockk()
    private val useCase = DetectBoardUseCase(detectionRepository)

    @Test
    fun `invoke calls repository detectBoard and returns DetectionResult`() = runTest {
        val mockImageProxy: ImageProxy = mockk()
        val expectedResult = DetectionResult(
            boardDetected = true,
            dictionary = "DICT_5X5_1000",
            markerCount = 6,
            charucoCornerCount = 8,
            markerIds = listOf(0, 1, 2, 3, 4, 5),
            charucoIds = listOf(0, 1, 2, 3, 4, 5, 6, 7),
            cornerCoordinates = emptyList(),
            boardPose = null,
            detectionConfidence = 0.95f,
            processingTimeMs = 15L
        )

        coEvery { detectionRepository.detectBoard(mockImageProxy, null, null) } returns expectedResult

        val result = useCase(mockImageProxy)

        assertThat(result.boardDetected).isTrue()
        assertThat(result.markerCount).isEqualTo(6)
        assertThat(result.charucoCornerCount).isEqualTo(8)
        assertThat(result.detectionConfidence).isEqualTo(0.95f)
    }

    @Test
    fun `invoke passes camera matrix and distortion parameters to repository`() = runTest {
        val mockImageProxy: ImageProxy = mockk()
        val cameraMatrix = doubleArrayOf(1000.0, 0.0, 500.0, 0.0, 1000.0, 500.0, 0.0, 0.0, 1.0)
        val distCoeffs = doubleArrayOf(0.1, -0.05, 0.0, 0.0, 0.0)

        val expectedResult = DetectionResult(
            boardDetected = true,
            dictionary = "DICT_5X5_1000",
            markerCount = 4,
            charucoCornerCount = 4,
            markerIds = listOf(0, 1, 2, 3),
            charucoIds = listOf(0, 1, 2, 3),
            cornerCoordinates = emptyList(),
            boardPose = null,
            detectionConfidence = 0.8f,
            processingTimeMs = 20L
        )

        coEvery {
            detectionRepository.detectBoard(mockImageProxy, cameraMatrix, distCoeffs)
        } returns expectedResult

        val result = useCase(mockImageProxy, cameraMatrix, distCoeffs)

        assertThat(result.boardDetected).isTrue()
    }
}
