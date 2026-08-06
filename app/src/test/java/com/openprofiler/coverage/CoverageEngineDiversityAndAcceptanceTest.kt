package com.openprofiler.coverage

import com.openprofiler.domain.model.BoardPose
import com.openprofiler.domain.model.CoverageThresholds
import com.openprofiler.domain.model.DetectedCorner
import com.openprofiler.domain.model.DetectionResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CoverageEngineDiversityAndAcceptanceTest {

    private lateinit var coverageEngine: CoverageEngine
    private val thresholds = CoverageThresholds(
        minDiversityDeltaToAccept = 0.1
    )

    @Before
    fun setUp() {
        coverageEngine = CoverageEngine()
        coverageEngine.setThresholds(thresholds)
    }

    private fun createFrameAt(x: Float, y: Float, distanceMm: Double = 800.0): DetectionResult {
        val corners = listOf(
            DetectedCorner(0, x, y, 1.0f),
            DetectedCorner(1, x + 100f, y, 1.0f),
            DetectedCorner(2, x + 100f, y + 100f, 1.0f),
            DetectedCorner(3, x, y + 100f, 1.0f)
        )
        val pose = BoardPose(
            rvec = doubleArrayOf(0.0, 0.0, 0.0),
            tvec = doubleArrayOf(0.0, 0.0, distanceMm)
        )
        return DetectionResult(
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
    }

    @Test
    fun `initial diversity score starts at 0`() {
        assertEquals(0.0, coverageEngine.getCurrentCoverage().overallPercentage, 0.001)
    }

    @Test
    fun `first valid frame is accepted and increases diversity score`() {
        val frame = createFrameAt(100.0f, 100.0f)
        val res = coverageEngine.evaluate(frame, 1000, 1000)

        assertTrue(res.isAccepted)
        assertNull(res.rejectReason)
        assertTrue(res.coverageDelta > 0.0)
        assertEquals(res.coverageDelta, res.diversityScore, 0.001)
        assertEquals(1, res.coverageData.acceptedFrameCount)
    }

    @Test
    fun `identical repeated frame is rejected with zero coverage delta`() {
        val frame1 = createFrameAt(100.0f, 100.0f)
        val res1 = coverageEngine.evaluate(frame1, 1000, 1000)
        assertTrue(res1.isAccepted)

        // Evaluate identical frame
        val frame2 = createFrameAt(100.0f, 100.0f)
        val res2 = coverageEngine.evaluate(frame2, 1000, 1000)

        assertFalse(res2.isAccepted)
        assertNotNull(res2.rejectReason)
        assertEquals(0.0, res2.coverageDelta, 0.001)
        assertEquals(1, res2.coverageData.acceptedFrameCount) // unchanged
    }

    @Test
    fun `frame with new position or scale or distance is accepted`() {
        val frame1 = createFrameAt(100.0f, 100.0f, 800.0)
        val res1 = coverageEngine.evaluate(frame1, 1000, 1000)
        assertTrue(res1.isAccepted)

        // Move to bottom-right cell (800, 800)
        val frame2 = createFrameAt(800.0f, 800.0f, 800.0)
        val res2 = coverageEngine.evaluate(frame2, 1000, 1000)
        assertTrue(res2.isAccepted)
        assertTrue(res2.coverageDelta > 0.0)
        assertEquals(2, res2.coverageData.acceptedFrameCount)
    }

    @Test
    fun `resetState resets all accumulated coverage metrics to zero`() {
        val frame = createFrameAt(100.0f, 100.0f)
        coverageEngine.evaluate(frame, 1000, 1000)
        assertTrue(coverageEngine.getCurrentCoverage().acceptedFrameCount > 0)

        coverageEngine.resetState()

        val resetCov = coverageEngine.getCurrentCoverage()
        assertEquals(0, resetCov.acceptedFrameCount)
        assertEquals(0, resetCov.visitedGridCells)
        assertEquals(0.0, resetCov.overallPercentage, 0.001)
    }

    @Test
    fun `undetected board frame is rejected`() {
        val invalidDet = DetectionResult(
            boardDetected = false,
            dictionary = "DICT_4X4",
            markerCount = 0,
            charucoCornerCount = 0,
            markerIds = emptyList(),
            charucoIds = emptyList(),
            cornerCoordinates = emptyList(),
            boardPose = null,
            detectionConfidence = 0.0f,
            processingTimeMs = 2
        )
        val res = coverageEngine.evaluate(invalidDet, 1000, 1000)

        assertFalse(res.isAccepted)
        assertEquals("No target detected", res.rejectReason)
        assertEquals(0.0, res.coverageDelta, 0.001)
    }
}
