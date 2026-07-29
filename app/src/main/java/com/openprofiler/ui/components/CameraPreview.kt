package com.openprofiler.ui.components

import androidx.camera.core.Preview
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Jetpack Compose host for CameraX [PreviewView].
 *
 * @param modifier Modifier for layout styling.
 * @param onSurfaceProviderReady Callback providing CameraX SurfaceProvider once PreviewView is initialized.
 */
@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    onSurfaceProviderReady: (Preview.SurfaceProvider) -> Unit,
) {
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            PreviewView(context).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                implementationMode = PreviewView.ImplementationMode.PERFORMANCE
                onSurfaceProviderReady(surfaceProvider)
            }
        },
    )
}
