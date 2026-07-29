package com.openprofiler.ui.navigation

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.openprofiler.domain.repository.CameraRepository
import com.openprofiler.ui.screens.AboutScreen
import com.openprofiler.ui.screens.CalibrationScreen
import com.openprofiler.ui.screens.CameraScreen
import com.openprofiler.ui.screens.DeveloperScreen
import com.openprofiler.ui.screens.InstructionsScreen
import com.openprofiler.ui.screens.PermissionsScreen
import com.openprofiler.ui.screens.ResultsScreen
import com.openprofiler.ui.screens.SettingsScreen
import com.openprofiler.ui.screens.SplashScreen

/**
 * Root navigation composable defining the complete 9-screen navigation graph.
 * Phase 2: CameraRepository injected into CameraScreen for live CameraX preview.
 */
@Composable
fun AppNavigation(
    cameraRepository: CameraRepository? = null,
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Route.Splash,
    ) {
        composable<Route.Splash> {
            SplashScreen(
                onContinue = { navController.navigate(Route.Instructions) },
            )
        }

        composable<Route.Instructions> {
            InstructionsScreen(
                onNext = { navController.navigate(Route.Permissions) },
                onSettings = { navController.navigate(Route.Settings) },
            )
        }

        composable<Route.Permissions> {
            PermissionsScreen(
                onGrantPermission = { navController.navigate(Route.Camera) },
            )
        }

        composable<Route.Camera> {
            CameraScreen(
                cameraRepository = cameraRepository,
                onStartCalibration = { navController.navigate(Route.Calibration) },
                onSettings = { navController.navigate(Route.Settings) },
            )
        }

        composable<Route.Calibration> {
            CalibrationScreen(
                onFinishCalibration = { navController.navigate(Route.Results) },
                onCancel = { navController.popBackStack() },
            )
        }

        composable<Route.Results> {
            ResultsScreen(
                onDone = {
                    navController.popBackStack(Route.Splash, inclusive = false)
                },
                onDeveloper = { navController.navigate(Route.Developer) },
            )
        }

        composable<Route.Settings> {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onAbout = { navController.navigate(Route.About) },
            )
        }

        composable<Route.About> {
            AboutScreen(
                onBack = { navController.popBackStack() },
            )
        }

        composable<Route.Developer> {
            DeveloperScreen(
                onBack = { navController.popBackStack() },
            )
        }
    }
}
