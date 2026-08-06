package com.openprofiler.quality

import com.openprofiler.domain.model.QualityMetric
import com.openprofiler.domain.model.QualityMetricId
import com.openprofiler.domain.model.QualityMetricStatus
import com.openprofiler.domain.model.QualityThresholds
import com.openprofiler.domain.model.IntrinsicsSource
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Pure threshold application for measured quality values.
 * Contains no I/O and no OpenCV — fully unit-testable.
 */
object QualityMetricRules {

    fun blur(variance: Double, t: QualityThresholds): QualityMetric {
        val status = when {
            variance < t.minBlurLaplacianVariance -> QualityMetricStatus.FAIL
            variance < t.warningBlurLaplacianVariance -> QualityMetricStatus.WARNING
            else -> QualityMetricStatus.PASS
        }
        val reason = when (status) {
            QualityMetricStatus.FAIL ->
                "Blur Laplacian variance $variance below minimum ${t.minBlurLaplacianVariance}"
            QualityMetricStatus.WARNING ->
                "Blur Laplacian variance $variance below warning ${t.warningBlurLaplacianVariance}"
            QualityMetricStatus.PASS ->
                "Blur Laplacian variance $variance meets threshold ${t.minBlurLaplacianVariance}"
        }
        return QualityMetric(QualityMetricId.BLUR, variance, t.minBlurLaplacianVariance, status, reason)
    }

    fun sharpness(magnitude: Double, t: QualityThresholds): QualityMetric {
        val status = when {
            magnitude < t.minSharpnessGradientMagnitude -> QualityMetricStatus.FAIL
            magnitude < t.warningSharpnessGradientMagnitude -> QualityMetricStatus.WARNING
            else -> QualityMetricStatus.PASS
        }
        val reason = when (status) {
            QualityMetricStatus.FAIL ->
                "Sharpness gradient magnitude $magnitude below minimum ${t.minSharpnessGradientMagnitude}"
            QualityMetricStatus.WARNING ->
                "Sharpness gradient magnitude $magnitude below warning ${t.warningSharpnessGradientMagnitude}"
            QualityMetricStatus.PASS ->
                "Sharpness gradient magnitude $magnitude meets threshold ${t.minSharpnessGradientMagnitude}"
        }
        return QualityMetric(
            QualityMetricId.SHARPNESS,
            magnitude,
            t.minSharpnessGradientMagnitude,
            status,
            reason
        )
    }

    fun exposure(
        meanBrightness: Double,
        darkRatio: Double,
        brightRatio: Double,
        t: QualityThresholds
    ): QualityMetric {
        val underExposed = meanBrightness < t.minMeanBrightness || darkRatio > t.maxDarkPixelRatio
        val overExposed = meanBrightness > t.maxMeanBrightness || brightRatio > t.maxBrightPixelRatio
        val warningBand = meanBrightness < t.warningMeanBrightnessLow ||
            meanBrightness > t.warningMeanBrightnessHigh

        val status = when {
            underExposed || overExposed -> QualityMetricStatus.FAIL
            warningBand -> QualityMetricStatus.WARNING
            else -> QualityMetricStatus.PASS
        }
        val reason = when {
            underExposed ->
                "Underexposed: mean=$meanBrightness darkRatio=$darkRatio"
            overExposed ->
                "Overexposed: mean=$meanBrightness brightRatio=$brightRatio"
            warningBand ->
                "Exposure near limits: mean=$meanBrightness"
            else ->
                "Exposure within limits: mean=$meanBrightness"
        }
        return QualityMetric(
            id = QualityMetricId.EXPOSURE,
            value = meanBrightness,
            threshold = t.minMeanBrightness,
            status = status,
            reason = reason,
            secondaryValues = mapOf(
                "darkRatio" to darkRatio,
                "brightRatio" to brightRatio,
                "maxMeanBrightness" to t.maxMeanBrightness,
                "underexposed" to if (underExposed) 1.0 else 0.0,
                "overexposed" to if (overExposed) 1.0 else 0.0
            )
        )
    }

    fun contrast(score: Double, t: QualityThresholds): QualityMetric {
        val status = when {
            score < t.minContrastScore -> QualityMetricStatus.FAIL
            score < t.warningContrastScore -> QualityMetricStatus.WARNING
            else -> QualityMetricStatus.PASS
        }
        val reason = when (status) {
            QualityMetricStatus.FAIL ->
                "Contrast score $score below minimum ${t.minContrastScore}"
            QualityMetricStatus.WARNING ->
                "Contrast score $score below warning ${t.warningContrastScore}"
            QualityMetricStatus.PASS ->
                "Contrast score $score meets threshold ${t.minContrastScore}"
        }
        return QualityMetric(QualityMetricId.CONTRAST, score, t.minContrastScore, status, reason)
    }

