package com.openprofiler.camera

import com.openprofiler.domain.model.CameraIntrinsics
import com.openprofiler.domain.model.IntrinsicsSource
import com.openprofiler.domain.repository.MetadataRepository
import com.openprofiler.metadata.model.CameraCharacteristicsMetadata
import com.openprofiler.metadata.model.SensorMetadata
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds live-session seed intrinsics from Camera2 characteristics already collected
 * by [MetadataRepository]. Does not invent focal lengths when the device omits them.
 *
 * Scaling onto ImageAnalysis resolution uses a center-crop of
 * [SensorMetadata.activeArrayWidth]/[SensorMetadata.activeArrayHeight] so the seed
 * FOV matches the analysis stream when aspects differ (e.g. 4:3 active → 640×480).
 */
@Singleton
class CameraIntrinsicsProviderImpl @Inject constructor(
    private val metadataRepository: MetadataRepository,
) : CameraIntrinsicsProvider {

    override fun getSeedIntrinsics(
        cameraId: String?,
        imageWidthPx: Int,
        imageHeightPx: Int,
    ): CameraIntrinsics? {
        if (imageWidthPx <= 0 || imageHeightPx <= 0) return null

        val meta = metadataRepository.collectMetadata(cameraId)
        val chars = meta.cameraCharacteristics
        val sensor = meta.sensor

        fromLensIntrinsicCalibration(chars, sensor, imageWidthPx, imageHeightPx)?.let {
            return it
        }
        return fromFocalLengthAndSensor(chars, sensor, imageWidthPx, imageHeightPx)
    }

    /**
     * Android [CameraCharacteristics.LENS_INTRINSIC_CALIBRATION] = {fx, fy, cx, cy, s}
     * in pixels of the active array (when present).
     */
    private fun fromLensIntrinsicCalibration(
        chars: CameraCharacteristicsMetadata,
        sensor: SensorMetadata,
        imageWidthPx: Int,
        imageHeightPx: Int,
    ): CameraIntrinsics? {
        val calib = chars.intrinsicCalibration ?: return null
        if (calib.size < 4) return null
        val fx = calib[0].toDouble()
        val fy = calib[1].toDouble()
        val cx = calib[2].toDouble()
        val cy = calib[3].toDouble()
        if (fx <= 0.0 || fy <= 0.0) return null

        val refW = (sensor.activeArrayWidth ?: sensor.pixelArrayWidth)?.toDouble() ?: return null
        val refH = (sensor.activeArrayHeight ?: sensor.pixelArrayHeight)?.toDouble() ?: return null
        if (refW <= 0.0 || refH <= 0.0) return null

        val K = ActiveArrayIntrinsicsScaler.scaleKToImage(
            fxActive = fx,
            fyActive = fy,
            cxActive = cx,
            cyActive = cy,
            activeWidth = refW,
            activeHeight = refH,
            imageWidth = imageWidthPx,
            imageHeight = imageHeightPx,
        )
        val crop = ActiveArrayIntrinsicsScaler.centerCropWindow(refW, refH, imageWidthPx, imageHeightPx)
        Timber.d(
            "Seed K from LENS_INTRINSIC_CALIBRATION active=%.0fx%.0f crop=%.1fx%.1f → %dx%d " +
                "fx=%.1f fy=%.1f",
            refW,
            refH,
            crop.cropWidth,
            crop.cropHeight,
            imageWidthPx,
            imageHeightPx,
            K[0],
            K[4],
        )
        return CameraIntrinsics(
            cameraMatrix = K,
            distCoeffs = lensDistortionOrZeros(chars),
            source = IntrinsicsSource.FACTORY_ESTIMATE,
            seedMethod = SEED_LENS_INTRINSIC_CALIBRATION,
        )
    }

    private fun fromFocalLengthAndSensor(
        chars: CameraCharacteristicsMetadata,
        sensor: SensorMetadata,
        imageWidthPx: Int,
        imageHeightPx: Int,
    ): CameraIntrinsics? {
        val focalMm = chars.availableFocalLengths.firstOrNull()?.toDouble() ?: return null
        if (focalMm <= 0.0) return null
        val sensorWmm = sensor.sensorWidthMm ?: return null
        val sensorHmm = sensor.sensorHeightMm ?: return null
        if (sensorWmm <= 0.0 || sensorHmm <= 0.0) return null

        val pixelW = (sensor.pixelArrayWidth ?: sensor.activeArrayWidth)?.toDouble() ?: return null
        val pixelH = (sensor.pixelArrayHeight ?: sensor.activeArrayHeight)?.toDouble() ?: return null
        if (pixelW <= 0.0 || pixelH <= 0.0) return null

        val activeW = (sensor.activeArrayWidth ?: sensor.pixelArrayWidth)?.toDouble() ?: return null
        val activeH = (sensor.activeArrayHeight ?: sensor.pixelArrayHeight)?.toDouble() ?: return null
        if (activeW <= 0.0 || activeH <= 0.0) return null

        // Physical pixel pitch = sensorWmm / pixelW (mm/px).
        // Focal length in pixel grid units = focal_mm / (sensor_width_mm / pixel_array_width_px)
        val fxActive = focalMm / sensorWmm * pixelW
        val fyActive = focalMm / sensorHmm * pixelH
        val cxActive = activeW / 2.0
        val cyActive = activeH / 2.0

        val K = ActiveArrayIntrinsicsScaler.scaleKToImage(
            fxActive = fxActive,
            fyActive = fyActive,
            cxActive = cxActive,
            cyActive = cyActive,
            activeWidth = activeW,
            activeHeight = activeH,
            imageWidth = imageWidthPx,
            imageHeight = imageHeightPx,
        )
        val crop = ActiveArrayIntrinsicsScaler.centerCropWindow(activeW, activeH, imageWidthPx, imageHeightPx)
        // Implied horizontal FOV of the analysis stream (degrees) for log diagnostics.
        val hFovDeg = Math.toDegrees(2.0 * kotlin.math.atan((imageWidthPx / 2.0) / K[0]))
        Timber.d(
            "Seed K from focal=%.3fmm sensor=%.3fx%.3fmm active=%.0fx%.0f " +
                "crop=%.1fx%.1f → %dx%d fx=%.1f fy=%.1f hFov≈%.1f°",
            focalMm,
            sensorWmm,
            sensorHmm,
            activeW,
            activeH,
            crop.cropWidth,
            crop.cropHeight,
            imageWidthPx,
            imageHeightPx,
            K[0],
            K[4],
            hFovDeg,
        )
        return CameraIntrinsics(
            cameraMatrix = K,
            distCoeffs = lensDistortionOrZeros(chars),
            source = IntrinsicsSource.FACTORY_ESTIMATE,
            seedMethod = SEED_FOCAL_LENGTH_AND_SENSOR_SIZE,
        )
    }

    private fun lensDistortionOrZeros(chars: CameraCharacteristicsMetadata): DoubleArray {
        val d = chars.lensDistortion
        if (d.isNullOrEmpty()) return DoubleArray(5) { 0.0 }
        return DoubleArray(d.size) { i -> d[i].toDouble() }
    }

    companion object {
        const val SEED_LENS_INTRINSIC_CALIBRATION = "LENS_INTRINSIC_CALIBRATION"
        const val SEED_FOCAL_LENGTH_AND_SENSOR_SIZE = "FOCAL_LENGTH_AND_SENSOR_SIZE"
    }
}
