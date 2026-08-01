package com.openprofiler.native_bridge

import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * JNI Bridge to C++ OpenCV ChArUco Detection Engine.
 * Follows RAII native lifecycle via native handles.
 */
@Singleton
class NativeDetectionEngine @Inject constructor() {

    companion object {
        init {
            try {
                System.loadLibrary("camera_profiler_native")
            } catch (e: UnsatisfiedLinkError) {
                // Ignore UnsatisfiedLinkError when running JVM host unit tests
            }
        }
    }

    private var nativeHandle: Long = 0L

    /**
     * Initializes the native ChArUco detector engine with board configuration JSON.
     */
    @Synchronized
    fun initialize(boardConfigJson: String): Boolean {
        if (nativeHandle != 0L) {
            nativeRelease(nativeHandle)
            nativeHandle = 0L
        }
        nativeHandle = nativeInit(boardConfigJson)
        return nativeHandle != 0L
    }

    /**
     * Executes calibration target board detection on frame buffers.
     */
    @Synchronized
    fun detectBoard(
        yBuffer: ByteBuffer,
        uBuffer: ByteBuffer,
        vBuffer: ByteBuffer,
        yRowStride: Int,
        uvRowStride: Int,
        uvPixelStride: Int,
        width: Int,
        height: Int,
        rotationDegrees: Int,
        cameraMatrix: DoubleArray? = null,
        distCoeffs: DoubleArray? = null
    ): NativeDetectionResult? {
        if (nativeHandle == 0L) {
            return null
        }
        return nativeDetectBoard(
            nativeHandle,
            yBuffer,
            uBuffer,
            vBuffer,
            yRowStride,
            uvRowStride,
            uvPixelStride,
            width,
            height,
            rotationDegrees,
            cameraMatrix,
            distCoeffs
        )
    }

    /**
     * Releases native memory and resources.
     */
    @Synchronized
    fun release() {
        if (nativeHandle != 0L) {
            nativeRelease(nativeHandle)
            nativeHandle = 0L
        }
    }

    /**
     * Returns true if native engine is initialized.
     */
    fun isInitialized(): Boolean = nativeHandle != 0L

    private external fun nativeInit(boardConfigJson: String): Long
    private external fun nativeDetectBoard(
        handle: Long,
        yBuffer: ByteBuffer,
        uBuffer: ByteBuffer,
        vBuffer: ByteBuffer,
        yRowStride: Int,
        uvRowStride: Int,
        uvPixelStride: Int,
        width: Int,
        height: Int,
        rotationDegrees: Int,
        cameraMatrix: DoubleArray?,
        distCoeffs: DoubleArray?
    ): NativeDetectionResult?
    private external fun nativeRelease(handle: Long)
}
