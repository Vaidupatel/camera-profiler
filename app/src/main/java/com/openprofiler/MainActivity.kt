package com.openprofiler

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.openprofiler.ui.navigation.AppNavigation
import com.openprofiler.ui.theme.CameraProfilerTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single-activity host for the Camera Profiler Compose application.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CameraProfilerTheme {
                AppNavigation()
            }
        }
    }
}
