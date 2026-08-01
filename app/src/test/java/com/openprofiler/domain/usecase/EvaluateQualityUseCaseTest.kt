package com.openprofiler.domain.usecase

import androidx.camera.core.ImageProxy
import com.google.common.truth.Truth.assertThat
import com.openprofiler.domain.model.QualityMetric
import com.openprofiler.domain.model.QualityMetricId
import com.openprofiler.domain.model.QualityMetricStatus
import com.openprofiler.domain.model.QualityResult
import com.openprofiler.domain.model.QualitySummary
import com.openprofiler.domain.repository.QualityRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class EvaluateQualityUseCaseTest {

    private val repository: QualityRepository = mockk()
    private val useCase = EvaluateQualityUseCase(repository)

    @Test
    fun invoke_delegatesToRepository() = runTest {
        val imageProxy: ImageProxy = mockk()
        val expected = QualityResult(
            metrics = listOf(
                QualityMetric(
                    QualityMetricId.BLUR,
                    200.0,
                    100.0,
                    QualityMetricStatus.PASS,
                    "ok"
                )
            ),
            summary = QualitySummary(
                isAccepted = true,
                overallScore = 1.0,
                passCount = 1,
                warningCount = 0,
                failCount = 0,
                primaryRejectReason = null
            ),
            processingTimeMs = 5L,
            frameWidthPx = 1920,
            frameHeightPx = 1080
        )
        coEvery { repository.evaluate(imageProxy, null, null) } returns expected

        val result = useCase(imageProxy)

        assertThat(result.summary.isAccepted).isTrue()
        coVerify(exactly = 1) { repository.evaluate(imageProxy, null, null) }
    }
}
