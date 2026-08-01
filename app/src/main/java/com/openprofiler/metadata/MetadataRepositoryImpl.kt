package com.openprofiler.metadata

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.os.Build
import com.openprofiler.common.util.Logger
import com.openprofiler.domain.repository.MetadataRepository
import com.openprofiler.metadata.model.AppMetadata
import com.openprofiler.metadata.model.CameraCharacteristicsMetadata
import com.openprofiler.metadata.model.CameraInfoMetadata
import com.openprofiler.metadata.model.DeviceMetadata
import com.openprofiler.metadata.model.FullMetadata
import com.openprofiler.metadata.model.OsMetadata
import com.openprofiler.metadata.model.SensorMetadata
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "MetadataRepository"

/**
 * Implementation of [MetadataRepository] using Camera2 API and Android [Build].
 * Queries CameraCharacteristics safely, mapping unavailable properties to null per JSON schema.
 */
@Singleton
class MetadataRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : MetadataRepository {

    private val cameraManager: CameraManager? by lazy {
        try {
            context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to obtain CameraManager service", e)
            null
        }
    }

    override fun getAvailableCameraIds(): List<String> {
        return try {
            cameraManager?.cameraIdList?.toList() ?: emptyList()
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to list camera IDs", e)
            emptyList()
        }
    }

    override fun collectMetadata(cameraId: String?): FullMetadata {
        val targetCameraId = cameraId ?: getAvailableCameraIds().firstOrNull() ?: "0"

        val deviceMeta = collectDeviceMetadata()
        val osMeta = collectOsMetadata()
        val appMeta = AppMetadata()

        val characteristics = try {
            cameraManager?.getCameraCharacteristics(targetCameraId)
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to get CameraCharacteristics for camera $targetCameraId", e)
            null
        }

        val cameraInfoMeta = collectCameraInfoMetadata(targetCameraId, characteristics)
        val sensorMeta = collectSensorMetadata(characteristics)
        val cameraCharMeta = collectCharacteristicsMetadata(characteristics)

        return FullMetadata(
            device = deviceMeta,
            operatingSystem = osMeta,
            application = appMeta,
            camera = cameraInfoMeta,
            sensor = sensorMeta,
            cameraCharacteristics = cameraCharMeta,
        )
    }

