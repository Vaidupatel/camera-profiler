package com.openprofiler.quality

import androidx.camera.core.ImageProxy
import com.openprofiler.domain.model.BoardConfig
import com.openprofiler.domain.model.DetectionResult
import com.openprofiler.domain.model.QualityMetric
import com.openprofiler.domain.model.QualityMetricId
import com.openprofiler.domain.model.QualityMetricStatus
import com.openprofiler.domain.model.QualityResult
import com.openprofiler.domain.model.QualitySummary
import com.openprofiler.domain.model.QualityThresholds
import com.openprofiler.domain.repository.QualityRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QualityRepositoryImpl @Inject constructor(
    private val qualityEngine: QualityEngine
) : QualityRepository {

    @Volatile
    private var initialized = false

    override suspend fun initialize(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val ok = qualityEngine.initialize()
            if (!ok) {
                error("Failed to initialize native QualityEngine")
            }
            initialized = true
            Timber.d("QualityRepository initialized")
        }
    }

    override suspend fun evaluate(
        imageProxy: ImageProxy,
        detection: DetectionResult?,
        boardConfig: BoardConfig?
    ): QualityResult = withContext(Dispatchers.Default) {
        val start = System.currentTimeMillis()
        if (!initialized) {
            val init = initialize()
            if (init.isFailure) {
                return@withContext failureResult(
                    "Quality engine not initialized",
                    imageProxy.width,
                    imageProxy.height,
                    start
                )
            }
        }

        try {
            val planes = imageProxy.planes
            if (planes.isEmpty()) {
                return@withContext failureResult(
                    "ImageProxy planes empty",
                    imageProxy.width,
                    imageProxy.height,
                    start
                )
            }
            val yPlane = planes[0]
            qualityEngine.evaluate(
                yBuffer = yPlane.buffer,
                width = imageProxy.width,
                height = imageProxy.height,
                yRowStride = yPlane.rowStride,
                rotationDegrees = imageProxy.imageInfo.rotationDegrees,
                detection = detection,
                boardConfig = boardConfig
            )
        } catch (e: Exception) {
            Timber.e(e, "Quality evaluation failed")
            failureResult(
                e.message ?: "Unknown quality evaluation error",
                imageProxy.width,
                imageProxy.height,
                start
            )
        }
    }

    override fun resetMotionState() {
        qualityEngine.resetMotionState()
    }

    override fun release() {
        qualityEngine.release()
        initialized = false
    }

    override fun getThresholds(): QualityThresholds = qualityEngine.currentThresholds()

    private fun failureResult(
        reason: String,
        width: Int,
        height: Int,
        startMs: Long
    ): QualityResult {
        val metric = QualityMetric(
            id = QualityMetricId.BLUR,
            value = 0.0,
            threshold = 0.0,
            status = QualityMetricStatus.FAIL,
            reason = reason
        )
        return QualityResult(
            metrics = listOf(metric),
            summary = QualitySummary(
                isAccepted = false,
                overallScore = 0.0,
                passCount = 0,
                warningCount = 0,
                failCount = 1,
                primaryRejectReason = reason
            ),
            processingTimeMs = System.currentTimeMillis() - startMs,
            frameWidthPx = width,
            frameHeightPx = height
        )
    }
}
