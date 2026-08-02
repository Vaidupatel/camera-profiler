package com.openprofiler.domain.usecase

import androidx.camera.core.ImageProxy
import com.openprofiler.domain.model.DetectionResult
import com.openprofiler.domain.model.IntrinsicsSource
import com.openprofiler.domain.repository.DetectionRepository
import javax.inject.Inject

/**
 * Use case to analyze a camera frame and detect calibration target boards.
 */
class DetectBoardUseCase @Inject constructor(
    private val detectionRepository: DetectionRepository
) {
    suspend operator fun invoke(
        imageProxy: ImageProxy,
        cameraMatrix: DoubleArray? = null,
        distCoeffs: DoubleArray? = null,
        intrinsicsSource: IntrinsicsSource = IntrinsicsSource.UNAVAILABLE,
    ): DetectionResult {
        return detectionRepository.detectBoard(
            imageProxy,
            cameraMatrix,
            distCoeffs,
            intrinsicsSource,
        )
    }
}
