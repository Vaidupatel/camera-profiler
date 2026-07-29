package com.openprofiler.ui.screens

import androidx.camera.core.CameraSelector
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
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
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import com.openprofiler.domain.repository.CameraRepository
import com.openprofiler.ui.components.CameraPreview

/**
 * Live CameraX preview screen with lens switching and session controls.
 * Phase 2: Live CameraX preview integration — No OpenCV / Metadata logic.
 *
 * @param cameraRepository Injected CameraRepository handling CameraX session.
 * @param onStartCalibration Navigation callback to begin calibration.
 * @param onSettings Navigation callback to open settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(
    cameraRepository: CameraRepository? = null,
    onStartCalibration: () -> Unit = {},
    onSettings: () -> Unit = {},
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraState by (cameraRepository?.cameraState?.collectAsState()
        ?: androidx.compose.runtime.mutableStateOf(com.openprofiler.camera.CameraState()))

    DisposableEffect(Unit) {
        onDispose {
            cameraRepository?.stopCamera()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Live CameraX Preview") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White,
                ),
                actions = {
                    IconButton(onClick = { cameraRepository?.switchCamera() }) {
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
            // Live CameraX Preview Surface
            if (cameraRepository != null) {
                CameraPreview(
                    onSurfaceProviderReady = { surfaceProvider ->
                        cameraRepository.startCamera(lifecycleOwner, surfaceProvider)
                    },
                )
            } else {
                Text(
                    text = "[ CameraX Live Preview Ready ]",
                    color = Color.Gray,
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            // Bottom overlay controls
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val lensText = if (cameraState.lensFacing == CameraSelector.LENS_FACING_BACK) "Rear Camera" else "Front Camera"
                Text(
                    text = "Active Lens: $lensText | Resolution: ${cameraState.resolution}",
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
