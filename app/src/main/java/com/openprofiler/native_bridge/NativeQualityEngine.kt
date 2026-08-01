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
        brightPixelThreshold: Double
    ): NativeImageQualityMeasurements? {
        if (nativeHandle == 0L) {
            if (!initialize()) return null
        }
        return nativeEvaluate(
            nativeHandle,
            yBuffer,
            width,
            height,
            yRowStride,
            rotationDegrees,
            darkPixelThreshold,
            brightPixelThreshold
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
        brightPixelThreshold: Double
    ): NativeImageQualityMeasurements?

    private external fun nativeResetMotion(handle: Long)

    private external fun nativeRelease(handle: Long)
}
