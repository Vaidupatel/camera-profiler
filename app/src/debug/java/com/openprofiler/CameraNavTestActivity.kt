package com.openprofiler

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.openprofiler.ui.screens.CalibrationScreen
import com.openprofiler.ui.screens.CameraScreen
import com.openprofiler.ui.theme.CameraProfilerTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Debug-only host for instrumented navigation tests of Camera↔Calibration rebind.
 */
@AndroidEntryPoint
class CameraNavTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CameraProfilerTheme {
                CameraNavTestGraph()
            }
        }
    }
}

@Composable
private fun CameraNavTestGraph() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "camera") {
        composable("camera") {
            CameraScreen(
                onStartCalibration = { navController.navigate("calibration") },
                onSettings = {},
            )
        }
        composable("calibration") {
            CalibrationScreen(
                onFinishCalibration = {},
                onCancel = { navController.popBackStack() },
            )
        }
    }
}
