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
     * Scales active-array K (fx, fy, cx, cy) into analysis-image pixels, accounting
     * for [rotationDegrees] applied to the buffer before OpenCV processing.
     * Returns row-major 3x3.
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
        rotationDegrees: Int,
    ): DoubleArray {
        // 1. Determine sensor-relative dimensions of the analysis buffer.
        // If rotation is 90/270, the image's X maps to the sensor's Y.
        val isSwapped = rotationDegrees == 90 || rotationDegrees == 270
        val sensorOrientedWidth = if (isSwapped) imageHeight else imageWidth
        val sensorOrientedHeight = if (isSwapped) imageWidth else imageHeight

        // 2. Calculate crop and scale in sensor-basis.
        val crop = centerCropWindow(activeWidth, activeHeight, sensorOrientedWidth, sensorOrientedHeight)
        val sX = sensorOrientedWidth / crop.cropWidth
        val sY = sensorOrientedHeight / crop.cropHeight

        val cxS = (cxActive - crop.offsetX) * sX
        val cyS = (cyActive - crop.offsetY) * sY
        val fxS = fxActive * sX
        val fyS = fyActive * sY

        // 3. Transform sensor-basis K to image-basis K based on rotationDegrees.
        // CameraX rotationDegrees is CLOCKWISE rotation to make the image upright.
        // We must apply the same rotation to the K matrix parameters.
        return when (rotationDegrees) {
            90 -> doubleArrayOf(
                fyS, 0.0, imageHeight - cyS,
                0.0, fxS, cxS,
                0.0, 0.0, 1.0
            )
            180 -> doubleArrayOf(
                fxS, 0.0, imageWidth - cxS,
                0.0, fyS, imageHeight - cyS,
                0.0, 0.0, 1.0
            )
            270 -> doubleArrayOf(
                fyS, 0.0, cyS,
                0.0, fxS, imageWidth - cxS,
                0.0, 0.0, 1.0
            )
            else -> doubleArrayOf(
                fxS, 0.0, cxS,
                0.0, fyS, cyS,
                0.0, 0.0, 1.0
            )
        }
    }
}
