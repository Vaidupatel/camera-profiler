package com.openprofiler.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openprofiler.domain.model.DetectionResult

/**
 * Live Compose overlay component for rendering calibration target detection graphics in real time.
 * Renders:
 * - Detected ArUco markers (green quad outline)
 * - Subpixel-refined ChArUco corners (cyan/blue dots)
 * - Board bounding box (yellow outline)
 * - 3D Pose Axes overlay (X=Red, Y=Green, Z=Blue)
 * - HUD panel displaying confidence, processing time, and counts
 *
 * @param detectionResult Immutable detection result from [CameraUiState].
 * @param modifier Composable modifier.
 */
@Composable
fun DetectionOverlay(
    detectionResult: DetectionResult?,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        if (detectionResult != null && detectionResult.boardDetected) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val canvasWidth = size.width
                val canvasHeight = size.height

                // Assume coordinates are normalized if in [0, 1] range, or map directly
                // Scaling factors handle pixel vs normalized space gracefully
                val maxX = maxOf(
                    detectionResult.cornerCoordinates.maxOfOrNull { it.x } ?: 1.0f,
                    detectionResult.detectedMarkers.flatMap { m -> m.corners.map { it.x } }.maxOfOrNull { it } ?: 1.0f,
                    1.0f
                )
                val maxY = maxOf(
                    detectionResult.cornerCoordinates.maxOfOrNull { it.y } ?: 1.0f,
                    detectionResult.detectedMarkers.flatMap { m -> m.corners.map { it.y } }.maxOfOrNull { it } ?: 1.0f,
                    1.0f
                )

                val scaleX = if (maxX > canvasWidth) 1.0f else if (maxX <= 1.0f) canvasWidth else 1.0f
                val scaleY = if (maxY > canvasHeight) 1.0f else if (maxY <= 1.0f) canvasHeight else 1.0f

                // 1. Draw Bounding Box (Yellow)
                detectionResult.boundingBox?.let { boxPoints ->
                    if (boxPoints.size >= 4) {
                        val path = Path().apply {
                            moveTo(boxPoints[0].x * scaleX, boxPoints[0].y * scaleY)
                            lineTo(boxPoints[1].x * scaleX, boxPoints[1].y * scaleY)
                            lineTo(boxPoints[2].x * scaleX, boxPoints[2].y * scaleY)
                            lineTo(boxPoints[3].x * scaleX, boxPoints[3].y * scaleY)
                            close()
                        }
                        drawPath(
                            path = path,
                            color = Color(0xFFFFD700), // Gold/Yellow
                            style = Stroke(width = 4.dp.toPx()),
                        )
                    }
                }

                // 2. Draw Detected Markers (Green Quads)
                detectionResult.detectedMarkers.forEach { marker ->
                    if (marker.corners.size == 4) {
                        val path = Path().apply {
                            moveTo(marker.corners[0].x * scaleX, marker.corners[0].y * scaleY)
                            lineTo(marker.corners[1].x * scaleX, marker.corners[1].y * scaleY)
                            lineTo(marker.corners[2].x * scaleX, marker.corners[2].y * scaleY)
                            lineTo(marker.corners[3].x * scaleX, marker.corners[3].y * scaleY)
                            close()
                        }
                        drawPath(
                            path = path,
                            color = Color(0xFF00FF66), // Bright Green
                            style = Stroke(width = 2.5f.dp.toPx()),
                        )
                    }
                }

                // 3. Draw Subpixel ChArUco Corners (Cyan Dots with Dark Border)
                detectionResult.cornerCoordinates.forEach { corner ->
                    val cx = corner.x * scaleX
                    val cy = corner.y * scaleY
                    drawCircle(
                        color = Color(0xFF003366),
                        radius = 6.dp.toPx(),
                        center = Offset(cx, cy),
                    )
                    drawCircle(
                        color = Color(0xFF00E5FF), // Cyan
                        radius = 4.dp.toPx(),
                        center = Offset(cx, cy),
                    )
                }

                // 4. Draw 3D Pose Axes (X=Red, Y=Green, Z=Blue)
                detectionResult.boardAxes?.let { axes ->
                    val origin = Offset(axes.origin.x * scaleX, axes.origin.y * scaleY)
                    val xEnd = Offset(axes.xAxisEnd.x * scaleX, axes.xAxisEnd.y * scaleY)
                    val yEnd = Offset(axes.yAxisEnd.x * scaleX, axes.yAxisEnd.y * scaleY)
                    val zEnd = Offset(axes.zAxisEnd.x * scaleX, axes.zAxisEnd.y * scaleY)

                    // Origin Dot
                    drawCircle(color = Color.White, radius = 5.dp.toPx(), center = origin)

                    // X-Axis (Red)
                    drawLine(
                        color = Color(0xFFFF3333),
                        start = origin,
                        end = xEnd,
                        strokeWidth = 4.dp.toPx(),
                    )
                    // Y-Axis (Green)
                    drawLine(
                        color = Color(0xFF33FF33),
                        start = origin,
                        end = yEnd,
                        strokeWidth = 4.dp.toPx(),
                    )
                    // Z-Axis (Blue)
                    drawLine(
                        color = Color(0xFF3388FF),
                        start = origin,
                        end = zEnd,
                        strokeWidth = 4.dp.toPx(),
                    )
                }
            }
        }

        // 5. Detection Status & Info HUD Panel
        DetectionHudPanel(
            detectionResult = detectionResult,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp),
        )
    }
}

@Composable
private fun DetectionHudPanel(
    detectionResult: DetectionResult?,
    modifier: Modifier = Modifier,
) {
    val isDetected = detectionResult?.boardDetected == true
    val statusColor = if (isDetected) Color(0xFF00E676) else Color(0xFFFFB300)
    val statusText = if (isDetected) "TARGET DETECTED" else "SEARCHING TARGET..."

    Box(
        modifier = modifier
            .background(
                color = Color.Black.copy(alpha = 0.75f),
                shape = RoundedCornerShape(8.dp),
            )
            .padding(12.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .width(10.dp)
                        .height(10.dp)
                        .background(statusColor, shape = RoundedCornerShape(5.dp)),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = statusText,
                    color = statusColor,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                    ),
                )
            }

            if (detectionResult != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Confidence: ${(detectionResult.detectionConfidence * 100).toInt()}% | Time: ${detectionResult.processingTimeMs} ms",
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = "Markers: ${detectionResult.markerCount} | Corners: ${detectionResult.charucoCornerCount}",
                    color = Color.LightGray,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
