package com.openprofiler.domain.model

/**
 * Seed or calibrated camera intrinsics for pose estimation.
 *
 * @property cameraMatrix Row-major 3×3 K (length 9).
 * @property distCoeffs Distortion coefficients (typically length 5); empty means zero distortion.
 * @property source Provenance tag — must never be [IntrinsicsSource.CALIBRATED] for the live seed path.
 * @property seedMethod Human-readable derivation method for factory seeds
 *   (e.g. `LENS_INTRINSIC_CALIBRATION`, `FOCAL_LENGTH_AND_SENSOR_SIZE`).
 */
data class CameraIntrinsics(
    val cameraMatrix: DoubleArray,
    val distCoeffs: DoubleArray,
    val source: IntrinsicsSource,
    val seedMethod: String? = null,
) {
    init {
        require(cameraMatrix.size == 9) { "cameraMatrix must have length 9 (row-major 3x3)" }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as CameraIntrinsics
        if (!cameraMatrix.contentEquals(other.cameraMatrix)) return false
        if (!distCoeffs.contentEquals(other.distCoeffs)) return false
        if (source != other.source) return false
        if (seedMethod != other.seedMethod) return false
        return true
    }

    override fun hashCode(): Int {
        var result = cameraMatrix.contentHashCode()
        result = 31 * result + distCoeffs.contentHashCode()
        result = 31 * result + source.hashCode()
        result = 31 * result + (seedMethod?.hashCode() ?: 0)
        return result
    }
}
