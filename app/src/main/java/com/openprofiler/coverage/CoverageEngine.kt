package com.openprofiler.coverage

import com.openprofiler.domain.model.CoverageData
import com.openprofiler.domain.model.CoverageEvaluationResult
import com.openprofiler.domain.model.CoverageGuidance
import com.openprofiler.domain.model.CoverageThresholds
import com.openprofiler.domain.model.DetectionResult
import com.openprofiler.domain.model.ScaleBucket
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Core engine evaluating calibration pose diversity and frame acceptance.
 *
 * Enforces position grid coverage, board scale buckets, pose angle bins (pitch/yaw/roll),
 * and distance bins to maximize calibration matrix conditioning and reject redundant frames.
 *
 * Pure business logic with zero Android, CameraX, Compose, or OpenCV JNI dependencies.
 */
@Singleton
class CoverageEngine @Inject constructor() {

    private val lock = Any()

    private var activeThresholds: CoverageThresholds = CoverageThresholds()

    // Session state
    private val visitedGridCells = mutableSetOf<Pair<Int, Int>>() // (row, col)
    private val visitedScaleBuckets = mutableSetOf<ScaleBucket>()
    private val visitedPitchBins = mutableSetOf<Int>()
    private val visitedYawBins = mutableSetOf<Int>()
    private val visitedRollBins = mutableSetOf<Int>()
    private val visitedDistanceBins = mutableSetOf<Int>()
    private var acceptedFrameCount = 0
    private var currentDiversityScore = 0.0

    /**
     * Updates active thresholds configuration.
     */
    fun setThresholds(thresholds: CoverageThresholds) {
        synchronized(lock) {
            activeThresholds = thresholds
            recalculateScore()
        }
    }

    /**
     * Returns active coverage thresholds.
     */
    fun getThresholds(): CoverageThresholds = synchronized(lock) { activeThresholds }

    /**
     * Resets session coverage state.
     */
    fun resetState() {
        synchronized(lock) {
            visitedGridCells.clear()
            visitedScaleBuckets.clear()
            visitedPitchBins.clear()
            visitedYawBins.clear()
            visitedRollBins.clear()
            visitedDistanceBins.clear()
            acceptedFrameCount = 0
            currentDiversityScore = 0.0
        }
    }

    /**
     * Returns current accumulated coverage data breakdown.
     */
    fun getCurrentCoverage(): CoverageData = synchronized(lock) {
        computeCoverageData(activeThresholds)
    }

