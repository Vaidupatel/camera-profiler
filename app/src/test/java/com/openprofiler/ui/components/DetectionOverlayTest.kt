package com.openprofiler.ui.components

import com.google.common.truth.Truth.assertThat
import com.openprofiler.domain.model.BoardAxesOverlay
import com.openprofiler.domain.model.DetectedCorner
import com.openprofiler.domain.model.DetectedMarker
import com.openprofiler.domain.model.DetectionResult
import com.openprofiler.domain.model.Point2D
import com.openprofiler.ui.viewmodel.CameraUiState
import org.junit.Test

class DetectionOverlayTest {

    @Test
    fun `CameraUiState defaults contain null detectionResult and false isDetecting`() {
        val state = CameraUiState()
        assertThat(state.detectionResult).isNull()
        assertThat(state.isDetecting).isFalse()
    }

    @Test
    fun `DetectionResult encapsulates all required overlay data fields`() {
        val markers = listOf(
            DetectedMarker(
                id = 1,
                corners = listOf(
                    Point2D(0f, 0f),
                    Point2D(10f, 0f),
                    Point2D(10f, 10f),
                    Point2D(0f, 10f)
                )
            )
        )
        val corners = listOf(DetectedCorner(id = 10, x = 5f, y = 5f, subpixelPrecision = 0.98f))
        val axes = BoardAxesOverlay(
            origin = Point2D(5f, 5f),
            xAxisEnd = Point2D(15f, 5f),
            yAxisEnd = Point2D(5f, 15f),
            zAxisEnd = Point2D(5f, 5f)
        )
        val boundingBox = listOf(
            Point2D(0f, 0f),
            Point2D(20f, 0f),
            Point2D(20f, 20f),
            Point2D(0f, 20f)
        )

        val result = DetectionResult(
            boardDetected = true,
            dictionary = "DICT_4X4_50",
            markerCount = 1,
            charucoCornerCount = 1,
            markerIds = listOf(1),
            charucoIds = listOf(10),
            cornerCoordinates = corners,
            boardPose = null,
            detectionConfidence = 0.99f,
            processingTimeMs = 8L,
            detectedMarkers = markers,
            boardAxes = axes,
            boundingBox = boundingBox
        )

        val uiState = CameraUiState(
            detectionResult = result,
            isDetecting = true
        )

        assertThat(uiState.isDetecting).isTrue()
        assertThat(uiState.detectionResult?.markerCount).isEqualTo(1)
        assertThat(uiState.detectionResult?.charucoCornerCount).isEqualTo(1)
        assertThat(uiState.detectionResult?.detectedMarkers).hasSize(1)
        assertThat(uiState.detectionResult?.cornerCoordinates).hasSize(1)
        assertThat(uiState.detectionResult?.boardAxes).isEqualTo(axes)
        assertThat(uiState.detectionResult?.boundingBox).hasSize(4)
        assertThat(uiState.detectionResult?.detectionConfidence).isEqualTo(0.99f)
        assertThat(uiState.detectionResult?.processingTimeMs).isEqualTo(8L)
    }
}
