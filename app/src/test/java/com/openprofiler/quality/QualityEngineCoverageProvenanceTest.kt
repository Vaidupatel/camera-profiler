package com.openprofiler.quality

import com.google.common.truth.Truth.assertThat
import com.openprofiler.domain.model.BoardConfig
import com.openprofiler.domain.model.BoardPose
import com.openprofiler.domain.model.DetectedCorner
import com.openprofiler.domain.model.DetectionResult
import com.openprofiler.domain.model.IntrinsicsSource
import com.openprofiler.domain.model.Point2D
import com.openprofiler.domain.model.QualityMetricId
import com.openprofiler.domain.model.QualityMetricStatus
import com.openprofiler.domain.model.QualityThresholds
import com.openprofiler.native_bridge.NativeImageQualityMeasurements
import com.openprofiler.native_bridge.NativeQualityEngine
import io.mockk.every
import io.mockk.mockk
import org.junit.Before
import org.junit.Test

/**
 * TARGET_COVERAGE must use observed (image-space) corners, not pose-reprojected bbox.
 */
class QualityEngineCoverageProvenanceTest {

    private val thresholds = QualityThresholds(
        minBlurLaplacianVariance = 100.0,
        warningBlurLaplacianVariance = 150.0,
        minSharpnessGradientMagnitude = 20.0,
        warningSharpnessGradientMagnitude = 35.0,
        minContrastScore = 0.35,
        warningContrastScore = 0.50,
        maxNoiseScore = 25.0,
        warningNoiseScore = 15.0,
        minBoardVisibilityPercent = 25.0,
        warningBoardVisibilityPercent = 50.0,
        minCornerCount = 4,
        minAverageCornerResponse = 0.40,
        minMinimumCornerResponse = 0.20,
        minSubpixelSuccessRatio = 0.80,
        minFrontalScore = 0.35,
        minBoardWidthPercent = 20.0,
        minBoardHeightPercent = 20.0,
        minImageCoveragePercent = 5.0,
        warningImageCoveragePercent = 10.0,
        minWidthPx = 640,
        minHeightPx = 480,
        supportedAspectRatios = listOf(16.0 / 9.0, 4.0 / 3.0),
        aspectRatioTolerance = 0.08
    )

    private val board = BoardConfig(
        dictionaryName = "DICT_5X5_1000",
        squaresX = 9,
        squaresY = 6,
        squareLengthMm = 30f,
        markerLengthMm = 22f
    )

    private lateinit var engine: QualityEngine

    @Before
    fun setUp() {
        val loader = mockk<QualityThresholdsLoader>()
        every { loader.load(any()) } returns Result.success(thresholds)
        every { loader.load() } returns Result.success(thresholds)
        val native = mockk<NativeQualityEngine>(relaxed = true)
        every { native.initialize() } returns true
        engine = QualityEngine(native, loader)
        engine.initialize()
    }

    private fun nativeOk() = NativeImageQualityMeasurements(
        success = true,
        blurLaplacianVariance = 200.0,
        sharpnessGradientMagnitude = 40.0,
        meanBrightness = 120.0,
        darkPixelRatio = 0.05,
        brightPixelRatio = 0.05,
        contrastScore = 0.7,
        noiseScore = 8.0,
        motionMad = 2.0,
        hasPriorFrame = true,
        frameWidth = 1000,
        frameHeight = 1000
    )

