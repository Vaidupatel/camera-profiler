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
        if (detectionResult != null && detectionResult.boardDetected &&
            detectionResult.frameWidth > 0 && detectionResult.frameHeight > 0
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val canvasWidth = size.width
                val canvasHeight = size.height
                val imgWidth = detectionResult.frameWidth.toFloat()
                val imgHeight = detectionResult.frameHeight.toFloat()

                val canvasAspect = canvasWidth / canvasHeight
                val imgAspect = imgWidth / imgHeight

                val scale: Float
                val offsetX: Float
                val offsetY: Float

                if (canvasAspect > imgAspect) {
                    // Canvas is wider than image (relatively) -> match width, crop image top/bottom
                    scale = canvasWidth / imgWidth
                    offsetX = 0f
                    offsetY = (canvasHeight - imgHeight * scale) / 2f
                } else {
                    // Canvas is taller than image (relatively) -> match height, crop image sides
                    scale = canvasHeight / imgHeight
                    offsetY = 0f
                    offsetX = (canvasWidth - imgWidth * scale) / 2f
                }

                // Helper to map image coords to canvas coords
                fun mapX(x: Float) = x * scale + offsetX
                fun mapY(y: Float) = y * scale + offsetY

                // 1. Draw Bounding Box (Yellow)
                detectionResult.boundingBox?.let { boxPoints ->
                    if (boxPoints.size >= 4) {
                        val path = Path().apply {
                            moveTo(mapX(boxPoints[0].x), mapY(boxPoints[0].y))
                            lineTo(mapX(boxPoints[1].x), mapY(boxPoints[1].y))
                            lineTo(mapX(boxPoints[2].x), mapY(boxPoints[2].y))
                            lineTo(mapX(boxPoints[3].x), mapY(boxPoints[3].y))
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
                            moveTo(mapX(marker.corners[0].x), mapY(marker.corners[0].y))
                            lineTo(mapX(marker.corners[1].x), mapY(marker.corners[1].y))
                            lineTo(mapX(marker.corners[2].x), mapY(marker.corners[2].y))
                            lineTo(mapX(marker.corners[3].x), mapY(marker.corners[3].y))
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
                    val cx = mapX(corner.x)
                    val cy = mapY(corner.y)
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
                    val origin = Offset(mapX(axes.origin.x), mapY(axes.origin.y))
                    val xEnd = Offset(mapX(axes.xAxisEnd.x), mapY(axes.xAxisEnd.y))
                    val yEnd = Offset(mapX(axes.yAxisEnd.x), mapY(axes.yAxisEnd.y))
                    val zEnd = Offset(mapX(axes.zAxisEnd.x), mapY(axes.zAxisEnd.y))

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
