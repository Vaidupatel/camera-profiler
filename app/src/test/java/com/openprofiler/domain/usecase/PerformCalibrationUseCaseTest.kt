package com.openprofiler.domain.usecase

import com.openprofiler.domain.model.*
import com.openprofiler.domain.repository.CalibrationRepository
import com.openprofiler.domain.repository.CoverageRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PerformCalibrationUseCaseTest {

    private val calibrationRepository: CalibrationRepository = mockk()
    private val coverageRepository: CoverageRepository = mockk()
    private val validateDatasetUseCase: ValidateDatasetUseCase = mockk()
    
    private val useCase = PerformCalibrationUseCase(
        calibrationRepository,
        coverageRepository,
        validateDatasetUseCase
    )

    @Test
    fun `invoke returns failure when validation fails`() = runBlocking {
        val boardConfig = BoardConfig("DICT_4X4_50", 5, 7, 30f, 15f)
        val dataset = CalibrationDataset(emptyList(), boardConfig, "Model", "Camera")
        
        every { calibrationRepository.getDataset(boardConfig) } returns dataset
        coEvery { validateDatasetUseCase.invoke(dataset) } returns DatasetValidationResult(false, listOf("Error"), emptyList())
        
        val result = useCase.invoke(boardConfig)
        
        assertFalse(result.success)
    }

    @Test
    fun `invoke returns success when validation and calibration succeed`() = runBlocking {
        val boardConfig = BoardConfig("DICT_4X4_50", 5, 7, 30f, 15f)
        val dataset = CalibrationDataset(emptyList(), boardConfig, "Model", "Camera")
        
        every { calibrationRepository.getDataset(boardConfig) } returns dataset
        coEvery { validateDatasetUseCase.invoke(dataset) } returns DatasetValidationResult(true, emptyList(), emptyList())
        coEvery { calibrationRepository.solve() } returns CalibrationResult(success = true, rms = 0.2)
        every { coverageRepository.getCurrentCoverage() } returns CoverageData(overallPercentage = 80.0)
        
        val result = useCase.invoke(boardConfig)
        
        assertTrue(result.success)
        assertTrue(result.rms == 0.2)
        assertTrue(result.statistics?.coveragePercent == 80.0)
    }
}
