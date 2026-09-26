package com.openprofiler.quality

import com.openprofiler.common.util.ImageDimensionUtils
import com.openprofiler.domain.model.BoardConfig
import com.openprofiler.domain.model.DetectionResult
import com.openprofiler.domain.model.IntrinsicsSource
import com.openprofiler.domain.model.Point2D
import com.openprofiler.domain.model.QualityMetric
import com.openprofiler.domain.model.QualityMetricStatus
import com.openprofiler.domain.model.QualityResult
import com.openprofiler.domain.model.QualitySummary
import com.openprofiler.domain.model.QualityThresholds
import com.openprofiler.native_bridge.NativeImageQualityMeasurements
import com.openprofiler.native_bridge.NativeQualityEngine
import timber.log.Timber
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Production Quality Engine.
 *
 * Orchestrates native image measurements and detection-derived measurements.
 * Applies configurable thresholds. Never estimates or guesses missing values.
 */
@Singleton
class QualityEngine @Inject constructor(
    private val nativeQualityEngine: NativeQualityEngine,
    private val thresholdsLoader: QualityThresholdsLoader
) {
    @Volatile
    private var thresholds: QualityThresholds = QualityThresholds()

    @Synchronized
    fun initialize(): Boolean {
        val loaded = thresholdsLoader.load()
        thresholds = loaded.getOrElse {
            Timber.e(it, "Failed to load quality.json; using defaults")
            QualityThresholds()
        }
        return nativeQualityEngine.initialize()
    }

    @Synchronized
    fun resetMotionState() {
        nativeQualityEngine.resetMotionState()
    }

    @Synchronized
    fun release() {
        nativeQualityEngine.release()
    }

    /**
     * Evaluates frame suitability for calibration capture.
     *
     * @param yBuffer Direct Y-plane buffer (CameraX YUV_420_888).
     * @param width Sensor frame width before rotation.
     * @param height Sensor frame height before rotation.
     * @param yRowStride Y plane row stride.
     * @param rotationDegrees Display rotation applied to measurements.
     * @param detection Optional detection result for board-derived metrics.
     * @param boardConfig Board geometry for expected corner count.
     */
    @Synchronized
    fun evaluate(
        yBuffer: ByteBuffer,
        width: Int,
        height: Int,
        yRowStride: Int,
        rotationDegrees: Int,
        detection: DetectionResult?,
        boardConfig: BoardConfig?
    ): QualityResult {
        val start = System.currentTimeMillis()
        val t = thresholds

        if (!nativeQualityEngine.isInitialized()) {
            initialize()
        }

        val native = nativeQualityEngine.evaluate(
            yBuffer = yBuffer,
            width = width,
            height = height,
            yRowStride = yRowStride,
            rotationDegrees = rotationDegrees,
            darkPixelThreshold = t.darkPixelThreshold,
            brightPixelThreshold = t.brightPixelThreshold,
            detection = detection,
            boardConfig = boardConfig
        )

        val metrics = ArrayList<QualityMetric>(12)

        val isTargetAware = detection?.boardDetected == true
        if (native == null || !native.success) {
            Timber.e("Native image quality evaluation failed")
            metrics += failedNativePlaceholders(t)
        } else {
            metrics += metricsFromNative(native, t, isTargetAware)
        }

        val frameW = native?.frameWidth?.takeIf { it > 0 } ?: ImageDimensionUtils.rotatedWidth(width, height, rotationDegrees)
        val frameH = native?.frameHeight?.takeIf { it > 0 } ?: ImageDimensionUtils.rotatedHeight(width, height, rotationDegrees)

        metrics += metricsFromDetection(detection, boardConfig, frameW, frameH, t)
        metrics += QualityMetricRules.imageResolution(frameW, frameH, t)
        metrics += QualityMetricRules.aspectRatio(frameW, frameH, t)

        val summary = buildSummary(metrics)
        val elapsed = System.currentTimeMillis() - start
        Timber.d(
            "Quality evaluated in %dms accepted=%s fails=%d",
            elapsed,
            summary.isAccepted,
            summary.failCount
        )

        return QualityResult(
            metrics = metrics.toList(),
            summary = summary,
            processingTimeMs = elapsed,
            frameWidthPx = frameW,
            frameHeightPx = frameH
        )
    }

    /**
     * Applies thresholds to already-measured native scores (unit-test entry).
     */
    fun evaluateFromMeasurements(
        native: NativeImageQualityMeasurements,
        detection: DetectionResult?,
        boardConfig: BoardConfig?,
        thresholdsOverride: QualityThresholds = thresholds
    ): QualityResult {
        val start = System.currentTimeMillis()
        val t = thresholdsOverride
        val isTargetAware = detection?.boardDetected == true
        val metrics = ArrayList<QualityMetric>(12)
        metrics += metricsFromNative(native, t, isTargetAware)
        metrics += metricsFromDetection(
            detection,
            boardConfig,
            native.frameWidth,
            native.frameHeight,
            t
        )
        metrics += QualityMetricRules.imageResolution(native.frameWidth, native.frameHeight, t)
        metrics += QualityMetricRules.aspectRatio(native.frameWidth, native.frameHeight, t)
        return QualityResult(
            metrics = metrics.toList(),
            summary = buildSummary(metrics),
            processingTimeMs = System.currentTimeMillis() - start,
            frameWidthPx = native.frameWidth,
            frameHeightPx = native.frameHeight
        )
    }

    fun currentThresholds(): QualityThresholds = thresholds

    private fun metricsFromNative(
        native: NativeImageQualityMeasurements,
        t: QualityThresholds,
        isTargetAware: Boolean = false
    ): List<QualityMetric> {
        return listOf(
            QualityMetricRules.blur(native.blurLaplacianVariance, t),
            QualityMetricRules.sharpness(native.sharpnessGradientMagnitude, t),
            QualityMetricRules.exposure(
                native.meanBrightness,
                native.darkPixelRatio,
                native.brightPixelRatio,
                t,
                isTargetAware
            ),
            QualityMetricRules.blackLevel(
                native.blackMeanBrightness,
                native.blackClippingRatio,
                t,
                isTargetAware
            ),
            QualityMetricRules.contrast(native.contrastScore, t, isTargetAware),
            QualityMetricRules.noise(native.noiseScore, t),
            QualityMetricRules.motion(native.motionMad, native.hasPriorFrame, t)
        )
    }

    private fun failedNativePlaceholders(t: QualityThresholds): List<QualityMetric> {
        val reason = "Native image quality measurement failed"
        return listOf(
            QualityMetric(
                com.openprofiler.domain.model.QualityMetricId.BLUR,
                0.0,
                t.minBlurLaplacianVariance,
                QualityMetricStatus.FAIL,
                reason
            ),
            QualityMetric(
                com.openprofiler.domain.model.QualityMetricId.SHARPNESS,
                0.0,
                t.minSharpnessGradientMagnitude,
                QualityMetricStatus.FAIL,
                reason
            ),
            QualityMetric(
                com.openprofiler.domain.model.QualityMetricId.EXPOSURE,
                0.0,
                t.minMeanBrightness,
                QualityMetricStatus.FAIL,
                reason
            ),
            QualityMetric(
                com.openprofiler.domain.model.QualityMetricId.BLACK_LEVEL,
                0.0,
                t.maxBlackMean,
                QualityMetricStatus.FAIL,
                reason
            ),
            QualityMetric(
                com.openprofiler.domain.model.QualityMetricId.CONTRAST,
                0.0,
                t.minContrastScore,
                QualityMetricStatus.FAIL,
                reason
            ),
            QualityMetric(
                com.openprofiler.domain.model.QualityMetricId.NOISE,
                0.0,
                t.maxNoiseScore,
                QualityMetricStatus.FAIL,
                reason
            ),
            QualityMetric(
                com.openprofiler.domain.model.QualityMetricId.MOTION,
                0.0,
                t.maxMotionMad,
                QualityMetricStatus.FAIL,
                reason
            )
        )
    }

    private fun metricsFromDetection(
        detection: DetectionResult?,
        boardConfig: BoardConfig?,
        frameW: Int,
        frameH: Int,
        t: QualityThresholds
    ): List<QualityMetric> {
        val coverage = measureCoverage(detection?.observedBoundingBox, frameW, frameH)
        val coverageMetric = QualityMetricRules.targetCoverage(
            coverage?.widthPercent,
            coverage?.heightPercent,
            coverage?.areaPercent,
            t
        )

        val corners = detection?.cornerCoordinates
        val cornerMetric = if (corners.isNullOrEmpty()) {
            QualityMetricRules.cornerQuality(null, null, null, null, t)
        } else {
            val responses = corners.map { it.subpixelPrecision.toDouble() }
            val avg = responses.average()
            val minResp = responses.minOrNull() ?: 0.0
            val successRatio = responses.count { it > 0.0 }.toDouble() / responses.size
            QualityMetricRules.cornerQuality(avg, minResp, corners.size, successRatio, t)
        }

        val expectedCorners = expectedCharucoCorners(boardConfig)
        val visibilityPercent = if (detection == null || !detection.boardDetected || expectedCorners <= 0) {
            null
        } else {
            (detection.charucoCornerCount.toDouble() / expectedCorners.toDouble()) * 100.0
        }
        val visibilityMetric = QualityMetricRules.boardVisibility(visibilityPercent, t)

        val poseMetric = if (detection?.boardPose == null) {
            QualityMetricRules.poseQuality(null, null, null, null, null, t)
        } else {
            val pose = detection.boardPose
            val measured = QualityMetricRules.poseFromRodrigues(pose.rvec, pose.tvec)
            QualityMetricRules.poseQuality(
                measured.pitchDegrees,
                measured.yawDegrees,
                measured.rollDegrees,
                measured.distanceMm,
                measured.frontalScore,
                t,
                intrinsicsSource = pose.intrinsicsSource,
            )
        }

        return listOf(coverageMetric, cornerMetric, visibilityMetric, poseMetric)
    }

    private fun measureCoverage(
        bbox: List<Point2D>?,
        frameW: Int,
        frameH: Int
    ): CoverageMeasurements? {
        if (bbox.isNullOrEmpty() || frameW <= 0 || frameH <= 0) return null
        var minX = Double.POSITIVE_INFINITY
        var minY = Double.POSITIVE_INFINITY
        var maxX = Double.NEGATIVE_INFINITY
        var maxY = Double.NEGATIVE_INFINITY
        for (p in bbox) {
            minX = min(minX, p.x.toDouble())
            minY = min(minY, p.y.toDouble())
            maxX = max(maxX, p.x.toDouble())
            maxY = max(maxY, p.y.toDouble())
        }
        val boardW = abs(maxX - minX)
        val boardH = abs(maxY - minY)
        val widthPercent = (boardW / frameW) * 100.0
        val heightPercent = (boardH / frameH) * 100.0
        val areaPercent = ((boardW * boardH) / (frameW.toDouble() * frameH.toDouble())) * 100.0
        return CoverageMeasurements(widthPercent, heightPercent, areaPercent)
    }

    private fun expectedCharucoCorners(boardConfig: BoardConfig?): Int {
        if (boardConfig == null) return 0
        val cx = boardConfig.squaresX - 1
        val cy = boardConfig.squaresY - 1
        return if (cx > 0 && cy > 0) cx * cy else 0
    }

    private fun buildSummary(metrics: List<QualityMetric>): QualitySummary {
        val pass = metrics.count { it.status == QualityMetricStatus.PASS }
        val warn = metrics.count { it.status == QualityMetricStatus.WARNING }
        val fail = metrics.count { it.status == QualityMetricStatus.FAIL }
        val total = metrics.size.coerceAtLeast(1)
        val score = (pass + warn * 0.5) / total
        val reject = metrics.firstOrNull { it.status == QualityMetricStatus.FAIL }?.reason
        return QualitySummary(
            isAccepted = fail == 0,
            overallScore = score.coerceIn(0.0, 1.0),
            passCount = pass,
            warningCount = warn,
            failCount = fail,
            primaryRejectReason = reject
        )
    }

    private data class CoverageMeasurements(
        val widthPercent: Double,
        val heightPercent: Double,
        val areaPercent: Double
    )
}
