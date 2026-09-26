package com.openprofiler.native_bridge

import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * JNI bridge to the native OpenCV image quality evaluator.
 * RAII lifecycle via native handles. Thread-safe via synchronized methods.
 */
@Singleton
class NativeQualityEngine @Inject constructor() {

    companion object {
        init {
            try {
                System.loadLibrary("camera_profiler_native")
            } catch (_: UnsatisfiedLinkError) {
                // Host JVM unit tests may not load the native library.
            }
        }
    }

    private var nativeHandle: Long = 0L

    @Synchronized
    fun initialize(): Boolean {
        if (nativeHandle != 0L) {
            nativeRelease(nativeHandle)
            nativeHandle = 0L
        }
        nativeHandle = nativeInit()
        return nativeHandle != 0L
    }

    @Synchronized
    fun evaluate(
        yBuffer: ByteBuffer,
        width: Int,
        height: Int,
        yRowStride: Int,
        rotationDegrees: Int,
        darkPixelThreshold: Double,
        brightPixelThreshold: Double,
        detection: com.openprofiler.domain.model.DetectionResult? = null,
        boardConfig: com.openprofiler.domain.model.BoardConfig? = null
    ): NativeImageQualityMeasurements? {
        if (nativeHandle == 0L) {
            if (!initialize()) return null
        }

        val corners = detection?.cornerCoordinates
        val cornerArray = if (corners != null && corners.isNotEmpty()) {
            val arr = FloatArray(corners.size * 2)
            corners.forEachIndexed { i, c ->
                arr[i * 2] = c.x
                arr[i * 2 + 1] = c.y
            }
            arr
        } else null

        val idArray = corners?.map { it.id }?.toIntArray()

        val sx = boardConfig?.squaresX ?: -1
        val sy = boardConfig?.squaresY ?: -1

        // Calculate ROI for noise exclusion if needed
        val bbox = detection?.observedBoundingBox
        var roiL = -1
        var roiT = -1
        var roiR = -1
        var roiB = -1
        if (bbox != null && bbox.size >= 4) {
            roiL = bbox.minOf { it.x }.toInt()
            roiT = bbox.minOf { it.y }.toInt()
            roiR = bbox.maxOf { it.x }.toInt()
            roiB = bbox.maxOf { it.y }.toInt()
        }

        return nativeEvaluate(
            nativeHandle,
            yBuffer,
            width,
            height,
            yRowStride,
            rotationDegrees,
            darkPixelThreshold,
            brightPixelThreshold,
            roiL,
            roiT,
            roiR,
            roiB,
            cornerArray,
            idArray,
            sx,
            sy
        )
    }

    @Synchronized
    fun resetMotionState() {
        if (nativeHandle != 0L) {
            nativeResetMotion(nativeHandle)
        }
    }

    @Synchronized
    fun release() {
        if (nativeHandle != 0L) {
            nativeRelease(nativeHandle)
            nativeHandle = 0L
        }
    }

    fun isInitialized(): Boolean = nativeHandle != 0L

    private external fun nativeInit(): Long

    private external fun nativeEvaluate(
        handle: Long,
        yBuffer: ByteBuffer,
        width: Int,
        height: Int,
        yRowStride: Int,
        rotationDegrees: Int,
        darkPixelThreshold: Double,
        brightPixelThreshold: Double,
        roiLeft: Int,
        roiTop: Int,
        roiRight: Int,
        roiBottom: Int,
        charucoCorners: FloatArray?,
        charucoIds: IntArray?,
        squaresX: Int,
        squaresY: Int
    ): NativeImageQualityMeasurements?

    private external fun nativeResetMotion(handle: Long)

    private external fun nativeRelease(handle: Long)
}
