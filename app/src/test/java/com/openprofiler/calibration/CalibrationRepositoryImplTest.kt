package com.openprofiler.calibration

import com.google.common.truth.Truth.assertThat
import com.openprofiler.domain.model.BoardConfig
import com.openprofiler.native_bridge.NativeCalibrationEngine
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test

class CalibrationRepositoryImplTest {

    private val session: CalibrationSession = mockk(relaxed = true)
    private val nativeEngine: NativeCalibrationEngine = mockk(relaxed = true)
    private lateinit var repository: CalibrationRepositoryImpl

    private val boardConfig = BoardConfig(
        dictionaryName = "DICT_5X5_1000",
        squaresX = 9,
        squaresY = 6,
        squareLengthMm = 30f,
        markerLengthMm = 22f
    )

    @Before
    fun setUp() {
        repository = CalibrationRepositoryImpl(session, nativeEngine)
    }

    @Test
    fun `addFrame accepts first frame dimensions`() {
        repository.addFrame(emptyList(), boardConfig, 1080, 1920)
        verify { session.addFrame(emptyList(), boardConfig) }
    }

    @Test
    fun `addFrame rejects subsequent frame with different dimensions`() {
        every { session.getAcceptedFrameCount() } returns 0 andThen 1
        
        repository.addFrame(emptyList(), boardConfig, 1080, 1920)
        repository.addFrame(emptyList(), boardConfig, 1920, 1080)

        verify(exactly = 1) { session.addFrame(any(), any()) }
    }

    @Test
    fun `addFrame accepts subsequent frame with same dimensions`() {
        every { session.getAcceptedFrameCount() } returns 0 andThen 1
        
        repository.addFrame(emptyList(), boardConfig, 1080, 1920)
        repository.addFrame(emptyList(), boardConfig, 1080, 1920)

        verify(exactly = 2) { session.addFrame(any(), any()) }
    }
}
