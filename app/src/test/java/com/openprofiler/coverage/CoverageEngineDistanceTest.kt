package com.openprofiler.coverage

import com.openprofiler.domain.model.BoardPose
import com.openprofiler.domain.model.CoverageThresholds
import com.openprofiler.domain.model.DetectedCorner
import com.openprofiler.domain.model.DetectionResult
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class CoverageEngineDistanceTest {

    private lateinit var coverageEngine: CoverageEngine
    private val thresholds = CoverageThresholds(
        distanceBinsMm = listOf(500.0, 1000.0, 1500.0, 2500.0)
    )

    @Before
    fun setUp() {
        coverageEngine = CoverageEngine()
        coverageEngine.setThresholds(thresholds)
    }

    @Test
    fun `computeBinIndex maps distance values correctly`() {
        val cuts = listOf(500.0, 1000.0, 1500.0, 2500.0)

        // < 500mm -> Bin 0
        assertEquals(0, coverageEngine.computeBinIndex(400.0, cuts))

        // 500-1000mm -> Bin 1
        assertEquals(1, coverageEngine.computeBinIndex(750.0, cuts))

        // 1000-1500mm -> Bin 2
        assertEquals(2, coverageEngine.computeBinIndex(1200.0, cuts))

        // 1500-2500mm -> Bin 3
        assertEquals(3, coverageEngine.computeBinIndex(2000.0, cuts))

        // > 2500mm -> Bin 4
        assertEquals(4, coverageEngine.computeBinIndex(3000.0, cuts))
    }

    @Test
    fun `evaluating frame with distinct distance populates distance bin`() {
        val corners = listOf(
            DetectedCorner(0, 100.0f, 100.0f, 1.0f),
            DetectedCorner(1, 200.0f, 100.0f, 1.0f),
            DetectedCorner(2, 200.0f, 200.0f, 1.0f),
            DetectedCorner(3, 100.0f, 200.0f, 1.0f)
        )
        // tvec distance = sqrt(0^2 + 0^2 + 800^2) = 800mm (Bin 1)
        val pose = BoardPose(
            rvec = doubleArrayOf(0.0, 0.0, 0.0),
            tvec = doubleArrayOf(0.0, 0.0, 800.0)
        )
        val detection = DetectionResult(
            boardDetected = true,
            dictionary = "DICT_4X4",
            markerCount = 4,
            charucoCornerCount = 4,
            markerIds = listOf(0, 1, 2, 3),
            charucoIds = listOf(0, 1, 2, 3),
            cornerCoordinates = corners,
            boardPose = pose,
            detectionConfidence = 1.0f,
            processingTimeMs = 5
        )

        val result = coverageEngine.evaluate(detection, 1000, 1000, thresholds)
        assertEquals(1, result.coverageData.visitedDistanceBins)
        assertEquals(5, result.coverageData.totalDistanceBins)
    }
}
