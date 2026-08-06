package com.openprofiler.calibration

import com.google.common.truth.Truth.assertThat
import com.openprofiler.domain.model.BoardConfig
import com.openprofiler.domain.model.DetectedCorner
import com.openprofiler.domain.model.Point2D
import org.junit.Test

class CalibrationSessionTest {

    private val session = CalibrationSession()
    
    private val boardConfig = BoardConfig(
        dictionaryName = "DICT_4X4_50",
        squaresX = 4,
        squaresY = 3,
        squareLengthMm = 20f,
        markerLengthMm = 15f
    )

    @Test
    fun addFrame_reconstructsObjectPointsCorrectly() {
        // SquaresX = 4 -> Interior corners along X = 3
        // SquaresY = 3 -> Interior corners along Y = 2
        // Total interior corners = 6 (IDs 0 to 5)
        
        val corners = listOf(
            DetectedCorner(id = 0, x = 100f, y = 100f, subpixelPrecision = 1f), // Row 0, Col 0
            DetectedCorner(id = 3, x = 100f, y = 120f, subpixelPrecision = 1f)  // Row 1, Col 0
        )
        
        session.addFrame(corners, boardConfig)
        
        val objPoints = session.getObjectPoints()[0]
        val imgPoints = session.getImagePoints()[0]
        
        // ID 0 -> (0, 0, 0)
        assertThat(objPoints[0]).isEqualTo(0f)
        assertThat(objPoints[1]).isEqualTo(0f)
        assertThat(objPoints[2]).isEqualTo(0f)
        
        // ID 3 -> (0, 0.02, 0) since id / 3 = 1 (row 1), id % 3 = 0 (col 0). squareLength = 0.02m
        assertThat(objPoints[3]).isEqualTo(0f)
        assertThat(objPoints[4]).isEqualTo(0.02f)
        assertThat(objPoints[5]).isEqualTo(0f)
        
        assertThat(imgPoints[0]).isEqualTo(100f)
        assertThat(imgPoints[1]).isEqualTo(100f)
        assertThat(imgPoints[2]).isEqualTo(100f)
        assertThat(imgPoints[3]).isEqualTo(120f)
        
        assertThat(session.getAcceptedFrameCount()).isEqualTo(1)
    }

    @Test
    fun reset_clearsAccumulatedPoints() {
        val corners = listOf(DetectedCorner(id = 0, x = 100f, y = 100f, subpixelPrecision = 1f))
        session.addFrame(corners, boardConfig)
        assertThat(session.getAcceptedFrameCount()).isEqualTo(1)
        
        session.reset()
        assertThat(session.getAcceptedFrameCount()).isEqualTo(0)
        assertThat(session.getObjectPoints()).isEmpty()
    }
}
