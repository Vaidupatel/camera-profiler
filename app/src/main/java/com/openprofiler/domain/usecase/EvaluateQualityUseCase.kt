package com.openprofiler.domain.usecase

import androidx.camera.core.ImageProxy
import com.openprofiler.domain.model.BoardConfig
import com.openprofiler.domain.model.DetectionResult
import com.openprofiler.domain.model.QualityResult
import com.openprofiler.domain.repository.QualityRepository
import javax.inject.Inject

/**
 * Use case to evaluate whether a camera frame is suitable for calibration.
 */
class EvaluateQualityUseCase @Inject constructor(
    private val qualityRepository: QualityRepository
) {
    suspend operator fun invoke(
        imageProxy: ImageProxy,
        detection: DetectionResult? = null,
        boardConfig: BoardConfig? = null
    ): QualityResult {
        return qualityRepository.evaluate(imageProxy, detection, boardConfig)
    }
}
