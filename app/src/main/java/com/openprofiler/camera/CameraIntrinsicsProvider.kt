package com.openprofiler.camera

import com.openprofiler.domain.model.CameraIntrinsics

/**
 * Provides device-reported seed camera intrinsics for live pose estimation.
 *
 * Never returns [com.openprofiler.domain.model.IntrinsicsSource.CALIBRATED] —
 * that provenance is reserved for `cv::calibrateCamera` export only.
 */
interface CameraIntrinsicsProvider {

    /**
     * Resolves seed K/D for the given analysis frame size.
     *
     * Prefer [android.hardware.camera2.CameraCharacteristics.LENS_INTRINSIC_CALIBRATION]
     * when the device exposes it; otherwise derive fx/fy from reported focal length (mm),
     * sensor physical size (mm), and pixel/active array size, scaled to [imageWidthPx] ×
     * [imageHeightPx].
     *
     * @return Seed intrinsics, or null when the device does not report enough data
     *   (caller must not fabricate a heuristic K).
     */
    fun getSeedIntrinsics(
        cameraId: String? = null,
        imageWidthPx: Int,
        imageHeightPx: Int,
    ): CameraIntrinsics?
}