    private fun collectDeviceMetadata(): DeviceMetadata {
        return try {
            DeviceMetadata(
                manufacturer = Build.MANUFACTURER,
                brand = Build.BRAND,
                model = Build.MODEL,
                device = Build.DEVICE,
                product = Build.PRODUCT,
                board = Build.BOARD,
                hardware = Build.HARDWARE,
                fingerprint = Build.FINGERPRINT,
                supportedAbis = Build.SUPPORTED_ABIS?.toList() ?: emptyList(),
                sdk = Build.VERSION.SDK_INT,
                securityPatch = try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Build.VERSION.SECURITY_PATCH else null
                } catch (e: Throwable) { null },
                buildId = Build.ID,
            )
        } catch (e: Throwable) {
            Logger.w(TAG, "Build properties unavailable in current runtime")
            DeviceMetadata()
        }
    }

    private fun collectOsMetadata(): OsMetadata {
        return try {
            OsMetadata(
                name = "Android",
                version = Build.VERSION.RELEASE ?: "",
                apiLevel = Build.VERSION.SDK_INT,
                codename = Build.VERSION.CODENAME,
                release = Build.VERSION.RELEASE,
                incremental = Build.VERSION.INCREMENTAL,
            )
        } catch (e: Throwable) {
            OsMetadata()
        }
    }

    private fun collectCameraInfoMetadata(
        cameraId: String,
        characteristics: CameraCharacteristics?,
    ): CameraInfoMetadata {
        if (characteristics == null) return CameraInfoMetadata(cameraId = cameraId)

        val lensFacingInt = characteristics.get(CameraCharacteristics.LENS_FACING)
        val lensFacingStr = when (lensFacingInt) {
            CameraCharacteristics.LENS_FACING_FRONT -> "FRONT"
            CameraCharacteristics.LENS_FACING_BACK -> "BACK"
            CameraCharacteristics.LENS_FACING_EXTERNAL -> "EXTERNAL"
            else -> "UNKNOWN"
        }

        val sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
        val flashAvailable = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false

        val hardwareLevelInt = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
        val hardwareLevelStr = when (hardwareLevelInt) {
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "LEGACY"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "LIMITED"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "FULL"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "LEVEL_3"
            else -> "UNKNOWN"
        }

        val timestampSourceInt = characteristics.get(CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE)
        val timestampSourceStr = when (timestampSourceInt) {
            CameraMetadata.SENSOR_INFO_TIMESTAMP_SOURCE_REALTIME -> "REALTIME"
            CameraMetadata.SENSOR_INFO_TIMESTAMP_SOURCE_UNKNOWN -> "UNKNOWN"
            else -> "UNKNOWN"
        }

        val capabilitiesIntArray = characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
        val capabilitiesList = capabilitiesIntArray?.map { cap ->
            when (cap) {
                CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE -> "BACKWARD_COMPATIBLE"
                CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR -> "MANUAL_SENSOR"
                CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING -> "MANUAL_POST_PROCESSING"
                CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_RAW -> "RAW"
                CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA -> "LOGICAL_MULTI_CAMERA"
                else -> "CAPABILITY_$cap"
            }
        } ?: emptyList()

        val physicalCameraIds = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            characteristics.physicalCameraIds.toList()
        } else {
            emptyList()
        }

        val isLogical = capabilitiesList.contains("LOGICAL_MULTI_CAMERA") || physicalCameraIds.isNotEmpty()

        return CameraInfoMetadata(
            cameraId = cameraId,
            logicalCamera = isLogical,
            physicalCameraIds = physicalCameraIds,
            lensFacing = lensFacingStr,
            sensorOrientation = sensorOrientation,
            flashAvailable = flashAvailable,
            hardwareLevel = hardwareLevelStr,
            timestampSource = timestampSourceStr,
            requestCapabilities = capabilitiesList,
        )
    }

    private fun collectSensorMetadata(characteristics: CameraCharacteristics?): SensorMetadata {
        if (characteristics == null) return SensorMetadata()

        val sensorSize = characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
        val pixelArraySize = characteristics.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
        val activeArrayRect = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)

        val widthMm = sensorSize?.width?.toDouble()
        val heightMm = sensorSize?.height?.toDouble()

        val pixelWidth = pixelArraySize?.width
        val pixelHeight = pixelArraySize?.height

        val activeWidth = activeArrayRect?.width()
        val activeHeight = activeArrayRect?.height()

        val pixelPitch = if (widthMm != null && pixelWidth != null && pixelWidth > 0) {
            (widthMm / pixelWidth) * 1000.0 // Convert mm to µm
        } else {
            null
        }

        val cfaInt = characteristics.get(CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT)
        val cfaStr = when (cfaInt) {
            CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_RGGB -> "RGGB"
            CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_GRBG -> "GRBG"
            CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_GBRG -> "GBRG"
            CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_BGGR -> "BGGR"
            CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_RGB -> "RGB"
            CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_MONO -> "MONO"
            else -> null
        }

        return SensorMetadata(
            sensorWidthMm = widthMm,
            sensorHeightMm = heightMm,
            pixelArrayWidth = pixelWidth,
            pixelArrayHeight = pixelHeight,
            activeArrayWidth = activeWidth,
            activeArrayHeight = activeHeight,
            pixelPitchUm = pixelPitch,
            colorFilterArrangement = cfaStr,
        )
    }

    private fun collectCharacteristicsMetadata(characteristics: CameraCharacteristics?): CameraCharacteristicsMetadata {
        if (characteristics == null) return CameraCharacteristicsMetadata()

        val focalLengths = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.toList() ?: emptyList()

        val intrinsicsArray = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            characteristics.get(CameraCharacteristics.LENS_INTRINSIC_CALIBRATION)?.toList()
        } else {
            null
        }

        val distortionArray = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            characteristics.get(CameraCharacteristics.LENS_DISTORTION)?.toList()
        } else {
            null
        }

        val minFocusDistance = characteristics.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE)
        val hyperfocalDistance = characteristics.get(CameraCharacteristics.LENS_INFO_HYPERFOCAL_DISTANCE)

        val oisModesIntArray = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION)
        val oisModesStrList = oisModesIntArray?.map { mode ->
            when (mode) {
                CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_OFF -> "OFF"
                CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON -> "ON"
                else -> "MODE_$mode"
            }
        } ?: emptyList()

        return CameraCharacteristicsMetadata(
            availableFocalLengths = focalLengths,
            intrinsicCalibration = intrinsicsArray,
            lensDistortion = distortionArray,
            minimumFocusDistance = minFocusDistance,
            hyperfocalDistance = hyperfocalDistance,
            opticalStabilizationModes = oisModesStrList,
        )
    }
}
