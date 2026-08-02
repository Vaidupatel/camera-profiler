package com.openprofiler.camera

import com.google.common.truth.Truth.assertThat
import com.openprofiler.domain.model.IntrinsicsSource
import com.openprofiler.domain.repository.MetadataRepository
import com.openprofiler.metadata.model.CameraCharacteristicsMetadata
import com.openprofiler.metadata.model.FullMetadata
import com.openprofiler.metadata.model.SensorMetadata
import io.mockk.every
import io.mockk.mockk
import org.junit.Test

class CameraIntrinsicsProviderImplTest {

    private val metadataRepository: MetadataRepository = mockk()

    @Test
    fun prefersLensIntrinsicCalibration_centerCropScaledToImageSize() {
        every { metadataRepository.collectMetadata(any()) } returns FullMetadata(
            sensor = SensorMetadata(
                sensorWidthMm = 5.0,
                sensorHeightMm = 4.0,
                pixelArrayWidth = 4000,
                pixelArrayHeight = 3000,
                activeArrayWidth = 4000,
                activeArrayHeight = 3000,
            ),
            cameraCharacteristics = CameraCharacteristicsMetadata(
                availableFocalLengths = listOf(4.2f),
                intrinsicCalibration = listOf(3000f, 3000f, 2000f, 1500f, 0f),
                lensDistortion = listOf(0.1f, -0.05f, 0f, 0f, 0f),
            )
        )
        val provider = CameraIntrinsicsProviderImpl(metadataRepository)
        // Same 4:3 aspect → crop == full active array, scale=0.25
        val seed = provider.getSeedIntrinsics(imageWidthPx = 1000, imageHeightPx = 750)!!

        assertThat(seed.source).isEqualTo(IntrinsicsSource.FACTORY_ESTIMATE)
        assertThat(seed.seedMethod)
            .isEqualTo(CameraIntrinsicsProviderImpl.SEED_LENS_INTRINSIC_CALIBRATION)
        assertThat(seed.cameraMatrix[0]).isWithin(1e-6).of(750.0)
        assertThat(seed.cameraMatrix[2]).isWithin(1e-6).of(500.0)
        assertThat(seed.cameraMatrix[4]).isWithin(1e-6).of(750.0)
        assertThat(seed.cameraMatrix[5]).isWithin(1e-6).of(375.0)
    }

    @Test
    fun focalLengthSeed_usesActiveArrayCenterCrop_whenAspectDiffers() {
        every { metadataRepository.collectMetadata(any()) } returns FullMetadata(
            sensor = SensorMetadata(
                sensorWidthMm = 6.0,
                sensorHeightMm = 4.5,
                pixelArrayWidth = 4000,
                pixelArrayHeight = 3000,
                activeArrayWidth = 4000,
                activeArrayHeight = 3000,
            ),
            cameraCharacteristics = CameraCharacteristicsMetadata(
                availableFocalLengths = listOf(4.5f),
                intrinsicCalibration = null,
            )
        )
        val provider = CameraIntrinsicsProviderImpl(metadataRepository)
        // 16:9 analysis from 4:3 active → horizontal crop of active stays full width,
        // vertical crop shrinks; fx/fy share the same isotropic scale.
        val seed = provider.getSeedIntrinsics(imageWidthPx = 1920, imageHeightPx = 1080)!!

        assertThat(seed.source).isEqualTo(IntrinsicsSource.FACTORY_ESTIMATE)
        assertThat(seed.seedMethod)
            .isEqualTo(CameraIntrinsicsProviderImpl.SEED_FOCAL_LENGTH_AND_SENSOR_SIZE)

        val fxActive = 4.5 / 6.0 * 4000.0 // 3000
        val crop = ActiveArrayIntrinsicsScaler.centerCropWindow(4000.0, 3000.0, 1920, 1080)
        val scale = 1920.0 / crop.cropWidth
        assertThat(crop.cropWidth).isWithin(1e-6).of(4000.0)
        assertThat(crop.cropHeight).isWithin(1e-6).of(2250.0)
        assertThat(seed.cameraMatrix[0]).isWithin(1e-6).of(fxActive * scale)
        assertThat(seed.cameraMatrix[4]).isWithin(1e-6).of(fxActive * scale) // fyActive==fxActive here
        assertThat(seed.cameraMatrix[2]).isWithin(1e-6).of(960.0)
        assertThat(seed.cameraMatrix[5]).isWithin(1e-6).of(540.0)

        // Naive imageWidth scaling would keep fx=1440 but stretch fy to 1080 — we must not.
        assertThat(seed.cameraMatrix[4]).isNotWithin(1.0).of(1080.0)
    }

    @Test
    fun returnsNull_whenDeviceReportsNothing_neverFabricatesHeuristic() {
        every { metadataRepository.collectMetadata(any()) } returns FullMetadata()
        val provider = CameraIntrinsicsProviderImpl(metadataRepository)
        assertThat(provider.getSeedIntrinsics(imageWidthPx = 1920, imageHeightPx = 1080)).isNull()
    }
}