    /**
     * Evaluates a frame detection result against current session coverage state.
     */
    fun evaluate(
        detection: DetectionResult,
        frameWidth: Int,
        frameHeight: Int,
        thresholds: CoverageThresholds = activeThresholds
    ): CoverageEvaluationResult = synchronized(lock) {
        if (!detection.boardDetected) {
            val coverageData = computeCoverageData(thresholds)
            return CoverageEvaluationResult(
                isAccepted = false,
                rejectReason = "No target detected",
                coverageDelta = 0.0,
                diversityScore = currentDiversityScore,
                guidance = computeGuidance(thresholds),
                remainingRequirements = computeRemainingRequirements(thresholds),
                coverageData = coverageData
            )
        }

        if (frameWidth <= 0 || frameHeight <= 0) {
            val coverageData = computeCoverageData(thresholds)
            return CoverageEvaluationResult(
                isAccepted = false,
                rejectReason = "Invalid frame dimensions ($frameWidth x $frameHeight)",
                coverageDelta = 0.0,
                diversityScore = currentDiversityScore,
                guidance = computeGuidance(thresholds),
                remainingRequirements = computeRemainingRequirements(thresholds),
                coverageData = coverageData
            )
        }

        val boardCenter = computeBoardCenter(detection)
        if (boardCenter == null) {
            val coverageData = computeCoverageData(thresholds)
            return CoverageEvaluationResult(
                isAccepted = false,
                rejectReason = "Board center could not be computed",
                coverageDelta = 0.0,
                diversityScore = currentDiversityScore,
                guidance = computeGuidance(thresholds),
                remainingRequirements = computeRemainingRequirements(thresholds),
                coverageData = coverageData
            )
        }

        // 1. Grid cell index
        val gridCell = computeGridCell(boardCenter.first, boardCenter.second, frameWidth, frameHeight, thresholds)

        // 2. Scale bucket
        val scaleBucket = computeScaleBucket(detection, frameWidth, frameHeight, thresholds)

        // 3. Pose angle bins & distance bin
        val pose = detection.boardPose
        val pitchBin: Int?
        val yawBin: Int?
        val rollBin: Int?
        val distanceBin: Int?

        if (pose != null) {
            val eulerDeg = rvecToEulerDegrees(pose.rvec)
            pitchBin = computeBinIndex(eulerDeg[0], thresholds.pitchBinsDegrees)
            yawBin = computeBinIndex(eulerDeg[1], thresholds.yawBinsDegrees)
            rollBin = computeBinIndex(eulerDeg[2], thresholds.rollBinsDegrees)

            val distanceMm = sqrt(pose.tvec[0] * pose.tvec[0] + pose.tvec[1] * pose.tvec[1] + pose.tvec[2] * pose.tvec[2])
            distanceBin = computeBinIndex(distanceMm, thresholds.distanceBinsMm)
        } else {
            pitchBin = null
            yawBin = null
            rollBin = null
            distanceBin = null
        }

        // Calculate potential new score if accepted
        val newGridCells = visitedGridCells.toMutableSet().apply { add(gridCell) }
        val newScaleBuckets = visitedScaleBuckets.toMutableSet().apply { add(scaleBucket) }

        val newPitchBins = visitedPitchBins.toMutableSet()
        if (pitchBin != null) newPitchBins.add(pitchBin)

        val newYawBins = visitedYawBins.toMutableSet()
        if (yawBin != null) newYawBins.add(yawBin)

        val newRollBins = visitedRollBins.toMutableSet()
        if (rollBin != null) newRollBins.add(rollBin)

        val newDistanceBins = visitedDistanceBins.toMutableSet()
        if (distanceBin != null) newDistanceBins.add(distanceBin)

        val proposedScore = computeScoreFromSets(
            gridCells = newGridCells,
            scaleBuckets = newScaleBuckets,
            pitchBins = newPitchBins,
            yawBins = newYawBins,
            rollBins = newRollBins,
            distanceBins = newDistanceBins,
            thresholds = thresholds
        )

        val delta = proposedScore - currentDiversityScore

        if (delta >= thresholds.minDiversityDeltaToAccept) {
            // ACCEPTED: Commit updates
            visitedGridCells.add(gridCell)
            visitedScaleBuckets.add(scaleBucket)
            if (pitchBin != null) visitedPitchBins.add(pitchBin)
            if (yawBin != null) visitedYawBins.add(yawBin)
            if (rollBin != null) visitedRollBins.add(rollBin)
            if (distanceBin != null) visitedDistanceBins.add(distanceBin)

            acceptedFrameCount++
            currentDiversityScore = proposedScore

            val coverageData = computeCoverageData(thresholds)
            val guidance = computeGuidance(thresholds)
            val remaining = computeRemainingRequirements(thresholds)

            CoverageEvaluationResult(
                isAccepted = true,
                rejectReason = null,
                coverageDelta = delta,
                diversityScore = currentDiversityScore,
                guidance = guidance,
                remainingRequirements = remaining,
                coverageData = coverageData
            )
        } else {
            // REJECTED: Duplicate coverage / insufficient score gain
            val coverageData = computeCoverageData(thresholds)
            val guidance = computeGuidance(thresholds)
            val remaining = computeRemainingRequirements(thresholds)

            val reason = when {
                gridCell in visitedGridCells && scaleBucket in visitedScaleBuckets -> "Duplicate grid cell ($gridCell) and scale bucket ($scaleBucket)"
                else -> "Duplicate frame (coverage delta $delta below threshold ${thresholds.minDiversityDeltaToAccept})"
            }

            CoverageEvaluationResult(
                isAccepted = false,
                rejectReason = reason,
                coverageDelta = 0.0,
                diversityScore = currentDiversityScore,
                guidance = guidance,
                remainingRequirements = remaining,
                coverageData = coverageData
            )
        }
    }

