package com.openprofiler.native_bridge

import javax.inject.Inject
import javax.inject.Singleton

/**
 * JNI Bridge to C++ OpenCV Calibration Solver.
 */
@Singleton
class NativeCalibrationEngine @Inject constructor() {

    companion object {
        init {
            try {
                System.loadLibrary("camera_profiler_native")
            } catch (e: UnsatisfiedLinkError) {
                // Ignore for host unit tests
            }
        }
    }

    /**
     * Executes cv::calibrateCamera using accumulated point correspondences.
     *
     * @param objectPoints Flattened object points (3 floats per point) per image.
     * @param imagePoints Flattened image points (2 floats per point) per image.
     * @param width Image width in pixels.
     * @param height Image height in pixels.
     * @return NativeCalibrationResult containing RMS, K, and D.
     */
    fun calibrate(
        objectPoints: Array<FloatArray>,
        imagePoints: Array<FloatArray>,
        width: Int,
        height: Int
    ): NativeCalibrationResult? {
        return nativeCalibrate(objectPoints, imagePoints, width, height)
    }

    private external fun nativeCalibrate(
        objectPoints: Array<FloatArray>,
        imagePoints: Array<FloatArray>,
        width: Int,
        height: Int
    ): NativeCalibrationResult?
}

/**
 * Data transfer object for calibration results from native code.
 */
data class NativeCalibrationResult(
    val rms: Double,
    val cameraMatrix: DoubleArray, // 9 elements
    val distCoeffs: DoubleArray,   // 5+ elements
    val success: Boolean
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as NativeCalibrationResult

        if (rms != other.rms) return false
        if (!cameraMatrix.contentEquals(other.cameraMatrix)) return false
        if (!distCoeffs.contentEquals(other.distCoeffs)) return false
        if (success != other.success) return false

        return true
    }

    override fun hashCode(): Int {
        var result = rms.hashCode()
        result = 31 * result + cameraMatrix.contentHashCode()
        result = 31 * result + distCoeffs.contentHashCode()
        result = 31 * result + success.hashCode()
        return result
    }
}
