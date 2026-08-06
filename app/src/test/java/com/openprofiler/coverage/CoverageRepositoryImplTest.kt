package com.openprofiler.coverage

import com.openprofiler.domain.model.CoverageThresholds
import com.openprofiler.domain.model.DetectedCorner
import com.openprofiler.domain.model.DetectionResult
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CoverageRepositoryImplTest {

    private lateinit var coverageEngine: CoverageEngine
    private lateinit var loader: CoverageThresholdsLoader
    private lateinit var repository: CoverageRepositoryImpl

    @Before
    fun setUp() {
        coverageEngine = CoverageEngine()
        loader = mockk {
            every { load(any()) } returns Result.success(CoverageThresholds())
        }
        repository = CoverageRepositoryImpl(coverageEngine, loader)
    }

    @Test
    fun `initialize loads thresholds and resets state`() = runTest {
        val result = repository.initialize()
        assertTrue(result.isSuccess)
        assertNotNull(repository.getThresholds())
    }

    @Test
    fun `evaluate delegates to coverageEngine and returns evaluation result`() = runTest {
        val corners = listOf(
            DetectedCorner(0, 100.0f, 100.0f, 1.0f),
            DetectedCorner(1, 200.0f, 100.0f, 1.0f),
            DetectedCorner(2, 200.0f, 200.0f, 1.0f),
            DetectedCorner(3, 100.0f, 200.0f, 1.0f)
        )
        val det = DetectionResult(
            boardDetected = true,
            dictionary = "DICT_4X4",
            markerCount = 4,
            charucoCornerCount = 4,
            markerIds = listOf(0, 1, 2, 3),
            charucoIds = listOf(0, 1, 2, 3),
            cornerCoordinates = corners,
            boardPose = null,
            detectionConfidence = 1.0f,
            processingTimeMs = 5
        )

        val evalResult = repository.evaluate(det, 1000, 1000)
        assertTrue(evalResult.isAccepted)
        assertEquals(1, repository.getCurrentCoverage().acceptedFrameCount)
    }

    @Test
    fun `resetState resets repository coverage data`() = runTest {
        repository.resetState()
        assertEquals(0, repository.getCurrentCoverage().acceptedFrameCount)
    }
}
