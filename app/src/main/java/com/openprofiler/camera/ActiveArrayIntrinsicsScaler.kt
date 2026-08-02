package com.openprofiler.camera

/**
 * Maps camera intrinsics from the active-array pixel grid onto an ImageAnalysis
 * output size using a center-crop model (CameraX's typical fill behavior).
 *
 * Independent scaleX/scaleY against the full active array is wrong when the
 * analysis stream aspect ratio differs from the active array — that stretches
 * FOV and corrupts pose pitch/yaw/distance.
 */
internal object ActiveArrayIntrinsicsScaler {

    data class CropWindow(
        val cropWidth: Double,
        val cropHeight: Double,
        val offsetX: Double,
        val offsetY: Double,
    )

    /**
     * Center-crop window of [activeWidth]×[activeHeight] that matches
     * [imageWidth]×[imageHeight] aspect ratio.
     */
    fun centerCropWindow(
        activeWidth: Double,
        activeHeight: Double,
        imageWidth: Int,
        imageHeight: Int,
    ): CropWindow {
        require(activeWidth > 0.0 && activeHeight > 0.0)
        require(imageWidth > 0 && imageHeight > 0)
        val activeAspect = activeWidth / activeHeight
        val imageAspect = imageWidth.toDouble() / imageHeight.toDouble()
        val cropW: Double
        val cropH: Double
        if (activeAspect > imageAspect) {
            // Active wider than output → crop left/right
            cropH = activeHeight
            cropW = activeHeight * imageAspect
        } else {
            // Active taller (or equal) → crop top/bottom
            cropW = activeWidth
            cropH = activeWidth / imageAspect
        }
        val offsetX = (activeWidth - cropW) / 2.0
        val offsetY = (activeHeight - cropH) / 2.0
        return CropWindow(cropW, cropH, offsetX, offsetY)
    }

    /**
     * Scales active-array K (fx, fy, cx, cy) into analysis-image pixels.
     * Returns row-major 3×3.
     */
    fun scaleKToImage(
        fxActive: Double,
        fyActive: Double,
        cxActive: Double,
        cyActive: Double,
        activeWidth: Double,
        activeHeight: Double,
        imageWidth: Int,
        imageHeight: Int,
    ): DoubleArray {
        val crop = centerCropWindow(activeWidth, activeHeight, imageWidth, imageHeight)
        val scaleX = imageWidth / crop.cropWidth
        val scaleY = imageHeight / crop.cropHeight
        val fx = fxActive * scaleX
        val fy = fyActive * scaleY
        val cx = (cxActive - crop.offsetX) * scaleX
        val cy = (cyActive - crop.offsetY) * scaleY
        return doubleArrayOf(
            fx, 0.0, cx,
            0.0, fy, cy,
            0.0, 0.0, 1.0,
        )
    }
}