    // --- Helper calculations ---

    internal fun computeBoardCenter(detection: DetectionResult): Pair<Double, Double>? {
        val corners = detection.cornerCoordinates
        if (corners.isNotEmpty()) {
            var sumX = 0.0
            var sumY = 0.0
            for (c in corners) {
                sumX += c.x
                sumY += c.y
            }
            return Pair(sumX / corners.size, sumY / corners.size)
        }
        val obb = detection.observedBoundingBox
        if (!obb.isNullOrEmpty()) {
            var sumX = 0.0
            var sumY = 0.0
            for (pt in obb) {
                sumX += pt.x
                sumY += pt.y
            }
            return Pair(sumX / obb.size, sumY / obb.size)
        }
        val bbox = detection.boundingBox
        if (!bbox.isNullOrEmpty()) {
            var sumX = 0.0
            var sumY = 0.0
            for (pt in bbox) {
                sumX += pt.x
                sumY += pt.y
            }
            return Pair(sumX / bbox.size, sumY / bbox.size)
        }
        return null
    }

    internal fun computeGridCell(
        centerX: Double,
        centerY: Double,
        frameWidth: Int,
        frameHeight: Int,
        thresholds: CoverageThresholds
    ): Pair<Int, Int> {
        val normX = (centerX / frameWidth.toDouble()).coerceIn(0.0, 0.999999)
        val normY = (centerY / frameHeight.toDouble()).coerceIn(0.0, 0.999999)
        val col = (normX * thresholds.gridCols).toInt().coerceIn(0, thresholds.gridCols - 1)
        val row = (normY * thresholds.gridRows).toInt().coerceIn(0, thresholds.gridRows - 1)
        return Pair(row, col)
    }

    internal fun computeScaleBucket(
        detection: DetectionResult,
        frameWidth: Int,
        frameHeight: Int,
        thresholds: CoverageThresholds
    ): ScaleBucket {
        val imageArea = frameWidth.toDouble() * frameHeight.toDouble()
        if (imageArea <= 0.0) return ScaleBucket.SMALL

        val corners = detection.cornerCoordinates
        val boardAreaPx = if (corners.size >= 3) {
            var minX = Double.MAX_VALUE
            var maxX = -Double.MAX_VALUE
            var minY = Double.MAX_VALUE
            var maxY = -Double.MAX_VALUE
            for (c in corners) {
                if (c.x < minX) minX = c.x.toDouble()
                if (c.x > maxX) maxX = c.x.toDouble()
                if (c.y < minY) minY = c.y.toDouble()
                if (c.y > maxY) maxY = c.y.toDouble()
            }
            (maxX - minX) * (maxY - minY)
        } else {
            val obb = detection.observedBoundingBox
            if (!obb.isNullOrEmpty()) {
                var minX = Double.MAX_VALUE
                var maxX = -Double.MAX_VALUE
                var minY = Double.MAX_VALUE
                var maxY = -Double.MAX_VALUE
                for (pt in obb) {
                    if (pt.x < minX) minX = pt.x.toDouble()
                    if (pt.x > maxX) maxX = pt.x.toDouble()
                    if (pt.y < minY) minY = pt.y.toDouble()
                    if (pt.y > maxY) maxY = pt.y.toDouble()
                }
                (maxX - minX) * (maxY - minY)
            } else {
                0.0
            }
        }

        val scalePercent = (boardAreaPx / imageArea) * 100.0
        val cuts = thresholds.scaleThresholdsPercent

        return when {
            scalePercent < cuts.getOrElse(0) { 10.0 } -> ScaleBucket.VERY_SMALL
            scalePercent < cuts.getOrElse(1) { 25.0 } -> ScaleBucket.SMALL
            scalePercent < cuts.getOrElse(2) { 40.0 } -> ScaleBucket.MEDIUM
            scalePercent < cuts.getOrElse(3) { 60.0 } -> ScaleBucket.LARGE
            else -> ScaleBucket.VERY_LARGE
        }
    }

