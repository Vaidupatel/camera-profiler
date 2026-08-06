package com.openprofiler.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.openprofiler.domain.model.CalibrationResult
import com.openprofiler.domain.model.CoverageData
import com.openprofiler.domain.model.IntrinsicsSource
import com.openprofiler.domain.repository.CalibrationRepository
import com.openprofiler.domain.repository.CoverageRepository
import com.openprofiler.domain.repository.MetadataRepository
import com.openprofiler.metadata.model.FullMetadata
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class GenerateProfileUseCaseTest {

    private val metadataRepository: MetadataRepository = mockk()
    private val calibrationRepository: CalibrationRepository = mockk()
    private val coverageRepository: CoverageRepository = mockk()
    
    private val useCase = GenerateProfileUseCase(
        metadataRepository,
        calibrationRepository,
        coverageRepository
    )

    @Test
    fun `invoke aggregates data from repositories`() = runTest {
        val calibration = CalibrationResult(rms = 0.5, success = true)
        val metadata = FullMetadata()
        val coverage = CoverageData(overallPercentage = 80.0)

        coEvery { calibrationRepository.solve() } returns calibration
        every { metadataRepository.collectMetadata() } returns metadata
        every { coverageRepository.getCurrentCoverage() } returns coverage

        val profile = useCase()

        assertThat(profile.calibrated).isEqualTo(calibration)
        assertThat(profile.metadata).isEqualTo(metadata)
        assertThat(profile.coverage).isEqualTo(coverage)
        assertThat(profile.source).isEqualTo(IntrinsicsSource.CALIBRATED)
        assertThat(profile.profileId).isNotEmpty()
        assertThat(profile.createdAt).isNotEmpty()
    }
}
