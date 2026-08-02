package com.openprofiler.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.openprofiler.common.util.Logger
import com.openprofiler.ui.components.CameraPreview
import com.openprofiler.ui.components.DetectionOverlay
import com.openprofiler.ui.util.rememberCameraLifecycleOwner
import com.openprofiler.ui.viewmodel.CalibrationViewModel

private const val TAG = "CalibrationScreen"

/**
 * Calibration Session screen — owns the live CameraX Preview + ImageAnalysis session.
 *
 * Camera session ownership is token-based on the shared [com.openprofiler.domain.repository.CameraRepository]
 * singleton. A late [DisposableEffect] stop from CameraScreen cannot unbind this session once
 * Calibration has acquired a newer [com.openprofiler.domain.repository.CameraSessionToken].
 *
 * Overlay draws above preview; it never replaces PreviewView.
 * Coverage UI remains a Phase-6 placeholder (always 0%).
 *
 * CameraX binds to the host Activity lifecycle so a Camera→Calibration navigation
 * reuses the use-case graph instead of tearing down CameraDevice.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalibrationScreen(
    viewModel: CalibrationViewModel = hiltViewModel(),
    onFinishCalibration: () -> Unit = {},
    onCancel: () -> Unit = {},
) {
    val lifecycleOwner = rememberCameraLifecycleOwner()
    val uiState by viewModel.uiState.collectAsState()

    DisposableEffect(Unit) {
        Logger.i(TAG, "Entered calibration session composition")
        onDispose {
            // Unbind when leaving this screen so only one session exists app-wide.
            Logger.i(TAG, "Leaving calibration session — stopping camera")
            viewModel.stopCamera()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Calibrating...") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White,
                ),
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.Black),
        ) {
            // 1. Live CameraX Preview (must remain visible behind overlay)
            CameraPreview(
                onSurfaceProviderReady = { surfaceProvider ->
                    Logger.i(TAG, "Calibration Preview SurfaceProvider ready — binding camera")
                    viewModel.startCamera(lifecycleOwner, surfaceProvider)
                },
            )

            // 2. Detection overlay drawn above preview (never replaces it)
            DetectionOverlay(
                detectionResult = uiState.detectionResult,
            )

            // 3. Bottom status card
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Coverage: 0%",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { 0.0f },
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text("Accepted Frames: 0 / 15 minimum")
                        Text(uiState.targetStatusText)
                        Text(uiState.qualityStatusText)

                        val streaming = if (uiState.cameraState.isStreaming) "ON" else "OFF"
                        Text(
                            text = "Camera: $streaming · ${uiState.cameraState.resolution}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    OutlinedButton(onClick = onCancel) {
                        Text("Cancel")
                    }
                    Button(onClick = onFinishCalibration) {
                        Text("View Results")
                    }
                }
            }
        }
    }
}
