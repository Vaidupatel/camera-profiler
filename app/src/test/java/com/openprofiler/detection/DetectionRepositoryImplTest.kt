package com.openprofiler.detection

import com.google.common.truth.Truth.assertThat
import com.openprofiler.domain.model.BoardConfig
import com.openprofiler.native_bridge.NativeDetectionEngine
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DetectionRepositoryImplTest {

    private val boardConfigLoader: BoardConfigLoader = mockk()
    private val nativeEngine: NativeDetectionEngine = mockk(relaxed = true)

    @Test
    fun `initialize loads config and initializes native engine`() = runTest {
        val boardConfig = BoardConfig(
            dictionaryName = "DICT_5X5_1000",
            squaresX = 9,
            squaresY = 6,
            squareLengthMm = 30.0f,
            markerLengthMm = 22.0f
        )

        every { boardConfigLoader.loadBoardConfig() } returns Result.success(boardConfig)
        every { nativeEngine.initialize(any()) } returns true

        val repository = DetectionRepositoryImpl(boardConfigLoader, nativeEngine)
        val result = repository.initialize()

        assertThat(result.isSuccess).isTrue()
        assertThat(repository.getBoardConfig()).isEqualTo(boardConfig)
        verify { nativeEngine.initialize(any()) }
    }

    @Test
    fun `release releases native engine resources`() {
        val repository = DetectionRepositoryImpl(boardConfigLoader, nativeEngine)
        repository.release()

        verify { nativeEngine.release() }
        assertThat(repository.getBoardConfig()).isNull()
    }
}
