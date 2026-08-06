package com.openprofiler.calibration

import com.openprofiler.domain.model.BoardConfig
import com.openprofiler.domain.model.DetectedCorner
import com.openprofiler.domain.model.Point3D
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the accumulation of calibration point correspondences.
 * Reconstructs 3D object points from ChArUco geometry.
 */
@Singleton
class CalibrationSession @Inject constructor() {
    private val lock = Any()
    
    // List of frames, each frame is a list of (ObjectPoint, ImagePoint)
    private val accumulatedObjectPoints = mutableListOf<FloatArray>()
    private val accumulatedImagePoints = mutableListOf<FloatArray>()
    
    fun addFrame(
        corners: List<DetectedCorner>,
        boardConfig: BoardConfig
    ) {
        synchronized(lock) {
            val objPoints = mutableListOf<Float>()
            val imgPoints = mutableListOf<Float>()
            
            val squareSize = boardConfig.squareLengthMm / 1000f // mm to meters
            val cornersX = boardConfig.squaresX - 1
            
            for (corner in corners) {
                val id = corner.id
                val row = id / cornersX
                val col = id % cornersX
                
                // Object Point (3D)
                objPoints.add(col * squareSize)
                objPoints.add(row * squareSize)
                objPoints.add(0f)
                
                // Image Point (2D)
                imgPoints.add(corner.x)
                imgPoints.add(corner.y)
            }
            
            accumulatedObjectPoints.add(objPoints.toFloatArray())
            accumulatedImagePoints.add(imgPoints.toFloatArray())
        }
    }
    
    fun reset() {
        synchronized(lock) {
            accumulatedObjectPoints.clear()
            accumulatedImagePoints.clear()
        }
    }
    
    fun getAcceptedFrameCount(): Int = synchronized(lock) {
        accumulatedObjectPoints.size
    }
    
    fun getObjectPoints(): Array<FloatArray> = synchronized(lock) {
        accumulatedObjectPoints.toTypedArray()
    }
    
    fun getImagePoints(): Array<FloatArray> = synchronized(lock) {
        accumulatedImagePoints.toTypedArray()
    }
}