    fun noise(score: Double, t: QualityThresholds): QualityMetric {
        val status = when {
            score > t.maxNoiseScore -> QualityMetricStatus.FAIL
            score > t.warningNoiseScore -> QualityMetricStatus.WARNING
            else -> QualityMetricStatus.PASS
        }
        val reason = when (status) {
            QualityMetricStatus.FAIL ->
                "Noise score $score above maximum ${t.maxNoiseScore}"
            QualityMetricStatus.WARNING ->
                "Noise score $score above warning ${t.warningNoiseScore}"
            QualityMetricStatus.PASS ->
                "Noise score $score within maximum ${t.maxNoiseScore}"
        }
        return QualityMetric(QualityMetricId.NOISE, score, t.maxNoiseScore, status, reason)
    }

    fun targetCoverage(
        boardWidthPercent: Double?,
        boardHeightPercent: Double?,
        imageCoveragePercent: Double?,
        t: QualityThresholds
    ): QualityMetric {
        if (boardWidthPercent == null || boardHeightPercent == null || imageCoveragePercent == null) {
            return QualityMetric(
                QualityMetricId.TARGET_COVERAGE,
                0.0,
                t.minImageCoveragePercent,
                QualityMetricStatus.FAIL,
                "Target coverage unavailable: board bounding box not measured"
            )
        }
        val widthFail = boardWidthPercent < t.minBoardWidthPercent
        val heightFail = boardHeightPercent < t.minBoardHeightPercent
        val coverageFail = imageCoveragePercent < t.minImageCoveragePercent
        val coverageWarn = imageCoveragePercent < t.warningImageCoveragePercent
        val status = when {
            widthFail || heightFail || coverageFail -> QualityMetricStatus.FAIL
            coverageWarn -> QualityMetricStatus.WARNING
            else -> QualityMetricStatus.PASS
        }
        val reason = when (status) {
            QualityMetricStatus.FAIL ->
                "Coverage insufficient: width%=$boardWidthPercent height%=$boardHeightPercent area%=$imageCoveragePercent"
            QualityMetricStatus.WARNING ->
                "Coverage marginal: area%=$imageCoveragePercent"
            QualityMetricStatus.PASS ->
                "Coverage adequate: area%=$imageCoveragePercent"
        }
        return QualityMetric(
            id = QualityMetricId.TARGET_COVERAGE,
            value = imageCoveragePercent,
            threshold = t.minImageCoveragePercent,
            status = status,
            reason = reason,
            secondaryValues = mapOf(
                "boardWidthPercent" to boardWidthPercent,
                "boardHeightPercent" to boardHeightPercent
            )
        )
    }

    fun cornerQuality(
        averageResponse: Double?,
        minimumResponse: Double?,
        cornerCount: Int?,
        subpixelSuccessRatio: Double?,
        t: QualityThresholds
    ): QualityMetric {
        if (averageResponse == null || minimumResponse == null ||
            cornerCount == null || subpixelSuccessRatio == null
        ) {
            return QualityMetric(
                QualityMetricId.CORNER_QUALITY,
                0.0,
                t.minAverageCornerResponse,
                QualityMetricStatus.FAIL,
                "Corner quality unavailable: corners not measured"
            )
        }
        val fail = averageResponse < t.minAverageCornerResponse ||
            minimumResponse < t.minMinimumCornerResponse ||
            cornerCount < t.minCornerCount ||
            subpixelSuccessRatio < t.minSubpixelSuccessRatio
        val status = if (fail) QualityMetricStatus.FAIL else QualityMetricStatus.PASS
        val reason = if (fail) {
            "Corner quality below thresholds: avg=$averageResponse min=$minimumResponse " +
                "count=$cornerCount subpixelRatio=$subpixelSuccessRatio"
        } else {
            "Corner quality meets thresholds: avg=$averageResponse count=$cornerCount"
        }
        return QualityMetric(
            id = QualityMetricId.CORNER_QUALITY,
            value = averageResponse,
            threshold = t.minAverageCornerResponse,
            status = status,
            reason = reason,
            secondaryValues = mapOf(
                "minimumResponse" to minimumResponse,
                "cornerCount" to cornerCount.toDouble(),
                "subpixelSuccessRatio" to subpixelSuccessRatio
            )
        )
    }