    internal fun computeBinIndex(value: Double, cutoffs: List<Double>): Int {
        for (i in cutoffs.indices) {
            if (value < cutoffs[i]) return i
        }
        return cutoffs.size
    }

    /**
     * Converts Rodrigues rotation vector [rx, ry, rz] to Euler angles in degrees: [pitch, yaw, roll].
     */
    internal fun rvecToEulerDegrees(rvec: DoubleArray): DoubleArray {
        if (rvec.size < 3) return doubleArrayOf(0.0, 0.0, 0.0)
        val rx = rvec[0]
        val ry = rvec[1]
        val rz = rvec[2]
        val theta = sqrt(rx * rx + ry * ry + rz * rz)
        if (theta < 1e-8) {
            return doubleArrayOf(0.0, 0.0, 0.0)
        }

        val ux = rx / theta
        val uy = ry / theta
        val uz = rz / theta

        val cosT = cos(theta)
        val sinT = sin(theta)
        val oneMinusCos = 1.0 - cosT

        val r00 = cosT + ux * ux * oneMinusCos
        val r01 = ux * uy * oneMinusCos - uz * sinT
        val r02 = ux * uz * oneMinusCos + uy * sinT

        val r10 = uy * ux * oneMinusCos + uz * sinT
        val r11 = cosT + uy * uy * oneMinusCos
        val r12 = uy * uz * oneMinusCos - ux * sinT

        val r20 = uz * ux * oneMinusCos - uy * sinT
        val r21 = uz * uy * oneMinusCos + ux * sinT
        val r22 = cosT + uz * uz * oneMinusCos

        val sinYaw = r02.coerceIn(-1.0, 1.0)
        val yaw = asin(sinYaw)
        val pitch: Double
        val roll: Double

        if (abs(r02) < 0.99999) {
            pitch = atan2(-r12, r22)
            roll = atan2(-r01, r00)
        } else {
            pitch = 0.0
            roll = atan2(r10, r11)
        }

        val radToDeg = 180.0 / Math.PI
        return doubleArrayOf(pitch * radToDeg, yaw * radToDeg, roll * radToDeg)
    }

    private fun computeScoreFromSets(
        gridCells: Set<Pair<Int, Int>>,
        scaleBuckets: Set<ScaleBucket>,
        pitchBins: Set<Int>,
        yawBins: Set<Int>,
        rollBins: Set<Int>,
        distanceBins: Set<Int>,
        thresholds: CoverageThresholds
    ): Double {
        val totalGrid = (thresholds.gridRows * thresholds.gridCols).toDouble()
        val gridScore = if (totalGrid > 0) (gridCells.size / totalGrid) * 100.0 else 0.0

        val totalScale = 5.0
        val scaleScore = (scaleBuckets.size / totalScale) * 100.0

        val totalPitch = (thresholds.pitchBinsDegrees.size + 1).toDouble()
        val totalYaw = (thresholds.yawBinsDegrees.size + 1).toDouble()
        val totalRoll = (thresholds.rollBinsDegrees.size + 1).toDouble()

        val pitchPct = if (totalPitch > 0) pitchBins.size / totalPitch else 0.0
        val yawPct = if (totalYaw > 0) yawBins.size / totalYaw else 0.0
        val rollPct = if (totalRoll > 0) rollBins.size / totalRoll else 0.0
        val poseScore = ((pitchPct + yawPct + rollPct) / 3.0) * 100.0

        val totalDistance = (thresholds.distanceBinsMm.size + 1).toDouble()
        val distanceScore = if (totalDistance > 0) (distanceBins.size / totalDistance) * 100.0 else 0.0

        val rawScore = (gridScore * thresholds.gridWeight) +
                (scaleScore * thresholds.scaleWeight) +
                (poseScore * thresholds.poseWeight) +
                (distanceScore * thresholds.distanceWeight)

        return rawScore.coerceIn(0.0, 100.0)
    }

