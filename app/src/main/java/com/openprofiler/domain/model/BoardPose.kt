package com.openprofiler.domain.model

/**
 * 3D pose of the calibration target board relative to the camera coordinate frame.
 *
 * @property rvec Rodrigues rotation vector [rx, ry, rz] in radians.
 * @property tvec Translation vector [tx, ty, tz] in millimeters.
 * @property intrinsicsSource Provenance of the K/D used to compute this pose.
 *   Live session pose uses [IntrinsicsSource.FACTORY_ESTIMATE]; exported profile
 *   intrinsics must use [IntrinsicsSource.CALIBRATED] from `cv::calibrateCamera`.
 */
data class BoardPose(
    val rvec: DoubleArray,
    val tvec: DoubleArray,
    val intrinsicsSource: IntrinsicsSource = IntrinsicsSource.UNAVAILABLE,
) {
    init {
        require(rvec.size == 3) { "rvec must have length 3" }
        require(tvec.size == 3) { "tvec must have length 3" }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BoardPose

        if (!rvec.contentEquals(other.rvec)) return false
        if (!tvec.contentEquals(other.tvec)) return false
        if (intrinsicsSource != other.intrinsicsSource) return false

        return true
    }

    override fun hashCode(): Int {
        var result = rvec.contentHashCode()
        result = 31 * result + tvec.contentHashCode()
        result = 31 * result + intrinsicsSource.hashCode()
        return result
    }
}
