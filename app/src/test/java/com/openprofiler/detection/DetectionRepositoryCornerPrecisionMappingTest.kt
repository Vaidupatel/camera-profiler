package com.openprofiler.detection

import com.google.common.truth.Truth.assertThat
import com.openprofiler.domain.model.IntrinsicsSource
import com.openprofiler.native_bridge.NativeDetectionEngine
import com.openprofiler.native_bridge.NativeDetectionResult
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.nio.ByteBuffer

class DetectionRepositoryCornerPrecisionMappingTest {

    @Test
    fun mapsNativeCornerPrecision_withoutFabricatingConstantOne() = runTest {
        val loader: BoardConfigLoader = mockk()
        val native: NativeDetectionEngine = mockk(relaxed = true)
        every { native.isInitialized() } returns true
        every {
            native.detectBoard(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
            )
        } returns NativeDetectionResult(
            boardDetected = true,
            dictionaryName = "DICT_5X5_1000",
            markerCount = 4,
            charucoCornerCount = 4,
            markerIds = intArrayOf(0, 1, 2, 3),
            charucoIds = intArrayOf(0, 1, 2, 3),
            cornerX = floatArrayOf(10f, 20f, 20f, 10f),
            cornerY = floatArrayOf(10f, 10f, 20f, 20f),
            cornerPrecision = floatArrayOf(0.95f, 0.80f, 0.55f, 0.40f),
            detectionConfidence = 0.9f,
            processingTimeMs = 5L,
            rejectedReason = null,
            rvec = doubleArrayOf(0.0, 0.0, 0.0),
            tvec = doubleArrayOf(0.0, 0.0, 500.0),
            markerOutlineCoords = null,
            boardAxesCoords = null,
            boundingBoxCoords = floatArrayOf(0f, 0f, 100f, 0f, 100f, 100f, 0f, 100f),
            stageTimingsMs = longArrayOf(5, 1, 1, 1, 1),
        )

        val repo = DetectionRepositoryImpl(loader, native)
        val image = mockk<androidx.camera.core.ImageProxy>(relaxed = true)
        val plane = mockk<androidx.camera.core.ImageProxy.PlaneProxy>(relaxed = true)
        every { image.planes } returns arrayOf(plane, plane, plane)
        every { plane.buffer } returns ByteBuffer.allocateDirect(64)
        every { plane.rowStride } returns 8
        every { plane.pixelStride } returns 1
        every { image.width } returns 8
        every { image.height } returns 8
        every { image.imageInfo.rotationDegrees } returns 0

        val K = doubleArrayOf(
            500.0, 0.0, 4.0,
            0.0, 500.0, 4.0,
            0.0, 0.0, 1.0
        )
        val result = repo.detectBoard(
            image,
            cameraMatrix = K,
            distCoeffs = DoubleArray(5),
            intrinsicsSource = IntrinsicsSource.FACTORY_ESTIMATE,
        )

        val precisions = result.cornerCoordinates.map { it.subpixelPrecision }
        assertThat(precisions).containsExactly(0.95f, 0.80f, 0.55f, 0.40f).inOrder()
        assertThat(precisions.toSet().size).isGreaterThan(1)
        assertThat(precisions.all { it == 1.0f }).isFalse()
        assertThat(result.observedBoundingBox).isNotNull()
        assertThat(result.boardPose?.intrinsicsSource)
            .isEqualTo(IntrinsicsSource.FACTORY_ESTIMATE)
    }
}
