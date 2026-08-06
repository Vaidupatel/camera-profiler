package com.openprofiler.domain.usecase

import com.openprofiler.domain.model.CoverageData
import com.openprofiler.domain.model.CoverageEvaluationResult
import com.openprofiler.domain.model.CoverageGuidance
import com.openprofiler.domain.model.DetectionResult
import com.openprofiler.domain.repository.CoverageRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EvaluateCoverageUseCaseTest {

    @Test
    fun `use case delegates evaluation to CoverageRepository`() = runTest {
        val expectedResult = CoverageEvaluationResult(
            isAccepted = true,
            rejectReason = null,
            coverageDelta = 10.0,
            diversityScore = 10.0,
            guidance = CoverageGuidance.EXCELLENT,
            remainingRequirements = emptyList(),
            coverageData = CoverageData(overallPercentage = 10.0)
        )

        val det = DetectionResult(
            boardDetected = true,
            dictionary = "DICT_4X4",
            markerCount = 4,
            charucoCornerCount = 4,
            markerIds = listOf(0, 1, 2, 3),
            charucoIds = listOf(0, 1, 2, 3),
            cornerCoordinates = emptyList(),
            boardPose = null,
            detectionConfidence = 1.0f,
            processingTimeMs = 5
        )

        val repository: CoverageRepository = mockk {
            coEvery { evaluate(det, 1000, 1000) } returns expectedResult
        }

        val useCase = EvaluateCoverageUseCase(repository)
        val result = useCase(det, 1000, 1000)

        assertTrue(result.isAccepted)
        assertEquals(10.0, result.diversityScore, 0.001)
        coVerify { repository.evaluate(det, 1000, 1000) }
    }
}