    fun boardVisibility(visibilityPercent: Double?, t: QualityThresholds): QualityMetric {
        if (visibilityPercent == null) {
            return QualityMetric(
                QualityMetricId.BOARD_VISIBILITY,
                0.0,
                t.minBoardVisibilityPercent,
                QualityMetricStatus.FAIL,
                "Board visibility unavailable: expected corners unknown or board not detected"
            )
        }
        val status = when {
            visibilityPercent < t.minBoardVisibilityPercent -> QualityMetricStatus.FAIL
            visibilityPercent < t.warningBoardVisibilityPercent -> QualityMetricStatus.WARNING
            else -> QualityMetricStatus.PASS
        }
        val reason = when (status) {
            QualityMetricStatus.FAIL ->
                "Board visibility $visibilityPercent% below minimum ${t.minBoardVisibilityPercent}%"
            QualityMetricStatus.WARNING ->
                "Board visibility $visibilityPercent% below warning ${t.warningBoardVisibilityPercent}%"
            QualityMetricStatus.PASS ->
                "Board visibility $visibilityPercent% meets threshold ${t.minBoardVisibilityPercent}%"
        }
        return QualityMetric(
            QualityMetricId.BOARD_VISIBILITY,
            visibilityPercent,
            t.minBoardVisibilityPercent,
            status,
            reason
        )
    }

    fun poseQuality(
        pitchDegrees: Double?,
        yawDegrees: Double?,
        rollDegrees: Double?,
        distanceMm: Double?,
        frontalScore: Double?,
        t: QualityThresholds,
        intrinsicsSource: IntrinsicsSource = IntrinsicsSource.UNAVAILABLE,
    ): QualityMetric {
        if (pitchDegrees == null || yawDegrees == null || rollDegrees == null ||
            distanceMm == null || frontalScore == null
        ) {
            return QualityMetric(
                QualityMetricId.POSE_QUALITY,
                0.0,
                t.minFrontalScore,
                QualityMetricStatus.FAIL,
                "Pose quality unavailable: board pose not measured"
            )
        }
        val angleFail = abs(pitchDegrees) > t.maxAbsPitchDegrees ||
            abs(yawDegrees) > t.maxAbsYawDegrees ||
            abs(rollDegrees) > t.maxAbsRollDegrees
        val distanceFail = distanceMm < t.minDistanceMm || distanceMm > t.maxDistanceMm
        val frontalFail = frontalScore < t.minFrontalScore
        val frontalWarn = frontalScore < t.warningFrontalScore
        val status = when {
            angleFail || distanceFail || frontalFail -> QualityMetricStatus.FAIL
            frontalWarn -> QualityMetricStatus.WARNING
            else -> QualityMetricStatus.PASS
        }
        val provenanceNote = "intrinsicsSource=${intrinsicsSource.name}"
        val reason = when {
            angleFail ->
                "Pose angles exceed limits: pitch=$pitchDegrees yaw=$yawDegrees roll=$rollDegrees ($provenanceNote)"
            distanceFail ->
                "Pose distance $distanceMm mm outside [${t.minDistanceMm}, ${t.maxDistanceMm}] ($provenanceNote)"
            frontalFail ->
                "Frontal score $frontalScore below minimum ${t.minFrontalScore} (near-singular view) ($provenanceNote)"
            frontalWarn ->
                "Frontal score $frontalScore below warning ${t.warningFrontalScore} ($provenanceNote)"
            else ->
                "Pose quality acceptable: frontal=$frontalScore distanceMm=$distanceMm ($provenanceNote)"
        }
        return QualityMetric(
            id = QualityMetricId.POSE_QUALITY,
            value = frontalScore,
            threshold = t.minFrontalScore,
            status = status,
            reason = reason,
            secondaryValues = mapOf(
                "pitchDegrees" to pitchDegrees,
                "yawDegrees" to yawDegrees,
                "rollDegrees" to rollDegrees,
                "distanceMm" to distanceMm,
                "intrinsicsSourceOrdinal" to intrinsicsSource.ordinal.toDouble(),
            )
        )
    }

    fun motion(mad: Double, hasPriorFrame: Boolean, t: QualityThresholds): QualityMetric {
        if (!hasPriorFrame) {
            return QualityMetric(
                QualityMetricId.MOTION,
                0.0,
                t.maxMotionMad,
                QualityMetricStatus.WARNING,
                "Motion not measured: no prior frame available"
            )
        }
        val status = when {
            mad > t.maxMotionMad -> QualityMetricStatus.FAIL
            mad > t.warningMotionMad -> QualityMetricStatus.WARNING
            else -> QualityMetricStatus.PASS
        }
        val reason = when (status) {
            QualityMetricStatus.FAIL ->
                "Inter-frame motion MAD $mad above maximum ${t.maxMotionMad}"
            QualityMetricStatus.WARNING ->
                "Inter-frame motion MAD $mad above warning ${t.warningMotionMad}"
            QualityMetricStatus.PASS ->
                "Inter-frame motion MAD $mad within maximum ${t.maxMotionMad}"
        }
        return QualityMetric(QualityMetricId.MOTION, mad, t.maxMotionMad, status, reason)
    }

