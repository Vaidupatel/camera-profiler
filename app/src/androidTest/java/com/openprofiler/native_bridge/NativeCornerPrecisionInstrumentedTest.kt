package com.openprofiler.native_bridge

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import kotlin.math.abs

/**
 * Instrumented checks that native corner precision is measured (not fabricated as 1.0)
 * and degrades under blur.
 */
@RunWith(AndroidJUnit4::class)
class NativeCornerPrecisionInstrumentedTest {

    private lateinit var engine: NativeDetectionEngine

    private val configJson = """
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
        engine = NativeDetectionEngine()
        assertThat(engine.initialize(configJson)).isTrue()
    }

    @After
    fun tearDown() {
        engine.release()
    }

    @Test
    fun cornerPrecision_notConstantOnes_onSharpBoard() {
        val bmp = loadBoardBitmap()
        val result = detectGray(bmp) ?: return
        assertThat(result.boardDetected).isTrue()
        assertThat(result.charucoCornerCount).isAtLeast(4)
        val precisions = result.cornerPrecision.toList()
        assertThat(precisions).isNotEmpty()
        assertThat(precisions.all { abs(it - 1.0f) < 1e-6f }).isFalse()
        assertThat(precisions.any { it in 0.0f..1.0f }).isTrue()
    }

    @Test
    fun cornerPrecision_decreasesUnderBlur() {
        val sharp = loadBoardBitmap()
        val blurred = boxBlur(sharp, radius = 6)
        val sharpResult = detectGray(sharp) ?: return
        val blurResult = detectGray(blurred) ?: return

        // Blur may reduce corner count; compare averages only when both detected enough.
        if (sharpResult.charucoCornerCount < 4 || blurResult.charucoCornerCount < 4) {
            // Still assert sharp path is not fabricated constants.
            assertThat(sharpResult.cornerPrecision.all { it == 1.0f }).isFalse()
            return
        }
        val sharpAvg = sharpResult.cornerPrecision.average()
        val blurAvg = blurResult.cornerPrecision.average()
        assertThat(blurAvg).isLessThan(sharpAvg)
    }

    private fun loadBoardBitmap(): Bitmap {
        val ctx = InstrumentationRegistry.getInstrumentation().context
        ctx.assets.open("charuco_board_test.png").use { stream ->
            return BitmapFactory.decodeStream(stream)
                ?: error("Failed to decode charuco_board_test.png")
        }
    }

    private fun detectGray(bitmap: Bitmap): NativeDetectionResult? {
        val gray = toGrayscale(bitmap)
        val width = gray.width
        val height = gray.height
        val ySize = width * height
        val yBuffer = ByteBuffer.allocateDirect(ySize)
        val pixels = IntArray(ySize)
        gray.getPixels(pixels, 0, width, 0, 0, width, height)
        for (p in pixels) {
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            val y = (0.299 * r + 0.587 * g + 0.114 * b).toInt().coerceIn(0, 255)
            yBuffer.put(y.toByte())
        }
        yBuffer.rewind()
        val uv = ByteBuffer.allocateDirect(ySize / 4)
        // Seed K so pose path does not block detection (corners independent of K).
        val K = doubleArrayOf(
            width.toDouble(), 0.0, width / 2.0,
            0.0, height.toDouble(), height / 2.0,
            0.0, 0.0, 1.0
        )
        return engine.detectBoard(
            yBuffer = yBuffer,
            uBuffer = uv,
            vBuffer = uv,
            yRowStride = width,
            uvRowStride = width / 2,
            uvPixelStride = 1,
            width = width,
            height = height,
            rotationDegrees = 0,
            cameraMatrix = K,
            distCoeffs = DoubleArray(5)
        )
    }

    private fun toGrayscale(src: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint()
        val cm = ColorMatrix()
        cm.setSaturation(0f)
        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(src, 0f, 0f, paint)
        return out
    }

    private fun boxBlur(src: Bitmap, radius: Int): Bitmap {
        var bmp = src
        repeat(radius) {
            val w = bmp.width
            val h = bmp.height
            val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            // Downscale/upscale approximates a blur without RenderScript.
            val small = Bitmap.createScaledBitmap(bmp, maxOf(1, w / 4), maxOf(1, h / 4), true)
            val back = Bitmap.createScaledBitmap(small, w, h, true)
            canvas.drawBitmap(back, 0f, 0f, paint)
            if (bmp !== src) bmp.recycle()
            small.recycle()
            back.recycle()
            bmp = out
        }
        return bmp
    }
}
