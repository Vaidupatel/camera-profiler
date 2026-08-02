package com.openprofiler.ui.screens

import androidx.camera.core.CameraSelector
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.openprofiler.ui.viewmodel.CameraViewModel

private const val TAG = "CameraScreen"

/**
 * Live CameraX preview screen with real-time target detection overlay.
 *
 * Session ownership:
 * [CameraViewModel] acquires a [com.openprofiler.domain.repository.CameraSessionToken] on
 * start and releases only that token on dispose. If CalibrationScreen has already started a
 * newer session, this dispose stop is a deterministic no-op on the shared repository.
 *
 * CameraX is bound to the host [androidx.activity.ComponentActivity] lifecycle (not the
 * NavBackStackEntry) so navigation between camera screens can reuse the bound use-case graph.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(
    viewModel: CameraViewModel = hiltViewModel(),
    onStartCalibration: () -> Unit = {},
    onSettings: () -> Unit = {},
) {
    val lifecycleOwner = rememberCameraLifecycleOwner()
    val uiState by viewModel.uiState.collectAsState()

    DisposableEffect(Unit) {
        Logger.i(TAG, "Entered live camera screen")
        onDispose {
            Logger.i(TAG, "Leaving live camera screen — releasing this screen's camera session token")
            viewModel.stopCamera()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Live CameraX Preview & Target Detection") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White,
                ),
                actions = {
                    IconButton(onClick = { viewModel.switchCamera() }) {
                        Text("🔄", color = Color.White)
                    }
                    IconButton(onClick = onSettings) {
                        Text("⚙️", color = Color.White)
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            CameraPreview(
                onSurfaceProviderReady = { surfaceProvider ->
                    Logger.i(TAG, "Camera Preview SurfaceProvider ready — binding camera")
                    viewModel.startCamera(lifecycleOwner, surfaceProvider)
                },
            )

            DetectionOverlay(
                detectionResult = uiState.detectionResult,
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val lensText = if (uiState.cameraState.lensFacing == CameraSelector.LENS_FACING_BACK) {
                    "Rear Camera"
                } else {
                    "Front Camera"
                }
                Text(
                    text = "Active Lens: $lensText | Res: ${uiState.cameraState.resolution}",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onStartCalibration,
                ) {
                    Text("Start Calibration Session")
                }
            }
        }
    }
}
