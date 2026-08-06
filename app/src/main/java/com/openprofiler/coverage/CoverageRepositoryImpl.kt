package com.openprofiler.coverage

import com.openprofiler.domain.model.CoverageData
import com.openprofiler.domain.model.CoverageEvaluationResult
import com.openprofiler.domain.model.CoverageThresholds
import com.openprofiler.domain.model.DetectionResult
import com.openprofiler.domain.repository.CoverageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of [CoverageRepository] managing thresholds loading and delegating to [CoverageEngine].
 */
@Singleton
class CoverageRepositoryImpl @Inject constructor(
    private val coverageEngine: CoverageEngine,
    private val thresholdsLoader: CoverageThresholdsLoader
) : CoverageRepository {

    @Volatile
    private var initialized = false

    override suspend fun initialize(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val thresholdsResult = thresholdsLoader.load()
            val thresholds = thresholdsResult.getOrDefault(CoverageThresholds())
            coverageEngine.setThresholds(thresholds)
            coverageEngine.resetState()
            initialized = true
            Timber.d("CoverageRepository initialized with thresholds: %s", thresholds)
        }
    }

    override suspend fun evaluate(
        detection: DetectionResult,
        frameWidth: Int,
        frameHeight: Int
    ): CoverageEvaluationResult = withContext(Dispatchers.Default) {
        if (!initialized) {
            val initResult = initialize()
            if (initResult.isFailure) {
                Timber.w(initResult.exceptionOrNull(), "CoverageRepository auto-initialization failed; using defaults")
            }
        }
        coverageEngine.evaluate(detection, frameWidth, frameHeight)
    }

    override fun resetState() {
        coverageEngine.resetState()
    }

    override fun getCurrentCoverage(): CoverageData = coverageEngine.getCurrentCoverage()

    override fun getThresholds(): CoverageThresholds = coverageEngine.getThresholds()
}
