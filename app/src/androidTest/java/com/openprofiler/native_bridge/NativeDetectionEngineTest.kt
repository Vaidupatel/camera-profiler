package com.openprofiler.native_bridge

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer

@RunWith(AndroidJUnit4::class)
class NativeDetectionEngineTest {

    private lateinit var nativeEngine: NativeDetectionEngine

    private val defaultConfigJson = """
        {
          "dictionaryName": "DICT_5X5_1000",
          "squaresX": 9,
          "squaresY": 6,
          "squareLengthMm": 30.0,
          "markerLengthMm": 22.0,
          "minMarkers": 4,
          "minCorners": 4,
          "minConfidence": 0.5
        }
    """.trimIndent()

    @Before
    fun setUp() {
        nativeEngine = NativeDetectionEngine()
    }

    @After
    fun tearDown() {
        nativeEngine.release()
    }

    @Test
    fun testNativeEngineInitialization() {
        val success = nativeEngine.initialize(defaultConfigJson)
        assertThat(success).isTrue()
        assertThat(nativeEngine.isInitialized()).isTrue()
    }

    @Test
    fun testNativeEngineRelease() {
        nativeEngine.initialize(defaultConfigJson)
        nativeEngine.release()
        assertThat(nativeEngine.isInitialized()).isFalse()
    }

    @Test
    fun testSyntheticBoardDetection() {
        nativeEngine.initialize(defaultConfigJson)

        val width = 640
        val height = 480
        val yBuffer = ByteBuffer.allocateDirect(width * height)
        val uBuffer = ByteBuffer.allocateDirect(width * height / 4)
        val vBuffer = ByteBuffer.allocateDirect(width * height / 4)

        // Fill with uniform synthetic background gray
        for (i in 0 until width * height) {
            yBuffer.put(128.toByte())
        }
        yBuffer.rewind()
        uBuffer.rewind()
        vBuffer.rewind()

        val result = nativeEngine.detectBoard(
            yBuffer = yBuffer,
            uBuffer = uBuffer,
            vBuffer = vBuffer,
            yRowStride = width,
            uvRowStride = width / 2,
            uvPixelStride = 1,
            width = width,
            height = height,
            rotationDegrees = 0
        )

        assertThat(result).isNotNull()
        assertThat(result?.dictionaryName).isEqualTo("DICT_5X5_1000")
    }

    @Test
    fun testPartialOcclusionHandling() {
        nativeEngine.initialize(defaultConfigJson)

        val width = 640
        val height = 480
        val yBuffer = ByteBuffer.allocateDirect(width * height)

        // Simulate partial occlusion by zeroing out bottom-right quad
        val bytes = ByteArray(width * height) { 200.toByte() }
        for (y in (height / 2) until height) {
            for (x in (width / 2) until width) {
                bytes[y * width + x] = 0.toByte()
            }
        }
        yBuffer.put(bytes)
        yBuffer.rewind()

        val result = nativeEngine.detectBoard(
            yBuffer = yBuffer,
            uBuffer = yBuffer,
            vBuffer = yBuffer,
            yRowStride = width,
            uvRowStride = width,
            uvPixelStride = 1,
            width = width,
            height = height,
            rotationDegrees = 0
        )

        assertThat(result).isNotNull()
    }

    @Test
    fun testBlurRobustness() {
        nativeEngine.initialize(defaultConfigJson)

        val width = 640
        val height = 480
        val yBuffer = ByteBuffer.allocateDirect(width * height)

        // Low contrast smooth gradient simulating heavy blur
        val bytes = ByteArray(width * height)
        for (i in bytes.indices) {
            bytes[i] = (120 + (i % 20)).toByte()
        }
        yBuffer.put(bytes)
        yBuffer.rewind()

        val result = nativeEngine.detectBoard(
            yBuffer = yBuffer,
            uBuffer = yBuffer,
            vBuffer = yBuffer,
            yRowStride = width,
            uvRowStride = width,
            uvPixelStride = 1,
            width = width,
            height = height,
            rotationDegrees = 0
        )

        assertThat(result).isNotNull()
    }

    @Test
    fun testRotationHandling() {
        nativeEngine.initialize(defaultConfigJson)

        val width = 640
        val height = 480
        val yBuffer = ByteBuffer.allocateDirect(width * height)

        val result = nativeEngine.detectBoard(
            yBuffer = yBuffer,
            uBuffer = yBuffer,
            vBuffer = yBuffer,
            yRowStride = width,
            uvRowStride = width,
            uvPixelStride = 1,
            width = width,
            height = height,
            rotationDegrees = 90
        )

        assertThat(result).isNotNull()
    }

    @Test
    fun testLightingVariationHandling() {
        nativeEngine.initialize(defaultConfigJson)

        val width = 640
        val height = 480
        val yBuffer = ByteBuffer.allocateDirect(width * height)

        // Low light / dark image simulation
        val bytes = ByteArray(width * height) { 20.toByte() }
        yBuffer.put(bytes)
        yBuffer.rewind()

        val result = nativeEngine.detectBoard(
            yBuffer = yBuffer,
            uBuffer = yBuffer,
            vBuffer = yBuffer,
            yRowStride = width,
            uvRowStride = width,
            uvPixelStride = 1,
            width = width,
            height = height,
            rotationDegrees = 0
        )

        assertThat(result).isNotNull()
    }
}
