package com.openprofiler.camera

import android.content.Context
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production [CameraProviderClient] backed by CameraX [ProcessCameraProvider.getInstance].
 */
@Singleton
class ProcessCameraProviderClient @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : CameraProviderClient {

    override fun get(
        onReady: (ProcessCameraProvider) -> Unit,
        onError: (Exception) -> Unit,
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                onReady(cameraProviderFuture.get())
            } catch (e: Exception) {
                onError(e)
            }
        }, ContextCompat.getMainExecutor(context))
    }
}
