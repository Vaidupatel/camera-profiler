package com.openprofiler.calibration

import com.openprofiler.domain.model.BoardConfig
import com.openprofiler.domain.model.CalibrationResult
import com.openprofiler.domain.model.DetectedCorner
import com.openprofiler.domain.repository.CalibrationRepository
import com.openprofiler.native_bridge.NativeCalibrationEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CalibrationRepositoryImpl @Inject constructor(
    private val session: CalibrationSession,
    private val nativeEngine: NativeCalibrationEngine
) : CalibrationRepository {

    private var lastWidth: Int = 0
    private var lastHeight: Int = 0

    override fun addFrame(
        corners: List<DetectedCorner>,
        boardConfig: BoardConfig,
        width: Int,
        height: Int
    ) {
        if (session.getAcceptedFrameCount() > 0 && (width != lastWidth || height != lastHeight)) {
            com.openprofiler.common.util.Logger.w(
                "CalibrationRepository",
                "Rejecting frame with inconsistent dimensions: ${width}x${height} " +
                    "(session expected ${lastWidth}x${lastHeight})"
            )
            return
        }
        session.addFrame(corners, boardConfig)
        lastWidth = width
        lastHeight = height
    }

    override fun reset() {
        session.reset()
    }

    override fun getAcceptedFrameCount(): Int {
        return session.getAcceptedFrameCount()
    }

    override suspend fun solve(): CalibrationResult = withContext(Dispatchers.Default) {
        val objPoints = session.getObjectPoints()
        val imgPoints = session.getImagePoints()
        
        if (objPoints.isEmpty() || lastWidth <= 0 || lastHeight <= 0) {
            return@withContext CalibrationResult(success = false)
        }
        
        val nativeResult = nativeEngine.calibrate(
            objPoints,
            imgPoints,
            lastWidth,
            lastHeight
        ) ?: return@withContext CalibrationResult(success = false)
        
        CalibrationResult(
            rms = nativeResult.rms,
            cameraMatrix = listOf(
                nativeResult.cameraMatrix.slice(0..2),
                nativeResult.cameraMatrix.slice(3..5),
                nativeResult.cameraMatrix.slice(6..8)
            ),
            distortionCoefficients = nativeResult.distCoeffs.toList(),
            imageCount = objPoints.size,
            success = nativeResult.success
        )
    }
}
