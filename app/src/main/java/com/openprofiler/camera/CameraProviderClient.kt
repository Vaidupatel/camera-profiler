package com.openprofiler.camera

import androidx.camera.lifecycle.ProcessCameraProvider

/**
 * Obtains a [ProcessCameraProvider] asynchronously.
 * Extracted so [CameraRepositoryImpl] session-ownership logic can be unit-tested
 * without relying on the static CameraX [ProcessCameraProvider.getInstance] API.
 */
fun interface CameraProviderClient {
    fun get(
        onReady: (ProcessCameraProvider) -> Unit,
        onError: (Exception) -> Unit,
    )
}