    private fun recalculateScore() {
        currentDiversityScore = computeScoreFromSets(
            visitedGridCells,
            visitedScaleBuckets,
            visitedPitchBins,
            visitedYawBins,
            visitedRollBins,
            visitedDistanceBins,
            activeThresholds
        )
    }

    private fun computeCoverageData(thresholds: CoverageThresholds): CoverageData {
        val totalGrid = thresholds.gridRows * thresholds.gridCols
        val gridPct = if (totalGrid > 0) (visitedGridCells.size.toDouble() / totalGrid.toDouble()) * 100.0 else 0.0
        val scalePct = (visitedScaleBuckets.size.toDouble() / 5.0) * 100.0

        val totalPitch = thresholds.pitchBinsDegrees.size + 1
        val totalYaw = thresholds.yawBinsDegrees.size + 1
        val totalRoll = thresholds.rollBinsDegrees.size + 1

        val pPct = if (totalPitch > 0) visitedPitchBins.size.toDouble() / totalPitch else 0.0
        val yPct = if (totalYaw > 0) visitedYawBins.size.toDouble() / totalYaw else 0.0
        val rPct = if (totalRoll > 0) visitedRollBins.size.toDouble() / totalRoll else 0.0
        val posePct = ((pPct + yPct + rPct) / 3.0) * 100.0

        val totalDist = thresholds.distanceBinsMm.size + 1
        val distPct = if (totalDist > 0) (visitedDistanceBins.size.toDouble() / totalDist.toDouble()) * 100.0 else 0.0

        // Region breakdowns (for 3x3 default grid)
        val centerVisited = visitedGridCells.count { (r, c) -> r == 1 && c == 1 }
        val topVisited = visitedGridCells.count { (r, _) -> r == 0 }
        val bottomVisited = visitedGridCells.count { (r, _) -> r == thresholds.gridRows - 1 }
        val leftVisited = visitedGridCells.count { (_, c) -> c == 0 }
        val rightVisited = visitedGridCells.count { (_, c) -> c == thresholds.gridCols - 1 }

        val centerPct = (centerVisited.toDouble() / 1.0) * 100.0
        val topPct = (topVisited.toDouble() / thresholds.gridCols.toDouble()) * 100.0
        val bottomPct = (bottomVisited.toDouble() / thresholds.gridCols.toDouble()) * 100.0
        val leftPct = (leftVisited.toDouble() / thresholds.gridRows.toDouble()) * 100.0
        val rightPct = (rightVisited.toDouble() / thresholds.gridRows.toDouble()) * 100.0

        val totalPoseBinsCount = totalPitch + totalYaw + totalRoll
        val visitedPoseBinsCount = visitedPitchBins.size + visitedYawBins.size + visitedRollBins.size

        return CoverageData(
            overallPercentage = currentDiversityScore,
            center = centerPct.coerceIn(0.0, 100.0),
            top = topPct.coerceIn(0.0, 100.0),
            bottom = bottomPct.coerceIn(0.0, 100.0),
            left = leftPct.coerceIn(0.0, 100.0),
            right = rightPct.coerceIn(0.0, 100.0),
            gridCoveragePercent = gridPct.coerceIn(0.0, 100.0),
            scaleCoveragePercent = scalePct.coerceIn(0.0, 100.0),
            poseCoveragePercent = posePct.coerceIn(0.0, 100.0),
            distanceCoveragePercent = distPct.coerceIn(0.0, 100.0),
            visitedGridCells = visitedGridCells.size,
            totalGridCells = totalGrid,
            visitedScaleBuckets = visitedScaleBuckets.size,
            totalScaleBuckets = 5,
            visitedPoseBins = visitedPoseBinsCount,
            totalPoseBins = totalPoseBinsCount,
            visitedDistanceBins = visitedDistanceBins.size,
            totalDistanceBins = totalDist,
            acceptedFrameCount = acceptedFrameCount
        )
    }

