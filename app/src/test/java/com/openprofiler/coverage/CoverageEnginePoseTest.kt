package com.openprofiler.coverage

import com.openprofiler.domain.model.CoverageThresholds
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class CoverageEnginePoseTest {

    private lateinit var coverageEngine: CoverageEngine
    private val thresholds = CoverageThresholds(
        pitchBinsDegrees = listOf(-45.0, -15.0, 15.0, 45.0),
        yawBinsDegrees = listOf(-45.0, -15.0, 15.0, 45.0),
        rollBinsDegrees = listOf(-30.0, -10.0, 10.0, 30.0)
    )

    @Before
    fun setUp() {
        coverageEngine = CoverageEngine()
        coverageEngine.setThresholds(thresholds)
    }

    @Test
    fun `rvecToEulerDegrees converts zero rotation vector to zero pitch yaw roll`() {
        val rvec = doubleArrayOf(0.0, 0.0, 0.0)
        val euler = coverageEngine.rvecToEulerDegrees(rvec)

        assertEquals(0.0, euler[0], 0.001) // pitch
        assertEquals(0.0, euler[1], 0.001) // yaw
        assertEquals(0.0, euler[2], 0.001) // roll
    }

    @Test
    fun `rvecToEulerDegrees converts pure X rotation to pitch`() {
        // 30 degrees = 30 * PI / 180 = 0.52359877559 rad around X
        val rad = Math.toRadians(30.0)
        val rvec = doubleArrayOf(rad, 0.0, 0.0)
        val euler = coverageEngine.rvecToEulerDegrees(rvec)

        assertEquals(30.0, euler[0], 0.1) // pitch
        assertEquals(0.0, euler[1], 0.1)  // yaw
        assertEquals(0.0, euler[2], 0.1)  // roll
    }

    @Test
    fun `computeBinIndex maps angle values to correct bin indices`() {
        val cuts = listOf(-45.0, -15.0, 15.0, 45.0)

        // < -45 -> Bin 0
        assertEquals(0, coverageEngine.computeBinIndex(-60.0, cuts))
        // [-45, -15) -> Bin 1
        assertEquals(1, coverageEngine.computeBinIndex(-30.0, cuts))
        // [-15, 15) -> Bin 2
        assertEquals(2, coverageEngine.computeBinIndex(0.0, cuts))
        // [15, 45) -> Bin 3
        assertEquals(3, coverageEngine.computeBinIndex(30.0, cuts))
        // >= 45 -> Bin 4
        assertEquals(4, coverageEngine.computeBinIndex(60.0, cuts))
    }
}
