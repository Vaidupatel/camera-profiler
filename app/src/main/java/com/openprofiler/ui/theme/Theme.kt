package com.openprofiler.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = ProfilerBlue,
    onPrimary = ProfilerOnPrimary,
    surface = ProfilerSurface,
    onSurface = ProfilerOnSurface,
    error = ProfilerError,
)

private val DarkColorScheme = darkColorScheme(
    primary = ProfilerBlueLight,
    onPrimary = ProfilerOnPrimary,
    surface = ProfilerSurfaceDark,
    onSurface = ProfilerOnSurfaceDark,
    error = ProfilerError,
)

/**
 * Camera Profiler application theme.
 */
@Composable
fun CameraProfilerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = CameraProfilerTypography,
        content = content,
    )
}
