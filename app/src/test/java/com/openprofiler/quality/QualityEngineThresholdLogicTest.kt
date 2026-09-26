package com.openprofiler.quality

import com.google.common.truth.Truth.assertThat
import com.openprofiler.domain.model.BoardConfig
import com.openprofiler.domain.model.BoardPose
import com.openprofiler.domain.model.DetectedCorner
import com.openprofiler.domain.model.DetectionResult
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

class QualityEngineThresholdLogicTest {

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

    private fun goodNative(
        blur: Double = 200.0,
        sharpness: Double = 40.0,
        mean: Double = 120.0,
        contrast: Double = 0.7,
        noise: Double = 8.0,
        motion: Double = 2.0,
        hasPrior: Boolean = true
    ) = NativeImageQualityMeasurements(
        success = true,
        blurLaplacianVariance = blur,
        sharpnessGradientMagnitude = sharpness,
        meanBrightness = mean,
        darkPixelRatio = 0.05,
        brightPixelRatio = 0.05,
        contrastScore = contrast,
        noiseScore = noise,
        motionMad = motion,
        hasPriorFrame = hasPrior,
        frameWidth = 1920,
        frameHeight = 1080
    )

    private fun goodDetection(): DetectionResult {
        val corners = (0 until 12).map {
            DetectedCorner(id = it, x = 100f + it, y = 100f + it, subpixelPrecision = 0.9f)
        }
        return DetectionResult(
            boardDetected = true,
            dictionary = "DICT_5X5_1000",
            markerCount = 8,
            charucoCornerCount = 12,
            markerIds = (0 until 8).toList(),
            charucoIds = corners.map { it.id },
            cornerCoordinates = corners,
            boardPose = BoardPose(
                rvec = doubleArrayOf(0.01, 0.02, 0.01),
                tvec = doubleArrayOf(0.0, 0.0, 400.0)
            ),
            detectionConfidence = 0.95f,
            processingTimeMs = 10L,
            boundingBox = listOf(
                Point2D(50f, 50f),
                Point2D(900f, 50f),
                Point2D(900f, 900f),
                Point2D(50f, 900f)
            ),
            observedBoundingBox = listOf(
                Point2D(200f, 200f),
                Point2D(800f, 200f),
                Point2D(800f, 700f),
                Point2D(200f, 700f)
            )
        )
    }

    @Test
    fun passCase_allCriticalMetricsAccept() {
        val result = engine.evaluateFromMeasurements(goodNative(), goodDetection(), board, thresholds)
        assertThat(result.summary.isAccepted).isTrue()
        assertThat(result.summary.failCount).isEqualTo(0)
        assertThat(result.metrics).hasSize(13)
        assertThat(result.metric(QualityMetricId.BLUR)!!.status).isEqualTo(QualityMetricStatus.PASS)
        assertThat(result.metric(QualityMetricId.EXPOSURE)!!.status).isEqualTo(QualityMetricStatus.PASS)
        assertThat(result.metric(QualityMetricId.CONTRAST)!!.status).isEqualTo(QualityMetricStatus.PASS)
        assertThat(result.metric(QualityMetricId.IMAGE_RESOLUTION)!!.status)
            .isEqualTo(QualityMetricStatus.PASS)
    }

    @Test
    fun failureCase_blurRejectsFrame() {
        val result = engine.evaluateFromMeasurements(
            goodNative(blur = 10.0),
            goodDetection(),
            board,
            thresholds
        )
        assertThat(result.summary.isAccepted).isFalse()
        assertThat(result.summary.primaryRejectReason).contains("Blur")
        assertThat(result.metric(QualityMetricId.BLUR)!!.status).isEqualTo(QualityMetricStatus.FAIL)
    }

    @Test
    fun failureCase_missingDetectionFailsBoardMetrics() {
        val result = engine.evaluateFromMeasurements(goodNative(), detection = null, board, thresholds)
        assertThat(result.summary.isAccepted).isFalse()
        assertThat(result.metric(QualityMetricId.TARGET_COVERAGE)!!.status)
            .isEqualTo(QualityMetricStatus.FAIL)
        assertThat(result.metric(QualityMetricId.BOARD_VISIBILITY)!!.status)
            .isEqualTo(QualityMetricStatus.FAIL)
        assertThat(result.metric(QualityMetricId.POSE_QUALITY)!!.status)
            .isEqualTo(QualityMetricStatus.FAIL)
    }

    @Test
    fun boardVisibility_warningBelowWarningThreshold() {
        val visibility = QualityMetricRules.boardVisibility(49.0, thresholds)
        assertThat(visibility.status).isEqualTo(QualityMetricStatus.WARNING)
    }

    @Test
    fun pose_rejectsNearSingularLowFrontal() {
        val m = QualityMetricRules.poseQuality(
            pitchDegrees = 10.0,
            yawDegrees = 10.0,
            rollDegrees = 5.0,
            distanceMm = 400.0,
            frontalScore = 0.1,
            t = thresholds
        )
        assertThat(m.status).isEqualTo(QualityMetricStatus.FAIL)
        assertThat(m.reason).contains("near-singular")
    }

    @Test
    fun cornerQuality_failWhenBelowCount() {
        val m = QualityMetricRules.cornerQuality(
            averageResponse = 0.9,
            minimumResponse = 0.5,
            cornerCount = 2,
            subpixelSuccessRatio = 1.0,
            t = thresholds
        )
        assertThat(m.status).isEqualTo(QualityMetricStatus.FAIL)
    }
}
