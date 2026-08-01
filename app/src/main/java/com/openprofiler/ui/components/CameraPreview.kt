package com.openprofiler.ui.components

import androidx.camera.core.Preview
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.openprofiler.common.util.Logger

private const val TAG = "CameraPreview"

/**
 * Jetpack Compose host for CameraX [PreviewView].
 *
 * [PreviewView] is created once in [AndroidView]'s factory and retained across
 * recompositions (update is a no-op). Recreating PreviewView every recomposition
 * would detach the SurfaceProvider and black out the preview.
 *
 * @param modifier Modifier for layout styling.
 * @param onSurfaceProviderReady Invoked once the PreviewView surface is ready to bind.
 */
@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    onSurfaceProviderReady: (Preview.SurfaceProvider) -> Unit,
) {
    val latestCallback = rememberUpdatedState(onSurfaceProviderReady)

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            PreviewView(context).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                implementationMode = PreviewView.ImplementationMode.PERFORMANCE
                // Defer until the view is attached so CameraX can acquire a valid surface.
                post {
                    Logger.i(TAG, "PreviewView surface ready — notifying SurfaceProvider")
                    latestCallback.value(surfaceProvider)
                }
            }
        },
        update = {
            // Intentionally empty: keep the same PreviewView instance across recompositions.
        },
    )
}