    fun imageResolution(widthPx: Int, heightPx: Int, t: QualityThresholds): QualityMetric {
        val minDimFrame = minOf(widthPx, heightPx)
        val maxDimFrame = maxOf(widthPx, heightPx)
        val minDimReq = minOf(t.minWidthPx, t.minHeightPx)
        val maxDimReq = maxOf(t.minWidthPx, t.minHeightPx)

        val pass = minDimFrame >= minDimReq && maxDimFrame >= maxDimReq
        val status = if (pass) QualityMetricStatus.PASS else QualityMetricStatus.FAIL
        val minDim = minDimFrame.toDouble()
        val reason = if (pass) {
            "Resolution ${widthPx}x${heightPx} meets minimum requirement (${minDimReq}x${maxDimReq})"
        } else {
            "Resolution ${widthPx}x${heightPx} below minimum requirement (${minDimReq}x${maxDimReq})"
        }
        return QualityMetric(
            id = QualityMetricId.IMAGE_RESOLUTION,
            value = minDim,
            threshold = minDimReq.toDouble(),
            status = status,
            reason = reason,
            secondaryValues = mapOf(
                "widthPx" to widthPx.toDouble(),
                "heightPx" to heightPx.toDouble()
            )
        )
    }

    fun aspectRatio(widthPx: Int, heightPx: Int, t: QualityThresholds): QualityMetric {
        if (widthPx <= 0 || heightPx <= 0) {
            return QualityMetric(
                QualityMetricId.ASPECT_RATIO,
                0.0,
                1.0,
                QualityMetricStatus.FAIL,
                "Aspect ratio undefined for non-positive dimensions"
            )
        }
        val ratio = widthPx.toDouble() / heightPx.toDouble()
        val matched = t.supportedAspectRatios.any { supported ->
            abs(ratio - supported) <= t.aspectRatioTolerance ||
                abs(ratio - (1.0 / supported)) <= t.aspectRatioTolerance
        }
        val status = if (matched) QualityMetricStatus.PASS else QualityMetricStatus.FAIL
        val reason = if (matched) {
            "Aspect ratio $ratio matches a supported ratio within tolerance ${t.aspectRatioTolerance}"
        } else {
            "Aspect ratio $ratio does not match supported ratios ${t.supportedAspectRatios}"
        }
        return QualityMetric(
            QualityMetricId.ASPECT_RATIO,
            ratio,
            t.aspectRatioTolerance,
            status,
            reason
        )
    }

    /**
     * Converts Rodrigues rvec to approximate pitch/yaw/roll (degrees) and frontal score.
     * Frontal score = |R[2,2]| from the rotation matrix (board facing camera).
     */
    fun poseFromRodrigues(rvec: DoubleArray, tvec: DoubleArray): PoseMeasurements {
        require(rvec.size == 3 && tvec.size == 3)
        val angle = sqrt(rvec[0] * rvec[0] + rvec[1] * rvec[1] + rvec[2] * rvec[2])
        val rotation = Array(3) { DoubleArray(3) }
        if (angle < 1e-12) {
            rotation[0][0] = 1.0
            rotation[1][1] = 1.0
            rotation[2][2] = 1.0
        } else {
            val ax = rvec[0] / angle
            val ay = rvec[1] / angle
            val az = rvec[2] / angle
            val c = kotlin.math.cos(angle)
            val s = kotlin.math.sin(angle)
            val oneMinusC = 1.0 - c
            rotation[0][0] = c + ax * ax * oneMinusC
            rotation[0][1] = ax * ay * oneMinusC - az * s
            rotation[0][2] = ax * az * oneMinusC + ay * s
            rotation[1][0] = ay * ax * oneMinusC + az * s
            rotation[1][1] = c + ay * ay * oneMinusC
            rotation[1][2] = ay * az * oneMinusC - ax * s
            rotation[2][0] = az * ax * oneMinusC - ay * s
            rotation[2][1] = az * ay * oneMinusC + ax * s
            rotation[2][2] = c + az * az * oneMinusC
        }
        // ZYX intrinsic-style extraction for reporting
        val pitch = Math.toDegrees(kotlin.math.asin((-rotation[2][1]).coerceIn(-1.0, 1.0)))
        val yaw = Math.toDegrees(
            kotlin.math.atan2(rotation[2][0], rotation[2][2])
        )
        val roll = Math.toDegrees(
            kotlin.math.atan2(rotation[0][1], rotation[1][1])
        )
        val distanceMm = sqrt(tvec[0] * tvec[0] + tvec[1] * tvec[1] + tvec[2] * tvec[2])
        val frontalScore = abs(rotation[2][2])
        return PoseMeasurements(pitch, yaw, roll, distanceMm, frontalScore)
    }

    data class PoseMeasurements(
        val pitchDegrees: Double,
        val yawDegrees: Double,
        val rollDegrees: Double,
        val distanceMm: Double,
        val frontalScore: Double
    )
}