    @Test
    fun targetCoverage_usesObservedBox_notPoseReprojectedBox() {
        // Observed corners span 100×100 → 1% area of 1000×1000 (below typical min area).
        val corners = listOf(
            DetectedCorner(0, 100f, 100f, 0.9f),
            DetectedCorner(1, 200f, 100f, 0.9f),
            DetectedCorner(2, 200f, 200f, 0.9f),
            DetectedCorner(3, 100f, 200f, 0.9f),
        )
        // Pose-reprojected box is huge — would PASS coverage if mistakenly used.
        val poseBox = listOf(
            Point2D(0f, 0f),
            Point2D(900f, 0f),
            Point2D(900f, 900f),
            Point2D(0f, 900f),
        )
        val observed = listOf(
            Point2D(100f, 100f),
            Point2D(200f, 100f),
            Point2D(200f, 200f),
            Point2D(100f, 200f),
        )
        val detection = DetectionResult(
            boardDetected = true,
            dictionary = "DICT_5X5_1000",
            markerCount = 8,
            charucoCornerCount = 4,
            markerIds = (0 until 8).toList(),
            charucoIds = corners.map { it.id },
            cornerCoordinates = corners,
            boardPose = BoardPose(
                rvec = doubleArrayOf(0.01, 0.02, 0.01),
                tvec = doubleArrayOf(0.0, 0.0, 400.0),
                intrinsicsSource = IntrinsicsSource.FACTORY_ESTIMATE,
            ),
            detectionConfidence = 0.9f,
            processingTimeMs = 5L,
            boundingBox = poseBox,
            observedBoundingBox = observed,
        )

        val result = engine.evaluateFromMeasurements(nativeOk(), detection, board, thresholds)
        val coverage = result.metric(QualityMetricId.TARGET_COVERAGE)!!
        // 100×100 / 1000×1000 ⇒ area 1% — below minImageCoveragePercent 5 → FAIL
        // (pose-reprojected 900×900 box would have passed if used by mistake)
        assertThat(coverage.status).isEqualTo(QualityMetricStatus.FAIL)
        assertThat(coverage.value).isLessThan(thresholds.minImageCoveragePercent)
    }

    @Test
    fun targetCoverage_passesWhenObservedBoxLarge_evenIfPoseBoxNull() {
        val corners = listOf(
            DetectedCorner(0, 50f, 50f, 0.95f),
            DetectedCorner(1, 750f, 50f, 0.95f),
            DetectedCorner(2, 750f, 750f, 0.95f),
            DetectedCorner(3, 50f, 750f, 0.95f),
        )
        val observed = listOf(
            Point2D(50f, 50f),
            Point2D(750f, 50f),
            Point2D(750f, 750f),
            Point2D(50f, 750f),
        )
        val detection = DetectionResult(
            boardDetected = true,
            dictionary = "DICT_5X5_1000",
            markerCount = 10,
            charucoCornerCount = 20,
            markerIds = (0 until 10).toList(),
            charucoIds = (0 until 20).toList(),
            cornerCoordinates = corners,
            boardPose = BoardPose(
                doubleArrayOf(0.0, 0.0, 0.0),
                doubleArrayOf(0.0, 0.0, 500.0),
                IntrinsicsSource.FACTORY_ESTIMATE,
            ),
            detectionConfidence = 0.95f,
            processingTimeMs = 5L,
            boundingBox = null, // pose box unavailable must not block coverage
            observedBoundingBox = observed,
        )

        val result = engine.evaluateFromMeasurements(nativeOk(), detection, board, thresholds)
        assertThat(result.metric(QualityMetricId.TARGET_COVERAGE)!!.status)
            .isEqualTo(QualityMetricStatus.PASS)
    }

    @Test
    fun poseQuality_embedsIntrinsicsProvenance() {
        val corners = (0 until 8).map { DetectedCorner(it, 100f + it * 50f, 100f + it * 40f, 0.9f) }
        val detection = DetectionResult(
            boardDetected = true,
            dictionary = "DICT_5X5_1000",
            markerCount = 8,
            charucoCornerCount = 8,
            markerIds = (0 until 8).toList(),
            charucoIds = corners.map { it.id },
            cornerCoordinates = corners,
            boardPose = BoardPose(
                rvec = doubleArrayOf(0.01, 0.02, 0.01),
                tvec = doubleArrayOf(0.0, 0.0, 400.0),
                intrinsicsSource = IntrinsicsSource.FACTORY_ESTIMATE,
            ),
            detectionConfidence = 0.9f,
            processingTimeMs = 5L,
            observedBoundingBox = listOf(
                Point2D(50f, 50f), Point2D(800f, 50f), Point2D(800f, 800f), Point2D(50f, 800f)
            ),
        )
        val result = engine.evaluateFromMeasurements(nativeOk(), detection, board, thresholds)
        val pose = result.metric(QualityMetricId.POSE_QUALITY)!!
        assertThat(pose.reason).contains("FACTORY_ESTIMATE")
        assertThat(pose.secondaryValues["intrinsicsSourceOrdinal"])
            .isEqualTo(IntrinsicsSource.FACTORY_ESTIMATE.ordinal.toDouble())
    }
}
