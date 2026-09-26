package com.openprofiler.domain.model

import kotlinx.serialization.Serializable

/**
 * Origin of the camera intrinsics used for a measurement.
 *
 * Per scientific-integrity rules: every exported/gated value must have a known origin.
 * Live pose during capture may use a device-reported factory seed
 * ([FACTORY_ESTIMATE]); the exported camera-profile.json intrinsics must always
 * come from [CALIBRATED] (`cv::calibrateCamera` over accepted frames) and must
 * never be copied from the live seed.
 */
@Serializable
enum class IntrinsicsSource {
    /** No usable intrinsics; pose must not be fabricated. */
    UNAVAILABLE,

    /**
     * Device-reported seed: [android.hardware.camera2.CameraCharacteristics.LENS_INTRINSIC_CALIBRATION]
     * when exposed, otherwise focal length (mm) + sensor size (mm) + pixel array size.
     * Never write this into the exported camera profile.
     */
    FACTORY_ESTIMATE,

    /**
     * Intrinsics from `cv::calibrateCamera` over the accepted calibration corner set.
     * Sole allowed source for exported camera-profile.json K/D.
     */
    CALIBRATED,
}
