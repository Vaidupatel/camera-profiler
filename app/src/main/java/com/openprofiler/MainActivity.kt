package com.openprofiler

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.openprofiler.domain.repository.CameraRepository
import com.openprofiler.ui.navigation.AppNavigation
import com.openprofiler.ui.theme.CameraProfilerTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Single-activity host for the Camera Profiler Compose application.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var cameraRepository: CameraRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CameraProfilerTheme {
                AppNavigation(
                    cameraRepository = cameraRepository,
                )
            }
        }
    }
}
