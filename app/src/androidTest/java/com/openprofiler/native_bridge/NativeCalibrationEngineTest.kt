package com.openprofiler.native_bridge

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativeCalibrationEngineTest {

    private val engine = NativeCalibrationEngine()

    @Test
    fun calibrate_withSyntheticPoints_returnsSuccessfulResult() {
        // Create 10 varied views of a 4x4 grid (16 points)
        val allObj = mutableListOf<FloatArray>()
        val allImg = mutableListOf<FloatArray>()
        
        val squareSize = 0.03f // 30mm
        val focalLength = 800f
        val cx = 640f
        val cy = 480f
        
        for (v in 0 until 10) {
            val obj = mutableListOf<Float>()
            val img = mutableListOf<Float>()
            
            // Simulating a board at different Z distances and slight tilts
            val z = 0.5f + v * 0.1f
            val tiltX = v * 0.02f
            
            for (y in 0 until 4) {
                for (x in 0 until 4) {
                    val px = x * squareSize
                    val py = y * squareSize
                    val pz = 0f
                    
                    // Simple projection: u = f*x/z + cx
                    // Apply slight tilt: x' = px, y' = py * cos(tilt) - pz * sin(tilt), z' = py * sin(tilt) + pz * cos(tilt) + distance
                    val ty = py * Math.cos(tiltX.toDouble()).toFloat()
                    val tz = py * Math.sin(tiltX.toDouble()).toFloat() + z
                    
                    obj.add(px); obj.add(py); obj.add(pz)
                    img.add(focalLength * px / tz + cx)
                    img.add(focalLength * ty / tz + cy)
                }
            }
            allObj.add(obj.toFloatArray())
            allImg.add(img.toFloatArray())
        }

        val result = engine.calibrate(allObj.toTypedArray(), allImg.toTypedArray(), 1280, 960)

        assertThat(result).isNotNull()
        assertThat(result?.success).isTrue()
        assertThat(result?.rms).isLessThan(1.0) // Real data usually < 1.0, synthetic perfect should be < 0.01
        assertThat(result?.cameraMatrix).hasLength(9)
        
        // Principal point should be near (640, 480)
        assertThat(result?.cameraMatrix?.get(2)).isWithin(5.0).of(640.0)
        assertThat(result?.cameraMatrix?.get(5)).isWithin(5.0).of(480.0)
    }
    
    @Test
    fun calibrate_withMismatchedData_returnsNullOrFailure() {
        val allObj = arrayOf(floatArrayOf(0f, 0f, 0f))
        val allImg = arrayOf(floatArrayOf(0f, 0f)) // 1 point each
        
        // OpenCV calibrateCamera needs at least 4 points per image if D is estimated
        val result = engine.calibrate(allObj, allImg, 1000, 1000)
        
        // It might return success=false or null depending on how JNI handles OpenCV failure
        if (result != null) {
            assertThat(result.success).isFalse()
        }
    }
}
