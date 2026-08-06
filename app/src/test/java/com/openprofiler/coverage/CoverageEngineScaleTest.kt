package com.openprofiler.coverage

import com.openprofiler.domain.model.CoverageThresholds
import com.openprofiler.domain.model.DetectedCorner
import com.openprofiler.domain.model.DetectionResult
import com.openprofiler.domain.model.ScaleBucket
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class CoverageEngineScaleTest {

    private lateinit var coverageEngine: CoverageEngine
    private val thresholds = CoverageThresholds(
        scaleThresholdsPercent = listOf(10.0, 25.0, 40.0, 60.0)
    )

    @Before
    fun setUp() {
        coverageEngine = CoverageEngine()
        coverageEngine.setThresholds(thresholds)
    }

    private fun createDetectionForBox(widthPx: Float, heightPx: Float): DetectionResult {
        val corners = listOf(
            DetectedCorner(0, 0.0f, 0.0f, 1.0f),
            DetectedCorner(1, widthPx, 0.0f, 1.0f),
            DetectedCorner(2, widthPx, heightPx, 1.0f),
            DetectedCorner(3, 0.0f, heightPx, 1.0f)
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
    fun `computeScaleBucket categorizes scale correctly based on image area percentage`() {
        val frameW = 1000
        val frameH = 1000
        // Total area = 1,000,000 px^2

        // 5% area (50,000 px^2 -> 223.6 x 223.6) -> VERY_SMALL (< 10%)
        val detVerySmall = createDetectionForBox(223.6f, 223.6f)
        assertEquals(
            ScaleBucket.VERY_SMALL,
            coverageEngine.computeScaleBucket(detVerySmall, frameW, frameH, thresholds)
        )

        // 15% area (150,000 px^2 -> 387.3 x 387.3) -> SMALL ([10%, 25%))
        val detSmall = createDetectionForBox(387.3f, 387.3f)
        assertEquals(
            ScaleBucket.SMALL,
            coverageEngine.computeScaleBucket(detSmall, frameW, frameH, thresholds)
        )

        // 30% area (300,000 px^2 -> 547.7 x 547.7) -> MEDIUM ([25%, 40%))
        val detMedium = createDetectionForBox(547.7f, 547.7f)
        assertEquals(
            ScaleBucket.MEDIUM,
            coverageEngine.computeScaleBucket(detMedium, frameW, frameH, thresholds)
        )

        // 50% area (500,000 px^2 -> 707.1 x 707.1) -> LARGE ([40%, 60%))
        val detLarge = createDetectionForBox(707.1f, 707.1f)
        assertEquals(
            ScaleBucket.LARGE,
            coverageEngine.computeScaleBucket(detLarge, frameW, frameH, thresholds)
        )

        // 70% area (700,000 px^2 -> 836.6 x 836.6) -> VERY_LARGE (>= 60%)
        val detVeryLarge = createDetectionForBox(836.6f, 836.6f)
        assertEquals(
            ScaleBucket.VERY_LARGE,
            coverageEngine.computeScaleBucket(detVeryLarge, frameW, frameH, thresholds)
        )
    }
}
