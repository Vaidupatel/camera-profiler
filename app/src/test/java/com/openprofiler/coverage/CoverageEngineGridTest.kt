package com.openprofiler.coverage

import com.openprofiler.domain.model.CoverageThresholds
import com.openprofiler.domain.model.DetectedCorner
import com.openprofiler.domain.model.DetectionResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CoverageEngineGridTest {

    private lateinit var coverageEngine: CoverageEngine
    private val thresholds = CoverageThresholds(gridRows = 3, gridCols = 3)

    @Before
    fun setUp() {
        coverageEngine = CoverageEngine()
        coverageEngine.setThresholds(thresholds)
    }

    @Test
    fun `computeGridCell maps coordinates correctly across 3x3 grid`() {
        val w = 1000
        val h = 1000

        // Top-Left (0, 0)
        assertEquals(Pair(0, 0), coverageEngine.computeGridCell(100.0, 100.0, w, h, thresholds))

        // Center (1, 1)
        assertEquals(Pair(1, 1), coverageEngine.computeGridCell(500.0, 500.0, w, h, thresholds))

        // Bottom-Right (2, 2)
        assertEquals(Pair(2, 2), coverageEngine.computeGridCell(900.0, 900.0, w, h, thresholds))
    }

    @Test
    fun `computeGridCell clamps edge coordinates properly`() {
        val w = 1000
        val h = 1000

        assertEquals(Pair(0, 0), coverageEngine.computeGridCell(-50.0, -10.0, w, h, thresholds))
        assertEquals(Pair(2, 2), coverageEngine.computeGridCell(1500.0, 1200.0, w, h, thresholds))
    }

    @Test
    fun `evaluating distinct grid cells updates visited cells and regional breakdown`() {
        val w = 1000
        val h = 1000

        // Frame 1 in Top-Left (0, 0)
        val corners1 = listOf(
            DetectedCorner(0, 50.0f, 50.0f, 1.0f),
            DetectedCorner(1, 150.0f, 50.0f, 1.0f),
            DetectedCorner(2, 150.0f, 150.0f, 1.0f),
            DetectedCorner(3, 50.0f, 150.0f, 1.0f)
        )
        val det1 = DetectionResult(
            boardDetected = true,
            dictionary = "DICT_4X4",
            markerCount = 4,
            charucoCornerCount = 4,
            markerIds = listOf(0, 1, 2, 3),
            charucoIds = listOf(0, 1, 2, 3),
            cornerCoordinates = corners1,
            boardPose = null,
            detectionConfidence = 1.0f,
            processingTimeMs = 5
        )

        val res1 = coverageEngine.evaluate(det1, w, h)
        assertTrue(res1.isAccepted)
        assertEquals(1, res1.coverageData.visitedGridCells)
        assertTrue(res1.coverageData.left > 0.0)
        assertTrue(res1.coverageData.top > 0.0)

        // Frame 2 in Center (1, 1)
        val corners2 = listOf(
            DetectedCorner(0, 450.0f, 450.0f, 1.0f),
            DetectedCorner(1, 550.0f, 450.0f, 1.0f),
            DetectedCorner(2, 550.0f, 550.0f, 1.0f),
            DetectedCorner(3, 450.0f, 550.0f, 1.0f)
        )
        val det2 = DetectionResult(
            boardDetected = true,
            dictionary = "DICT_4X4",
            markerCount = 4,
            charucoCornerCount = 4,
            markerIds = listOf(0, 1, 2, 3),
            charucoIds = listOf(0, 1, 2, 3),
            cornerCoordinates = corners2,
            boardPose = null,
            detectionConfidence = 1.0f,
            processingTimeMs = 5
        )

        val res2 = coverageEngine.evaluate(det2, w, h)
        assertTrue(res2.isAccepted)
        assertEquals(2, res2.coverageData.visitedGridCells)
        assertEquals(100.0, res2.coverageData.center, 0.01)
    }
}