    internal fun computeGuidance(thresholds: CoverageThresholds): CoverageGuidance {
        if (currentDiversityScore >= thresholds.targetDiversityScore) {
            return CoverageGuidance.EXCELLENT
        }

        // Priority 1: Position grid coverage
        val unvisitedLeft = (0 until thresholds.gridRows).count { r -> Pair(r, 0) !in visitedGridCells }
        val unvisitedRight = (0 until thresholds.gridRows).count { r -> Pair(r, thresholds.gridCols - 1) !in visitedGridCells }
        val unvisitedTop = (0 until thresholds.gridCols).count { c -> Pair(0, c) !in visitedGridCells }
        val unvisitedBottom = (0 until thresholds.gridCols).count { c -> Pair(thresholds.gridRows - 1, c) !in visitedGridCells }

        if (unvisitedLeft > 0 && unvisitedLeft >= unvisitedRight) {
            return CoverageGuidance.MOVE_LEFT
        }
        if (unvisitedRight > 0) {
            return CoverageGuidance.MOVE_RIGHT
        }
        if (unvisitedTop > 0 && unvisitedTop >= unvisitedBottom) {
            return CoverageGuidance.MOVE_UP
        }
        if (unvisitedBottom > 0) {
            return CoverageGuidance.MOVE_DOWN
        }

        // Priority 2: Scale & Distance
        if (ScaleBucket.VERY_LARGE !in visitedScaleBuckets || 0 !in visitedDistanceBins) {
            return CoverageGuidance.MOVE_CLOSER
        }
        if (ScaleBucket.VERY_SMALL !in visitedScaleBuckets || (thresholds.distanceBinsMm.size) !in visitedDistanceBins) {
            return CoverageGuidance.MOVE_FARTHER
        }

        // Priority 3: Pose angles (Pitch / Yaw)
        val totalPitchBins = thresholds.pitchBinsDegrees.size + 1
        if (0 !in visitedPitchBins) {
            return CoverageGuidance.TILT_DOWN
        }
        if ((totalPitchBins - 1) !in visitedPitchBins) {
            return CoverageGuidance.TILT_UP
        }

        val totalYawBins = thresholds.yawBinsDegrees.size + 1
        if (0 !in visitedYawBins) {
            return CoverageGuidance.ROTATE_LEFT
        }
        if ((totalYawBins - 1) !in visitedYawBins) {
            return CoverageGuidance.ROTATE_RIGHT
        }

        return CoverageGuidance.EXCELLENT
    }

    internal fun computeRemainingRequirements(thresholds: CoverageThresholds): List<String> {
        val reqs = mutableListOf<String>()

        val totalGrid = thresholds.gridRows * thresholds.gridCols
        if (visitedGridCells.size < totalGrid) {
            reqs.add("Visited grid cells: ${visitedGridCells.size}/$totalGrid")
        }

        val missingScales = ScaleBucket.values().filter { it !in visitedScaleBuckets }
        if (missingScales.isNotEmpty()) {
            reqs.add("Missing scale buckets: ${missingScales.joinToString { it.name }}")
        }

        val totalPitch = thresholds.pitchBinsDegrees.size + 1
        val missingPitchCount = totalPitch - visitedPitchBins.size
        if (missingPitchCount > 0) {
            reqs.add("Missing pitch angle bins: $missingPitchCount/$totalPitch")
        }

        val totalYaw = thresholds.yawBinsDegrees.size + 1
        val missingYawCount = totalYaw - visitedYawBins.size
        if (missingYawCount > 0) {
            reqs.add("Missing yaw angle bins: $missingYawCount/$totalYaw")
        }

        val totalDist = thresholds.distanceBinsMm.size + 1
        val missingDistCount = totalDist - visitedDistanceBins.size
        if (missingDistCount > 0) {
            reqs.add("Missing distance bins: $missingDistCount/$totalDist")
        }

        return reqs
    }
}
