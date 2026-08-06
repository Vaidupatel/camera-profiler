package com.openprofiler.coverage

import com.openprofiler.domain.model.CoverageGuidance
import com.openprofiler.domain.model.CoverageThresholds
import com.openprofiler.domain.model.DetectedCorner
import com.openprofiler.domain.model.DetectionResult
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class CoverageEngineGuidanceTest {

    private lateinit var coverageEngine: CoverageEngine
    private val thresholds = CoverageThresholds(
        targetDiversityScore = 80.0
    )

    @Before
    fun setUp() {
        coverageEngine = CoverageEngine()
        coverageEngine.setThresholds(thresholds)
    }

    private fun createFrameAt(x: Float, y: Float): DetectionResult {
        val corners = listOf(
            DetectedCorner(0, x, y, 1.0f),
            DetectedCorner(1, x + 100f, y, 1.0f),
            DetectedCorner(2, x + 100f, y + 100f, 1.0f),
            DetectedCorner(3, x, y + 100f, 1.0f)
        )
        return DetectionResult(
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
    }

    @Test
    fun `guidance prompts MOVE_LEFT when left grid column is unvisited`() {
        // Evaluate frame in right-most column (800, 500)
        val frameRight = createFrameAt(800.0f, 500.0f)
        val res = coverageEngine.evaluate(frameRight, 1000, 1000, thresholds)

        assertEquals(CoverageGuidance.MOVE_LEFT, res.guidance)
    }

    @Test
    fun `guidance prompts EXCELLENT when target diversity score is achieved`() {
        val customEngine = CoverageEngine()
        customEngine.setThresholds(CoverageThresholds(targetDiversityScore = 0.0))

        val frame = createFrameAt(500.0f, 500.0f)
        val res = customEngine.evaluate(frame, 1000, 1000)

        assertEquals(CoverageGuidance.EXCELLENT, res.guidance)
    }
}
